package com.keybridge.dto.token;

import com.keybridge.enums.KeyStorageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateTokenRequest(
    @NotBlank(message = "Serial number is required")
    String serialNo,

    String label,

    /** Optional; defaults to SOFT_P12. */
    KeyStorageType keyStorage,

    /** Required when keyStorage is PKCS11, otherwise must be empty. */
    @PositiveOrZero(message = "PKCS#11 slot must be zero or positive")
    Long pkcs11Slot
) {}
