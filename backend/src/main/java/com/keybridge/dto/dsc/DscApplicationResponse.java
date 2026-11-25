package com.keybridge.dto.dsc;

import com.keybridge.entity.Certificate;
import com.keybridge.entity.DscApplication;
import com.keybridge.enums.CertStatus;
import com.keybridge.enums.DocumentStatus;
import com.keybridge.enums.DscApplicationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record DscApplicationResponse(
    Long id,
    Long applicantId,
    String applicantName,
    String fullName,
    String email,
    String phone,
    String organization,
    int validityDays,
    DscApplicationStatus status,
    Long renewalOfId,
    Long certId,
    String certSerialHex,
    CertStatus certStatus,
    LocalDateTime certValidTo,
    List<ChecklistItem> documents,
    List<StatusEvent> events,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public record ChecklistItem(String document, DocumentStatus status) {}

    public record StatusEvent(DscApplicationStatus status, String note, String actorName, LocalDateTime at) {}

    public static DscApplicationResponse fromEntity(DscApplication app) {
        if (app == null) return null;
        Certificate cert = app.getCertificate();
        return new DscApplicationResponse(
            app.getId(),
            app.getApplicant() != null ? app.getApplicant().getId() : null,
            app.getApplicant() != null ? app.getApplicant().getName() : null,
            app.getFullName(),
            app.getEmail(),
            app.getPhone(),
            app.getOrganization(),
            app.getValidityDays(),
            app.getStatus(),
            app.getRenewalOfId(),
            cert != null ? cert.getId() : null,
            cert != null ? cert.getSerialHex() : null,
            cert != null ? cert.getStatus() : null,
            cert != null ? cert.getValidTo() : null,
            app.getDocuments().stream().map(d -> new ChecklistItem(d.getDocument(), d.getStatus())).toList(),
            app.getEvents().stream().map(e -> new StatusEvent(e.getStatus(), e.getNote(), e.getActorName(), e.getAt())).toList(),
            app.getCreatedAt(),
            app.getUpdatedAt()
        );
    }
}
