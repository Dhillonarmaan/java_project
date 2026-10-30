package com.keybridge.signing;

import com.keybridge.entity.Certificate;
import com.keybridge.enums.KeyStorageType;
import com.keybridge.exception.BadRequestException;
import com.keybridge.exception.CryptoException;
import com.keybridge.service.CryptoService;
import com.keybridge.service.TestCaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;

/**
 * Signs with a key from a PKCS#12 file in keystore-data/. All files are
 * protected by the server-wide KS_PASSWORD; the user's PIN is not used.
 */
@Component
public class P12SigningProvider implements SigningProvider {

    static final String USER_ALIAS = "user";

    private final TestCaService testCaService;
    private final CryptoService cryptoService;
    private final String dir;
    private final String ksPass;

    public P12SigningProvider(
            TestCaService testCaService,
            CryptoService cryptoService,
            @Value("${keybridge.keystore.dir:./keystore-data}") String dir,
            @Value("${keybridge.keystore.password}") String ksPass
    ) {
        this.testCaService = testCaService;
        this.cryptoService = cryptoService;
        this.dir = dir;
        this.ksPass = ksPass;
    }

    @Override
    public KeyStorageType type() {
        return KeyStorageType.SOFT_P12;
    }

    @Override
    public byte[] sign(Certificate cert, byte[] data, SigningPin pin) {
        if (cert.getKeystoreFile() == null) {
            throw new BadRequestException("No private key available for this certificate");
        }
        Path file = Path.of(dir, cert.getKeystoreFile());
        if (!Files.isRegularFile(file)) {
            throw new BadRequestException("No private key available for this certificate");
        }

        PrivateKey key;
        try {
            KeyStore ks = testCaService.load(file);
            key = (PrivateKey) ks.getKey(USER_ALIAS, ksPass.toCharArray());
        } catch (Exception e) {
            throw new CryptoException("Could not open keystore for certificate " + cert.getId(), e);
        }
        if (key == null) {
            throw new BadRequestException("Private key alias '" + USER_ALIAS + "' not found in keystore");
        }
        return cryptoService.sign(data, key);
    }
}
