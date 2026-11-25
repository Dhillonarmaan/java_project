# KeyBridge code walkthrough

How the code works, flow by flow, with the exact places to point at. Paths are relative to `backend/src/main/java/com/keybridge/` unless they start with `frontend/`.

Read section 1, then trace sections 2–5 in your editor with the file open. That is the core: **sign → verify → tamper**. Sections 6–9 cover the rest. Section 10 lists questions to expect, and section 11 is how to practise.

---

## 1. The 30-second version

KeyBridge is a small PKI. It runs its **own certificate authority** (a test root CA), issues **X.509 certificates** to users, keeps their **private keys out of the database**, lets users **sign files** (any file as a separate `.sig`, or a PDF with the signature inside it), and **verifies** signatures with a five-step pipeline that says exactly why a signature is bad: untrusted issuer, revoked, expired, or tampered content.

Spring Boot backend, React frontend, MySQL. Roles: ADMIN, USER, AUDITOR.

---

## 2. One request, end to end (login and every call after it)

1. **Login.** `AuthController.login` (`controller/AuthController.java:22`) → `AuthService.login` (`service/AuthService.java:32`) checks the password with BCrypt (`:40`; the encoder is defined in `config/SecurityConfig.java:47`).
2. **Token.** `JwtService.generate` (`security/JwtService.java:40`) builds a JWT: subject = email, claims = role, userId, name, expiry 120 minutes (`application.yml`). It is signed with HS256 using `JWT_SECRET` (`:36`, which refuses keys under 32 bytes).
3. **Browser.** The token is kept in `localStorage` and added to every call as `Authorization: Bearer …` (`frontend/src/api/axios.js:10`). A 401 logs the user out (`:18`).
4. **Every later request.** `JwtAuthFilter.doFilterInternal` (`security/JwtAuthFilter.java:31`) validates the token, loads the user and puts them in Spring's `SecurityContext`. Only `/api/auth/**` is public (`config/SecurityConfig.java:37`).
5. **Authorisation in two layers:**
   - *Role:* `@PreAuthorize("hasRole('ADMIN')")` on controller methods, e.g. issuing and revoking certificates.
   - *Ownership:* services filter rows by user, e.g. `CertificateService.java:57`: a `USER` only gets their own certificates; others get all.
6. **Response.** Controllers return Java `record` DTOs (`dto/`), never JPA entities, so internal fields can't leak by accident. Errors are thrown as `NotFoundException` / `BadRequestException` / `ForbiddenException` / `InvalidStateTransitionException`, and `exception/GlobalExceptionHandler.java` turns them into 404/400/403/409 JSON.

> **Say it like this:** "The token proves who you are; `@PreAuthorize` checks your role; the service checks that the row is yours."

---

## 3. Issuing a certificate

`service/TestCaService.java`

- **Root CA:** `ensureRoot()` (`:59`) runs at startup. If `keystore-data/root-ca.p12` doesn't exist, it generates an RSA-2048 key pair and a **self-signed** certificate valid for 10 years, and saves both in a password-protected PKCS#12 file.
- **User certificate:** `issue()` (`:75`) generates a new RSA-2048 key pair, then `build()` (`:108`) creates an X.509 v3 certificate whose subject is the user and whose issuer is the root. The root's private key signs it with SHA256withRSA. Key usage is `digitalSignature | nonRepudiation` (`:132`), meaning "this key is for signing".
- **Storage:** the user's key and certificate go into a new file `user-<uuid>.p12` (`:79`). `CertificateService.issueSoftware` (`service/CertificateService.java:115`) stores **only public data** in the `certificates` table (subject, issuer, serial, dates, SHA-256 fingerprint, PEM) plus the *file name* of the `.p12`.
- **Hardware token variant:** `issueOnPkcs11Token` (`:135`). The key pair is generated **on the device** and cannot be exported; only the certificate is stored in the database, with `keyAlias` pointing at the key on the token.

> **Key point:** the database could leak and no private key would leak with it.

---

## 4. Signing a file (`POST /api/sign`)

`service/SigningService.java:45`

1. `usableCertificate` (`:74`): the certificate must exist, the caller must **own it or be ADMIN**, and it must not be **REVOKED or EXPIRED**.
2. `signingProviders.forCertificate(cert).sign(cert, data, pin)` (`:55`). The registry (`signing/SigningProviderRegistry.java:26`) picks the provider from where the key lives (`storageOf`, `:39`):
   - `P12SigningProvider.sign` (`signing/P12SigningProvider.java:49`) opens the `.p12` with `KS_PASSWORD`, takes the private key and calls `CryptoService.sign`.
   - `Pkcs11SigningProvider.sign` (`signing/Pkcs11SigningProvider.java:44`) logs into the token with the user's PIN, checks that the certificate on the device matches the database fingerprint, and asks **the device** to sign.
3. `CryptoService.sign` (`service/CryptoService.java:21`) is three lines of Java: `Signature.getInstance("SHA256withRSA")`, `initSign(key)`, `update(data)`, `sign()`.
4. A `signed_documents` row records the file name, its SHA-256 and the Base64 signature. The signature is returned to the browser, which downloads it as a `.sig` file.

**What "SHA256withRSA" actually does:**
1. Hash the whole file with SHA-256 → a 32-byte fingerprint of the content.
2. Pad it (PKCS#1 v1.5) and apply RSA with the **private** key → the signature (256 bytes for RSA-2048).

Anyone with the **public** key (in the certificate) can check it, but only the private key could have produced it.

> **Why providers?** The signing code never touches a key directly; it asks "the thing that holds this certificate's key" to sign. Supporting a new key store (e.g. a future agent on the user's laptop) means adding one class, not changing signing or PDF code.

---

## 5. Verifying, and why tampering is caught

`service/VerificationService.java:61` (`POST /api/verify`). The checks run in order, and the first failure stops the run with its own result:

| # | Step | Code | Fails as |
|---|---|---|---|
| 1 | **Parse** the certificate (uploaded, or from the DB by id) | `:61` onwards | `ERROR` |
| 2 | **Chain:** `cert.verify(root.getPublicKey())` checks the CA's signature *on the certificate* | `:243` | `UNTRUSTED` |
| 3 | **Revocation:** look up the certificate's SHA-256 fingerprint in the DB; status REVOKED? | `:254` | `REVOKED` |
| 4 | **Validity dates:** `cert.checkValidity()` | `:264` | `EXPIRED` / `NOT_YET_VALID` |
| 5 | **Signature:** `CryptoService.verify(document, signature, cert.getPublicKey())` | `:135`, `CryptoService.java:42` | `TAMPERED` |

Steps 2–4 live in one helper, `checkTrust` (`:239`), shared by file and PDF verification. Every attempt is saved to `verification_logs` (`log`, `:278`).

**Why the order matters:** there's no point checking the maths if the certificate isn't from our CA (step 2), because anyone can make a key pair and sign. Revocation comes before dates, because a revoked certificate is bad even when it is in date.

**Why one changed byte fails:** verification re-hashes the document *as it is now*. Change one byte and SHA-256 gives a completely different hash, which no longer matches the hash sealed inside the signature, so `Signature.verify` returns false → `TAMPERED`. The *Simulate Tampering* button appends bytes to the file in the browser to show this (`frontend/src/pages/VerifyDocument.jsx`).

> **Hashing vs signing vs encryption:** a hash detects *change*, but anyone can recompute one. A signature binds the hash to a *private key*, so it proves *who* and *unchanged*. Nothing here is encrypted: the document stays readable; we only prove its integrity and origin.

---

## 6. PDF signing (`POST /api/sign/pdf`, `/api/verify/pdf`)

The signature goes *inside* the PDF instead of a separate `.sig`, in the standard format PDF readers understand (CMS / PKCS#7, `adbe.pkcs7.detached`).

- `PdfSigningService.sign` (`service/PdfSigningService.java:56`) uses the same `usableCertificate` check, then calls `PdfSigner.sign` (`signing/PdfSigner.java:43`).
- **PDFBox** reserves a gap in the file for the signature and hands us *every byte except that gap* (the **ByteRange**).
- **Bouncy Castle** builds the CMS structure: the signer's certificate chain, plus "signed attributes" that include the SHA-256 of those bytes.
- **The trick:** `DelegatingContentSigner` (`:123`). Bouncy Castle asks it to sign the attributes, and `getSignature()` (`:146`) passes them to **the same `SigningProvider`** as in section 4. So PDF signing works with `.p12` and hardware-token keys with no new key-handling code.
- **Verification** (`VerificationService.verifyPdf`, `:161`): read the last signature (`PdfSigner.lastSignature`, `PdfSigner.java:83`), take the signer certificate **from the signature itself**, run the same `checkTrust`, then:
  - if bytes were added after signing, the ByteRange no longer covers the whole file → `TAMPERED` (`:205`);
  - otherwise Bouncy Castle checks the digest and the RSA signature (`:211`).

---

## 7. Where the security rules are enforced

| Rule | Where |
|---|---|
| No private keys in the DB, logs or API | Only public fields plus `keystore_file` / `key_alias` in `entity/Certificate.java`; providers return signature bytes only |
| PINs never stored or logged | `signing/SigningPin.java:10`: `toString()` prints `SigningPin[****]` (`:34`), `close()` zeroes the characters (`:29`); services use `try (pin)` so it's always cleared |
| Audit, but never secrets | `@Audited` → `audit/AuditAspect.java:19` (`@AfterReturning`) → `AuditService.record` (`service/AuditService.java:38`). Arguments of type `SigningPin`, `char[]`, `byte[]`, `MultipartFile`, `Key` are skipped (`:91`); strings containing "password" or "@", or 120+ characters long, are skipped too (`:82`) |
| Hardware keys can't be exported | PKCS#11 keys are generated on the device as sensitive and non-extractable (`signing/Pkcs11Slots.java`); a SoftHSM test reads the device attributes to prove it |
| One PIN login per request | `Pkcs11Slots.withLogin` (`:54`) logs in, does the work, always logs out; a wrong PIN → 400 "Invalid PIN" (`:80`) |
| Users only touch their own data | service-level `Role.USER` filters, e.g. `CertificateService.java:57`; owner check in `SigningService.java:74` |

---

## 8. State machines: DSC applications and service requests

`service/DscApplicationService.java`

- Allowed moves are a map (`TRANSITIONS`, `:50`): `SUBMITTED → VERIFICATION → APPROVED → ISSUED`, and `REJECTED` from the first three.
- `updateStatus` (`:145`) rejects any other move with `InvalidStateTransitionException` → HTTP **409**. Rejecting needs a reason. Approval needs all four documents `RECEIVED` (`:165`).
- `ISSUED` actually issues a certificate (`issueCertificate`, `:225`) inside the same transaction: the status change and the certificate either both happen or neither does.
- Renewal reminders (`renewalReminders`, `:187`): issued applications whose certificate expires within 30 days, unless a renewal is already in progress.
- Service requests (`service/ServiceRequestService.java:98`) follow the same pattern: `PENDING → APPROVED → COMPLETED`, or `REJECTED`.
- Expiry: `ExpiryMonitorService.refreshStatuses` (`service/ExpiryMonitorService.java:34`) runs daily at 01:00 (`@Scheduled`, `:32`) and moves certificates VALID → EXPIRING → EXPIRED.

---

## 9. Tests

`backend/src/test/java`, run with `sh mvnw test`: 88 tests, plain JUnit 5 + Mockito, no database. Services are built by hand with mocked repositories and a real `TestCaService` in a temp folder, so the cryptography is real. Worth knowing:

- `VerificationServiceTest`: valid, tampered, revoked, expired, untrusted.
- `signing/PdfSigningTest`: PDF sign → VALID; edited bytes → TAMPERED; appended content → TAMPERED; revoked; other CA → UNTRUSTED; the PIN is zeroed.
- `DscApplicationServiceTest`: the whole timeline, illegal moves → 409, approval blocked by missing documents, users can't see others' applications.
- `signing/Pkcs11*SoftHsmTest`: the same flows on a real (software) hardware token; skipped unless a SoftHSM slot is given.

---

## 10. Questions you are likely to get

**Where is the private key? Can I see it in the database?**
No. The DB has the certificate (public) and either a `.p12` file name or a token key alias. `.p12` files are on disk, password-protected, and git-ignored. Keys on a hardware token never leave it.

**What exactly gets signed?**
The SHA-256 hash of the file's bytes, signed with RSA-2048 (`SHA256withRSA`). For PDFs, the hash of every byte except the signature slot, wrapped in a CMS structure.

**How do you detect tampering?**
Re-hash the document now and check it against the signature using the public key. Any change gives a different hash, so verification fails (`VerificationService.java:135`).

**Why do you need the certificate to verify?**
It carries the public key and the signer's identity, and its CA signature lets us check that *our* CA vouched for that key (step 2).

**What does "chain of trust" mean in your code?**
`cert.verify(rootPublicKey)`: the certificate must have been signed by our root CA. A self-made certificate fails with `UNTRUSTED`.

**What if a certificate is revoked after a document was signed?**
Verification reports `REVOKED`. There's no trusted timestamp proving *when* the signature was made, so we can't say "it was valid at signing time". That is a known limitation; the fix is RFC 3161 timestamps.

**Can a user sign with someone else's certificate?**
No. `usableCertificate` (`SigningService.java:74`) requires owner or ADMIN, and refuses revoked or expired certificates.

**How are roles enforced?**
Two layers: `@PreAuthorize` on controllers for the role, and service-level filtering for ownership.

**How do you make sure a PIN is never logged?**
It's never a `String`. It's a `SigningPin` whose `toString()` is masked and whose characters are zeroed after use, and the audit code skips that type entirely (`AuditService.java:91`).

**What are PKCS#12 and PKCS#11?**
PKCS#12 (`.p12`) is a password-protected *file* holding a key and certificate. PKCS#11 is the standard *API for hardware tokens/HSMs*: you ask the device to sign, and the key never leaves it.

**Why JWT? What if a token is stolen?**
Stateless: the server needs no session store, and the token carries user and role, signed with HS256. A stolen token works until it expires (120 minutes), and there's no revocation list for tokens. That's a known trade-off.

**How does audit logging work?**
Spring AOP: methods annotated `@Audited("ACTION")` are intercepted after they return successfully (`AuditAspect.java:19`), and a row is written with the user, IP and safe arguments.

**Why return DTO records and not entities?**
So the API exposes only chosen fields (never, for example, a password hash or key path by accident) and isn't tied to the database layout.

**Why 409 for an illegal state change?**
The request is valid but conflicts with the current state of the resource, which is what 409 Conflict means.

**Where's the signature in a signed PDF?**
In a signature dictionary inside the PDF: a reserved hex slot holds the CMS blob, and `/ByteRange` lists exactly which bytes are covered (everything except that slot).

**What are the weaknesses? What would you do next?**
Be honest: signing happens on the server, which holds or reaches every key, and `.p12` keys share one server password. The CA is a test CA that nobody else trusts. Revocation is a database lookup, with no CRL or OCSP. There are no timestamps. Next steps: a local signing agent so keys and PINs stay on the user's machine (the `SigningProvider` interface is the seam for it), a real or importable CA, OCSP, timestamps, and PAdES-LT for PDFs.

**How did you test it?**
JUnit + Mockito with real cryptography (section 9), SoftHSM for hardware-token paths, and the README demo run end to end from an empty database.

---

## 11. How to practise (about 2 hours)

1. **Trace with the debugger (45 min).** Start the backend in your IDE in debug mode. Put breakpoints at `SigningService.java:55`, `CryptoService.java:21`, `VerificationService.java:243` and `:135`. Sign and verify in the UI, step through, and watch `data`, `sig`, and the `steps` list fill up.
2. **Break it on purpose (20 min).** Tamper (step 5 fails). Revoke the certificate (step 3). Upload a certificate from somewhere else (step 2). Predict the result before clicking.
3. **Explain out loud without notes (30 min).** Use the five prompts below, each in under a minute. If you get stuck, reread that section and try again.
   - What happens from clicking *Execute Digital Signature* to downloading the `.sig`?
   - Walk me through the five verification steps and why they're in that order.
   - Why can't the database leak a private key?
   - How does the PIN stay out of logs?
   - What would you change to make this production-grade?
4. **Practise the questions (25 min).** Have someone read section 10's questions in random order. Answer first, then compare with the written answer.

**Rule of thumb in the viva:** answer in one sentence, then point at the file. "It's checked in `usableCertificate`. Let me show you." Opening the right file quickly earns more trust than a long explanation.
