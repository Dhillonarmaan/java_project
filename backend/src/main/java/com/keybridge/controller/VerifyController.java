package com.keybridge.controller;

import com.keybridge.audit.Audited;
import com.keybridge.dto.verify.VerificationLogResponse;
import com.keybridge.dto.verify.VerifyResponse;
import com.keybridge.entity.User;
import com.keybridge.exception.BadRequestException;
import com.keybridge.service.AuthService;
import com.keybridge.service.VerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class VerifyController {

    private final VerificationService verificationService;
    private final AuthService authService;

    public VerifyController(VerificationService verificationService, AuthService authService) {
        this.verificationService = verificationService;
        this.authService = authService;
    }

    @PostMapping("/verify")
    @Audited("DOCUMENT_VERIFY")
    public ResponseEntity<VerifyResponse> verify(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "document", required = false) MultipartFile document,
            @RequestParam(value = "signature", required = false) Object signatureParam,
            @RequestParam(value = "signatureFile", required = false) MultipartFile signatureFile,
            @RequestParam(value = "certId", required = false) Long certId,
            @RequestParam(value = "certificateId", required = false) Long certificateId,
            @RequestParam(value = "certFile", required = false) MultipartFile certFile,
            @RequestParam(value = "certificate", required = false) MultipartFile certificate
    ) throws IOException {
        User currentUser = authService.getCurrentUserEntity();

        MultipartFile targetFile = (file != null) ? file : document;
        if (targetFile == null || targetFile.isEmpty()) {
            throw new BadRequestException("Document file is required (use 'file' or 'document')");
        }

        String sig = null;
        if (signatureParam instanceof MultipartFile mf && !mf.isEmpty()) {
            sig = new String(mf.getBytes(), StandardCharsets.UTF_8).trim();
        } else if (signatureParam instanceof String s && !s.isBlank()) {
            sig = s.trim();
        } else if (signatureFile != null && !signatureFile.isEmpty()) {
            sig = new String(signatureFile.getBytes(), StandardCharsets.UTF_8).trim();
        }

        if (sig == null || sig.isBlank()) {
            throw new BadRequestException("Signature is required (provide Base64 string or file)");
        }

        MultipartFile targetCertFile = (certFile != null) ? certFile : certificate;
        byte[] certBytes = null;
        if (targetCertFile != null && !targetCertFile.isEmpty()) {
            certBytes = targetCertFile.getBytes();
        }

        Long targetCertId = (certId != null) ? certId : certificateId;

        return ResponseEntity.ok(verificationService.verify(targetFile, sig, certBytes, targetCertId, currentUser));
    }

    /** Verifies the signature embedded in a PDF; the signer certificate is read from the signature. */
    @PostMapping("/verify/pdf")
    @Audited("PDF_VERIFY")
    public ResponseEntity<VerifyResponse> verifyPdf(@RequestParam("file") MultipartFile file) {
        User currentUser = authService.getCurrentUserEntity();
        return ResponseEntity.ok(verificationService.verifyPdf(file, currentUser));
    }

    @GetMapping("/verifications")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<List<VerificationLogResponse>> listVerifications() {
        User currentUser = authService.getCurrentUserEntity();
        return ResponseEntity.ok(verificationService.listVerifications(currentUser));
    }
}
