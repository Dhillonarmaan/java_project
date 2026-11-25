package com.keybridge;

import com.keybridge.entity.Certificate;
import com.keybridge.entity.User;
import com.keybridge.enums.CertStatus;
import com.keybridge.enums.Role;
import com.keybridge.repository.CertificateRepository;
import com.keybridge.service.ExpiryMonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ExpiryMonitorServiceTest {

    private CertificateRepository certificateRepository;
    private ExpiryMonitorService expiryMonitorService;

    @BeforeEach
    void setUp() {
        certificateRepository = mock(CertificateRepository.class);
        expiryMonitorService = new ExpiryMonitorService(certificateRepository, 30);
    }

    @Test
    void testExpiryTransitions() {
        User user = new User("Alice", "alice@example.com", "pass", Role.USER);
        LocalDateTime now = LocalDateTime.now();

        // 1. Valid certificate: valid for 60 days
        Certificate validCert = new Certificate();
        validCert.setId(1L);
        validCert.setOwner(user);
        validCert.setStatus(CertStatus.VALID);
        validCert.setValidTo(now.plusDays(60));

        // 2. Expiring certificate: valid for 15 days
        Certificate expiringCert = new Certificate();
        expiringCert.setId(2L);
        expiringCert.setOwner(user);
        expiringCert.setStatus(CertStatus.VALID);
        expiringCert.setValidTo(now.plusDays(15));

        // 3. Expired certificate: expired 5 days ago
        Certificate expiredCert = new Certificate();
        expiredCert.setId(3L);
        expiredCert.setOwner(user);
        expiredCert.setStatus(CertStatus.VALID);
        expiredCert.setValidTo(now.minusDays(5));

        when(certificateRepository.findByStatusNot(CertStatus.REVOKED))
                .thenReturn(List.of(validCert, expiringCert, expiredCert));

        int updated = expiryMonitorService.refreshStatuses();

        assertEquals(2, updated); // expiringCert and expiredCert changed
        assertEquals(CertStatus.VALID, validCert.getStatus());
        assertEquals(CertStatus.EXPIRING, expiringCert.getStatus());
        assertEquals(CertStatus.EXPIRED, expiredCert.getStatus());

        verify(certificateRepository, times(1)).saveAll(any());
    }

    @Test
    void certificateRetiredByRenewalStaysExpired() {
        Certificate renewed = new Certificate();
        renewed.setId(4L);
        renewed.setStatus(CertStatus.EXPIRED);
        renewed.setValidTo(LocalDateTime.now().plusDays(10));
        when(certificateRepository.findByStatusNot(CertStatus.REVOKED)).thenReturn(List.of(renewed));

        assertEquals(0, expiryMonitorService.refreshStatuses());
        assertEquals(CertStatus.EXPIRED, renewed.getStatus());
    }
}
