package com.keybridge.signing;

import com.keybridge.entity.Certificate;
import com.keybridge.enums.KeyStorageType;
import com.keybridge.exception.CryptoException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Chooses the signing provider for a certificate based on its key storage. */
@Component
public class SigningProviderRegistry {

    private final Map<KeyStorageType, SigningProvider> providers = new EnumMap<>(KeyStorageType.class);

    public SigningProviderRegistry(List<SigningProvider> all) {
        for (SigningProvider p : all) {
            if (providers.putIfAbsent(p.type(), p) != null) {
                throw new IllegalStateException("Two signing providers registered for " + p.type());
            }
        }
    }

    public SigningProvider forCertificate(Certificate cert) {
        KeyStorageType type = storageOf(cert);
        SigningProvider p = providers.get(type);
        if (p == null) {
            throw new CryptoException("No signing provider configured for " + type);
        }
        return p;
    }

    /** Every certificate is a .p12 for now; token-based selection is added with Token.keyStorage. */
    static KeyStorageType storageOf(Certificate cert) {
        return KeyStorageType.SOFT_P12;
    }
}
