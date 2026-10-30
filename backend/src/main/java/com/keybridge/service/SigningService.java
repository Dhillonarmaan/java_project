package com.keybridge.service;

import com.keybridge.dto.sign.SignResponse;
import com.keybridge.dto.sign.SignedDocumentResponse;
import com.keybridge.entity.Certificate;
import com.keybridge.entity.SignedDocument;
import com.keybridge.entity.User;
import com.keybridge.enums.CertStatus;
import com.keybridge.enums.Role;
import com.keybridge.exception.BadRequestException;
import com.keybridge.exception.ForbiddenException;
import com.keybridge.exception.NotFoundException;
import com.keybridge.repository.CertificateRepository;
import com.keybridge.repository.SignedDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.List;

@Service
public class SigningService {

    private final CertificateRepository certificateRepository;
    private final SignedDocumentRepository signedDocumentRepository;
    private final CryptoService cryptoService;
    private final TestCaService testCaService;
    private final String dir;
    private final String ksPass;

    public SigningService(
            CertificateRepository certificateRepository,
            SignedDocumentRepository signedDocumentRepository,
            CryptoService cryptoService,
            TestCaService testCaService,
            @Value("${keybridge.keystore.dir:./keystore-data}") String dir,
            @Value("${keybridge.keystore.password}") String ksPass
    ) {
        this.certificateRepository = certificateRepository;
        this.signedDocumentRepository = signedDocumentRepository;
        this.cryptoService = cryptoService;
        this.testCaService = testCaService;
        this.dir = dir;
        this.ksPass = ksPass;
    }

    @Transactional
    public SignResponse sign(Long certId, MultipartFile file, User current) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File to sign cannot be empty");
        }

        Certificate cert = certificateRepository.findById(certId)
                .orElseThrow(() -> new NotFoundException("Certificate not found with ID: " + certId));

        if (!cert.getOwner().getId().equals(current.getId()) && current.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Not your certificate");
        }

        if (cert.getStatus() == CertStatus.REVOKED || cert.getStatus() == CertStatus.EXPIRED) {
            throw new BadRequestException("Certificate is " + cert.getStatus());
        }

        if (cert.getKeystoreFile() == null) {
            throw new BadRequestException("No private key available for this certificate");
        }

        byte[] data = file.getBytes();
        KeyStore ks = testCaService.load(Path.of(dir, cert.getKeystoreFile()));
        PrivateKey key = (PrivateKey) ks.getKey("user", ksPass.toCharArray());

        if (key == null) {
            throw new BadRequestException("Private key alias 'user' not found in keystore");
        }

        byte[] sig = cryptoService.sign(data, key);
        SignedDocument d = new SignedDocument();
        d.setUser(current);
        d.setCert(cert);
        d.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.bin");
        d.setDocHash(cryptoService.sha256Hex(data));
        d.setSignatureB64(Base64.getEncoder().encodeToString(sig));
        d.setAlgorithm("SHA256withRSA");
        signedDocumentRepository.save(d);

        return new SignResponse(d.getId(), d.getDocHash(), d.getSignatureB64(), cert.getSha256Fingerprint(), d.getFileName());
    }

    @Transactional(readOnly = true)
    public List<SignedDocumentResponse> listSignedDocuments(User current) {
        List<SignedDocument> list = (current.getRole() == Role.USER)
                ? signedDocumentRepository.findByUserIdOrderBySignedAtDesc(current.getId())
                : signedDocumentRepository.findAllByOrderBySignedAtDesc();

        return list.stream()
                .map(SignedDocumentResponse::fromEntity)
                .toList();
    }
}
