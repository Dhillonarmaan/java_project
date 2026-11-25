package com.keybridge.signing;

import com.keybridge.dto.verify.VerificationStepDto;
import com.keybridge.dto.verify.VerifyResponse;
import com.keybridge.entity.Certificate;
import com.keybridge.entity.SignedDocument;
import com.keybridge.entity.User;
import com.keybridge.enums.CertStatus;
import com.keybridge.enums.Role;
import com.keybridge.enums.VerifyResult;
import com.keybridge.exception.BadRequestException;
import com.keybridge.exception.ForbiddenException;
import com.keybridge.repository.CertificateRepository;
import com.keybridge.repository.SignedDocumentRepository;
import com.keybridge.repository.VerificationLogRepository;
import com.keybridge.service.CertificateParserService;
import com.keybridge.service.CryptoService;
import com.keybridge.service.PdfSigningService;
import com.keybridge.service.SigningService;
import com.keybridge.service.TestCaService;
import com.keybridge.service.VerificationService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Signs PDFs through PdfSigningService with a .p12 key and checks them with VerificationService.verifyPdf. */
class PdfSigningTest {

    @TempDir
    Path dir;

    private final CryptoService crypto = new CryptoService();
    private TestCaService ca;
    private CertificateRepository certs;
    private SignedDocumentRepository signedDocs;
    private PdfSigningService pdfSigning;
    private VerificationService verification;

    private User alice;
    private User bob;
    private Certificate aliceCert;

    @BeforeEach
    void setUp() throws Exception {
        ca = new TestCaService(dir.toString(), "changeit", "keybridge-root");
        ca.ensureRoot();
        CertificateParserService parser = new CertificateParserService(crypto);
        certs = mock(CertificateRepository.class);
        signedDocs = mock(SignedDocumentRepository.class);
        SigningProviderRegistry registry = new SigningProviderRegistry(
                List.of(new P12SigningProvider(ca, crypto, dir.toString(), "changeit")));
        SigningService signing = new SigningService(certs, signedDocs, crypto, registry);
        pdfSigning = new PdfSigningService(signing, registry, signedDocs, parser, ca, crypto);
        verification = new VerificationService(crypto, parser, ca, certs, mock(VerificationLogRepository.class));

        alice = new User("Alice", "alice@example.com", "pass", Role.USER);
        alice.setId(1L);
        bob = new User("Bob", "bob@example.com", "pass", Role.USER);
        bob.setId(2L);

        TestCaService.IssuedCert issued = ca.issue("Alice Smith", "alice@example.com", 30);
        aliceCert = new Certificate();
        aliceCert.setId(7L);
        aliceCert.setOwner(alice);
        aliceCert.setPem(parser.toPem(issued.cert()));
        aliceCert.setKeystoreFile(issued.keystoreFile());
        aliceCert.setSha256Fingerprint(crypto.sha256Hex(issued.cert().getEncoded()));
        aliceCert.setStatus(CertStatus.VALID);
        when(certs.findById(7L)).thenReturn(Optional.of(aliceCert));
        when(certs.findBySha256Fingerprint(aliceCert.getSha256Fingerprint())).thenReturn(Optional.of(aliceCert));
    }

    @Test
    void signedPdfVerifiesAndCarriesTheSignerChain() throws Exception {
        PdfSigningService.SignedPdf signed = pdfSigning.sign(7L, pdf("contract.pdf"), alice, null);

        assertEquals("contract-signed.pdf", signed.fileName());
        try (PDDocument doc = Loader.loadPDF(signed.content())) {
            PDSignature sig = doc.getLastSignatureDictionary();
            assertNotNull(sig);
            assertEquals(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED.getName(), sig.getSubFilter());
            assertTrue(sig.getName().contains("CN=Alice Smith"));
        }

        VerifyResponse res = verifyPdf(signed.content());
        assertEquals(VerifyResult.VALID, res.result(), res.reason());
        assertEquals(List.of("PARSE", "CHAIN", "REVOCATION", "VALIDITY", "SIGNATURE"),
                res.steps().stream().map(VerificationStepDto::name).toList());
        assertTrue(res.steps().stream().allMatch(VerificationStepDto::passed));

        ArgumentCaptor<SignedDocument> saved = ArgumentCaptor.forClass(SignedDocument.class);
        verify(signedDocs).save(saved.capture());
        assertEquals(PdfSigningService.ALGORITHM, saved.getValue().getAlgorithm());
        assertEquals(crypto.sha256Hex(signed.content()), saved.getValue().getDocHash());
        assertSame(aliceCert, saved.getValue().getCert());
    }

    @Test
    void changingSignedContentIsTampered() throws Exception {
        byte[] signed = pdfSigning.sign(7L, pdf("contract.pdf"), alice, null).content();
        byte[] altered = replace(signed, "Original contract", "Modified contract");

        VerifyResponse res = verifyPdf(altered);
        assertEquals(VerifyResult.TAMPERED, res.result());
        assertEquals("SIGNATURE", res.steps().get(res.steps().size() - 1).name());
    }

    @Test
    void contentAddedAfterSigningIsTampered() throws Exception {
        byte[] signed = pdfSigning.sign(7L, pdf("contract.pdf"), alice, null).content();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = Loader.loadPDF(signed)) {
            doc.getDocumentInformation().setTitle("Edited later");
            doc.getDocumentInformation().getCOSObject().setNeedToBeUpdated(true);
            doc.saveIncremental(out);
        }

        VerifyResponse res = verifyPdf(out.toByteArray());
        assertEquals(VerifyResult.TAMPERED, res.result());
        assertEquals("PDF was changed after it was signed", res.reason());
    }

    @Test
    void revokedSignerIsReportedRevoked() throws Exception {
        byte[] signed = pdfSigning.sign(7L, pdf("contract.pdf"), alice, null).content();
        aliceCert.setStatus(CertStatus.REVOKED);

        assertEquals(VerifyResult.REVOKED, verifyPdf(signed).result());
    }

    @Test
    void signerFromAnotherCaIsUntrusted() throws Exception {
        byte[] signed = pdfSigning.sign(7L, pdf("contract.pdf"), alice, null).content();
        // A fresh root in another directory no longer trusts Alice's certificate.
        TestCaService otherCa = new TestCaService(dir.resolve("other").toString(), "changeit", "keybridge-root");
        otherCa.ensureRoot();
        VerificationService otherVerifier = new VerificationService(
                crypto, new CertificateParserService(crypto), otherCa, certs, mock(VerificationLogRepository.class));

        VerifyResponse res = otherVerifier.verifyPdf(new MockMultipartFile("file", "x.pdf", "application/pdf", signed), alice);
        assertEquals(VerifyResult.UNTRUSTED, res.result());
    }

    @Test
    void unsignedPdfIsAnError() throws Exception {
        VerifyResponse res = verifyPdf(pdf("plain.pdf").getBytes());
        assertEquals(VerifyResult.ERROR, res.result());
        assertEquals("PDF is not signed", res.reason());
    }

    @Test
    void onlyTheOwnerOrAnAdminCanSign() throws Exception {
        assertThrows(ForbiddenException.class, () -> pdfSigning.sign(7L, pdf("contract.pdf"), bob, null));

        User admin = new User("Admin", "admin@example.com", "pass", Role.ADMIN);
        admin.setId(3L);
        assertEquals(VerifyResult.VALID, verifyPdf(pdfSigning.sign(7L, pdf("contract.pdf"), admin, null).content()).result());
    }

    @Test
    void revokedCertificateCannotSign() {
        aliceCert.setStatus(CertStatus.REVOKED);
        assertThrows(BadRequestException.class, () -> pdfSigning.sign(7L, pdf("contract.pdf"), alice, null));
    }

    @Test
    void nonPdfIsRejected() {
        MockMultipartFile text = new MockMultipartFile("file", "notes.pdf", "application/pdf",
                "just text".getBytes(StandardCharsets.UTF_8));
        assertThrows(BadRequestException.class, () -> pdfSigning.sign(7L, text, alice, null));
        verify(signedDocs, never()).save(any());
    }

    @Test
    void pinIsClearedAfterSigning() throws Exception {
        SigningPin pin = new SigningPin("123456");
        pdfSigning.sign(7L, pdf("contract.pdf"), alice, pin);
        assertArrayEquals(new char[6], pin.chars());
    }

    private VerifyResponse verifyPdf(byte[] content) {
        return verification.verifyPdf(new MockMultipartFile("file", "signed.pdf", "application/pdf", content), alice);
    }

    /** One-page PDF, saved uncompressed so tests can find and change text in it. */
    private static MockMultipartFile pdf(String name) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.getDocumentInformation().setTitle("Original contract");
            doc.save(out, CompressParameters.NO_COMPRESSION);
        }
        return new MockMultipartFile("file", name, "application/pdf", out.toByteArray());
    }

    private static byte[] replace(byte[] data, String from, String to) {
        byte[] f = from.getBytes(StandardCharsets.ISO_8859_1);
        byte[] t = to.getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(f.length, t.length);
        outer:
        for (int i = 0; i <= data.length - f.length; i++) {
            for (int j = 0; j < f.length; j++) {
                if (data[i + j] != f[j]) continue outer;
            }
            byte[] copy = data.clone();
            System.arraycopy(t, 0, copy, i, t.length);
            return copy;
        }
        throw new AssertionError("'" + from + "' not found in PDF");
    }
}
