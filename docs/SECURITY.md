# Security guide

This is the operator- and reviewer-facing companion to `docs/THREAT_MODEL.md`: how authentication
works, how to run the CI security gates locally, and how to report a vulnerability.

## Authentication, session, and CSRF flow

1. Login verifies the submitted password with Argon2id (`Argon2PasswordVerifier`), using a
   memory/iteration/parallelism/hash-length/salt-length floor that is validated at startup and
   cannot be configured below the supported baseline.
2. On success, the server generates a 256-bit random session token (`SessionTokenService.newToken`)
   and persists only its HMAC-SHA256 keyed hash in `user_sessions.token_hash` — the raw token never
   touches the database.
3. Two cookies are issued (`CookieManager.issue`): `GLACIER_SESSION` (`HttpOnly`, session- or
   remember-me-scoped) and `GLACIER_CSRF` (readable by JavaScript, so the frontend can echo it back
   as a header). Both are `SameSite=Lax` and `Secure` whenever the configured public base URL uses
   `https`.
4. Every state-changing request (anything other than `GET`/`HEAD`/`OPTIONS`) must present an
   `X-CSRF-Token` header that matches both the `GLACIER_CSRF` cookie and an HMAC derived from the
   caller's own session token (`CsrfFilter`, `SessionTokenService.matches` — constant-time
   comparison). A mismatch returns `CSRF_INVALID`.
5. Logout, password changes, and other security-relevant account changes revoke all of a user's
   sessions; a previously valid token is rejected on its next use. See
   `SecurityAttackSimulationTest` for the behavioral proof of session fixation resistance and token
   replay rejection.
6. Repeated failed logins trigger progressive per-identifier and per-IP throttling
   (`LoginThrottlePolicy`): no delay below a configurable threshold, exponential backoff up to a
   lock threshold, then a timed lock. Throttle state is persisted, so a restart does not reset it.

Angular router guards are navigation conveniences only. Every `USER`/`ADMIN`-gated operation is
enforced server-side by Quarkus security identity roles regardless of what the router allows.

## Optional second factor

Off unless `GLACIER_MFA_ENABLED` is set, and opt-in per account even then. When an enrolled account
submits a correct password, step 1 issues no session and no cookies — only a short-lived challenge.
The session is created solely by step 2 (`POST /api/v1/auth/login/mfa`), which accepts either a
TOTP code or one of ten single-use recovery codes.

- **Stored material.** Authenticator secrets are encrypted with AES-GCM under a dedicated key
  (`GLACIER_MFA_ENCRYPTION_SECRET`, separate from the session secret so session-key rotation cannot
  invalidate enrollments). Recovery codes and challenge tokens are stored only as keyed hashes.
  Plaintext secrets are zeroed after use and never logged; a secret and its recovery codes are
  returned exactly once, in the response that creates them.
- **Replay.** Each accepted TOTP step is recorded, and steps at or below it are refused, so a code
  observed in transit cannot be reused — including the code that confirmed the enrollment.
  A recovery code is consumed by a single conditional update, which is what makes concurrent use of
  the same code resolve to one success.
- **State changes between the steps.** Before verifying a code, step 2 re-checks that the account is
  still active, unlocked, still enrolled, and that its password has not changed since the challenge
  was issued. Every rejection — including an unknown or expired challenge — returns the same
  `AUTH_MFA_CHALLENGE_INVALID` response, so a caller holding only a password learns nothing about
  account state.
- **Guessing.** A challenge dies after a configurable number of wrong codes, failures feed a
  dedicated per-IP limiter and the account's own lock counter, and the identifier limiter is
  deliberately *not* cleared by a correct password alone — otherwise a caller who knows the password
  could reset it indefinitely while brute-forcing the second factor. At most three challenges stay
  open per account.
- **Lost authenticator.** The break-glass reset in `deployment/README.md` requires the bootstrap
  token, revokes the account's sessions, and answers identically whether or not the account existed.
  Administrators cannot clear another account's second factor through the admin API.

## Running the dependency and image scans locally

### Backend (OWASP dependency-check)

```bash
./mvnw -pl backend org.owasp:dependency-check-maven:check
```

Fails the build on any finding at CVSS 7.0 (High) or above. Works without credentials but is faster
and more reliable with an NVD API key exported as `NVD_API_KEY` (CI reads the same variable from the
optional `NVD_API_KEY` GitHub Actions secret). Reports are written to
`backend/target/dependency-check-report`.

If a finding is a false positive or genuinely unreachable in this application, add a suppression to
`backend/owasp-suppressions.xml` with a `notes` element explaining why, following the
[suppression file schema](https://jeremylong.github.io/DependencyCheck/general/suppression.html), and
record it in `docs/KNOWN_ISSUES.md`. Do not suppress a finding solely to unblock CI without recording
the justification.

### Frontend (npm audit)

```bash
cd frontend
npm audit --omit=dev --audit-level=high
```

### Application image (Trivy)

```bash
docker compose up --build --wait
trivy image glacier-notes-cloud:local --scanners vuln,secret
```

CI builds the same image with `GIT_SHA`, `APP_VERSION`, and `BUILD_DATE` build arguments (surfaced as
`org.opencontainers.image.*` labels) and runs two Trivy passes: `CRITICAL` findings across
vulnerabilities and embedded secrets fail the build; `HIGH` vulnerability findings are uploaded as a
report-only artifact so they can be triaged without blocking every merge on a transitive dependency
the team does not directly control.

## Log-hygiene enforcement

The project rule is: never log note content, checklist text, filenames, passwords, or tokens.
`backend/scripts/check-log-hygiene.sh` enforces this in CI by extracting every `LOG.<method>(...)`
call site under `backend/src/main/java` and flagging any whose arguments reference `.content()`,
`.title()`, `.text()`, `.password`, `.token`, `password_hash`, `.fileName()`/`.originalFileName()`,
`checklistText`, or `noteContent`. This is a coarse, allowlist-style heuristic — not sound taint
analysis — documented in the script itself. Run it locally with:

```bash
bash backend/scripts/check-log-hygiene.sh
```

Widen the pattern list deliberately if a new prohibited field is introduced; treat a match as a
signal to review the call site, not a guaranteed violation.

## Reporting a vulnerability

This project does not yet have a dedicated security contact or a private disclosure channel. If you
find a vulnerability, please open a GitHub issue on this repository with as much detail as you are
comfortable including publicly, or contact the maintainer directly through their GitHub profile for
anything you would rather not disclose in public first.
