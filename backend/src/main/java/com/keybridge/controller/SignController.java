package com.keybridge.controller;

import com.keybridge.audit.Audited;
import com.keybridge.dto.sign.SignResponse;
import com.keybridge.dto.sign.SignedDocumentResponse;
import com.keybridge.entity.User;
import com.keybridge.exception.BadRequestException;
import com.keybridge.service.AuthService;
import com.keybridge.service.PdfSigningService;
import com.keybridge.service.SigningService;
import com.keybridge.signing.SigningPin;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SignController {

    private final SigningService signingService;
    private final PdfSigningService pdfSigningService;
    private final AuthService authService;

    public SignController(SigningService signingService, PdfSigningService pdfSigningService, AuthService authService) {
        this.signingService = signingService;
        this.pdfSigningService = pdfSigningService;
        this.authService = authService;
    }

    @PostMapping("/sign")
    @Audited("DOCUMENT_SIGN")
    public ResponseEntity<SignResponse> signDocument(
            @RequestParam(value = "certId", required = false) Long certId,
            @RequestParam(value = "certificateId", required = false) Long certificateId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "document", required = false) MultipartFile document,
            // Only PKCS#11 tokens use it. Never logged: SigningPin redacts itself and AuditService skips it.
            @RequestParam(value = "pin", required = false) SigningPin pin
    ) throws Exception {
        Long targetCertId = (certId != null) ? certId : certificateId;
        if (targetCertId == null) {
            throw new BadRequestException("Certificate ID is required (use certId or certificateId)");
        }

        MultipartFile targetFile = (file != null) ? file : document;
        if (targetFile == null || targetFile.isEmpty()) {
            throw new BadRequestException("Document file is required");
        }

        User currentUser = authService.getCurrentUserEntity();
        return ResponseEntity.ok(signingService.sign(targetCertId, targetFile, currentUser, pin));
    }

    /** Signs a PDF with an embedded signature and returns the signed PDF. */
    @PostMapping("/sign/pdf")
    @Audited("PDF_SIGN")
    public ResponseEntity<byte[]> signPdf(
            @RequestParam("certId") Long certId,
            @RequestParam("file") MultipartFile file,
            // Only PKCS#11 tokens use it. Never logged: SigningPin redacts itself and AuditService skips it.
            @RequestParam(value = "pin", required = false) SigningPin pin
    ) throws Exception {
        User currentUser = authService.getCurrentUserEntity();
        PdfSigningService.SignedPdf signed = pdfSigningService.sign(certId, file, currentUser, pin);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + signed.fileName() + "\"")
                .body(signed.content());
    }

    @GetMapping("/signed-documents")
    public ResponseEntity<List<SignedDocumentResponse>> listSignedDocuments() {
        User currentUser = authService.getCurrentUserEntity();
        return ResponseEntity.ok(signingService.listSignedDocuments(currentUser));
    }
}
