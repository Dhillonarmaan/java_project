package com.keybridge.dto.dsc;

import com.keybridge.enums.CertStatus;

import java.time.LocalDateTime;

/** An issued DSC whose certificate expires within the warning window (or already has). */
public record DscRenewalReminder(
    Long applicationId,
    Long applicantId,
    String fullName,
    Long certId,
    CertStatus certStatus,
    LocalDateTime validTo,
    long daysLeft
) {}
