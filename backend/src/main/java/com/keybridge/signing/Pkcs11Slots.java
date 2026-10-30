package com.keybridge.signing;

import com.keybridge.exception.BadRequestException;
import com.keybridge.exception.CryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AuthProvider;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Access to PKCS#11 slots through the JDK's SunPKCS11 provider, shared by
 * signing and certificate issuing.
 *
 * There is one provider per slot. A provider holds a single login, so each
 * slot is used by one request at a time: {@link #withLogin} locks the slot,
 * logs in with the request's PIN, runs the work, and always logs out. The
 * PIN is only passed to the device and never kept.
 */
@Component
public class Pkcs11Slots {

    private static final Logger log = LoggerFactory.getLogger(Pkcs11Slots.class);

    private final String library;
    private final Map<Long, Provider> providers = new ConcurrentHashMap<>();

    public Pkcs11Slots(@Value("${keybridge.pkcs11.library:}") String library) {
        this.library = library == null ? "" : library.trim();
    }

    @FunctionalInterface
    public interface SlotWork<T> {
        /** The keystore is logged in for the duration of the call. */
        T run(Provider provider, KeyStore keyStore) throws Exception;
    }

    /**
     * Runs work on the slot while logged in with the PIN.
     * BadRequestException and CryptoException pass through; anything else
     * becomes a CryptoException with {@code failureMessage}.
     */
    public <T> T withLogin(long slot, SigningPin pin, String failureMessage, SlotWork<T> work) {
        if (pin == null || pin.isEmpty()) {
            throw new BadRequestException("PIN is required for this token");
        }
        Provider provider = providerFor(slot);
        synchronized (provider) {
            try {
                return work.run(provider, login(provider, pin));
            } catch (BadRequestException | CryptoException e) {
                throw e;
            } catch (Exception e) {
                throw new CryptoException(failureMessage, e);
            } finally {
                logout(provider);
            }
        }
    }

    private static KeyStore login(Provider provider, SigningPin pin) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS11", provider);
        try {
            ks.load(null, pin.chars());
            return ks;
        } catch (IOException e) {
            // SunPKCS11 wraps login errors: IOException -> UnrecoverableKeyException -> FailedLoginException.
            if (findCause(e, FailedLoginException.class) != null) {
                throw new BadRequestException("Invalid PIN");
            }
            LoginException login = findCause(e, LoginException.class);
            if (login != null) {
                // e.g. PIN locked or expired; the message comes from the device, not from the PIN.
                throw new BadRequestException("Token login failed: " + login.getMessage());
            }
            throw e;
        }
    }

    private static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        for (Throwable c = t; c != null && c.getCause() != c; c = c.getCause()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
        }
        return null;
    }

    private static void logout(Provider provider) {
        try {
            ((AuthProvider) provider).logout();
        } catch (LoginException e) {
            log.debug("PKCS#11 logout failed: {}", e.getMessage());
        }
    }

    private Provider providerFor(long slot) {
        if (library.isEmpty()) {
            throw new CryptoException("PKCS#11 is not configured (set PKCS11_LIBRARY)");
        }
        return providers.computeIfAbsent(slot, this::createProvider);
    }

    private Provider createProvider(long slot) {
        if (!Files.isRegularFile(Path.of(library))) {
            throw new CryptoException("PKCS#11 library not found: " + library);
        }
        // Private keys generated through this provider are sensitive and
        // non-extractable, so they can sign but never be exported.
        String config = "--name=KeyBridge-slot-" + slot + "\n"
                + "library=" + library + "\n"
                + "slot=" + slot + "\n"
                + "attributes(generate, CKO_PRIVATE_KEY, *) = {\n"
                + "  CKA_SENSITIVE = true\n"
                + "  CKA_EXTRACTABLE = false\n"
                + "}\n";
        try {
            Provider base = Security.getProvider("SunPKCS11");
            if (base == null) {
                throw new CryptoException("SunPKCS11 provider is not available in this JDK");
            }
            return base.configure(config);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Cannot open PKCS#11 slot " + slot, e);
        }
    }
}
