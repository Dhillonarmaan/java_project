# KeyBridge

A PKI and digital-signature platform: a test certificate authority, certificate and token lifecycle, DSC (digital signature certificate) applications, signing of any file or of PDFs, and a five-step verification pipeline.

Spring Boot 3.3 (Java 17, Bouncy Castle, PDFBox, JJWT) in `backend/`, React 18 + Vite in `frontend/`, MySQL 8.0.

> **This is a demonstration system.** It runs its own test root CA, which nothing outside KeyBridge trusts, and it signs on the server. See [Current limitations](#5-current-limitations).

---

## 1. Architecture

```
 Browser (React + Vite, :5173)
   │  /api/* proxied to :8080, JWT in the Authorization header
   ▼
 Spring Boot (:8080)
   ├─ security/    JwtAuthFilter → JwtService (HS256), @PreAuthorize role checks
   ├─ controller/  REST endpoints; return record DTOs from dto/, never entities
   ├─ audit/       @Audited → AuditAspect → audit_logs (secrets skipped by type)
   ├─ service/     certificates, tokens, DSC applications, service requests,
   │               signing, PDF signing, verification, expiry monitor
   └─ signing/     SigningProviderRegistry
                     ├─ P12SigningProvider     → keystore-data/user-<uuid>.p12
                     ├─ Pkcs11SigningProvider  → PKCS#11 device via SunPKCS11
                     └─ PdfSigner (PDFBox + Bouncy Castle CMS), uses either provider
   ▼
 MySQL 8.0: users, tokens, certificates (public data only), signed_documents,
            verification_logs, audit_logs, service_requests, dsc_applications
```

**Main flows**

| Flow | Path |
| :--- | :--- |
| Issue certificate | `CertificateService` → `TestCaService` (RSA-2048 key, certificate signed by the test root) → `.p12` file, or key generated on a PKCS#11 token |
| DSC application | `SUBMITTED → VERIFICATION → APPROVED → ISSUED`, or `REJECTED` from any non-final state. Approval needs every checklist document `RECEIVED`; `ISSUED` issues a certificate to the applicant |
| Sign a file | `SigningService` checks owner/ADMIN and status, then the certificate's `SigningProvider` produces `SHA256withRSA` → `.sig` (Base64) |
| Sign a PDF | `PdfSigningService` → `PdfSigner` builds a CMS `adbe.pkcs7.detached` signature whose signed attributes are signed by the same `SigningProvider`; PDFBox embeds it with the signer chain |
| Verify | `VerificationService`: parse → chain → revocation → validity → signature. Stops at the first failure; every attempt goes to `verification_logs` |
| Expiry | `ExpiryMonitorService` (daily 01:00, or `POST /api/certs/refresh-status`): `VALID → EXPIRING` (30 days) `→ EXPIRED`. DSC renewal reminders use the same 30-day window |

**Roles**

| Role | Can |
| :--- | :--- |
| ADMIN | Everything: issue/revoke certificates, manage tokens and users, review DSC applications and service requests |
| USER | Own certificates, tokens, signed documents, requests and DSC applications only; sign and verify |
| AUDITOR | Read-only: audit logs, verification logs, all certificates and applications |

Per-user scoping is done in the services (`Role.USER` sees only its own rows); role checks are `@PreAuthorize` on the controllers.

---

## 2. Design decisions

**Private keys never go in the database.** The `certificates` table holds only public data (subject, issuer, serial, validity, SHA-256 fingerprint, PEM) and a pointer to the key: `keystore_file` for a `.p12`, or `key_alias` for a key on a PKCS#11 token. Keys are never logged or returned by the API. `.p12` files live in `backend/keystore-data/` (git-ignored), protected by `KS_PASSWORD`.

**Signing providers.** Code that signs asks `SigningProviderRegistry` for the provider that matches the certificate's token (`SOFT_P12` or `PKCS11`) and gets back only signature bytes. The key never leaves the provider. PDF signing reuses this: Bouncy Castle builds the CMS structure, and the provider signs the CMS signed attributes, so PDF signing works with `.p12` and PKCS#11 keys without new key-handling code. On PKCS#11, keys are generated on the device as sensitive and non-extractable.

**PINs are never stored or logged.** A token PIN arrives as a `SigningPin`, not a `String`: its `toString()` prints `SigningPin[****]`, `close()` zeroes the characters after use, and `AuditService` skips `SigningPin`, `char[]`, `byte[]`, `MultipartFile` and `Key` arguments by type. The frontend clears the PIN field after each request.

**Test CA.** `TestCaService` creates a self-signed root (RSA-2048, 10 years, `root-ca.p12`, alias `keybridge-root`) on first start and issues end-entity certificates (`digitalSignature`, `nonRepudiation`) from it. Verification trusts exactly this root. That keeps the system self-contained for a demo, but none of its signatures are trusted outside KeyBridge.

**Verification as an ordered pipeline.** Each step can fail with its own result (`ERROR`, `UNTRUSTED`, `REVOKED`, `EXPIRED`, `NOT_YET_VALID`, `TAMPERED`, else `VALID`). The UI shows which step failed. Detached and PDF verification share the chain, revocation and validity steps. For PDFs, the signer certificate comes from the embedded signature, and bytes added after signing count as `TAMPERED`.

**State machines with explicit transitions.** Service requests and DSC applications only allow listed transitions; anything else is `409 Conflict`. The side effect (issuing, revoking, assigning a token) runs in the same transaction as the transition.

**Everything security-relevant is audited.** `@Audited` controller methods (issue, revoke, sign, PDF sign/verify, DSC apply/review, token changes) write an `audit_logs` row with the caller, IP and safe arguments.

---

## 3. Running locally

**Prerequisites:** Java 17+, Node 18+, Docker (for MySQL).

```bash
# 1. Secrets (once)
cd backend
cp .env.example .env          # set DB_PASSWORD, KS_PASSWORD, and JWT_SECRET (openssl rand -hex 32)

# 2. MySQL (first time: create the container with DB_PASSWORD from backend/.env)
docker run -d --name keybridge-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD="<DB_PASSWORD>" -e MYSQL_DATABASE=keybridge mysql:8.0
# later runs:
docker start keybridge-mysql

# 3. Backend on :8080 — must be started from backend/ so it finds .env and keystore-data/
cd backend && sh mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# 4. Frontend on :5173 (proxies /api to :8080)
cd frontend && npm install && npm run dev
```

| Variable | Required | Purpose |
| :--- | :--- | :--- |
| `DB_USERNAME` | no (default `root`) | MySQL user |
| `DB_PASSWORD` | yes | MySQL password |
| `JWT_SECRET` | yes | HS256 key, at least 32 bytes or startup fails |
| `KS_PASSWORD` | yes | Protects every `.p12`. Changing it makes existing keystores unreadable: delete `backend/keystore-data/*.p12` **and** reset the database together |
| `KEYSTORE_DIR` | no (default `./keystore-data`) | Where `.p12` files go |
| `PKCS11_LIBRARY` | no | PKCS#11 module, e.g. `/opt/homebrew/lib/softhsm/libsofthsm2.so`. Empty disables PKCS#11 |

**Seeded accounts** (created when the `users` table is empty; local use only):

| Role | Email | Password |
| :--- | :--- | :--- |
| ADMIN | `admin@keybridge.com` | `Admin@123` |
| USER | `user@keybridge.com` | `User@123` |
| AUDITOR | `auditor@keybridge.com` | `Audit@123` |

Tokens `TKN-001` … `TKN-005` are seeded as software (`.p12`) tokens.

**Tests** (JUnit 5 + Mockito, no database needed):

```bash
cd backend
sh mvnw test                      # all tests; PKCS#11 ones are skipped without a token
sh scripts/softhsm-init.sh        # optional: SoftHSM token for the PKCS#11 tests, prints the exact command
```

---

## 4. Demo script

Start MySQL, backend and frontend as in section 3, then open http://localhost:5173. Have any PDF at hand (e.g. print a web page to PDF).

**A. Apply for a DSC (user)**
1. Log in as `user@keybridge.com` / `User@123`.
2. Sidebar → **DSC Applications** → **New Application**.
3. Full name `Asha Verma`, keep the email, phone `+91 98765 43210`, organization `KeyBridge`, validity `1 year` → **Submit Application**.
4. The list shows the application as `SUBMITTED`, documents `0/4 received`. Click **View**: the timeline shows only `SUBMITTED`; the checklist shows four `MISSING` documents.
5. Log out (icon next to the name, top right).

**B. Review and issue (admin)**
1. Log in as `admin@keybridge.com` / `Admin@123` → **DSC Applications** → **Review** on Asha's application.
2. Click **Start Verification**. The timeline adds `VERIFICATION`.
3. Note that **Approve** is disabled and the page says to mark every document received.
4. Click **Received** for all four documents → **Approve** → **Issue Certificate**.
5. The timeline is complete, and the applicant panel shows *Certificate #N issued* with its serial and expiry date. **Certificates** now lists `Asha Verma` (`VALID`).
6. Log out.

**C. Sign a PDF (user)**
1. Log in as the user → **Sign Document**.
2. Select the `Asha Verma` certificate. Drop the PDF into *Document Payload*. Leave **Embed the signature in the PDF** checked.
3. **Execute Digital Signature** → **Download Signed PDF**, which saves `<name>-signed.pdf`.

**D. Verify, then tamper**
1. **Verify Signature** → switch to **Signed PDF** → drop `<name>-signed.pdf`.
2. **Execute 5-Stage Verification**: all five steps `PASS`, result **VALID**, and the signer subject ends in `CN=Asha Verma`.
3. Click **Simulate Tampering**, then verify again: the signature step fails, result **TAMPERED**.
4. *Detached variant:* on **Sign Document**, uncheck *Embed* (or use a `.txt` file) to get a `.sig`. On **Verify Signature** → **Document + .sig**, give the document, the `.sig` and the certificate.

**E. Revocation**
1. Log in as admin → **Certificates** → on `Asha Verma`, click the revoke icon (table view) or flip the card → **Revoke** → **Revoke Now**.
2. Verify the signed PDF again: it stops at step 3 with **REVOKED**.

**F. Renewal reminder**
The form offers whole years, so create a short-lived DSC through the API (as the user):
```bash
TOKEN=$(curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"user@keybridge.com","password":"User@123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -s localhost:8080/api/dsc-applications -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"fullName":"Asha Verma","email":"user@keybridge.com","validityDays":20}'
```
1. As admin, take the new application through B.2–B.4.
2. As the user, open **DSC Applications**: a *Renewal reminders* banner says the DSC expires in 19–20 days, and the bell icon shows *DSC(s) Due for Renewal*.
3. Click **Apply for renewal**: the form opens pre-filled (*Renew DSC (application #N)*). Submit it. The new application is marked `renewal`, and the reminder disappears while the renewal is in progress.

**G. Audit trail (auditor)**
1. Log in as `auditor@keybridge.com` / `Audit@123` → **Audit Logs**.
2. The newest entries come first, 10 per page. Type an action into the search box to filter, e.g. `PDF_SIGN`, `CERTIFICATE_REVOKE`, `DSC_STATUS_UPDATE`. Each row shows the user and IP, and expands to show the recorded arguments. No row contains a PIN or a key.

**H. Optional: PKCS#11 on SoftHSM**
This needs `brew install softhsm` and `PKCS11_LIBRARY` set before starting the backend. Run `sh scripts/softhsm-init.sh` and note the slot. As admin, open **Tokens** → create a token with key storage *PKCS#11* and that slot → **Certificates** → **Issue Test Certificate** onto it, entering the token PIN (`123456`). Signing with that certificate then asks for the PIN. The automated SoftHSM tests cover issuing, file signing, PDF signing and wrong-PIN rejection on a real token.

---

## 5. Current limitations

- **Server-side signing.** The backend holds or reaches every private key and signs for the user. `.p12` keys are unlocked with one server-wide `KS_PASSWORD`, so `.p12` signing needs no user secret beyond the login. A PKCS#11 device must be attached to the server, not the user's machine, and the token PIN travels to the server.
- **Test trust environment.** All certificates chain to KeyBridge's own test root. A PDF reader will show the signature as structurally valid but from an unknown or untrusted signer. These signatures have no legal standing as a DSC.
- **Revocation is a database lookup.** There is no CRL or OCSP endpoint, so only KeyBridge itself can see that a certificate was revoked. Revocation and expiry are checked at verification time, not at signing time. There is no signature timestamp (RFC 3161), so a valid signature becomes `EXPIRED` when its certificate expires.
- **PDF signatures** are basic CMS (`adbe.pkcs7.detached`), invisible, and without long-term validation data, so they are not full PAdES-B-LT. Only the last signature in a PDF is verified. Encrypted PDFs are rejected.
- **DSC checklist** records only that a document was received. Files are not uploaded or stored, and identity is not verified electronically.
- **Schema** is managed by Hibernate `ddl-auto: update`. There are no migrations.
- **No frontend tests**; backend tests use Mockito, not a real database.

## 6. Future work

1. **Local signing agent:** a small program on the user's machine that talks to their own token, so keys and PINs never reach the server. The server would only send hashes and receive signatures. The `SigningProvider` interface is the seam for this, and new features should not assume the server can always sign.
2. Per-user keystore passwords, or no `.p12` on the server at all, once the agent exists.
3. CRL and OCSP endpoints for the test CA, and RFC 3161 timestamps on signatures.
4. PAdES-B-LT for PDFs (DSS dictionary with chain and revocation data), visible signature appearance, multiple signatures.
5. Integration with a licensed CA, or at least an importable trust anchor, so signatures validate outside KeyBridge.
6. DSC document uploads with virus scanning, retention rules and reviewer comments per document; email reminders for renewals.
7. Flyway migrations, Testcontainers-based integration tests, and frontend tests.

---

## 7. Layout

```
backend/src/main/java/com/keybridge/
  audit/        @Audited + AuditAspect
  config/       security, CORS, app properties
  controller/   REST controllers (auth, certs, tokens, sign, verify, requests, dsc-applications, users, audit, dashboard)
  dto/          record DTOs
  entity/       JPA entities
  enums/        roles and statuses
  exception/    exceptions + GlobalExceptionHandler
  repository/   Spring Data repositories
  runner/       DataSeeder
  security/     JWT filter and service
  service/      business logic, TestCaService, VerificationService, PdfSigningService, DscApplicationService
  signing/      SigningProvider implementations, PKCS#11 slots/enroller, PdfSigner, SigningPin
backend/scripts/softhsm-init.sh   SoftHSM token for the PKCS#11 tests
frontend/src/
  pages/        Dashboard, Certificates, Tokens, SignDocument, VerifyDocument, Requests,
                DscApplications, DscApplicationDetail, AuditLogs, Users, Login
  components/   ui/, crypto/, charts/
  layout/       AppShell, Sidebar, Topbar, CommandPalette, NotificationDrawer
```

`PROJECT_UNDERSTANDING.md` has the full specification (RBAC matrix, schema, flows). `CLAUDE.md` has notes for working on the code.
