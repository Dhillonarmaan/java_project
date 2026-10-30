package com.keybridge.signing;

import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.UUID;

/**
 * Puts a new key and certificate on a PKCS#11 device: the key pair is
 * generated on the device, a CA issues a certificate for its public key, and
 * the key and certificate chain are stored on the device under a new alias
 * (CKA_LABEL). The private key is created non-extractable (see Pkcs11Slots)
 * and never leaves the device.
 */
@Component
public class Pkcs11Enroller {

    static final String KEY_ALGORITHM = "RSA";
    static final int KEY_SIZE = 2048;

    private final Pkcs11Slots slots;

    public Pkcs11Enroller(Pkcs11Slots slots) {
        this.slots = slots;
    }

    /** Issues the certificate for the device's public key. */
    @FunctionalInterface
    public interface CertIssuer {
        X509Certificate issue(PublicKey publicKey) throws Exception;
    }

    /** Runs once the key and certificate are on the device, e.g. to save the database record. */
    @FunctionalInterface
    public interface AfterStore {
        void run(X509Certificate cert, String alias) throws Exception;
    }

    public record Enrolled(X509Certificate cert, String alias) {}

    /**
     * Enrols a new key on the slot while logged in with the PIN. If
     * {@code afterStore} fails, the new entry is deleted from the device
     * before the error is rethrown, so a failed request leaves nothing behind.
     */
    public Enrolled enroll(long slot, SigningPin pin, CertIssuer issuer, X509Certificate caCert, AfterStore afterStore) {
        return slots.withLogin(slot, pin, "Issuing onto the PKCS#11 token failed", (provider, ks) -> {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM, provider);
            generator.initialize(KEY_SIZE);
            KeyPair keyPair = generator.generateKeyPair();

            X509Certificate cert = issuer.issue(keyPair.getPublic());
            String alias = "kb-" + UUID.randomUUID();
            // Stores the private key and certificate as token objects labelled with the alias.
            ks.setKeyEntry(alias, keyPair.getPrivate(), null, new Certificate[]{cert, caCert});

            try {
                afterStore.run(cert, alias);
            } catch (Exception e) {
                try {
                    ks.deleteEntry(alias);
                } catch (Exception cleanup) {
                    e.addSuppressed(cleanup);
                }
                throw e;
            }
            return new Enrolled(cert, alias);
        });
    }
}
