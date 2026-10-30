# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

KeyBridge: a PKI / digital-signature platform. Spring Boot 3.3 backend (Java 17, Maven, Bouncy Castle, JJWT) in `backend/`, React 18 + Vite frontend in `frontend/`, MySQL 8.0. `PROJECT_UNDERSTANDING.md` is the full spec (RBAC matrix, schema, flows); `README.md` is the runbook.

## Rules

- **Work only on the `dev` branch.** Never commit to, merge into, or push `main`, and never rewrite history. Push with `git push origin dev`. `origin` is the user's fork; `upstream` is the original repo and is read-only.
- **Private keys never go in the database**, in logs, or in API responses. The DB stores only public certificate data and the keystore *file name* (`certificates.keystore_file`).
- **Never commit `.env` or key material** (`*.p12`, `*.jks`, `*.key`, `backend/keystore-data/`). These are ignored by the root `.gitignore`; check `git status` before committing.
- Controllers return Java `record` DTOs from `dto/`, never JPA entities.
- **PINs are never stored or logged.** Take them as `SigningPin` (redacted `toString()`, zeroed with `close()`), never as a plain `String`.

## Running locally

```bash
# MySQL (container created once; on later runs just start it)
docker start keybridge-mysql
# first time only, using DB_PASSWORD from backend/.env:
#   docker run -d --name keybridge-mysql -p 3306:3306 \
#     -e MYSQL_ROOT_PASSWORD="$DB_PASSWORD" -e MYSQL_DATABASE=keybridge mysql:8.0

# Backend on :8080. Must be started from backend/ (see .env and keystore-data below)
cd backend && sh mvnw spring-boot:run

# Frontend on :5173, proxies /api to :8080
cd frontend && npm install && npm run dev
npm run build        # production build (vite build)
```

`mvnw` is not executable in git (committed from Windows); invoke it as `sh mvnw` rather than changing its mode.

## Tests

```bash
cd backend
sh mvnw test                                               # all tests
sh mvnw test -Dtest=AuthTest                               # one class
sh mvnw test -Dtest=AuthTest#shortSecretFailsInsteadOfBeingPadded   # one method
```

PKCS#11 tests run against a throwaway SoftHSM token (`brew install softhsm`) and are skipped unless `-Dkeybridge.pkcs11.slot` is set:

```bash
cd backend
sh scripts/softhsm-init.sh     # creates a token under target/softhsm and prints the exact test command
SOFTHSM2_CONF=$PWD/target/softhsm/softhsm2.conf sh mvnw test \
  -Dkeybridge.pkcs11.library=/opt/homebrew/lib/softhsm/libsofthsm2.so \
  -Dkeybridge.pkcs11.slot=<slot printed by the script> -Dkeybridge.pkcs11.pin=123456
```

Re-run the script after `mvn clean` (it deletes `target/softhsm`); the slot number changes each time.

Tests are plain JUnit 5 + Mockito: no Spring context, no database. Services are constructed directly (e.g. `new TestCaService(tempDir.toString(), "changeit", "keybridge-root")` with a `@TempDir`), so tests don't need `.env` or MySQL. There is no frontend test suite or linter.

## Configuration / environment variables

Secrets are not in `application.yml`. Copy `backend/.env.example` to `backend/.env` (git-ignored). `application.yml` imports `optional:file:.env[.properties]` relative to the working directory, which is why the backend must run from `backend/`. Real environment variables override `.env`.

| Variable | Required | Notes |
| --- | --- | --- |
| `DB_USERNAME` | no (default `root`) | |
| `DB_PASSWORD` | yes | |
| `JWT_SECRET` | yes | ≥ 32 bytes or startup fails (`JwtService`). `openssl rand -hex 32` |
| `KS_PASSWORD` | yes | Protects every `.p12` keystore |
| `KEYSTORE_DIR` | no (default `./keystore-data`) | Relative to the working directory, i.e. `backend/keystore-data/` |
| `PKCS11_LIBRARY` | no | PKCS#11 module path (e.g. `/opt/homebrew/lib/softhsm/libsofthsm2.so`). Empty disables PKCS#11 signing |

Changing `KS_PASSWORD` makes existing `.p12` files unreadable. Keystores and the DB must be reset together: delete `backend/keystore-data/*.p12` **and** recreate the MySQL database/container, since `certificates.keystore_file` rows point at those files. Schema is managed by Hibernate `ddl-auto: update` (no migrations); `DataSeeder` seeds users and tokens only when the `users` table is empty.

## Architecture notes

**Signing is server-side for now.** The backend holds or reaches every private key and signs on the user's behalf; a PKCS#11 device must be attached to the server, not the user's machine. The intended future fix is a local signing agent on the user's machine that talks to their own token, so keys and PINs never reach the server. Keep that in mind: don't design new features that assume the server can always sign.

**Key storage and signing.** `SigningService.sign` checks ownership (owner or ADMIN) and status (not REVOKED/EXPIRED), then delegates to a `SigningProvider` (`signing/`) chosen by `SigningProviderRegistry` from the certificate's token: `Token.keyStorage` (`SOFT_P12` or `PKCS11`; null and certificates without a token mean `SOFT_P12`).
- `P12SigningProvider`: loads `keystore-data/<Certificate.keystoreFile>` (alias `user`) with the server-wide `KS_PASSWORD`. Ignores the PIN.
- `Pkcs11SigningProvider`: one SunPKCS11 instance per `Token.pkcs11Slot`, key found by `Certificate.keyAlias` (CKA_LABEL). Logs in with the request's PIN, checks that the certificate on the device matches `sha256Fingerprint`, signs with that provider, and logs out. Wrong PIN → 400 "Invalid PIN".
- `POST /api/sign` takes an optional `pin` form field (only for PKCS#11 certs; the UI shows it only when `CertResponse.keyStorage == PKCS11`).

`TestCaService` creates the root CA (`root-ca.p12`, alias `keybridge-root`) in `@PostConstruct` if missing, and issues user certs into `user-<uuid>.p12` (chain = user + root). Issuing certs with keys generated on a PKCS#11 token is not implemented yet; today PKCS#11 keys come from external tooling or the SoftHSM test setup.

**Verification** (`VerificationService`) is a strict ordered pipeline that stops at the first failure: parse → chain (`cert.verify(rootPublicKey)`) → revocation (DB lookup by SHA-256 fingerprint) → validity dates → signature. Each attempt is written to `verification_logs`. Results are the 7 `VerifyResult` values.

**Auditing.** Methods annotated `@Audited("ACTION")` (on controllers) are intercepted by `AuditAspect` (`@AfterReturning`), and `AuditService.record` writes each argument's `toString()` into `audit_logs.details`. It skips `SigningPin`, `char[]`, `byte[]`, `MultipartFile` and `Key` arguments by type, then any string containing `"password"` or `"@"` or ≥ 120 chars. Any other `String` argument to an `@Audited` method is stored, so secrets must use one of the skipped types.

**Auth / RBAC.** Stateless JWT (`JwtAuthFilter` → `JwtService`); only `/api/auth/**` is public. Roles ADMIN / USER / AUDITOR are enforced with `@PreAuthorize` on controllers, and per-user data scoping (USER sees only own certs, tokens, documents, requests) is done in services: controllers resolve the caller with `AuthService.getCurrentUserEntity()` and pass the `User` in, and services branch on `Role.USER`.

**Service requests.** `ServiceRequestService` is a state machine `PENDING → APPROVED → COMPLETED`, or `PENDING → REJECTED`. Illegal transitions throw `InvalidStateTransitionException` (HTTP 409). Transition to `COMPLETED` performs the side effect (renew/revoke cert, assign/lose token) in the same transaction.

**Expiry.** `ExpiryMonitorService` runs on `keybridge.expiry.cron` (daily 01:00) and via `POST /api/certs/refresh-status`; `warn-days: 30` drives VALID → EXPIRING → EXPIRED. REVOKED is never changed.

**Errors.** Throw the exceptions in `exception/` (`NotFoundException` 404, `BadRequestException` 400, `ForbiddenException` 403, `InvalidStateTransitionException` 409); `GlobalExceptionHandler` maps them to `{timestamp, status, error, message}`.

## Seeded accounts (local dev only)

`admin@keybridge.com / Admin@123`, `user@keybridge.com / User@123`, `auditor@keybridge.com / Audit@123`, tokens `TKN-001`…`TKN-005`.
