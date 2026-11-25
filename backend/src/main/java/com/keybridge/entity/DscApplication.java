package com.keybridge.entity;

import com.keybridge.enums.DscApplicationStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An application for a digital signature certificate (DSC). It moves
 * SUBMITTED -> VERIFICATION -> APPROVED -> ISSUED, or to REJECTED; on ISSUED
 * the test CA issues a certificate, which is linked here.
 */
@Entity
@Table(name = "dsc_applications")
public class DscApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 100)
    private String organization;

    @Column(name = "validity_days", nullable = false)
    private int validityDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DscApplicationStatus status = DscApplicationStatus.SUBMITTED;

    /** The issued application this one renews, if any. */
    @Column(name = "renewal_of_id")
    private Long renewalOfId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id")
    private Certificate certificate;

    @ElementCollection
    @CollectionTable(name = "dsc_application_documents", joinColumns = @JoinColumn(name = "application_id"))
    @OrderColumn(name = "position")
    private List<DscChecklistItem> documents = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "dsc_application_events", joinColumns = @JoinColumn(name = "application_id"))
    @OrderColumn(name = "position")
    private List<DscStatusEvent> events = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public DscApplication() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getApplicant() {
        return applicant;
    }

    public void setApplicant(User applicant) {
        this.applicant = applicant;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public int getValidityDays() {
        return validityDays;
    }

    public void setValidityDays(int validityDays) {
        this.validityDays = validityDays;
    }

    public DscApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(DscApplicationStatus status) {
        this.status = status;
    }

    public Long getRenewalOfId() {
        return renewalOfId;
    }

    public void setRenewalOfId(Long renewalOfId) {
        this.renewalOfId = renewalOfId;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
    }

    public List<DscChecklistItem> getDocuments() {
        return documents;
    }

    public List<DscStatusEvent> getEvents() {
        return events;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
