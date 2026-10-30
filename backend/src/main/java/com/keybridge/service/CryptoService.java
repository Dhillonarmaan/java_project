package com.keybridge.service;

import com.keybridge.exception.CryptoException;
import org.springframework.stereotype.Service;

import java.security.*;
import java.util.HexFormat;

@Service
public class CryptoService {

    public String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public byte[] sign(byte[] data, PrivateKey key) {
        return sign(data, key, null);
    }

    /**
     * Signs with an explicit JCA provider. Needed for keys that live on a
     * PKCS#11 device, which only that device's provider can use.
     */
    public byte[] sign(byte[] data, PrivateKey key, Provider provider) {
        try {
            Signature signature = provider == null
                    ? Signature.getInstance("SHA256withRSA")
                    : Signature.getInstance("SHA256withRSA", provider);
            signature.initSign(key);
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Digital signing failed: " + e.getMessage(), e);
        }
    }

    public boolean verify(byte[] data, byte[] signatureBytes, PublicKey key) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update(data);
            return signature.verify(signatureBytes);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
