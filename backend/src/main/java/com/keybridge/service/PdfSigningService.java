package com.keybridge.service;

import com.keybridge.entity.Certificate;
import com.keybridge.entity.SignedDocument;
import com.keybridge.entity.User;
import com.keybridge.exception.BadRequestException;
import com.keybridge.repository.SignedDocumentRepository;
import com.keybridge.signing.PdfSigner;
import com.keybridge.signing.SigningPin;
import com.keybridge.signing.SigningProvider;
import com.keybridge.signing.SigningProviderRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Signs PDFs with an embedded signature. Uses the same ownership and status
 * rules as {@link SigningService} and the same signing providers, so .p12 and
 * PKCS#11 keys both work and the key never leaves its provider.
 */
@Service
public class PdfSigningService {

    public static final String ALGORITHM = "PDF CMS SHA256withRSA";

    private final SigningService signingService;
    private final SigningProviderRegistry signingProviders;
    private final SignedDocumentRepository signedDocumentRepository;
    private final CertificateParserService parserService;
    private final TestCaService testCaService;
    private final CryptoService cryptoService;

    public PdfSigningService(
            SigningService signingService,
            SigningProviderRegistry signingProviders,
            SignedDocumentRepository signedDocumentRepository,
            CertificateParserService parserService,
            TestCaService testCaService,
            CryptoService cryptoService
    ) {
        this.signingService = signingService;
        this.signingProviders = signingProviders;
        this.signedDocumentRepository = signedDocumentRepository;
        this.parserService = parserService;
        this.testCaService = testCaService;
        this.cryptoService = cryptoService;
    }

    public record SignedPdf(String fileName, byte[] content) {}

    @Transactional
    public SignedPdf sign(Long certId, MultipartFile file, User current, SigningPin pin) throws Exception {
        try (pin) {
            if (file == null || file.isEmpty()) {
                throw new BadRequestException("PDF to sign cannot be empty");
            }
            Certificate cert = signingService.usableCertificate(certId, current);

            X509Certificate signer = parserService.parse(cert.getPem().getBytes(StandardCharsets.UTF_8));
            X509Certificate[] chain = {signer, testCaService.rootCert()};
            SigningProvider provider = signingProviders.forCertificate(cert);

            byte[] signed = PdfSigner.sign(file.getBytes(), chain, signer.getSubjectX500Principal().getName(),
                    toSign -> provider.sign(cert, toSign, pin));

            SignedDocument d = new SignedDocument();
            d.setUser(current);
            d.setCert(cert);
            d.setFileName(signedFileName(file.getOriginalFilename()));
            d.setDocHash(cryptoService.sha256Hex(signed));
            d.setSignatureB64(Base64.getEncoder().encodeToString(PdfSigner.lastSignature(signed).cms()));
            d.setAlgorithm(ALGORITHM);
            signedDocumentRepository.save(d);

            return new SignedPdf(d.getFileName(), signed);
        }
    }

    /** "report.pdf" -> "report-signed.pdf", keeping only characters safe in a Content-Disposition header. */
    static String signedFileName(String original) {
        String base = original == null || original.isBlank() ? "document" : original;
        base = base.replaceAll("(?i)\\.pdf$", "").replaceAll("[^A-Za-z0-9._-]", "_");
        return base + "-signed.pdf";
    }
}
