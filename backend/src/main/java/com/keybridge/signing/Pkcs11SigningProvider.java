package com.keybridge.signing;

import com.keybridge.entity.Certificate;
import com.keybridge.entity.Token;
import com.keybridge.enums.KeyStorageType;
import com.keybridge.exception.BadRequestException;
import com.keybridge.exception.CryptoException;
import com.keybridge.service.CryptoService;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

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

    private final CryptoService cryptoService;
    private final Pkcs11Slots slots;

    public Pkcs11SigningProvider(CryptoService cryptoService, Pkcs11Slots slots) {
        this.cryptoService = cryptoService;
        this.slots = slots;
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

        return slots.withLogin(token.getPkcs11Slot(), pin, "PKCS#11 signing failed", (provider, ks) -> {
            checkCertificateMatches(ks, cert);
            Key key = ks.getKey(cert.getKeyAlias(), null);
            if (!(key instanceof PrivateKey privateKey)) {
                throw new BadRequestException("Private key '" + cert.getKeyAlias() + "' not found on token");
            }
            return cryptoService.sign(data, privateKey, provider);
        });
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
}
