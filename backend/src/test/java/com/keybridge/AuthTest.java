package com.keybridge;

import com.keybridge.entity.User;
import com.keybridge.enums.Role;
import com.keybridge.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970", 120);
    }

    @Test
    void testTokenGenerationAndValidation() {
        User user = new User("Alice", "alice@example.com", "hash", Role.USER);
        user.setId(10L);

        String token = jwtService.generate(user);
        assertNotNull(token);
        assertTrue(jwtService.validate(token));
        assertEquals("alice@example.com", jwtService.subject(token));
        assertEquals("USER", jwtService.extractRole(token));
    }

    @Test
    void testInvalidToken() {
        assertFalse(jwtService.validate("invalid.token.string"));
    }

    @Test
    void shortSecretFailsInsteadOfBeingPadded() {
        String shortSecret = "a".repeat(31);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new JwtService(shortSecret, 120));
        assertTrue(e.getMessage().contains("at least 32 bytes"));
        assertFalse(e.getMessage().contains(shortSecret), "secret must not be logged");
    }

    @Test
    void missingSecretFails() {
        assertThrows(IllegalStateException.class, () -> new JwtService(null, 120));
        assertThrows(IllegalStateException.class, () -> new JwtService("   ", 120));
    }

    @Test
    void secretOfExactly32BytesIsAccepted() {
        JwtService service = new JwtService("b".repeat(32), 120);
        User user = new User("Bob", "bob@example.com", "hash", Role.USER);
        user.setId(11L);
        assertTrue(service.validate(service.generate(user)));
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        User user = new User("Carol", "carol@example.com", "hash", Role.USER);
        user.setId(12L);
        String token = new JwtService("c".repeat(32), 120).generate(user);
        assertFalse(jwtService.validate(token));
    }
}
