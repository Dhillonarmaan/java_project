package com.keybridge.service;

import com.keybridge.entity.Certificate;
import com.keybridge.enums.CertStatus;
import com.keybridge.repository.CertificateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExpiryMonitorService {

    private static final Logger log = LoggerFactory.getLogger(ExpiryMonitorService.class);

    private final CertificateRepository certificateRepository;
    private final int warnDays;

    public ExpiryMonitorService(
            CertificateRepository certificateRepository,
            @Value("${keybridge.expiry.warn-days:30}") int warnDays
    ) {
        this.certificateRepository = certificateRepository;
        this.warnDays = warnDays;
    }

    @Scheduled(cron = "${keybridge.expiry.cron:0 0 1 * * *}")
    @Transactional
    public int refreshStatuses() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warnThreshold = now.plusDays(warnDays);
        List<Certificate> certs = certificateRepository.findByStatusNot(CertStatus.REVOKED);

        int updatedCount = 0;
        for (Certificate c : certs) {
            // EXPIRED is final: a renewal retires the old certificate as EXPIRED before its end date,
            // and recomputing from dates would make it usable for signing again.
            if (c.getStatus() == CertStatus.EXPIRED) {
                continue;
            }
            CertStatus targetStatus;
            if (c.getValidTo().isBefore(now)) {
                targetStatus = CertStatus.EXPIRED;
            } else if (c.getValidTo().isBefore(warnThreshold)) {
                targetStatus = CertStatus.EXPIRING;
            } else {
                targetStatus = CertStatus.VALID;
            }

            if (c.getStatus() != targetStatus) {
                c.setStatus(targetStatus);
                updatedCount++;
            }
        }

        certificateRepository.saveAll(certs);
        log.info("Certificate expiry monitor completed. Evaluated {} certs, updated {} statuses.", certs.size(), updatedCount);
        return updatedCount;
    }
}
