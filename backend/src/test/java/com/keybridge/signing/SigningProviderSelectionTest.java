package com.keybridge.signing;

import com.keybridge.entity.Certificate;
import com.keybridge.entity.Token;
import com.keybridge.enums.KeyStorageType;
import com.keybridge.exception.CryptoException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SigningProviderSelectionTest {

    private static SigningProvider stub(KeyStorageType type) {
        return new SigningProvider() {
            public KeyStorageType type() { return type; }
            public byte[] sign(Certificate c, byte[] d, SigningPin p) { return new byte[0]; }
        };
    }

    private static Certificate certOn(Token token) {
        Certificate c = new Certificate();
        c.setToken(token);
        return c;
    }

    private final SigningProvider p12 = stub(KeyStorageType.SOFT_P12);
    private final SigningProvider pkcs11 = stub(KeyStorageType.PKCS11);
    private final SigningProviderRegistry registry = new SigningProviderRegistry(List.of(p12, pkcs11));

    @Test
    void certWithoutTokenUsesP12() {
        assertSame(p12, registry.forCertificate(certOn(null)));
    }

    @Test
    void tokenWithNoStorageSetUsesP12() {
        assertSame(p12, registry.forCertificate(certOn(new Token("TKN-1", "legacy"))));
    }

    @Test
    void pkcs11TokenUsesPkcs11Provider() {
        Token t = new Token("HSM-1", "SoftHSM");
        t.setKeyStorage(KeyStorageType.PKCS11);
        t.setPkcs11Slot(0L);
        assertSame(pkcs11, registry.forCertificate(certOn(t)));
    }

    @Test
    void pkcs11TokenWithoutPkcs11ProviderFailsClearly() {
        Token t = new Token("HSM-1", "SoftHSM");
        t.setKeyStorage(KeyStorageType.PKCS11);
        SigningProviderRegistry p12Only = new SigningProviderRegistry(List.of(p12));
        CryptoException e = assertThrows(CryptoException.class, () -> p12Only.forCertificate(certOn(t)));
        assertTrue(e.getMessage().contains("PKCS11"));
    }
}
