package com.keybridge.service;

import com.keybridge.dto.token.CreateTokenRequest;
import com.keybridge.dto.token.TokenResponse;
import com.keybridge.entity.Token;
import com.keybridge.enums.KeyStorageType;
import com.keybridge.exception.BadRequestException;
import com.keybridge.repository.TokenRepository;
import com.keybridge.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenServiceCreateTest {

    private TokenRepository tokens;
    private TokenService service;

    @BeforeEach
    void setUp() {
        tokens = mock(TokenRepository.class);
        when(tokens.findBySerialNo(anyString())).thenReturn(Optional.empty());
        service = new TokenService(tokens, mock(UserRepository.class));
    }

    @Test
    void defaultsToSoftP12() {
        TokenResponse r = service.createToken(new CreateTokenRequest("TKN-9", "soft", null, null));
        assertEquals(KeyStorageType.SOFT_P12, r.keyStorage());
    }

    @Test
    void pkcs11TokenStoresSlot() {
        service.createToken(new CreateTokenRequest("HSM-1", "SoftHSM", KeyStorageType.PKCS11, 1234L));
        ArgumentCaptor<Token> saved = ArgumentCaptor.forClass(Token.class);
        verify(tokens).save(saved.capture());
        assertEquals(KeyStorageType.PKCS11, saved.getValue().getKeyStorage());
        assertEquals(1234L, saved.getValue().getPkcs11Slot());
    }

    @Test
    void pkcs11TokenRequiresSlot() {
        assertThrows(BadRequestException.class,
                () -> service.createToken(new CreateTokenRequest("HSM-2", null, KeyStorageType.PKCS11, null)));
        verify(tokens, never()).save(any());
    }

    @Test
    void slotNotAllowedForSoftToken() {
        assertThrows(BadRequestException.class,
                () -> service.createToken(new CreateTokenRequest("TKN-10", null, KeyStorageType.SOFT_P12, 3L)));
    }
}
