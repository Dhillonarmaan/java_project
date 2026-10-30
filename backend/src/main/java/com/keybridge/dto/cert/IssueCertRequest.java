package com.keybridge.dto.cert;

import com.keybridge.signing.SigningPin;
import jakarta.validation.constraints.NotBlank;

public record IssueCertRequest(
    Long userId,

    @NotBlank(message = "Common Name is required")
    String commonName,

    Integer validDays,
    Integer validityDays,
    String organization,
    String country,
    Long tokenId,

    /**
     * PIN of a PKCS#11 token, used once to generate the key on it. A SigningPin
     * so the record's toString(), which @Audited writes to audit_logs, shows
     * it redacted. Ignored for software tokens.
     */
    SigningPin pin
) {
    public IssueCertRequest(Long userId, String commonName, int validDays, Long tokenId) {
        this(userId, commonName, validDays, validDays, null, null, tokenId, null);
    }

    public int resolvedDays() {
        if (validityDays != null && validityDays > 0) return validityDays;
        if (validDays != null && validDays > 0) return validDays;
        return 365;
    }
}
