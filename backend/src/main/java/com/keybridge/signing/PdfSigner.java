package com.keybridge.signing;

import com.keybridge.exception.BadRequestException;
import com.keybridge.exception.CryptoException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.function.UnaryOperator;

/**
 * Embeds a CMS (adbe.pkcs7.detached) signature in a PDF with PDFBox.
 *
 * The private key is never seen here: {@code rawSigner} gets the DER-encoded
 * CMS signed attributes and must return a SHA256withRSA signature over them,
 * which is exactly what {@link SigningProvider#sign} does for a .p12 or a
 * PKCS#11 key.
 */
public final class PdfSigner {

    private PdfSigner() {
    }

    /**
     * @param chain signer certificate first, then its issuers; all are embedded
     * @return the PDF with the signature appended as an incremental update
     */
    public static byte[] sign(byte[] pdf, X509Certificate[] chain, String signerName, UnaryOperator<byte[]> rawSigner) {
        try (PDDocument doc = load(pdf)) {
            if (doc.isEncrypted()) {
                throw new BadRequestException("Encrypted PDFs cannot be signed");
            }

            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName(signerName);
            signature.setReason("Signed with KeyBridge");
            signature.setSignDate(Calendar.getInstance());

            doc.addSignature(signature, cmsSigner(chain, rawSigner));
            ByteArrayOutputStream out = new ByteArrayOutputStream(pdf.length + 16_384);
            doc.saveIncremental(out);
            return out.toByteArray();
        } catch (BadRequestException | CryptoException e) {
            throw e;
        } catch (IOException e) {
            // Errors from the signing provider (wrong PIN, missing key) arrive wrapped; rethrow them as they were.
            for (Throwable t = e.getCause(); t != null; t = t.getCause()) {
                if (t instanceof BadRequestException || t instanceof CryptoException) {
                    throw (RuntimeException) t;
                }
            }
            throw new CryptoException("Failed to sign PDF: " + e.getMessage(), e);
        }
    }

    /**
     * The last signature in a PDF.
     *
     * @param cms           the embedded CMS SignedData
     * @param signedContent the bytes the signature covers (the file minus the signature value)
     * @param coversWholeFile false when bytes were appended after signing, e.g. a later edit
     */
    public record EmbeddedSignature(byte[] cms, byte[] signedContent, boolean coversWholeFile) {}

    /** @return the last signature, or null when the PDF is not signed */
    public static EmbeddedSignature lastSignature(byte[] pdf) {
        try (PDDocument doc = load(pdf)) {
            PDSignature sig = doc.getLastSignatureDictionary();
            if (sig == null) {
                return null;
            }
            int[] range = sig.getByteRange();
            boolean whole = range != null && range.length == 4 && range[0] == 0
                    && (long) range[2] + range[3] == pdf.length;
            return new EmbeddedSignature(sig.getContents(pdf), sig.getSignedContent(pdf), whole);
        } catch (IOException e) {
            throw new BadRequestException("Could not read the PDF signature: " + e.getMessage());
        }
    }

    static PDDocument load(byte[] pdf) {
        try {
            return Loader.loadPDF(pdf);
        } catch (IOException e) {
            throw new BadRequestException("File is not a readable PDF");
        }
    }

    private static SignatureInterface cmsSigner(X509Certificate[] chain, UnaryOperator<byte[]> rawSigner) {
        return content -> {
            try {
                CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
                gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
                        .build(new DelegatingContentSigner(rawSigner), chain[0]));
                gen.addCertificates(new JcaCertStore(Arrays.asList(chain)));
                return gen.generate(new CMSProcessableByteArray(content.readAllBytes()), false).getEncoded();
            } catch (RuntimeException e) {
                throw new IOException(e.getMessage(), e);
            } catch (Exception e) {
                throw new IOException("Failed to build CMS signature: " + e.getMessage(), e);
            }
        };
    }

    /** Collects what Bouncy Castle wants signed and hands it to the signing provider. */
    private static final class DelegatingContentSigner implements ContentSigner {

        private static final AlgorithmIdentifier SHA256_WITH_RSA =
                new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withRSA");

        private final UnaryOperator<byte[]> rawSigner;
        private final ByteArrayOutputStream toSign = new ByteArrayOutputStream();

        DelegatingContentSigner(UnaryOperator<byte[]> rawSigner) {
            this.rawSigner = rawSigner;
        }

        @Override
        public AlgorithmIdentifier getAlgorithmIdentifier() {
            return SHA256_WITH_RSA;
        }

        @Override
        public OutputStream getOutputStream() {
            return toSign;
        }

        @Override
        public byte[] getSignature() {
            return rawSigner.apply(toSign.toByteArray());
        }
    }
}
