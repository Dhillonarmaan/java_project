package com.keybridge.signing;

import com.keybridge.entity.Certificate;
import com.keybridge.entity.Token;
import com.keybridge.enums.KeyStorageType;
import com.keybridge.exception.BadRequestException;
import com.keybridge.exception.CryptoException;
import com.keybridge.service.CryptoService;
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
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Signs with a private key held on a PKCS#11 device through the JDK's
 * SunPKCS11 provider. The key never leaves the device. The user's PIN is
 * used for one login and never stored.
 *
 * The device is chosen by the token's pkcs11_slot and the key by the
 * certificate's key_alias (CKA_LABEL). Before signing, the certificate
 * stored on the device under that alias must match the database record.
 */
@Component
public class Pkcs11SigningProvider implements SigningProvider {

    private static final Logger log = LoggerFactory.getLogger(Pkcs11SigningProvider.class);

    private final CryptoService cryptoService;
    private final String library;

    /** One SunPKCS11 instance per slot; a device session is shared, so each slot is used by one thread at a time. */
    private final Map<Long, Provider> providers = new ConcurrentHashMap<>();

    public Pkcs11SigningProvider(
            CryptoService cryptoService,
            @Value("${keybridge.pkcs11.library:}") String library
    ) {
        this.cryptoService = cryptoService;
        this.library = library == null ? "" : library.trim();
    }

    @Override
    public KeyStorageType type() {
        return KeyStorageType.PKCS11;
    }

    @Override
    public byte[] sign(Certificate cert, byte[] data, SigningPin pin) {
        Token token = cert.getToken();
        if (token == null || token.getPkcs11Slot() == null) {
            throw new BadRequestException("Certificate is not linked to a PKCS#11 slot");
        }
        if (cert.getKeyAlias() == null || cert.getKeyAlias().isBlank()) {
            throw new BadRequestException("Certificate has no key alias on its PKCS#11 token");
        }
        if (pin == null || pin.isEmpty()) {
            throw new BadRequestException("PIN is required to sign with this token");
        }

        Provider provider = providerFor(token.getPkcs11Slot());
        synchronized (provider) {
            try {
                KeyStore ks = login(provider, pin);
                checkCertificateMatches(ks, cert);

                Key key = ks.getKey(cert.getKeyAlias(), null);
                if (!(key instanceof PrivateKey privateKey)) {
                    throw new BadRequestException("Private key '" + cert.getKeyAlias() + "' not found on token");
                }
                return cryptoService.sign(data, privateKey, provider);
            } catch (BadRequestException | CryptoException e) {
                throw e;
            } catch (Exception e) {
                throw new CryptoException("PKCS#11 signing failed", e);
            } finally {
                logout(provider);
            }
        }
    }

    private KeyStore login(Provider provider, SigningPin pin) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS11", provider);
        try {
            ks.load(null, pin.chars());
            return ks;
        } catch (IOException e) {
            if (e.getCause() instanceof FailedLoginException) {
                throw new BadRequestException("Invalid PIN");
            }
            if (e.getCause() instanceof LoginException) {
                // e.g. PIN locked or expired; the message comes from the device, not from the PIN.
                throw new BadRequestException("Token login failed: " + e.getCause().getMessage());
            }
            throw e;
        }
    }

    private static void checkCertificateMatches(KeyStore ks, Certificate cert) throws Exception {
        java.security.cert.Certificate onToken = ks.getCertificate(cert.getKeyAlias());
        if (!(onToken instanceof X509Certificate x509)) {
            throw new BadRequestException("Certificate '" + cert.getKeyAlias() + "' not found on token");
        }
        String fingerprint = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(x509.getEncoded()));
        if (!fingerprint.equalsIgnoreCase(cert.getSha256Fingerprint())) {
            throw new CryptoException("Key on token does not belong to certificate " + cert.getId());
        }
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
            throw new CryptoException("PKCS#11 signing is not configured (set PKCS11_LIBRARY)");
        }
        return providers.computeIfAbsent(slot, this::createProvider);
    }

    private Provider createProvider(long slot) {
        if (!Files.isRegularFile(Path.of(library))) {
            throw new CryptoException("PKCS#11 library not found: " + library);
        }
        String config = "--name=KeyBridge-slot-" + slot + "\n"
                + "library=" + library + "\n"
                + "slot=" + slot + "\n";
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
