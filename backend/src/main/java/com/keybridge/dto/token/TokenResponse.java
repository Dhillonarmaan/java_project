package com.keybridge.dto.token;

import com.keybridge.entity.Token;
import com.keybridge.enums.KeyStorageType;
import com.keybridge.enums.TokenStatus;

import java.time.LocalDate;

public record TokenResponse(
    Long id,
    String serialNo,
    String label,
    Long ownerId,
    String ownerName,
    String ownerEmail,
    TokenStatus status,
    LocalDate assignedDate,
    KeyStorageType keyStorage
) {
    public static TokenResponse fromEntity(Token token) {
        if (token == null) return null;
        return new TokenResponse(
            token.getId(),
            token.getSerialNo(),
            token.getLabel(),
            token.getOwner() != null ? token.getOwner().getId() : null,
            token.getOwner() != null ? token.getOwner().getName() : null,
            token.getOwner() != null ? token.getOwner().getEmail() : null,
            token.getStatus(),
            token.getAssignedDate(),
            token.effectiveKeyStorage()
        );
    }
}
