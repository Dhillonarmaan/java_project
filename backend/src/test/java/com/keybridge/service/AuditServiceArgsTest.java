package com.keybridge.service;

import com.keybridge.signing.SigningPin;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.*;

class AuditServiceArgsTest {

    private static final String PIN = "482913";

    @Test
    void signingPinIsNeverWrittenToAuditDetails() {
        String details = AuditService.describeArgs(new Object[]{42L, new SigningPin(PIN)});
        assertEquals("42", details);
        assertFalse(details.contains(PIN));
        assertFalse(details.contains("SigningPin"));
    }

    @Test
    void rawSecretsAndContentAreSkippedByType() throws Exception {
        var key = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate();
        var file = new MockMultipartFile("file", "doc.txt", "text/plain", "secret body".getBytes());
        String details = AuditService.describeArgs(new Object[]{
                PIN.toCharArray(), PIN.getBytes(), file, key, "REVOKE"});
        assertEquals("REVOKE", details);
    }

    @Test
    void existingStringFiltersStillApply() {
        String details = AuditService.describeArgs(new Object[]{
                "myPassword=x", "alice@example.com", "x".repeat(120), 7});
        assertEquals("7", details);
    }

    @Test
    void nullAndEmptyArgsGiveEmptyDetails() {
        assertEquals("", AuditService.describeArgs(null));
        assertEquals("", AuditService.describeArgs(new Object[]{null}));
    }
}
