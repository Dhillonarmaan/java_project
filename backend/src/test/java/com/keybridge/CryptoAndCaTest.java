package com.keybridge;

import com.keybridge.dto.cert.CertMeta;
import com.keybridge.service.CertificateParserService;
import com.keybridge.service.CryptoService;
import com.keybridge.service.TestCaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

class CryptoAndCaTest {

    private CryptoService cryptoService;
    private CertificateParserService parserService;
    private TestCaService testCaService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        cryptoService = new CryptoService();
        parserService = new CertificateParserService(cryptoService);
        testCaService = new TestCaService(tempDir.toString(), "changeit", "keybridge-root");
        testCaService.ensureRoot();
    }

    @Test
    void testSha256AndSignatureVerification() throws Exception {
        byte[] data = "Hello KeyBridge Digital Signature".getBytes(StandardCharsets.UTF_8);
        String hash = cryptoService.sha256Hex(data);
        assertNotNull(hash);
        assertEquals(64, hash.length());

        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();

        byte[] sig = cryptoService.sign(data, kp.getPrivate());
        assertNotNull(sig);
        assertTrue(cryptoService.verify(data, sig, kp.getPublic()));

        // Tamper test
        byte[] tampered = "Hello KeyBridge Digital Signature (tampered)".getBytes(StandardCharsets.UTF_8);
        assertFalse(cryptoService.verify(tampered, sig, kp.getPublic()));
    }

    @Test
    void testRootCaAndUserCertIssuance() throws Exception {
        X509Certificate rootCert = testCaService.rootCert();
        assertNotNull(rootCert);
        assertTrue(rootCert.getSubjectX500Principal().getName().contains("KeyBridge Test Root CA"));

        TestCaService.IssuedCert issued = testCaService.issue("Bob Smith", "bob@example.com", 30);
        assertNotNull(issued);
        assertNotNull(issued.cert());
        assertNotNull(issued.keystoreFile());

        // Verify that user certificate is signed by the Root CA
        assertDoesNotThrow(() -> issued.cert().verify(rootCert.getPublicKey()));

        // Extract metadata
        CertMeta meta = parserService.extract(issued.cert());
        assertNotNull(meta.pem());
        assertTrue(meta.pem().startsWith("-----BEGIN CERTIFICATE-----"));
        assertEquals(64, meta.fingerprint().length());
        assertTrue(meta.subject().contains("Bob Smith"));
    }

    @Test
    void issuesCertificateForExternalPublicKeyWithoutWritingKeystore() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair external = g.generateKeyPair();
        long filesBefore = java.nio.file.Files.list(tempDir).count();

        X509Certificate cert = testCaService.issueFor(external.getPublic(), "Carol HSM", "carol@example.com", 10);

        assertEquals(external.getPublic(), cert.getPublicKey());
        assertDoesNotThrow(() -> cert.verify(testCaService.rootCert().getPublicKey()));
        assertTrue(cert.getSubjectX500Principal().getName().contains("Carol HSM"));
        assertEquals(filesBefore, java.nio.file.Files.list(tempDir).count());
    }
}
