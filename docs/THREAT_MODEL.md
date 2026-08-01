# Threat model

This document records the trust boundaries in Glacier Notes Cloud and the concrete mitigation and
test evidence behind each one, organized by STRIDE category. It is grounded in the components that
exist in this repository today; update it when a boundary, mitigation, or test changes.

## Trust boundaries

1. **Browser ↔ API, session boundary.** The browser holds an opaque session cookie and a
   non-`HttpOnly` CSRF cookie; the API is the only party that can mint or validate either.
2. **API ↔ PostgreSQL.** The API is the only writer; content is scoped by `(owner_id, id)` composite
   keys so cross-owner rows are structurally unreachable, not just filtered at query time.
3. **API ↔ binary storage (filesystem, PostgreSQL large objects, or S3-compatible).** Storage keys
   are server-generated; user input must never resolve to a filesystem path outside the configured
   root or an object outside the configured bucket.
4. **Admin ↔ user privilege boundary.** Administrators manage accounts and instance settings but are
   bound by the same ownership rules as any user for their own notes, notebooks, and images.
5. **Cloud ↔ desktop interchange.** The portable `.glacier.json` schema-v1 format is the only channel
   between this server and a separate, untrusted desktop application; the server must not trust its
   contents beyond structural and quota validation.

## Spoofing

| Risk | Mitigation | Evidence |
| --- | --- | --- |
| Forged or attacker-supplied session token | Sessions use a 256-bit random token (`SessionTokenService.newToken`); only an HMAC-SHA256 keyed hash (`SessionTokenService.hashToken`) is persisted in `user_sessions.token_hash`, so a database read alone cannot produce a valid cookie | `SecurityAttackSimulationTest` (session fixation: an attacker-chosen cookie value is never adopted by login) |
| Session cookie theft via XSS | `GLACIER_SESSION` is `HttpOnly`; `Secure` is enforced whenever the configured public base URL is `https` (`CookieManager.secureCookies`) | `CookieManager` construction, `SecurityHeadersFilter`'s `script-src 'self'` CSP |
| Replayed session token after logout or forced revocation | Logout and password change call session revocation (`AccountService.changePassword` → `revokeAll`); revoked tokens are rejected on the next request | `SecurityAttackSimulationTest` (token replay after logout, token replay after forced revocation) |
| A stolen or guessed password alone yielding a session | Optional per-account TOTP second factor: for an enrolled account the password step issues no session and no cookies, only a hashed, expiring challenge; the session is minted only by `POST /api/v1/auth/login/mfa` | `MfaLoginTest` (the password step creates no `user_sessions` row, sets no cookie, and leaves `last_login_at` untouched) |
| A TOTP code observed in transit and replayed | Each accepted step is recorded in `user_mfa_totp.last_accepted_step`; that step and every earlier one are refused, including the code that confirmed the enrollment | `TotpVerifierTest`, `MfaLoginTest` (a code that completed a login fails on the next challenge) |

## Tampering

| Risk | Mitigation | Evidence |
| --- | --- | --- |
| Cross-site state-changing requests | Session-bound double-submit CSRF: `CsrfFilter` requires the `X-CSRF-Token` header to match both the `GLACIER_CSRF` cookie and an HMAC derived from the caller's own session token, using constant-time comparison (`SessionTokenService.matches`) | `AuthenticationResourceTest`, `SecurityAttackSimulationTest` |
| Oversized or resource-exhausting request bodies | Route-specific body limits ahead of Quarkus REST: 10 MiB ordinary requests, configured maxima plus a 1 MiB multipart allowance for images and portable imports (ADR 0007) | `RequestBodyLimitFilter`/`RequestBodyLimitPolicy`, startup validation that the absolute ceiling covers every route limit |
| Path traversal via a crafted storage key | `ImageBinaryStorage.safePath` resolves and requires `startsWith(root)` before any filesystem read/delete; `BackupService.addStored` rejects keys containing `/`-prefixed or `../` segments (after normalizing `\` to `/`) before reading a stored asset into a backup archive | `ImageBinaryStorageTest`, `BackupTransactionTest#rejectsAnUnsafeImageStorageKeyBeforeReadingTheAsset` |
| Tampered portable import content (relationships, UUIDs, sizes) | Imports run a bounded inspection phase validating structure, relationships, UUIDs, image types, size limits, entity limits, and quota before any permanent write | `docs/PORTABLE_TRANSFERS.md`, transfer job integration tests |

## Repudiation

| Risk | Mitigation | Evidence |
| --- | --- | --- |
| No record of administrative or security-relevant actions | Immutable `audit_events` table records actor, target, result, and a correlation ID for lifecycle, administrative, and blind-import actions; audit rows are never updated or deleted by application code | M11 audit event coverage, M9 blind-admin-import audit requirement |
| Logs cannot be correlated across a request | Every request carries a correlation ID (`CorrelationIdFilter`/`CorrelationIds`), echoed in both structured logs and `application/problem+json` error bodies | `ProblemExceptionMapper`, `RequestCompletionLogFilter` |
| Logs leak the content they were meant to explain | Structured request-completion logs never include note content, checklist text, filenames, passwords, or tokens; enforced by a CI grep gate over every `LOG.<method>()` call site | `backend/scripts/check-log-hygiene.sh`, wired into the `backend` CI job |

## Information disclosure

| Risk | Mitigation | Evidence |
| --- | --- | --- |
| Cross-owner content access or existence leakage | Composite `(owner_id, id)` primary/foreign keys make an unscoped lookup impossible at the repository interface; a cross-owner lookup returns the same not-found response as a genuinely missing record (ADR 0004) | Repository ownership tests across `content`, `image`, `transfer` domains |
| Password compromise via database leak | Argon2id (`Argon2PasswordVerifier`) with a minimum enforced memory/iteration/parallelism/hash-length/salt-length floor validated at startup; the floor cannot be configured below the supported baseline | `Argon2PasswordVerifier.validateParameters` |
| Blind administrative import exposing user content to an administrator | Blind imports return only counts, conflicts, and structural errors; titles, note bodies, checklist text, image data, and filenames are never included in the response | `docs/PORTABLE_TRANSFERS.md` |
| Backup archive exposing credentials if it leaks | Backups exclude database credentials, SMTP passwords, S3 credentials, bootstrap tokens, and cryptographic keys by construction; the operator runbook requires encrypting archives before moving them off-host | `docs/BACKUP_RESTORE.md` |
| Stored TOTP shared secrets recovered from a database leak | `user_mfa_totp.secret_ciphertext` holds AES-256-GCM ciphertext under a key derived from `GLACIER_MFA_ENCRYPTION_SECRET`, which is never written to the database or a backup archive; the row records only `key_id`, an eight-byte fingerprint of the key (`EnrollmentSecretCipher`) | `EnrollmentSecretCipherTest`, `MfaKeyRotationTest` (a login cannot complete against an enrollment sealed under another key) |
| Recovery codes recovered from a database leak, or one code replayed | Only a keyed HMAC-SHA256 under the same enrollment secret is stored (`MfaTokenService.hashRecoveryCode`); redemption is a conditional update on `used_at is null`, so a code is consumed at most once and an unknown code is indistinguishable from a spent one | `MfaLoginTest#acceptsEachRecoveryCodeExactlyOnce` |
| A stolen challenge token used to skip the password stage | `mfa_challenges.token_hash` holds a keyed hash of a 256-bit random token; the token is short-lived (`mfa_challenge_lifetime_minutes`), attempt-capped (`mfa_challenge_attempt_limit`), single-use, and at most three may be open per account (`MfaChallengeService.MAX_OPEN_CHALLENGES`) | `MfaLoginTest#refusesAnExpiredOrAlreadyConsumedChallenge`, `MfaLoginTest#keepsAtMostThreeOpenChallengesPerAccount` |
| Known-vulnerable dependency in a production artifact | OWASP dependency-check (backend, CVSS ≥ 7.0 blocks CI) and `npm audit --omit=dev --audit-level=high` (frontend) run on every CI build; Trivy scans the built application image for vulnerabilities and embedded secrets | `.github/workflows/ci.yml` `backend`, `frontend`, and `deployment` jobs |

## Denial of service

| Risk | Mitigation | Evidence |
| --- | --- | --- |
| Credential-stuffing or brute-force login attempts | Progressive per-identifier and per-IP throttling: no delay below a configurable failure threshold, exponential backoff up to a lock threshold, then a timed lock (`LoginThrottlePolicy`); throttle state persists in the database so a restart does not reset it | `LoginThrottlePolicy`, `AuthenticationResourceTest` rate-limit cases |
| Abuse of invitation, password-reset, or email-change endpoints | Persistent, endpoint-specific rate limits scoped by identifier, IP, or admin actor (`endpoint_rate_limits`, scopes added across V4/V9 migrations) | Endpoint rate-limit integration tests |
| Unbounded request bodies exhausting memory | Same route-specific body-limit handler as the tampering row above, applied to both fixed-length and chunked requests | ADR 0007 |
| A stuck external dump process blocking backup infrastructure indefinitely | `BackupService.awaitDump` bounds the wait on the `pg_dump` subprocess and forcibly terminates it on timeout | `BackupTransactionTest#dumpWaitIsBoundedAndTerminatesTheProcess` |

## Elevation of privilege

| Risk | Mitigation | Evidence |
| --- | --- | --- |
| Client-side router guards mistaken for an authorization boundary | Angular route guards are documented and treated as navigation aids only; every `USER`/`ADMIN`-gated operation is enforced server-side by Quarkus security identity roles regardless of what the router allows | `docs/ARCHITECTURE.md` |
| An administrator using admin tooling to bypass ownership on their own content | Administrators are bound by the same ownership rules as normal users for their own content; admin-only operations (e.g. blind import) go through separate, explicitly audited paths rather than an ownership bypass | ADR 0004 |
| Privilege escalation via a forged or stale session after a role change | Session revocation on security-relevant account changes forces re-authentication, so a previously issued token cannot retain a since-revoked privilege | `SecurityAttackSimulationTest` |

## Accepted residual risks

These are known, deliberate, and not defended against in the current release.

- **TOTP is phishable in real time.** A convincing proxy page can collect the password and the
  six-digit code and use both within the same 30-second step. The second factor raises the cost of a
  leaked or reused password; it is not a defence against a live adversary-in-the-middle. Phishing
  resistance needs origin-bound credentials (WebAuthn), which are not implemented.
- **Enforcement is per account, never instance-wide.** An administrator cannot require the second
  factor of anyone, and cannot see enough to enforce it socially beyond the enrollment counts on the
  admin pages. This is a first-release scoping decision, not an oversight
  (`GLACIER_NOTES_CLOUD_2FA_SPECIFICATION.md` §9.2), and it means an instance with the feature
  enabled can still be entirely password-only in practice.
- **Losing `GLACIER_MFA_ENCRYPTION_SECRET` is unrecoverable by design.** There is no escrow, no
  recovery key, and no re-encryption tool; the only remedy is the bootstrap-token break-glass reset,
  once per enrolled account. See `docs/BACKUP_RESTORE.md`.
- **Host clock drift is indistinguishable from an attack.** Verification accepts one 30-second step
  either side of the server's own clock, so a host drifting past about a minute rejects correct codes
  with the same response an attacker would get, and `glacier_mfa_verifications{outcome="rejected"}`
  cannot tell the two apart. Operators are expected to run NTP; the application does not measure or
  report drift.

## Out of scope for this document

Live browser-matrix testing, manual load testing, and a live release-candidate sign-off meeting are
process activities tracked separately; this document covers code- and CI-level mitigations only.
