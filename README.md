# KeyBridge: PKI, Cryptographic Tokens & Digital Signature Platform

> **Enterprise-grade Public Key Infrastructure (PKI), X.509 Certificate Management, Hardware/Soft Token Lifecycle, RSA-2048 Digital Signatures, and 5-Stage Cryptographic Verification Engine.**

---

## 1. System Overview & Core Architecture

KeyBridge is a production-ready enterprise security platform designed for strict regulatory compliance, cryptographic auditability, and zero-trust key management.

```
       +-------------------------------------------------------------+
       |               React 18 + Vite Glassmorphism UI             |
       |  (Framer Motion Springs, Recharts Analytics, Sonner Alerts) |
       +------------------------------+------------------------------+
                                      | HTTP REST / JSON
                                      v
       +-------------------------------------------------------------+
       |              Spring Boot 3.3.4 Security Gateway             |
       |    - Stateless JWT (HMAC-SHA256) Security Filter            |
       |    - Role-Based Access Control (ADMIN, USER, AUDITOR)       |
       |    - Spring AOP Audit Interceptor (@Audited)                |
       +------------------------------+------------------------------+
                                      |
         +----------------------------+----------------------------+
         |                                                         |
         v                                                         v
+-------------------------------+                       +-------------------------------+
|     Bouncy Castle PKI Engine  |                       |   MySQL 8.0 Persistence       |
| - Root CA Generator (@PostCon)|                       | - Users & Auth Credentials    |
| - User X.509 V3 Issuer        |                       | - Token Inventory (PKCS#11)   |
| - RSA-2048 Signer & Verifier  |                       | - Certificate Metadata & CRL  |
| - PKCS#12 (.p12) File Keystore|                       | - Signed Documents Index      |
|   (Strict ZERO Keys in DB!)   |                       | - Verification Audit Trail    |
+-------------------------------+                       | - Service Request State Mch   |
                                                        +-------------------------------+
```

---

## 2. Core Security & Cryptographic Principles

1. **Zero Private Keys in Database:**
   - As per ISO/IEC 27001 and FIPS guidelines, private keys are **never** stored in database tables or logs.
   - Private keys are stored strictly in password-protected PKCS#12 (`.p12`) keystores inside `keystore-data/`, which is excluded from Git tracking via the root `.gitignore`.
2. **Cryptographic Standards:**
   - **Digest Algorithm:** SHA-256 (`FIPS 180-4`)
   - **Digital Signature Scheme:** RSA 2048-bit with PKCS#1 v1.5 padding (`SHA256withRSA`)
   - **X.509 Certificates:** RFC 5280 v3 format with Subject Key Identifier and Authority Key Identifier extensions.
3. **5-Stage Ordered Verification Engine:**
   - **Stage 1 (Format & Parsing):** Parses X.509 PEM/DER bytes into an ASN.1 certificate structure.
   - **Stage 2 (Chain of Trust):** Cryptographically verifies the certificate's signature against the KeyBridge Test Root CA public key (`cert.verify(rootPublicKey)`).
   - **Stage 3 (CRL & Revocation):** Verifies the certificate's SHA-256 fingerprint against the database revocation list.
   - **Stage 4 (Temporal Validity):** Asserts the current timestamp falls between `notBefore` and `notAfter`.
   - **Stage 5 (Signature Match):** Recalculates document SHA-256 hash and verifies RSA mathematical match using the signer's public key.
4. **Service Request State Machine:**
   - Strict transition guards: `PENDING` &rarr; `APPROVED` &rarr; `COMPLETED` or `REJECTED`.
   - Illegal state jumps throw `409 Conflict`.
   - Administrative transition to `COMPLETED` automatically executes the underlying action (e.g. revoking certificate, extending validity, enrolling token).

---

## 3. Seeded Accounts & Default Credentials

On initial startup, `DataSeeder` seeds the system with pre-configured users and hardware tokens:

| Role | Username / Email | Password | Allowed Capabilities |
| :--- | :--- | :--- | :--- |
| **ADMIN** | `admin@keybridge.com` | `Admin@123` | Full PKI management, token enrollment/assignment, approve requests, manage users |
| **USER** | `user@keybridge.com` | `User@123` | Request certs/tokens, sign documents, verify signatures, submit requests |
| **AUDITOR** | `auditor@keybridge.com` | `Audit@123` | Read-only access to security audit trails and verification logs |

*Default PKCS#12 Keystore PIN for seeded tokens & certificates:* `KeyBridge@123`

---

## 4. How to Run the Platform

### Prerequisites
- **Java:** Eclipse Adoptium OpenJDK 25 LTS or Java 17+
- **Database:** MySQL 8.0 running on `localhost:3306` with database `keybridge`
- **Node.js:** v18+ or v22+

### Configuration (secrets)
Secrets are not stored in `application.yml`. Create `backend/.env` from the example and fill in real values:
```bash
cd backend
cp .env.example .env
openssl rand -hex 32   # use the output as JWT_SECRET
```
| Variable | Required | Purpose |
| :--- | :--- | :--- |
| `DB_USERNAME` | No (default `root`) | MySQL user |
| `DB_PASSWORD` | Yes | MySQL password |
| `JWT_SECRET` | Yes | HMAC-SHA256 JWT signing key (32+ characters) |
| `KS_PASSWORD` | Yes | Password protecting the `.p12` keystores |
| `KEYSTORE_DIR` | No (default `./keystore-data`) | Keystore directory |

`backend/.env` is git-ignored and loaded automatically when the backend is started from `backend/`. Real environment variables override it. If you change `KS_PASSWORD`, delete `backend/keystore-data/` and reset the database so the keystores and certificate records are regenerated together.

### Backend Startup
```powershell
# In PowerShell:
cd E:\java_project\backend
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"
.\mvnw.cmd spring-boot:run
```
*Backend runs on: `http://localhost:8080`*

### Frontend Startup
```powershell
# In PowerShell:
cd E:\java_project\frontend
npm run dev
```
*Frontend runs on: `http://localhost:5173` (with `/api` proxy configured to `http://localhost:8080`)*

---

## 5. End-to-End Cryptographic Validation Scenarios

### Scenario 1: Issue Test Certificate & Sign Document
1. Log in as `admin@keybridge.com`.
2. Navigate to **Certificates** &rarr; click **Issue Test Certificate**.
3. Fill in Common Name (e.g., `Alice Smith`), choose validity days, and click **Issue Certificate**.
4. Navigate to **Sign Document** &rarr; select Alice's certificate &rarr; drag-and-drop a document (e.g., `.txt` or `.pdf`) &rarr; enter PIN `KeyBridge@123` &rarr; click **Execute Digital Signature**.
5. Watch the 4-step pipeline execute, then download the generated `.sig` signature file.

### Scenario 2: Test 1-Byte Tamper Detection
1. Navigate to **Verify Signature**.
2. Upload the original document, the `.sig` file, and select Alice's certificate.
3. Click **Execute 5-Stage Verification** &rarr; result is **VALID** with confetti celebration!
4. Click **Simulate Tampering (1-Byte Modified)**.
5. Click **Execute 5-Stage Verification** again &rarr; engine detects payload divergence and instantly reports **TAMPERED** (failure at Stage 5).

### Scenario 3: Certificate Revocation & Verification Blocking
1. Navigate to **Certificates** &rarr; flip Alice's card &rarr; click **Revoke** &rarr; select reason `KEY_COMPROMISE`.
2. Return to **Verify Signature** and verify the document again.
3. Engine halts at **Stage 3 (Revocation Check)** and rejects verification with **REVOKED**.

### Scenario 4: Service Request State Machine
1. Log in as `user@keybridge.com` &rarr; navigate to **Service Requests** &rarr; submit `RENEW_CERT`.
2. Log in as `admin@keybridge.com` &rarr; see request in the `PENDING` column.
3. Click **Approve** (moves to `APPROVED`), then click **Complete Action** (moves to `COMPLETED` and executes renewal).

---

## 6. Project Directory Blueprint

```
java_project/
├── backend/
│   ├── pom.xml
│   ├── mvnw.cmd
│   └── src/
│       ├── main/
│       │   ├── java/com/keybridge/
│       │   │   ├── audit/          # AOP @Audited aspect & service
│       │   │   ├── config/         # Security & CORS configuration
│       │   │   ├── controller/     # 8 REST controllers
│       │   │   ├── dto/            # Immutable record DTOs
│       │   │   ├── entity/         # 7 JPA entities
│       │   │   ├── enums/          # Role, Status, VerifyResult enums
│       │   │   ├── exception/      # GlobalExceptionHandler & custom exceptions
│       │   │   ├── repository/     # 7 Spring Data JPA repositories
│       │   │   ├── runner/         # DataSeeder
│       │   │   ├── security/       # JWT filter & UserPrincipal
│       │   │   └── service/        # Crypto, CA, Cert, Token, Verify services
│       │   └── resources/
│       │       └── application.yml
│       └── test/                   # 5 test suites (13/13 passing)
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   └── src/
│       ├── api/axios.js
│       ├── context/                # AuthContext & ThemeContext
│       ├── components/
│       │   ├── charts/             # AreaChart, Donut, BarChart
│       │   ├── crypto/             # CertFlipCard, PipelineSteps, VerifyResultCard, TamperDemo
│       │   └── ui/                 # DataTable, StatCard, Modal, Drawer, FileDrop, Button
│       ├── layout/                 # AppShell, Sidebar, Topbar, CommandPalette, NotificationDrawer
│       └── pages/                  # Login, Dashboard, Certs, Tokens, Sign, Verify, Requests, Audit, Users
├── keystore-data/                  # .gitignore protected PKCS#12 keystores
├── PROJECT_UNDERSTANDING.md        # Comprehensive system specification & decisions
├── task.md                         # Phased master checklist
└── README.md                       # Operational runbook
```
