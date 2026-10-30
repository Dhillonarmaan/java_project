package com.keybridge.enums;

/** Where a token keeps its private keys. Selects the {@link com.keybridge.signing.SigningProvider}. */
public enum KeyStorageType {
    /** Password-protected PKCS#12 file in keystore-data/, opened with KS_PASSWORD. */
    SOFT_P12,
    /** Key held on a PKCS#11 device (e.g. SoftHSM, a smart card), unlocked with the user's PIN. */
    PKCS11
}
