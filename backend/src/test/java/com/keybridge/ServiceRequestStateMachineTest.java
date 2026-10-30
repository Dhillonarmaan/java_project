package com.keybridge;

import com.keybridge.dto.request.UpdateRequestStatusDto;
import com.keybridge.entity.ServiceRequest;
import com.keybridge.entity.User;
import com.keybridge.enums.RequestStatus;
import com.keybridge.enums.RequestType;
import com.keybridge.enums.Role;
import com.keybridge.exception.InvalidStateTransitionException;
import com.keybridge.repository.CertificateRepository;
import com.keybridge.repository.ServiceRequestRepository;
import com.keybridge.repository.TokenRepository;
import com.keybridge.service.CertificateService;
import com.keybridge.service.ServiceRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceRequestStateMachineTest {

    private ServiceRequestRepository requestRepository;
    private CertificateRepository certificateRepository;
    private TokenRepository tokenRepository;
    private CertificateService certificateService;
    private ServiceRequestService serviceRequestService;

    private User adminUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        requestRepository = mock(ServiceRequestRepository.class);
        certificateRepository = mock(CertificateRepository.class);
        tokenRepository = mock(TokenRepository.class);
        certificateService = new CertificateService(certificateRepository, null, tokenRepository, null, null, null);

        serviceRequestService = new ServiceRequestService(
                requestRepository,
                certificateRepository,
                tokenRepository,
                certificateService
        );

        adminUser = new User("Admin", "admin@example.com", "pass", Role.ADMIN);
        adminUser.setId(1L);

        regularUser = new User("User", "user@example.com", "pass", Role.USER);
        regularUser.setId(2L);
    }

    @Test
    void testValidTransitions() {
        ServiceRequest req = new ServiceRequest();
        req.setId(10L);
        req.setUser(regularUser);
        req.setStatus(RequestStatus.PENDING);
        req.setType(RequestType.NEW_TOKEN);

        when(requestRepository.findById(10L)).thenReturn(Optional.of(req));

        // 1. PENDING -> APPROVED
        var res1 = serviceRequestService.updateStatus(10L, new UpdateRequestStatusDto(RequestStatus.APPROVED, "Approved by admin"), adminUser);
        assertEquals(RequestStatus.APPROVED, res1.status());

        // 2. APPROVED -> COMPLETED
        var res2 = serviceRequestService.updateStatus(10L, new UpdateRequestStatusDto(RequestStatus.COMPLETED, "Completed"), adminUser);
        assertEquals(RequestStatus.COMPLETED, res2.status());
    }

    @Test
    void testInvalidTransitionPendingToCompletedThrows409() {
        ServiceRequest req = new ServiceRequest();
        req.setId(10L);
        req.setUser(regularUser);
        req.setStatus(RequestStatus.PENDING);

        when(requestRepository.findById(10L)).thenReturn(Optional.of(req));

        assertThrows(InvalidStateTransitionException.class, () ->
                serviceRequestService.updateStatus(10L, new UpdateRequestStatusDto(RequestStatus.COMPLETED, "Direct complete"), adminUser));
    }

    @Test
    void testInvalidTransitionRejectedToApprovedThrows409() {
        ServiceRequest req = new ServiceRequest();
        req.setId(10L);
        req.setUser(regularUser);
        req.setStatus(RequestStatus.REJECTED);

        when(requestRepository.findById(10L)).thenReturn(Optional.of(req));

        assertThrows(InvalidStateTransitionException.class, () ->
                serviceRequestService.updateStatus(10L, new UpdateRequestStatusDto(RequestStatus.APPROVED, "Re-approve"), adminUser));
    }
}
