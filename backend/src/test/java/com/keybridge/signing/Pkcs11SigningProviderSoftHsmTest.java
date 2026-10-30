package com.keybridge.signing;

import com.keybridge.entity.Certificate;
import com.keybridge.entity.Token;
import com.keybridge.enums.KeyStorageType;
import com.keybridge.exception.BadRequestException;
import com.keybridge.exception.CryptoException;
import com.keybridge.service.CryptoService;
import com.keybridge.service.TestCaService;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs Pkcs11SigningProvider against a real SoftHSM token. Skipped unless
 * keybridge.pkcs11.slot is set; see scripts/softhsm-init.sh for setup.
 */
@EnabledIfSystemProperty(named = "keybridge.pkcs11.slot", matches = "\\d+")
class Pkcs11SigningProviderSoftHsmTest {

    private static final String LIBRARY = System.getProperty("keybridge.pkcs11.library", "");
    private static final long SLOT = Long.parseLong(System.getProperty("keybridge.pkcs11.slot", "0"));
    private static final String PIN = System.getProperty("keybridge.pkcs11.pin", "");

    @TempDir
    static Path caDir;

    private static final CryptoService crypto = new CryptoService();
    private static Provider setupProvider;
    private static String alias;
    private static X509Certificate deviceCert;

    @BeforeAll
    static void createKeyOnToken() throws Exception {
        TestCaService ca = new TestCaService(caDir.toString(), "changeit", "keybridge-root");
        ca.ensureRoot();
        KeyStore root = ca.load(ca.rootPath());
        PrivateKey rootKey = (PrivateKey) root.getKey("keybridge-root", "changeit".toCharArray());
        X509Certificate rootCert = ca.rootCert();

        // A separate SunPKCS11 instance stands in for the enrolment tooling.
        setupProvider = Security.getProvider("SunPKCS11")
                .configure("--name=KeyBridge-test-setup\nlibrary=" + LIBRARY + "\nslot=" + SLOT + "\n");
        KeyStore token = KeyStore.getInstance("PKCS11", setupProvider);
        token.load(null, PIN.toCharArray());

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA", setupProvider);
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();

        Instant now = Instant.now();
        deviceCert = new JcaX509CertificateConverter().getCertificate(
                new JcaX509v3CertificateBuilder(
                        X500Name.getInstance(rootCert.getSubjectX500Principal().getEncoded()),
                        BigInteger.valueOf(now.toEpochMilli()),
                        Date.from(now.minus(1, ChronoUnit.MINUTES)),
                        Date.from(now.plus(1, ChronoUnit.DAYS)),
                        new X500Name("CN=SoftHSM Test, O=KeyBridge Users"),
                        kp.getPublic())
                        .build(new JcaContentSignerBuilder("SHA256withRSA").build(rootKey)));

        // Stores the private key and certificate as token objects under one label.
        alias = "kb-it-" + UUID.randomUUID();
        token.setKeyEntry(alias, kp.getPrivate(), null, new java.security.cert.Certificate[]{deviceCert, rootCert});
        ((AuthProvider) setupProvider).logout();
    }

    @AfterAll
    static void removeKeyFromToken() throws Exception {
        if (setupProvider == null || alias == null) return;
        KeyStore token = KeyStore.getInstance("PKCS11", setupProvider);
        token.load(null, PIN.toCharArray());
        token.deleteEntry(alias);
        ((AuthProvider) setupProvider).logout();
    }

    private static Certificate certRecord(String keyAlias, X509Certificate x509) throws Exception {
        Token t = new Token("HSM-TEST", "SoftHSM");
        t.setKeyStorage(KeyStorageType.PKCS11);
        t.setPkcs11Slot(SLOT);
        Certificate c = new Certificate();
        c.setId(99L);
        c.setToken(t);
        c.setKeyAlias(keyAlias);
        c.setSha256Fingerprint(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(x509.getEncoded())));
        return c;
    }

    private final Pkcs11SigningProvider provider = new Pkcs11SigningProvider(crypto, LIBRARY);

    @Test
    void signsOnDeviceAndSignatureVerifiesWithCertificate() throws Exception {
        byte[] data = "signed inside SoftHSM".getBytes(StandardCharsets.UTF_8);
        byte[] sig = provider.sign(certRecord(alias, deviceCert), data, new SigningPin(PIN));

        assertTrue(crypto.verify(data, sig, deviceCert.getPublicKey()));
        assertFalse(crypto.verify("tampered".getBytes(StandardCharsets.UTF_8), sig, deviceCert.getPublicKey()));
    }

    @Test
    void wrongPinIsRejected() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> provider.sign(certRecord(alias, deviceCert), new byte[]{1}, new SigningPin(PIN + "0")));
        assertEquals("Invalid PIN", e.getMessage());
    }

    @Test
    void unknownAliasIsRejected() {
        assertThrows(BadRequestException.class,
                () -> provider.sign(certRecord("no-such-key", deviceCert), new byte[]{1}, new SigningPin(PIN)));
    }

    @Test
    void certificateNotMatchingDeviceIsRejected() throws Exception {
        Certificate record = certRecord(alias, deviceCert);
        record.setSha256Fingerprint("00".repeat(32));
        assertThrows(CryptoException.class,
                () -> provider.sign(record, new byte[]{1}, new SigningPin(PIN)));
    }

    @Test
    void signsRepeatedlyBecauseEachRequestLogsOut() throws Exception {
        Certificate record = certRecord(alias, deviceCert);
        for (int i = 0; i < 3; i++) {
            byte[] data = ("request " + i).getBytes(StandardCharsets.UTF_8);
            byte[] sig = provider.sign(record, data, new SigningPin(PIN));
            assertTrue(crypto.verify(data, sig, deviceCert.getPublicKey()));
        }
    }
}
