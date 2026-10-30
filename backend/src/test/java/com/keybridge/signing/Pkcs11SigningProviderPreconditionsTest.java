package com.keybridge.signing;

import com.keybridge.entity.Certificate;
import com.keybridge.entity.Token;
import com.keybridge.enums.KeyStorageType;
import com.keybridge.exception.BadRequestException;
import com.keybridge.exception.CryptoException;
import com.keybridge.service.CryptoService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Checks that run before any PKCS#11 library is touched. Device tests are in Pkcs11SigningProviderIT. */
class Pkcs11SigningProviderPreconditionsTest {

    private final Pkcs11SigningProvider unconfigured = new Pkcs11SigningProvider(new CryptoService(), "");

    private static Certificate cert(Long slot, String alias) {
        Token t = new Token("HSM-1", "SoftHSM");
        t.setKeyStorage(KeyStorageType.PKCS11);
        t.setPkcs11Slot(slot);
        Certificate c = new Certificate();
        c.setToken(t);
        c.setKeyAlias(alias);
        return c;
    }

    @Test
    void handlesPkcs11Storage() {
        assertEquals(KeyStorageType.PKCS11, unconfigured.type());
    }

    @Test
    void missingSlotIsRejected() {
        assertThrows(BadRequestException.class,
                () -> unconfigured.sign(cert(null, "k"), new byte[]{1}, new SigningPin("1234")));
    }

    @Test
    void missingAliasIsRejected() {
        assertThrows(BadRequestException.class,
                () -> unconfigured.sign(cert(1L, null), new byte[]{1}, new SigningPin("1234")));
    }

    @Test
    void missingPinIsRejected() {
        assertThrows(BadRequestException.class, () -> unconfigured.sign(cert(1L, "k"), new byte[]{1}, null));
        assertThrows(BadRequestException.class, () -> unconfigured.sign(cert(1L, "k"), new byte[]{1}, new SigningPin("")));
    }

    @Test
    void unconfiguredLibraryFailsClearly() {
        CryptoException e = assertThrows(CryptoException.class,
                () -> unconfigured.sign(cert(1L, "k"), new byte[]{1}, new SigningPin("1234")));
        assertTrue(e.getMessage().contains("PKCS11_LIBRARY"));
    }

    @Test
    void missingLibraryFileFailsClearly() {
        Pkcs11SigningProvider p = new Pkcs11SigningProvider(new CryptoService(), "/nonexistent/libpkcs11.so");
        CryptoException e = assertThrows(CryptoException.class,
                () -> p.sign(cert(1L, "k"), new byte[]{1}, new SigningPin("1234")));
        assertTrue(e.getMessage().contains("not found"));
    }
}
