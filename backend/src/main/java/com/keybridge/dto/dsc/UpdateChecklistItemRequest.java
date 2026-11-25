package com.keybridge.dto.dsc;

import com.keybridge.enums.DocumentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateChecklistItemRequest(
    @NotBlank(message = "Document is required")
    String document,

    @NotNull(message = "Document status is required")
    DocumentStatus status
) {}
