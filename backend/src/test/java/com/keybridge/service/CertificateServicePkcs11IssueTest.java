package com.keybridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keybridge.dto.cert.CertResponse;
import com.keybridge.dto.cert.IssueCertRequest;
import com.keybridge.entity.Certificate;
import com.keybridge.entity.Token;
import com.keybridge.entity.User;
import com.keybridge.enums.KeyStorageType;
import com.keybridge.enums.Role;
import com.keybridge.enums.TokenStatus;
import com.keybridge.exception.BadRequestException;
import com.keybridge.repository.CertificateRepository;
import com.keybridge.repository.TokenRepository;
import com.keybridge.repository.UserRepository;
import com.keybridge.signing.Pkcs11Enroller;
import com.keybridge.signing.SigningPin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Issuing onto a PKCS#11 token, with the device replaced by a mocked enroller. */
class CertificateServicePkcs11IssueTest {

    private static final String PIN = "246810";

    @TempDir
    Path caDir;

    private final CertificateRepository certs = mock(CertificateRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TokenRepository tokens = mock(TokenRepository.class);
    private final Pkcs11Enroller enroller = mock(Pkcs11Enroller.class);
    private TestCaService ca;
    private CertificateService service;

    private final User admin = user(1L, Role.ADMIN, "admin@keybridge.com");
    private final User alice = user(2L, Role.USER, "alice@keybridge.com");

    @BeforeEach
    void setUp() throws Exception {
        ca = new TestCaService(caDir.toString(), "changeit", "keybridge-root");
        ca.ensureRoot();
        service = new CertificateService(certs, users, tokens, ca,
                new CertificateParserService(new CryptoService()), enroller);
        when(users.findById(2L)).thenReturn(Optional.of(alice));
        when(certs.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static User user(Long id, Role role, String email) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setEmail(email);
        u.setName(email);
        return u;
    }

    private Token pkcs11Token(Long slot, User owner) {
        Token t = new Token("HSM-1", "SoftHSM");
        t.setId(10L);
        t.setKeyStorage(KeyStorageType.PKCS11);
        t.setPkcs11Slot(slot);
        t.setOwner(owner);
        t.setStatus(owner != null ? TokenStatus.ASSIGNED : TokenStatus.AVAILABLE);
        when(tokens.findById(10L)).thenReturn(Optional.of(t));
        return t;
    }

    private static IssueCertRequest request(SigningPin pin) {
        return new IssueCertRequest(2L, "Alice HSM", null, 30, null, null, 10L, pin);
    }

    /** Stands in for the device: generates a software key and runs the callbacks the way the enroller does. */
    private void enrollerSucceeds() {
        when(enroller.enroll(anyLong(), any(), any(), any(), any())).thenAnswer(inv -> {
            KeyPair kp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            X509Certificate cert = inv.<Pkcs11Enroller.CertIssuer>getArgument(2).issue(kp.getPublic());
            inv.<Pkcs11Enroller.AfterStore>getArgument(4).run(cert, "kb-test-alias");
            return new Pkcs11Enroller.Enrolled(cert, "kb-test-alias");
        });
    }

    @Test
    void issuesOntoTokenAndStoresOnlyAliasAndPublicData() throws Exception {
        Token token = pkcs11Token(3L, alice);
        enrollerSucceeds();
        long filesBefore;
        try (var s = Files.list(caDir)) { filesBefore = s.count(); }

        CertResponse res = service.issueTestCertificate(request(new SigningPin(PIN)), admin);

        verify(enroller).enroll(eq(3L), any(), any(), any(), any());
        var saved = org.mockito.ArgumentCaptor.forClass(Certificate.class);
        verify(certs).saveAndFlush(saved.capture());
        Certificate c = saved.getValue();
        assertEquals("kb-test-alias", c.getKeyAlias());
        assertNull(c.getKeystoreFile());
        assertSame(token, c.getToken());
        assertSame(alice, c.getOwner());
        assertTrue(c.getSubject().contains("Alice HSM"));
        assertEquals(KeyStorageType.PKCS11, res.keyStorage());
        try (var s = Files.list(caDir)) { assertEquals(filesBefore, s.count(), "no .p12 written"); }
    }

    @Test
    void pinIsZeroedAfterIssuing() {
        pkcs11Token(3L, alice);
        enrollerSucceeds();
        SigningPin pin = new SigningPin(PIN);
        service.issueTestCertificate(request(pin), admin);
        assertArrayEquals(new char[PIN.length()], pin.chars());
    }

    @Test
    void pinIsZeroedWhenIssuingFails() {
        pkcs11Token(null, alice);
        SigningPin pin = new SigningPin(PIN);
        assertThrows(BadRequestException.class, () -> service.issueTestCertificate(request(pin), admin));
        assertArrayEquals(new char[PIN.length()], pin.chars());
    }

    @Test
    void missingPinIsRejectedBeforeTouchingTheDevice() {
        pkcs11Token(3L, alice);
        assertThrows(BadRequestException.class, () -> service.issueTestCertificate(request(null), admin));
        assertThrows(BadRequestException.class, () -> service.issueTestCertificate(request(new SigningPin("")), admin));
        verifyNoInteractions(enroller);
        verify(certs, never()).saveAndFlush(any());
    }

    @Test
    void tokenWithoutSlotIsRejected() {
        pkcs11Token(null, alice);
        assertThrows(BadRequestException.class,
                () -> service.issueTestCertificate(request(new SigningPin(PIN)), admin));
        verifyNoInteractions(enroller);
    }

    @Test
    void lostOrRetiredTokenIsRejected() {
        for (TokenStatus status : new TokenStatus[]{TokenStatus.LOST, TokenStatus.RETIRED}) {
            pkcs11Token(3L, alice).setStatus(status);
            assertThrows(BadRequestException.class,
                    () -> service.issueTestCertificate(request(new SigningPin(PIN)), admin));
        }
        verifyNoInteractions(enroller);
    }

    @Test
    void tokenAssignedToAnotherUserIsRejected() {
        pkcs11Token(3L, user(3L, Role.USER, "bob@keybridge.com"));
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.issueTestCertificate(request(new SigningPin(PIN)), admin));
        assertTrue(e.getMessage().contains("another user"));
        verifyNoInteractions(enroller);
    }

    @Test
    void unassignedTokenIsAccepted() {
        pkcs11Token(3L, null);
        enrollerSucceeds();
        assertDoesNotThrow(() -> service.issueTestCertificate(request(new SigningPin(PIN)), admin));
    }

    @Test
    void softwareTokenStillGetsAP12AndIgnoresPin() throws Exception {
        Token soft = new Token("TKN-001", "YubiKey");
        soft.setId(10L);
        when(tokens.findById(10L)).thenReturn(Optional.of(soft));

        service.issueTestCertificate(request(new SigningPin(PIN)), admin);

        var saved = org.mockito.ArgumentCaptor.forClass(Certificate.class);
        verify(certs).save(saved.capture());
        assertNotNull(saved.getValue().getKeystoreFile());
        assertTrue(Files.exists(caDir.resolve(saved.getValue().getKeystoreFile())));
        verifyNoInteractions(enroller);
    }

    @Test
    void jsonPinBindsAndIsRedactedInToStringAndAudit() throws Exception {
        IssueCertRequest req = new ObjectMapper().readValue(
                "{\"commonName\":\"Alice\",\"tokenId\":10,\"pin\":\"" + PIN + "\"}", IssueCertRequest.class);

        assertArrayEquals(PIN.toCharArray(), req.pin().chars());
        assertFalse(req.toString().contains(PIN));
        assertTrue(req.toString().contains("SigningPin[****]"));
        assertFalse(AuditService.describeArgs(new Object[]{req}).contains(PIN));
    }
}
