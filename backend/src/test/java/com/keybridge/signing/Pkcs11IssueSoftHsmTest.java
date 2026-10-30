package com.keybridge.signing;

import com.keybridge.dto.cert.IssueCertRequest;
import com.keybridge.dto.sign.SignResponse;
import com.keybridge.dto.verify.VerifyResponse;
import com.keybridge.entity.Certificate;
import com.keybridge.entity.Token;
import com.keybridge.entity.User;
import com.keybridge.enums.KeyStorageType;
import com.keybridge.enums.Role;
import com.keybridge.enums.TokenStatus;
import com.keybridge.enums.VerifyResult;
import com.keybridge.exception.BadRequestException;
import com.keybridge.repository.CertificateRepository;
import com.keybridge.repository.SignedDocumentRepository;
import com.keybridge.repository.TokenRepository;
import com.keybridge.repository.UserRepository;
import com.keybridge.repository.VerificationLogRepository;
import com.keybridge.service.CertificateParserService;
import com.keybridge.service.CertificateService;
import com.keybridge.service.CryptoService;
import com.keybridge.service.SigningService;
import com.keybridge.service.TestCaService;
import com.keybridge.service.VerificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AuthProvider;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * End to end against a real SoftHSM token: an admin issues a certificate
 * onto the token, the owner signs with it, and the signature verifies.
 * Only the database is mocked. Skipped unless keybridge.pkcs11.slot is set;
 * see scripts/softhsm-init.sh.
 */
@EnabledIfSystemProperty(named = "keybridge.pkcs11.slot", matches = "\\d+")
class Pkcs11IssueSoftHsmTest {

    private static final String LIBRARY = System.getProperty("keybridge.pkcs11.library", "");
    private static final long SLOT = Long.parseLong(System.getProperty("keybridge.pkcs11.slot", "0"));
    private static final String PIN = System.getProperty("keybridge.pkcs11.pin", "");

    @TempDir
    Path caDir;

    private final CertificateRepository certs = mock(CertificateRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TokenRepository tokens = mock(TokenRepository.class);

    private final CryptoService crypto = new CryptoService();
    private final CertificateParserService parser = new CertificateParserService(crypto);
    private final Pkcs11Slots slots = new Pkcs11Slots(LIBRARY);

    private TestCaService ca;
    private CertificateService certificateService;
    private SigningService signingService;
    private VerificationService verificationService;

    /** A separate SunPKCS11 instance for looking at the device, as an outside tool would. */
    private static Provider inspector;

    private final User admin = user(1L, Role.ADMIN, "admin@keybridge.com");
    private final User alice = user(2L, Role.USER, "alice@keybridge.com");
    private final Token token = new Token("HSM-E2E", "SoftHSM");
    private final List<String> createdAliases = new ArrayList<>();

    private static User user(Long id, Role role, String email) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setEmail(email);
        u.setName(email);
        return u;
    }

    @BeforeEach
    void setUp() throws Exception {
        ca = new TestCaService(caDir.toString(), "changeit", "keybridge-root");
        ca.ensureRoot();

        certificateService = new CertificateService(certs, users, tokens, ca, parser, new Pkcs11Enroller(slots));
        signingService = new SigningService(certs, mock(SignedDocumentRepository.class), crypto,
                new SigningProviderRegistry(List.of(new Pkcs11SigningProvider(crypto, slots))));
        verificationService = new VerificationService(crypto, parser, ca, certs, mock(VerificationLogRepository.class));

        token.setId(10L);
        token.setKeyStorage(KeyStorageType.PKCS11);
        token.setPkcs11Slot(SLOT);
        token.setOwner(alice);
        token.setStatus(TokenStatus.ASSIGNED);
        when(tokens.findById(10L)).thenReturn(Optional.of(token));
        when(users.findById(2L)).thenReturn(Optional.of(alice));

        if (inspector == null) {
            inspector = Security.getProvider("SunPKCS11")
                    .configure("--name=KeyBridge-test-inspector\nlibrary=" + LIBRARY + "\nslot=" + SLOT + "\n");
        }
    }

    @AfterEach
    void removeCreatedKeys() throws Exception {
        onDevice(ks -> {
            for (String alias : createdAliases) {
                if (ks.containsAlias(alias)) ks.deleteEntry(alias);
            }
            return null;
        });
    }

    private interface DeviceWork<T> { T run(KeyStore ks) throws Exception; }

    private static <T> T onDevice(DeviceWork<T> work) throws Exception {
        synchronized (inspector) {
            KeyStore ks = KeyStore.getInstance("PKCS11", inspector);
            ks.load(null, PIN.toCharArray());
            try {
                return work.run(ks);
            } finally {
                ((AuthProvider) inspector).logout();
            }
        }
    }

    private static java.util.Set<String> aliasesOnDevice() throws Exception {
        return onDevice(ks -> new java.util.HashSet<>(Collections.list(ks.aliases())));
    }

    private Certificate issue(String pin) {
        IssueCertRequest req = new IssueCertRequest(2L, "Alice SoftHSM", null, 30, null, null, 10L,
                pin == null ? null : new SigningPin(pin));
        certificateService.issueTestCertificate(req, admin);
        var saved = org.mockito.ArgumentCaptor.forClass(Certificate.class);
        verify(certs).saveAndFlush(saved.capture());
        Certificate cert = saved.getValue();
        cert.setId(77L);
        createdAliases.add(cert.getKeyAlias());
        when(certs.findById(77L)).thenReturn(Optional.of(cert));
        when(certs.findBySha256Fingerprint(cert.getSha256Fingerprint())).thenReturn(Optional.of(cert));
        return cert;
    }

    @Test
    void issuedOnDeviceThenSignedAndVerifiedThroughTheServices() throws Exception {
        when(certs.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        long filesBefore;
        try (var s = Files.list(caDir)) { filesBefore = s.count(); }

        Certificate cert = issue(PIN);

        // Database record: alias and public data only.
        assertTrue(cert.getKeyAlias().startsWith("kb-"));
        assertNull(cert.getKeystoreFile());
        try (var s = Files.list(caDir)) { assertEquals(filesBefore, s.count(), "no .p12 written"); }

        // Device: the certificate under the alias is the one recorded, and the key cannot be exported.
        X509Certificate x509 = parser.parse(cert.getPem().getBytes(StandardCharsets.UTF_8));
        onDevice(ks -> {
            X509Certificate onDevice = (X509Certificate) ks.getCertificate(cert.getKeyAlias());
            assertNotNull(onDevice, "certificate stored on the token");
            assertEquals(cert.getSha256Fingerprint(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(onDevice.getEncoded())));
            assertEquals(2, ks.getCertificateChain(cert.getKeyAlias()).length, "chain is user + root");
            assertInstanceOf(PrivateKey.class, ks.getKey(cert.getKeyAlias(), null));
            return null;
        });
        assertDoesNotThrow(() -> x509.verify(ca.rootCert().getPublicKey()));

        // Sign as the owner with the PIN, then verify.
        byte[] doc = "contract signed on SoftHSM".getBytes(StandardCharsets.UTF_8);
        SignResponse signed = signingService.sign(77L,
                new MockMultipartFile("file", "contract.txt", "text/plain", doc), alice, new SigningPin(PIN));

        VerifyResponse ok = verificationService.verify(
                new MockMultipartFile("file", "contract.txt", "text/plain", doc),
                signed.signatureB64(), x509.getEncoded(), null, alice);
        assertEquals(VerifyResult.VALID, ok.result(), ok.reason());

        VerifyResponse tampered = verificationService.verify(
                new MockMultipartFile("file", "contract.txt", "text/plain", "changed".getBytes(StandardCharsets.UTF_8)),
                signed.signatureB64(), x509.getEncoded(), null, alice);
        assertEquals(VerifyResult.TAMPERED, tampered.result());
    }

    /**
     * Java refuses to export any key loaded from a PKCS#11 keystore, so the
     * device attributes are read with OpenSC's pkcs11-tool instead. Without
     * Pkcs11Slots' generate attributes, SoftHSM keys are not sensitive.
     */
    @Test
    void privateKeyIsSensitiveAndNeverExtractableOnTheDevice() throws Exception {
        String tool = findPkcs11Tool();
        org.junit.jupiter.api.Assumptions.assumeTrue(tool != null, "pkcs11-tool not installed (brew install opensc)");
        when(certs.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        Certificate cert = issue(PIN);

        // SunPKCS11 stores the key with CKA_ID = alias bytes; the uri line shows it unwrapped as id=%6b%62...
        String id = "id=%" + HexFormat.ofDelimiter("%").formatHex(cert.getKeyAlias().getBytes(StandardCharsets.US_ASCII)) + ";";
        Process p = new ProcessBuilder(tool, "--module", LIBRARY, "--slot", String.valueOf(SLOT),
                "--login", "--pin", PIN, "--list-objects", "--type", "privkey")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), out);

        String access = null;
        String[] blocks = out.split("(?=Private Key Object)");
        for (String block : blocks) {
            if (block.contains(id)) {
                access = block.lines().filter(l -> l.trim().startsWith("Access:")).findFirst().orElse("");
            }
        }
        assertNotNull(access, "private key " + cert.getKeyAlias() + " not listed:\n" + out);
        assertTrue(access.contains("sensitive") && access.contains("always sensitive"), access);
        assertTrue(access.contains("never extractable"), access);
    }

    private static String findPkcs11Tool() {
        for (String dir : (System.getenv("PATH") + ":/opt/homebrew/bin:/usr/local/bin:/usr/bin").split(":")) {
            Path candidate = Path.of(dir, "pkcs11-tool");
            if (Files.isExecutable(candidate)) return candidate.toString();
        }
        return null;
    }

    @Test
    void signingWithWrongPinIsRejected() throws Exception {
        when(certs.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        issue(PIN);
        BadRequestException e = assertThrows(BadRequestException.class, () -> signingService.sign(77L,
                new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1}), alice, new SigningPin(PIN + "9")));
        assertEquals("Invalid PIN", e.getMessage());
    }

    @Test
    void wrongPinOnIssueLeavesNothingBehind() throws Exception {
        var before = aliasesOnDevice();

        IssueCertRequest req = new IssueCertRequest(2L, "Alice SoftHSM", null, 30, null, null, 10L,
                new SigningPin(PIN + "9"));
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> certificateService.issueTestCertificate(req, admin));

        assertEquals("Invalid PIN", e.getMessage());
        assertEquals(before, aliasesOnDevice());
        verify(certs, never()).saveAndFlush(any());
    }

    @Test
    void databaseFailureRemovesTheNewKeyFromTheDevice() throws Exception {
        var before = aliasesOnDevice();
        when(certs.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        IssueCertRequest req = new IssueCertRequest(2L, "Alice SoftHSM", null, 30, null, null, 10L,
                new SigningPin(PIN));
        assertThrows(RuntimeException.class, () -> certificateService.issueTestCertificate(req, admin));

        assertEquals(before, aliasesOnDevice());
    }
}
