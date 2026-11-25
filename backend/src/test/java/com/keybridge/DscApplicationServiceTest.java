package com.keybridge;

import com.keybridge.dto.cert.CertResponse;
import com.keybridge.dto.cert.IssueCertRequest;
import com.keybridge.dto.dsc.CreateDscApplicationRequest;
import com.keybridge.dto.dsc.DscApplicationResponse;
import com.keybridge.dto.dsc.DscRenewalReminder;
import com.keybridge.dto.dsc.UpdateChecklistItemRequest;
import com.keybridge.dto.dsc.UpdateDscStatusRequest;
import com.keybridge.entity.Certificate;
import com.keybridge.entity.DscApplication;
import com.keybridge.entity.DscChecklistItem;
import com.keybridge.entity.DscStatusEvent;
import com.keybridge.entity.User;
import com.keybridge.enums.CertStatus;
import com.keybridge.enums.DocumentStatus;
import com.keybridge.enums.DscApplicationStatus;
import com.keybridge.enums.Role;
import com.keybridge.exception.BadRequestException;
import com.keybridge.exception.ForbiddenException;
import com.keybridge.exception.InvalidStateTransitionException;
import com.keybridge.repository.CertificateRepository;
import com.keybridge.repository.DscApplicationRepository;
import com.keybridge.service.CertificateService;
import com.keybridge.service.DscApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.keybridge.enums.DscApplicationStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DscApplicationServiceTest {

    private DscApplicationRepository applicationRepository;
    private CertificateRepository certificateRepository;
    private CertificateService certificateService;
    private DscApplicationService service;

    private User admin;
    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(DscApplicationRepository.class);
        certificateRepository = mock(CertificateRepository.class);
        certificateService = mock(CertificateService.class);
        service = new DscApplicationService(applicationRepository, certificateRepository, certificateService, 30);

        admin = new User("Admin", "admin@example.com", "pass", Role.ADMIN);
        admin.setId(1L);
        user = new User("User", "user@example.com", "pass", Role.USER);
        user.setId(2L);
        otherUser = new User("Other", "other@example.com", "pass", Role.USER);
        otherUser.setId(3L);
    }

    @Test
    void createStartsSubmittedWithEveryDocumentMissing() {
        DscApplicationResponse res = service.create(
                new CreateDscApplicationRequest("Alice Smith", "alice@example.com", "", "Acme", null, null), user);

        assertEquals(SUBMITTED, res.status());
        assertEquals(365, res.validityDays());
        assertNull(res.phone());
        assertEquals(DscApplicationService.REQUIRED_DOCUMENTS.size(), res.documents().size());
        assertTrue(res.documents().stream().allMatch(d -> d.status() == DocumentStatus.MISSING));
        assertEquals(1, res.events().size());
        assertEquals(SUBMITTED, res.events().get(0).status());
        assertEquals("User", res.events().get(0).actorName());
    }

    @Test
    void walksTheWholeTimelineAndIssuesACertificate() {
        DscApplication app = application(10L, user, SUBMITTED);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(app));
        Certificate cert = certificate(50L, LocalDateTime.now().plusDays(365), CertStatus.VALID);
        when(certificateService.issueTestCertificate(any(IssueCertRequest.class))).thenReturn(CertResponse.fromEntity(cert));
        when(certificateRepository.findById(50L)).thenReturn(Optional.of(cert));

        service.updateStatus(10L, new UpdateDscStatusRequest(VERIFICATION, null), admin);
        for (String doc : DscApplicationService.REQUIRED_DOCUMENTS) {
            service.updateChecklistItem(10L, new UpdateChecklistItemRequest(doc, DocumentStatus.RECEIVED), admin);
        }
        service.updateStatus(10L, new UpdateDscStatusRequest(APPROVED, "Documents verified"), admin);
        DscApplicationResponse res = service.updateStatus(10L, new UpdateDscStatusRequest(ISSUED, null), admin);

        assertEquals(ISSUED, res.status());
        assertEquals(50L, res.certId());
        assertEquals(List.of(SUBMITTED, VERIFICATION, APPROVED, ISSUED),
                res.events().stream().map(DscApplicationResponse.StatusEvent::status).toList());

        ArgumentCaptor<IssueCertRequest> issued = ArgumentCaptor.forClass(IssueCertRequest.class);
        verify(certificateService).issueTestCertificate(issued.capture());
        assertEquals(user.getId(), issued.getValue().userId());
        assertEquals("Alice Smith", issued.getValue().commonName());
        assertEquals(730, issued.getValue().resolvedDays());
        assertNull(issued.getValue().tokenId());
    }

    @Test
    void cannotApproveWhileADocumentIsMissing() {
        DscApplication app = application(10L, user, VERIFICATION);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(app));
        service.updateChecklistItem(10L, new UpdateChecklistItemRequest("Identity proof", DocumentStatus.RECEIVED), admin);

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.updateStatus(10L, new UpdateDscStatusRequest(APPROVED, null), admin));
        assertTrue(e.getMessage().contains("Address proof"));
        assertEquals(VERIFICATION, app.getStatus());
    }

    @Test
    void illegalTransitionsAreRejected() {
        DscApplication app = application(10L, user, SUBMITTED);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(app));

        assertThrows(InvalidStateTransitionException.class,
                () -> service.updateStatus(10L, new UpdateDscStatusRequest(ISSUED, null), admin));
        assertThrows(InvalidStateTransitionException.class,
                () -> service.updateStatus(10L, new UpdateDscStatusRequest(APPROVED, null), admin));

        app.setStatus(REJECTED);
        assertThrows(InvalidStateTransitionException.class,
                () -> service.updateStatus(10L, new UpdateDscStatusRequest(VERIFICATION, null), admin));
        verifyNoInteractions(certificateService);
    }

    @Test
    void rejectingNeedsAReason() {
        DscApplication app = application(10L, user, VERIFICATION);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(app));

        assertThrows(BadRequestException.class,
                () -> service.updateStatus(10L, new UpdateDscStatusRequest(REJECTED, "  "), admin));
        DscApplicationResponse res = service.updateStatus(10L, new UpdateDscStatusRequest(REJECTED, "Photo unreadable"), admin);
        assertEquals(REJECTED, res.status());
        assertEquals("Photo unreadable", res.events().get(res.events().size() - 1).note());
    }

    @Test
    void checklistIsFrozenAfterApproval() {
        DscApplication app = application(10L, user, APPROVED);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(app));

        assertThrows(InvalidStateTransitionException.class, () -> service.updateChecklistItem(
                10L, new UpdateChecklistItemRequest("Photograph", DocumentStatus.MISSING), admin));
    }

    @Test
    void onlyAdminsReview() {
        DscApplication app = application(10L, user, SUBMITTED);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(app));

        assertThrows(ForbiddenException.class,
                () -> service.updateStatus(10L, new UpdateDscStatusRequest(VERIFICATION, null), user));
        assertThrows(ForbiddenException.class, () -> service.updateChecklistItem(
                10L, new UpdateChecklistItemRequest("Photograph", DocumentStatus.RECEIVED), user));
    }

    @Test
    void usersSeeOnlyTheirOwnApplications() {
        DscApplication app = application(10L, user, SUBMITTED);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(app));

        assertEquals(10L, service.get(10L, user).id());
        assertEquals(10L, service.get(10L, admin).id());
        assertThrows(ForbiddenException.class, () -> service.get(10L, otherUser));

        service.list(user);
        verify(applicationRepository).findByApplicantIdOrderByCreatedAtDesc(2L);
        service.list(admin);
        verify(applicationRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void remindsAboutCertificatesExpiringWithinTheWarningWindow() {
        DscApplication soon = application(10L, user, ISSUED);
        soon.setCertificate(certificate(50L, LocalDateTime.now().plusDays(10), CertStatus.EXPIRING));
        DscApplication later = application(11L, user, ISSUED);
        later.setCertificate(certificate(51L, LocalDateTime.now().plusDays(200), CertStatus.VALID));
        DscApplication revoked = application(12L, user, ISSUED);
        revoked.setCertificate(certificate(52L, LocalDateTime.now().plusDays(5), CertStatus.REVOKED));
        DscApplication others = application(13L, otherUser, ISSUED);
        others.setCertificate(certificate(53L, LocalDateTime.now().minusDays(2), CertStatus.EXPIRED));
        when(applicationRepository.findByStatus(ISSUED)).thenReturn(List.of(soon, later, revoked, others));

        List<DscRenewalReminder> forUser = service.renewalReminders(user);
        assertEquals(List.of(10L), forUser.stream().map(DscRenewalReminder::applicationId).toList());
        assertTrue(forUser.get(0).daysLeft() >= 9 && forUser.get(0).daysLeft() <= 10);

        // Admins see everyone's, ordered by expiry: the expired one first.
        assertEquals(List.of(13L, 10L),
                service.renewalReminders(admin).stream().map(DscRenewalReminder::applicationId).toList());
    }

    @Test
    void renewalInProgressSilencesTheReminderAndBlocksASecondRenewal() {
        DscApplication issued = application(10L, user, ISSUED);
        issued.setCertificate(certificate(50L, LocalDateTime.now().plusDays(10), CertStatus.EXPIRING));
        DscApplication renewal = application(20L, user, SUBMITTED);
        renewal.setRenewalOfId(10L);
        when(applicationRepository.findByStatus(ISSUED)).thenReturn(List.of(issued));
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(issued));
        when(applicationRepository.findByRenewalOfIdIn(List.of(10L))).thenReturn(List.of(renewal));

        assertTrue(service.renewalReminders(user).isEmpty());
        assertThrows(BadRequestException.class, () -> service.create(
                new CreateDscApplicationRequest("Alice Smith", "alice@example.com", null, null, 365, 10L), user));

        renewal.setStatus(REJECTED);
        assertEquals(1, service.renewalReminders(user).size());
        assertEquals(10L, service.create(
                new CreateDscApplicationRequest("Alice Smith", "alice@example.com", null, null, 365, 10L), user).renewalOfId());
    }

    @Test
    void cannotRenewSomeoneElsesOrAnUnissuedApplication() {
        DscApplication pending = application(10L, user, APPROVED);
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(pending));
        CreateDscApplicationRequest renew = new CreateDscApplicationRequest("Alice Smith", "alice@example.com", null, null, 365, 10L);

        assertThrows(ForbiddenException.class, () -> service.create(renew, otherUser));
        assertThrows(BadRequestException.class, () -> service.create(renew, user));
    }

    private DscApplication application(Long id, User applicant, DscApplicationStatus status) {
        DscApplication app = new DscApplication();
        app.setId(id);
        app.setApplicant(applicant);
        app.setFullName("Alice Smith");
        app.setEmail("alice@example.com");
        app.setValidityDays(730);
        app.setStatus(status);
        for (String doc : DscApplicationService.REQUIRED_DOCUMENTS) {
            app.getDocuments().add(new DscChecklistItem(doc, DocumentStatus.MISSING));
        }
        app.getEvents().add(new DscStatusEvent(SUBMITTED, null, applicant.getName(), LocalDateTime.now()));
        return app;
    }

    private Certificate certificate(Long id, LocalDateTime validTo, CertStatus status) {
        Certificate cert = new Certificate();
        cert.setId(id);
        cert.setOwner(user);
        cert.setSerialHex("ab" + id);
        cert.setValidFrom(LocalDateTime.now().minusDays(1));
        cert.setValidTo(validTo);
        cert.setStatus(status);
        return cert;
    }
}
