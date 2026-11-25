package com.keybridge.service;

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
import com.keybridge.exception.NotFoundException;
import com.keybridge.repository.CertificateRepository;
import com.keybridge.repository.DscApplicationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.keybridge.enums.DscApplicationStatus.*;

@Service
public class DscApplicationService {

    /** Documents every application must have received before it can be approved. */
    public static final List<String> REQUIRED_DOCUMENTS = List.of(
            "Application form",
            "Identity proof",
            "Address proof",
            "Photograph"
    );

    private static final Map<DscApplicationStatus, Set<DscApplicationStatus>> TRANSITIONS = Map.of(
            SUBMITTED, Set.of(VERIFICATION, REJECTED),
            VERIFICATION, Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(ISSUED, REJECTED),
            ISSUED, Set.of(),
            REJECTED, Set.of()
    );

    private final DscApplicationRepository applicationRepository;
    private final CertificateRepository certificateRepository;
    private final CertificateService certificateService;
    private final int warnDays;

    public DscApplicationService(
            DscApplicationRepository applicationRepository,
            CertificateRepository certificateRepository,
            CertificateService certificateService,
            @Value("${keybridge.expiry.warn-days:30}") int warnDays
    ) {
        this.applicationRepository = applicationRepository;
        this.certificateRepository = certificateRepository;
        this.certificateService = certificateService;
        this.warnDays = warnDays;
    }

    @Transactional
    public DscApplicationResponse create(CreateDscApplicationRequest dto, User currentUser) {
        if (dto.renewalOfId() != null) {
            DscApplication original = find(dto.renewalOfId());
            checkCanSee(original, currentUser);
            if (original.getStatus() != ISSUED) {
                throw new BadRequestException("Only an issued DSC can be renewed");
            }
            boolean alreadyRenewing = applicationRepository.findByRenewalOfIdIn(List.of(original.getId())).stream()
                    .anyMatch(a -> a.getStatus() != REJECTED);
            if (alreadyRenewing) {
                throw new BadRequestException("A renewal for application #" + original.getId() + " is already in progress");
            }
        }

        DscApplication app = new DscApplication();
        app.setApplicant(currentUser);
        app.setFullName(dto.fullName().trim());
        app.setEmail(dto.email().trim());
        app.setPhone(blankToNull(dto.phone()));
        app.setOrganization(blankToNull(dto.organization()));
        app.setValidityDays(dto.validityDays() != null ? dto.validityDays() : 365);
        app.setRenewalOfId(dto.renewalOfId());
        app.setStatus(SUBMITTED);
        for (String doc : REQUIRED_DOCUMENTS) {
            app.getDocuments().add(new DscChecklistItem(doc, DocumentStatus.MISSING));
        }
        app.getEvents().add(new DscStatusEvent(
                SUBMITTED,
                dto.renewalOfId() != null ? "Renewal of application #" + dto.renewalOfId() : "Application submitted",
                currentUser.getName(),
                LocalDateTime.now()));

        applicationRepository.save(app);
        return DscApplicationResponse.fromEntity(app);
    }

    @Transactional(readOnly = true)
    public List<DscApplicationResponse> list(User currentUser) {
        List<DscApplication> list = (currentUser.getRole() == Role.USER)
                ? applicationRepository.findByApplicantIdOrderByCreatedAtDesc(currentUser.getId())
                : applicationRepository.findAllByOrderByCreatedAtDesc();
        return list.stream().map(DscApplicationResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public DscApplicationResponse get(Long id, User currentUser) {
        DscApplication app = find(id);
        checkCanSee(app, currentUser);
        return DscApplicationResponse.fromEntity(app);
    }

    @Transactional
    public DscApplicationResponse updateChecklistItem(Long id, UpdateChecklistItemRequest dto, User adminUser) {
        requireAdmin(adminUser);
        DscApplication app = find(id);
        if (app.getStatus() != SUBMITTED && app.getStatus() != VERIFICATION) {
            throw new InvalidStateTransitionException("Documents cannot be changed once an application is " + app.getStatus());
        }
        DscChecklistItem item = app.getDocuments().stream()
                .filter(d -> d.getDocument().equalsIgnoreCase(dto.document().trim()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No document '" + dto.document() + "' on application #" + id));
        item.setStatus(dto.status());

        applicationRepository.save(app);
        return DscApplicationResponse.fromEntity(app);
    }

    @Transactional
    public DscApplicationResponse updateStatus(Long id, UpdateDscStatusRequest dto, User adminUser) {
        requireAdmin(adminUser);
        DscApplication app = find(id);

        DscApplicationStatus from = app.getStatus();
        DscApplicationStatus to = dto.status();
        if (!TRANSITIONS.get(from).contains(to)) {
            throw new InvalidStateTransitionException("Invalid state transition from " + from + " to " + to);
        }

        String note = blankToNull(dto.note());
        if (to == REJECTED && note == null) {
            throw new BadRequestException("A reason is required to reject an application");
        }
        if (to == APPROVED) {
            List<String> missing = app.getDocuments().stream()
                    .filter(d -> d.getStatus() != DocumentStatus.RECEIVED)
                    .map(DscChecklistItem::getDocument)
                    .toList();
            if (!missing.isEmpty()) {
                throw new BadRequestException("Cannot approve: missing " + String.join(", ", missing));
            }
        }
        if (to == ISSUED) {
            app.setCertificate(issueCertificate(app));
            if (note == null) {
                note = "Certificate #" + app.getCertificate().getId() + " issued";
            }
        }

        app.setStatus(to);
        app.getEvents().add(new DscStatusEvent(to, note, adminUser.getName(), LocalDateTime.now()));
        applicationRepository.save(app);
        return DscApplicationResponse.fromEntity(app);
    }

    /**
     * Issued applications whose certificate expires within the warning window
     * (keybridge.expiry.warn-days) or has expired, and that have no renewal in
     * progress. Revoked certificates are not renewed, so they are left out.
     */
    @Transactional(readOnly = true)
    public List<DscRenewalReminder> renewalReminders(User currentUser) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusDays(warnDays);

        List<DscApplication> due = applicationRepository.findByStatus(ISSUED).stream()
                .filter(a -> currentUser.getRole() != Role.USER || a.getApplicant().getId().equals(currentUser.getId()))
                .filter(a -> a.getCertificate() != null
                        && a.getCertificate().getStatus() != CertStatus.REVOKED
                        && a.getCertificate().getValidTo().isBefore(threshold))
                .toList();
        if (due.isEmpty()) {
            return List.of();
        }

        Set<Long> renewing = applicationRepository.findByRenewalOfIdIn(due.stream().map(DscApplication::getId).toList())
                .stream()
                .filter(a -> a.getStatus() != REJECTED)
                .map(DscApplication::getRenewalOfId)
                .collect(Collectors.toSet());

        return due.stream()
                .filter(a -> !renewing.contains(a.getId()))
                .map(a -> {
                    Certificate cert = a.getCertificate();
                    return new DscRenewalReminder(
                            a.getId(),
                            a.getApplicant().getId(),
                            a.getFullName(),
                            cert.getId(),
                            cert.getStatus(),
                            cert.getValidTo(),
                            ChronoUnit.DAYS.between(now, cert.getValidTo()));
                })
                .sorted(Comparator.comparing(DscRenewalReminder::validTo))
                .toList();
    }

    /** Issues a software-keystore certificate from the test CA to the applicant. */
    private Certificate issueCertificate(DscApplication app) {
        CertResponse issued = certificateService.issueTestCertificate(new IssueCertRequest(
                app.getApplicant().getId(),
                app.getFullName(),
                app.getValidityDays(),
                null));
        return certificateRepository.findById(issued.id())
                .orElseThrow(() -> new NotFoundException("Issued certificate not found with ID: " + issued.id()));
    }

    private DscApplication find(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("DSC application not found with ID: " + id));
    }

    private static void checkCanSee(DscApplication app, User currentUser) {
        if (currentUser.getRole() == Role.USER && !app.getApplicant().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Access denied to this application");
        }
    }

    private static void requireAdmin(User user) {
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only administrators can review DSC applications");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
