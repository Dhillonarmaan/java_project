package com.keybridge.entity;

import com.keybridge.enums.DocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** One required document of a DSC application and whether it has been received. */
@Embeddable
public class DscChecklistItem {

    @Column(nullable = false, length = 60)
    private String document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.MISSING;

    public DscChecklistItem() {
    }

    public DscChecklistItem(String document, DocumentStatus status) {
        this.document = document;
        this.status = status;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }
}
