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
import com.keybridge.signing.SigningPin;
import com.keybridge.signing.SigningProviderRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;

@Service
public class SigningService {

    private final CertificateRepository certificateRepository;
    private final SignedDocumentRepository signedDocumentRepository;
    private final CryptoService cryptoService;
    private final SigningProviderRegistry signingProviders;

    public SigningService(
            CertificateRepository certificateRepository,
            SignedDocumentRepository signedDocumentRepository,
            CryptoService cryptoService,
            SigningProviderRegistry signingProviders
    ) {
        this.certificateRepository = certificateRepository;
        this.signedDocumentRepository = signedDocumentRepository;
        this.cryptoService = cryptoService;
        this.signingProviders = signingProviders;
    }

    @Transactional
    public SignResponse sign(Long certId, MultipartFile file, User current, SigningPin pin) throws Exception {
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

        byte[] data = file.getBytes();
        byte[] sig;
        try {
            sig = signingProviders.forCertificate(cert).sign(cert, data, pin);
        } finally {
            if (pin != null) {
                pin.close();
            }
        }
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
