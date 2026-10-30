package com.keybridge.service;

import jakarta.annotation.PostConstruct;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.UUID;

@Service
public class TestCaService {

    private static final Logger log = LoggerFactory.getLogger(TestCaService.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final String dir;
    private final String ksPass;
    private final String rootAlias;

    public TestCaService(
            @Value("${keybridge.keystore.dir:./keystore-data}") String dir,
            @Value("${keybridge.keystore.password}") String ksPass,
            @Value("${keybridge.keystore.root-alias:keybridge-root}") String rootAlias
    ) {
        this.dir = dir;
        this.ksPass = ksPass;
        this.rootAlias = rootAlias;
    }

    public Path rootPath() {
        return Path.of(dir, "root-ca.p12");
    }

    @PostConstruct
    public void ensureRoot() throws Exception {
        Files.createDirectories(Path.of(dir));
        if (Files.exists(rootPath())) {
            log.info("KeyBridge Root CA already exists at {}", rootPath().toAbsolutePath());
            return;
        }

        log.info("Generating KeyBridge Test Root CA at {}", rootPath().toAbsolutePath());
        KeyPair kp = newKeyPair();
        X500Name name = new X500Name("CN=KeyBridge Test Root CA, O=KeyBridge, C=IN");
        X509Certificate cert = build(name, name, kp.getPublic(), kp.getPrivate(), 3650, true);
        saveP12(rootPath(), rootAlias, kp.getPrivate(), new Certificate[]{cert});
        log.info("KeyBridge Test Root CA successfully created.");
    }

    /** Generates a software key pair, issues a certificate for it and saves both to a new user-<uuid>.p12. */
    public IssuedCert issue(String commonName, String email, int validDays) throws Exception {
        KeyPair kp = newKeyPair();
        X509Certificate cert = issueFor(kp.getPublic(), commonName, email, validDays);

        String file = "user-" + UUID.randomUUID() + ".p12";
        saveP12(Path.of(dir, file), "user", kp.getPrivate(), new Certificate[]{cert, rootCert()});
        return new IssuedCert(cert, file);
    }

    /**
     * Issues a user certificate for a public key whose private key lives
     * elsewhere (e.g. on a PKCS#11 device). Nothing is written to disk.
     */
    public X509Certificate issueFor(PublicKey publicKey, String commonName, String email, int validDays) throws Exception {
        KeyStore rootKs = load(rootPath());
        PrivateKey rootKey = (PrivateKey) rootKs.getKey(rootAlias, ksPass.toCharArray());
        X509Certificate root = (X509Certificate) rootKs.getCertificate(rootAlias);

        X500Name subject = new X500Name("CN=" + commonName + ", E=" + email + ", O=KeyBridge Users");
        X500Name issuer = new org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(root).getSubject();
        return build(issuer, subject, publicKey, rootKey, validDays, false);
    }

    public X509Certificate rootCert() throws Exception {
        return (X509Certificate) load(rootPath()).getCertificate(rootAlias);
    }

    private KeyPair newKeyPair() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    private X509Certificate build(
            X500Name issuer,
            X500Name subject,
            PublicKey pub,
            PrivateKey signKey,
            int days,
            boolean ca
    ) throws Exception {
        Date from = new Date(System.currentTimeMillis() - 60_000);
        Date to = new Date(System.currentTimeMillis() + days * 86_400_000L);
        JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                issuer,
                new BigInteger(64, new SecureRandom()),
                from,
                to,
                subject,
                pub
        );
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        b.addExtension(
                Extension.keyUsage,
                true,
                ca
                    ? new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign)
                    : new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation)
        );
        ContentSigner cs = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(signKey);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(b.build(cs));
    }

    private void saveP12(Path p, String alias, PrivateKey k, Certificate[] chain) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(alias, k, ksPass.toCharArray(), chain);
        try (OutputStream o = Files.newOutputStream(p)) {
            ks.store(o, ksPass.toCharArray());
        }
    }

    public KeyStore load(Path p) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream i = Files.newInputStream(p)) {
            ks.load(i, ksPass.toCharArray());
        }
        return ks;
    }

    public record IssuedCert(X509Certificate cert, String keystoreFile) {}
}
