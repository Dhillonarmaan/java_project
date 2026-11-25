package com.keybridge.dto.dsc;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDscApplicationRequest(
    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must be at most 100 characters")
    String fullName,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @Pattern(regexp = "^$|^[+0-9 ()-]{6,20}$", message = "Invalid phone number")
    String phone,

    @Size(max = 100, message = "Organization must be at most 100 characters")
    String organization,

    /** Defaults to 365. */
    @Min(value = 1, message = "Validity must be at least 1 day")
    @Max(value = 1095, message = "Validity must be at most 1095 days")
    Integer validityDays,

    /** Set when this application renews an issued one. */
    Long renewalOfId
) {}
