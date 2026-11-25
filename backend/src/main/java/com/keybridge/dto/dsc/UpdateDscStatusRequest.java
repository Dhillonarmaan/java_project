package com.keybridge.dto.dsc;

import com.keybridge.enums.DscApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateDscStatusRequest(
    @NotNull(message = "Target status is required")
    DscApplicationStatus status,

    /** Required when rejecting. */
    @Size(max = 500, message = "Note must be at most 500 characters")
    String note
) {}
