package com.keybridge.signing;

import com.keybridge.entity.Certificate;
import com.keybridge.enums.KeyStorageType;
import com.keybridge.exception.BadRequestException;
import com.keybridge.exception.CryptoException;
import com.keybridge.service.CryptoService;
import com.keybridge.service.TestCaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class P12SigningProviderTest {

    @TempDir
    Path dir;

    private final CryptoService crypto = new CryptoService();
    private TestCaService ca;
    private P12SigningProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        ca = new TestCaService(dir.toString(), "changeit", "keybridge-root");
        ca.ensureRoot();
        provider = new P12SigningProvider(ca, crypto, dir.toString(), "changeit");
    }

    @Test
    void signsWithKeyFromP12AndSignatureVerifies() throws Exception {
        TestCaService.IssuedCert issued = ca.issue("Alice", "alice@example.com", 30);
        Certificate cert = new Certificate();
        cert.setKeystoreFile(issued.keystoreFile());

        byte[] data = "document".getBytes(StandardCharsets.UTF_8);
        byte[] sig = provider.sign(cert, data, null);

        assertTrue(crypto.verify(data, sig, issued.cert().getPublicKey()));
        assertFalse(crypto.verify("tampered".getBytes(StandardCharsets.UTF_8), sig, issued.cert().getPublicKey()));
    }

    @Test
    void certWithoutKeystoreFileIsRejected() {
        assertThrows(BadRequestException.class, () -> provider.sign(new Certificate(), new byte[]{1}, null));
    }

    @Test
    void missingKeystoreFileOnDiskIsRejected() {
        Certificate cert = new Certificate();
        cert.setKeystoreFile("user-does-not-exist.p12");
        assertThrows(BadRequestException.class, () -> provider.sign(cert, new byte[]{1}, null));
    }

    @Test
    void wrongKeystorePasswordFailsAsCryptoError() throws Exception {
        TestCaService.IssuedCert issued = ca.issue("Bob", "bob@example.com", 30);
        Certificate cert = new Certificate();
        cert.setKeystoreFile(issued.keystoreFile());
        TestCaService wrongCa = new TestCaService(dir.toString(), "wrong-password", "keybridge-root");
        P12SigningProvider wrong = new P12SigningProvider(wrongCa, crypto, dir.toString(), "wrong-password");

        assertTrue(Files.exists(dir.resolve(issued.keystoreFile())));
        assertThrows(CryptoException.class, () -> wrong.sign(cert, new byte[]{1}, null));
    }

    @Test
    void registryUsesP12ForCertificates() {
        SigningProviderRegistry registry = new SigningProviderRegistry(List.of(provider));
        assertSame(provider, registry.forCertificate(new Certificate()));
        assertEquals(KeyStorageType.SOFT_P12, provider.type());
    }

    @Test
    void registryRejectsDuplicateProviders() {
        assertThrows(IllegalStateException.class,
                () -> new SigningProviderRegistry(List.of(provider, provider)));
    }

    @Test
    void registryFailsClearlyWhenNoProviderForStorage() {
        SigningProviderRegistry empty = new SigningProviderRegistry(List.of());
        assertThrows(CryptoException.class, () -> empty.forCertificate(new Certificate()));
    }
}
