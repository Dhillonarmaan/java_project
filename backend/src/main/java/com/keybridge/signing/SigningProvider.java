package com.keybridge.signing;

import com.keybridge.entity.Certificate;
import com.keybridge.enums.KeyStorageType;

/**
 * Signs data with the private key that belongs to a certificate.
 * The key never leaves the provider; callers only get signature bytes.
 */
public interface SigningProvider {

    /** The key storage this provider handles. */
    KeyStorageType type();

    /**
     * @param pin the user's PIN for this request, or null when the storage
     *            does not need one. Providers must not store it.
     * @return SHA256withRSA signature over data
     */
    byte[] sign(Certificate cert, byte[] data, SigningPin pin);
}
