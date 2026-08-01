# Glacier Notes Cloud — Two-Factor Authentication Specification

**Status:** All specification decisions resolved — milestones T0, T1, T2a, and T2b approved and delivered, T3–T5 pending approval
**Specification version:** 0.7
**Date:** 2026-08-01
**Feature:** Optional time-based one-time password (TOTP) second factor with recovery codes
**Extends:** `GLACIER_NOTES_CLOUD_SPECIFICATION.md` section 8 (Authentication)

---

## 1. Document Purpose

This document defines the product, security, data, API, and operational requirements for adding an
optional second authentication factor to Glacier Notes Cloud. It is intended to be sufficient for
OpenAPI contract design, database schema design, security review, and test planning.

All specification decisions are resolved and recorded in section 21. The load-bearing ones are
captured in `docs/adr/0009-optional-second-authentication-factor.md` per section 20. Milestones T0
through T2b of `GLACIER_NOTES_CLOUD_MILESTONES_2FA.md` have been delivered; this document does not
authorize the milestones beyond them.

## 2. Scope

### 2.1 In scope

- TOTP second factor (RFC 6238) compatible with standard authenticator applications
  (Authy, Google Authenticator, 1Password, Bitwarden, Aegis, and equivalents)
- Single-use recovery codes for device loss
- Self-service enrollment, re-enrollment, and disablement
- A two-step login flow gated on a short-lived server-side challenge
- Administrative recovery for users who lose both device and recovery codes
- An operator break-glass path for a locked-out sole administrator

### 2.2 Deferred to a follow-up

Instance-wide enforcement — requiring a second factor for all users, or for administrators — is
specified in outline in section 9 but shall not ship with the first release. It needs settings
columns, an administration interface, a restricted-session state for non-enrolled users, and an
interaction with last-administrator protection. Those are better designed once real enrollment
behavior is observable, and the feature is fully useful without them.

### 2.3 Out of scope

- WebAuthn, passkeys, and hardware security keys
- SMS, email, or push-based second factors
- Trusted-device / "remember this browser" suppression of the second factor
- Multiple concurrent TOTP registrations per account
- Second-factor step-up on individual sensitive operations beyond those named in section 5.4

### 2.4 Explicitly excluded

Second-factor state shall never appear in the portable `.glacier.json` format. Multi-factor
configuration is account security, not portable content, and the schema-v1 contract shared with the
Glacier Notes desktop application shall remain unchanged by this feature.

## 3. Goals and Non-Goals

### 3.1 Goals

- A user may protect their account with a second factor without any administrator involvement.
- A user who loses their authenticator device may regain access without administrator involvement,
  provided they retained their recovery codes.
- The second factor shall not weaken existing guarantees: no account enumeration, no content
  disclosure, no cross-owner access, no secrets in logs.
- Operators shall be able to reason about the feature's effect on backup, restore, and key rotation.

### 3.2 Non-goals

- Reducing the strength or configurability of the existing password policy.
- Making the second factor mandatory by default.

## 4. Terminology

| Term | Meaning |
| --- | --- |
| TOTP secret | The shared symmetric key held by both the server and the authenticator application. |
| Step | The RFC 6238 counter value, `floor(unixTime / period)`. |
| Challenge | A short-lived server-side record proving that a password was verified but a session has not yet been issued. |
| Recovery code | A single-use high-entropy string that substitutes for a TOTP code exactly once. |
| Enrollment | The process of generating, displaying, and confirming a TOTP secret. |

## 5. Functional Requirements

### 5.1 Enrollment

Enrollment shall be a confirmed, two-phase process. A generated secret shall not protect the account
until the user proves possession of the configured authenticator.

1. The user opens the two-factor section of account settings and starts enrollment.
2. The server shall require the user's current password before generating a secret. Re-authentication
   prevents an unattended session from silently locking the legitimate owner out.
3. The server generates a secret, stores it in `PENDING` status, and returns the secret in Base32
   form together with an `otpauth://` provisioning URI.
4. The client renders the URI as a QR code and also displays the Base32 secret for manual entry.
5. The user submits a current TOTP code. On success the record transitions to `ACTIVE`.
6. The server generates a recovery-code set and returns it in this response only. Recovery codes
   shall never be retrievable again.

A `PENDING` enrollment shall have no effect on login. A `PENDING` enrollment shall expire and be
eligible for deletion after a bounded period (recommended: 30 minutes). Starting a new enrollment
shall discard any existing `PENDING` enrollment for that user. An account with an `ACTIVE`
enrollment shall not start a new enrollment until the existing one is disabled.

The user shall be able to abandon a `PENDING` enrollment explicitly, deleting the unconfirmed
secret immediately rather than waiting for expiry. Abandonment shall not require a code, since no
factor is active.

The confirmation step shall warn the user, before completion, that recovery codes are shown once.

### 5.2 Login

For accounts without an `ACTIVE` enrollment, login behavior shall be unchanged.

For accounts with an `ACTIVE` enrollment:

1. The user submits identifier and password to the existing login operation.
2. On correct credentials the server shall **not** issue session or CSRF cookies and shall **not**
   create a `user_sessions` row. It shall create a challenge record and return a response indicating
   that a second factor is required, carrying an opaque challenge token and an expiry.
3. The user submits the challenge token together with either a TOTP code or a recovery code.
4. On success the server consumes the challenge, creates the session, and issues cookies exactly as
   the single-step flow does today, preserving the original `rememberMe` selection.

The `rememberMe` preference shall be captured at step 1 and carried on the challenge record. It
shall not be re-supplied at step 4, so that a client cannot escalate session lifetime after the
password step.

Challenge tokens shall be single-use. Successful consumption, exhausted attempts, and expiry shall
all render a challenge permanently unusable.

An account shall hold at most a bounded number of unconsumed challenges (recommended: 3). Creating
a challenge beyond that bound shall invalidate the oldest. Without a bound, repeated password steps
grow the table without limit, since the existing limiter counts only failures and a correct password
is never a failure.

#### 5.2.1 Re-verification at challenge consumption

Account state shall be re-verified when the challenge is consumed, not only when the password was
verified. The two steps are separated in time, and an account may be deactivated, locked, deleted,
or have its password changed in between. Consumption shall confirm that the account is still
`ACTIVE`, that no temporary lock has since been applied, and that the enrollment is still `ACTIVE`.
Failing any of these shall destroy the challenge and produce the generic failure response.

A password change shall invalidate that user's outstanding challenges, consistent with its existing
effect on sessions.

#### 5.2.2 Recording success

`last_login_at`, the reset of the account failure counter, and the clearing of the identifier-scoped
rate-limit entry shall all occur when the session is issued, never at the password step. If the
identifier limiter were cleared on password success alone, a caller holding a correct password could
reset it indefinitely while brute-forcing the second factor, which would defeat the limiter's
purpose at exactly the point it is most needed.

### 5.3 Recovery codes

- A set shall contain 10 codes.
- Each code shall carry at least 128 bits of entropy, rendered in a transcription-friendly
  alphabet and grouped for readability.
- Each code shall be accepted at most once. Consumption shall be recorded with a timestamp.
- A code shall be accepted anywhere a TOTP code is accepted during login.
- The user shall be able to see how many unused codes remain.
- The user shall be able to regenerate the set. Regeneration shall require the current password and
  shall invalidate every previously issued code in one transaction.
- When the remaining count falls to 3 or fewer, the interface shall prompt the user to regenerate.
- Recovery codes shall be offered as a copyable block and as a plain-text download.

### 5.4 Disablement and re-authentication

Disabling the second factor shall require both the current password and a valid TOTP or recovery
code. Disablement shall delete the TOTP secret and all recovery codes for that account.

The following operations shall require the current password (consistent with existing behavior) and,
where a second factor is active, shall additionally require a current TOTP or recovery code:

- Disabling the second factor
- Regenerating recovery codes
- Account self-deletion — irreversible once the retention window elapses, and the most destructive
  action available to a hijacked session
- Changing the email address — control of the registered mailbox enables password reset, so leaving
  this password-only would reopen the takeover path that section 5.6 closes
- Administrative operations performed by an enrolled administrator against another account,
  including clearing another user's second factor

Starting a new enrollment, and abandoning a `PENDING` one, shall require the current password only,
since no second factor is active at that point.

Where an operation already accepts a password, the code shall be supplied alongside it in the same
request. A step-up shall never be satisfied by a code that was already consumed for login; codes
remain single-use per section 6.4 and, for recovery codes, per section 5.3.

#### 5.4.1 Abuse resistance for password-gated operations

Every operation above verifies a password from within an authenticated session, which makes each one
a password oracle for a hijacked session. The existing login limiter does not cover them. These
operations shall therefore be rate-limited per account and per IP on failed password or failed code
verification, and repeated failures shall be audited.

#### 5.4.2 Step-up grace period

A successful step-up shall mark the session as recently re-authenticated for a bounded period
(default 5 minutes, administrator-configurable per section 7.6). Further step-up operations within
that period shall not prompt for a code again.

Successfully completing the second factor at login shall start the same window on the session it
creates. The user has just proved possession of the authenticator, and without this a user who logs
in and immediately changes their email address would be prompted for a second code within the same
minute — precisely the friction the grace period exists to remove.

The grace period exists because prompting on every action during bulk administrative work trains
users to keep an authenticator permanently open next to the browser, which weakens the factor more
than the grace window does.

The grace period shall:

- Be scoped to the single session that performed the step-up, recorded server-side on the session
  record, never in a client-controlled value
- Be evaluated server-side on every affected operation; no client state shall be trusted
- Reset to expired when the session's password changes, when the second factor is disabled or
  re-enrolled, or when the session is revoked
- Never extend beyond the session's own expiry

The grace period never applies to the login challenge itself, which is always required regardless of
any prior session's state. Login *starts* the window; it is never *satisfied* by one.

A session created without a second factor — because the account has no enrollment — shall not carry
a grace window, since there was nothing to prove.

### 5.5 Session revocation

Enabling the second factor, disabling it, and regenerating recovery codes shall each revoke all
other sessions for that user, matching the existing behavior of a password change. The session
performing the operation shall survive.

### 5.6 Interaction with password reset

A password reset shall not clear or bypass the second factor. After completing a reset, an enrolled
user shall still be required to satisfy the second factor at the next login. Otherwise, control of
the registered mailbox would be sufficient to defeat the factor entirely.

### 5.7 Notifications

The account owner shall be emailed for every second-factor lifecycle event, through the existing
mail infrastructure:

| Event | Rationale |
| --- | --- |
| Enrollment started | Reveals an attacker preparing to bind their own authenticator |
| Enrollment confirmed | Confirms the protection is live |
| Second factor disabled | The event a second factor exists to make noisy; silent removal is the primary takeover signal |
| Recovery code used | Distinguishes the owner's own recovery from an attacker who obtained the code sheet |
| Recovery codes regenerated | Invalidates the owner's printed sheet, so they need to know |
| Second factor cleared by an administrator | The owner is otherwise unaware their protection was removed |

Notification content shall carry the event, its timestamp, and the client description already
recorded for audit purposes. It shall never carry a secret, a code, a provisioning URI, a challenge
token, or a remaining-code count.

Notification delivery shall be best-effort and shall never block or roll back the security operation
itself: a user must be able to disable a second factor on an instance with broken SMTP. Delivery
failures shall be logged operationally without revealing the recipient's content.

Where an instance has no SMTP configuration, these notifications shall be skipped silently rather
than surfacing errors to the user, consistent with how the product already degrades other
email-dependent flows.

## 6. Security Requirements

### 6.1 No account enumeration

Whether an account exists, and whether an account has a second factor, shall not be observable
before the correct password is supplied.

The existing constant-work login path — which verifies against a dummy Argon2 hash when no user
matches — shall be preserved. The branch that creates a challenge shall be reached only after the
password has been verified and the account status confirmed `ACTIVE`, so the outcome of a failed
password attempt remains indistinguishable regardless of enrollment state.

### 6.2 Secret at rest

A TOTP secret is a symmetric shared key and shall be recoverable by the server; it cannot be hashed.
It shall therefore be encrypted at rest with an authenticated cipher (AES-256-GCM), storing the
ciphertext, a per-record random nonce, and a key identifier.

The encryption key shall be derived from a dedicated deployment secret, not from the existing
session secret. Rotating the session secret today only invalidates live sessions; if enrollment
secrets shared that key, rotation would permanently destroy every enrollment. The dedicated secret
shall be resolved through the existing secret-provider mechanism, supporting both an inline value
and a file path, and shall be subject to the same length and whitespace policy as the session
secret.

The secret shall be returned to the client exactly once, during enrollment, and never again by any
endpoint, including administrative endpoints.

### 6.3 Recovery code storage

Recovery codes shall be stored as keyed hashes, not plaintext, and not as Argon2 hashes.

Argon2 exists to raise the cost of attacking low-entropy, human-chosen secrets. Recovery codes are
high-entropy machine-generated values, so a keyed HMAC (as already used for session and invitation
tokens) provides equivalent resistance while remaining deterministic. Determinism matters: it allows
verification to be a single indexed lookup rather than up to ten sequential Argon2 verifications per
login attempt, which would otherwise be a denial-of-service amplifier on an unauthenticated path.

Codes shall be compared using a constant-time comparison.

### 6.4 TOTP verification

- Algorithm: HMAC-SHA1, 6 digits, 30-second period. These are the interoperable defaults; deviating
  breaks common authenticator applications for no security benefit at this threat level.
- Verification shall accept a window of ±1 step to tolerate clock skew, and no wider.
- The server shall record the highest step number it has accepted for an account and shall reject any
  code whose step is less than or equal to that value. Without this, an observed code remains
  replayable for the remainder of its window.
- Code comparison shall be constant-time.
- Time shall be obtained through the injected time provider so that step boundaries, skew tolerance,
  and replay rejection are deterministically testable.

### 6.5 Rate limiting and abuse resistance

A six-digit code over a ±1-step window is roughly one million candidates, and the second step of
login is not covered by the existing login limiter. Therefore:

- Each challenge shall carry an attempt counter and shall be destroyed after 5 failed attempts. The
  user must then repeat the password step.
- Failed second-factor attempts shall additionally increment an IP-scoped counter in the existing
  rate-limit table, so that challenge churn cannot be used to escape per-IP limits. The existing
  scope constraint permits only `IDENTIFIER` and `IP`; a further scope value requires a migration.
- Failed second-factor attempts shall increment the account's existing `failed_login_count` and
  shall be capable of triggering the configured temporary lock, on the same thresholds as password
  failures. Reaching the second step proves the password is already known, so the account is under
  active attack and shall be defended with the same mechanism rather than a weaker one. A locked
  account shall fail the second step for the duration of the lock, and an administrator shall be
  able to unlock it through the existing path.
- A successful second-factor verification shall reset that counter, mirroring the existing reset on
  successful login.
- Because a legitimate user can now lock themselves out with mistyped codes, the second-stage
  interface shall show remaining attempts before the challenge is destroyed, and the ±1 step
  tolerance in section 6.4 exists specifically to keep ordinary clock skew from consuming attempts.
- Challenge creation shall be subject to the existing per-identifier and per-IP login limits, since
  it is only reachable through a successful password verification, and to the per-account cap in
  section 5.2.
- Challenge lifetime and the attempt cap are administrator-configurable per section 7.6.
- Expired and consumed challenges shall be purged by the existing scheduled maintenance mechanism.

### 6.6 Logging and disclosure

Extending the existing prohibition on logging note content, passwords, and tokens: TOTP secrets,
provisioning URIs, submitted codes, recovery codes, recovery-code hashes, and challenge tokens shall
never appear in logs, audit metadata, problem details, diagnostics, or error messages.

Administrators shall not be able to read any user's TOTP secret or recovery codes through any
interface.

### 6.7 Failure responses

Second-factor failures shall not distinguish between "wrong TOTP code", "already-used recovery
code", and "unknown recovery code". All shall produce the same generic response and the same
attempt-counter effect.

The response may carry the number of attempts remaining on the challenge, which section 11.1
requires the interface to display. That count is identical across all three failure causes, so it
discloses nothing about which one occurred; it is a property of the challenge, not of the submitted
value.

## 7. Data Model

Schema changes shall be delivered as a single new forward migration, `V13__multi_factor_authentication.sql`.
Existing migrations shall not be edited. All changes are additive.

### 7.1 `user_mfa_totp`

One row per account, holding at most one enrollment.

| Column | Notes |
| --- | --- |
| `user_id` | Primary key, references `app_users(id)` on delete cascade |
| `status` | `PENDING` or `ACTIVE`, checked |
| `secret_ciphertext` | `BYTEA`, AES-GCM ciphertext with authentication tag |
| `secret_nonce` | `BYTEA`, unique per record |
| `key_id` | Identifies the encryption key, to permit future rotation |
| `algorithm`, `digits`, `period` | Persisted so stored enrollments survive default changes |
| `last_accepted_step` | `BIGINT`, nullable, for replay rejection |
| `created_at`, `confirmed_at`, `last_used_at` | Timestamps |
| `version` | `BIGINT`, optimistic locking, consistent with existing mutable tables |

### 7.2 `user_mfa_recovery_codes`

| Column | Notes |
| --- | --- |
| `id` | Primary key |
| `user_id` | References `app_users(id)` on delete cascade |
| `code_hash` | Keyed hash, unique |
| `generated_at`, `used_at` | `used_at` null while unused |

Indexed on `(user_id)` and on `(code_hash)`.

### 7.3 `mfa_challenges`

| Column | Notes |
| --- | --- |
| `id` | Primary key |
| `user_id` | References `app_users(id)` on delete cascade |
| `token_hash` | Keyed hash of the opaque challenge token, unique |
| `remember_me` | Captured at the password step |
| `created_at`, `expires_at`, `consumed_at` | Timestamps |
| `attempt_count` | Non-negative, checked |
| `ip_address`, `client_description` | Carried forward to the resulting session record |

Indexed on `expires_at` for purge, filtered on unconsumed rows.

### 7.4 `login_rate_limits`

The `scope` check constraint shall be widened to admit the second-factor scope introduced in
section 6.5.

### 7.5 `user_sessions`

An additive nullable timestamp recording the most recent successful step-up for that session,
supporting the grace period of section 5.4.2. It is null for every existing row, which correctly
means "no recent step-up".

### 7.6 `instance_settings`

The second-factor tunables shall be administrator-configurable columns, defaulted and bounded by
check constraints in the same style as the existing `login_delay_threshold`,
`login_lock_threshold`, and `login_lock_minutes`:

| Setting | Default | Bounds |
| --- | --- | --- |
| Challenge lifetime, minutes | 5 | 1–30 |
| Challenge attempt cap | 5 | 3–10 |
| Pending-enrollment expiry, minutes | 30 | 5–120 |
| Step-up grace window, minutes | 5 | 0–60, where 0 disables the grace period |

An operator who can already tune login lockout in the administration settings screen will expect to
tune second-factor lockout in the same place; splitting these across deployment properties would put
two halves of one security policy in two mechanisms.

Each shall be exposed through the existing admin settings read and update operations, which requires
extending those schemas — including the explicit `required` list on the settings response.

The enforcement policy of section 9.2 remains deferred per section 2.2. Its columns shall be added
by the migration that delivers it rather than reserved in advance.

## 8. API Contract

The OpenAPI document is the source of truth and shall be changed before any implementation.
Generated backend sources and the committed frontend client shall be regenerated and committed in
the same change, with no drift.

### 8.1 Modified operations

`POST /api/v1/auth/login` shall return a single `200` schema carrying a required discriminator and
two mutually exclusive optional payloads:

```yaml
LoginOutcome:
  type: object
  required: [result]
  properties:
    result:
      type: string
      enum: [SESSION, MFA_REQUIRED]
    context:
      $ref: '#/components/schemas/SessionContext'
    challenge:
      $ref: '#/components/schemas/MfaChallenge'
```

Exactly one payload shall be populated, determined by `result`. Clients shall branch on `result`
and shall not infer the outcome from field presence.

The session payload member is named `context` rather than `session` because `SessionContext` itself
contains a `session` member; the latter name would produce the path `session.session.current`.

A tagged wrapper is preferred over a `oneOf` discriminated union or a distinct `202` status. The
`oneOf` support in the TypeScript Angular generator is uneven and risks emitting a type that needs
hand-written narrowing in committed generated code. A distinct `202` is more HTTP-idiomatic, but
generated clients type the success return from a single 2xx response, so the challenge shape may not
survive into the client type at all. The wrapper also extends cleanly if a second factor type is
added later.

`MfaChallenge` shall carry only the opaque challenge token, its expiry, the number of attempts
permitted, and which factor types are accepted. It shall not carry the user identifier, username,
email, display name, role, or any enrollment metadata, since it is returned prior to session
establishment.

`SessionContext` is unchanged, so the existing session and current-user operations are unaffected.

### 8.2 New operations

| Operation | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `completeMfaLogin` | `POST /api/v1/auth/login/mfa` | none (`security: []`) | Exchange challenge + code for a session |
| `getMfaStatus` | `GET /api/v1/me/mfa` | session | Enrollment state and remaining recovery codes |
| `startTotpEnrollment` | `POST /api/v1/me/mfa/totp` | session + CSRF | Password-gated; returns secret and provisioning URI |
| `confirmTotpEnrollment` | `POST /api/v1/me/mfa/totp/confirm` | session + CSRF | Activates; returns recovery codes once |
| `cancelTotpEnrollment` | `DELETE /api/v1/me/mfa/totp/pending` | session + CSRF | Abandons an unconfirmed enrollment |
| `disableTotp` | `POST /api/v1/me/mfa/totp/disable` | session + CSRF | Password + second factor required |
| `regenerateRecoveryCodes` | `POST /api/v1/me/mfa/recovery-codes` | session + CSRF | Password + second factor required |

Existing operations gain an optional second-factor code field to satisfy section 5.4: account
self-deletion, email-address change, and the administrative operations named there. The field shall
be required only when the acting account has an active enrollment.

The `/api/v1/me/*` prefix matches the existing current-user operations.

The unauthenticated second-factor operation is protected by the challenge token itself and shall not
require the CSRF header, consistent with the existing login operation.

### 8.3 Administrative operations

| Operation | Path | Purpose |
| --- | --- | --- |
| `getUserMfaStatus` | Existing admin user detail response, extended | Whether a second factor is active, and since when |
| `clearUserMfa` | `POST` under the existing admin user path family | Remove a user's second factor |

Administrative clearing shall be an explicit, separately audited action that deletes the enrollment
and recovery codes and revokes the target user's sessions. It shall never expose the secret or the
codes. This mirrors how administrative content operations work today: a separate audited path rather
than a bypass of the normal rules. Where the acting administrator is themselves enrolled, the
operation requires a step-up code per section 5.4.

#### 8.3.1 Operator escape hatch

Administrative clearing has no actor when the locked-out account is the only administrator. That
account cannot clear itself, and no other account has the authority. The same dead end occurs if the
enrollment encryption key is lost, since every stored secret becomes undecryptable at once.

The instance shall therefore expose an operator-level path that clears a named account's second
factor, authorized by the existing bootstrap token. The bootstrap token is already read from the
filesystem or environment of the running instance, so an actor able to present it can already
reconfigure or replace the deployment; this grants no capability they did not have, while reusing a
secret-handling flow operators already know from initial setup.

This path shall:

- Accept only an account identifier, never return or accept secret material, and never return
  account content
- Delete the enrollment and all recovery codes for that account, and revoke its sessions
- Be audited with an explicit operator-action event type and notify the account owner per section 5.7
- Be subject to the same rate limiting as the existing bootstrap operations
- Be documented as a break-glass procedure, not routine administration

### 8.4 Error responses

New failure reasons shall map to `application/problem+json` following the existing `AUTH_*`
convention, where the problem type URI is derived from the error code.

| Condition | Status | Error code |
| --- | --- | --- |
| Second factor required (not an error; see 8.1) | 200 | — |
| Invalid or already-used TOTP / recovery code | 401 | `AUTH_MFA_INVALID_CODE` |
| Challenge unknown, consumed, or expired | 401 | `AUTH_MFA_CHALLENGE_INVALID` |
| Challenge attempts exhausted | 429 | `AUTH_MFA_ATTEMPTS_EXCEEDED` |
| Account locked by failed second-factor attempts | 429 | Existing lock response, with `Retry-After` |
| Enrollment already active | 409 | `MFA_ALREADY_ENROLLED` |
| Operation requires an active enrollment | 409 | `MFA_NOT_ENROLLED` |
| Sensitive operation needs a step-up code (section 5.4) | 403 | `MFA_STEP_UP_REQUIRED` |

`MFA_REQUIRED_BY_POLICY` belongs to the deferred enforcement work of section 9.2 and shall not be
defined until that ships.

Account state that changes between the two login steps — deactivation, deletion, lock, or a password
change, per section 5.2.1 — shall produce the generic `AUTH_MFA_CHALLENGE_INVALID` response. A
distinct code would disclose account state to a caller holding only a password.

Rate-limited responses shall carry `Retry-After`, consistent with the existing login limiter.

## 9. Administration and Policy

### 9.1 First release

Administrators shall be able to see, on a user detail page, whether that user has an active second
factor and when it was confirmed. They shall not see the secret, the recovery codes, or the number
of remaining codes.

Administrators shall be able to clear a user's second factor per section 8.3.

The instance settings page shall expose the four tunables of section 7.6, validated against their
bounds on both sides and rejected server-side regardless of what the client permits. They join the
existing login-throttling settings on that page, since they govern the same class of behavior.

### 9.2 Enforcement policy — deferred

Per section 2.2, instance-wide enforcement does not ship with the first release. The intended shape
is recorded here so the deferred work has a starting point, not as a first-release requirement:

- An instance setting selecting optional (default), required for administrators, or required for all
  users
- An affected user without an active enrollment may authenticate but is restricted to the enrollment
  endpoints and account settings until enrollment completes
- Enabling enforcement never locks existing users out retroactively
- The existing last-administrator protection is respected: no policy change may render the instance
  unadministrable

Deferring this is safe in both directions. Nothing in the first release forecloses it, and the
operator escape hatch of section 8.3.1 already covers the lockout scenario enforcement would
otherwise make more likely.

## 10. Auditing and Observability

### 10.1 Audit events

New audit event types shall be recorded through the existing audit mechanism, with the existing
`SUCCESS` / `FAILURE` / `DENIED` result values, correlation ID, IP address, and client description.

| Event | Result values | Notes |
| --- | --- | --- |
| Enrollment started | SUCCESS, FAILURE | Failure covers a wrong current password |
| Enrollment confirmed | SUCCESS, FAILURE | |
| Second factor disabled | SUCCESS, FAILURE | |
| Second-factor challenge failed | FAILURE | Emitted per failed attempt |
| Recovery code used | SUCCESS | Records remaining count in metadata |
| Recovery codes regenerated | SUCCESS | |
| Administrative second-factor clear | SUCCESS, DENIED | Actor and target both recorded |
| Operator second-factor clear | SUCCESS, DENIED | Break-glass path, section 8.3.1; no actor account |
| Enrollment abandoned | SUCCESS | Unconfirmed enrollment discarded |
| Step-up verification failed | FAILURE | Section 5.4 operations |

Audit metadata shall contain counts and states only. No secret material shall enter `metadata_json`.

### 10.2 Metrics

The instance already exposes Micrometer metrics on the private management port. The feature shall
publish counters sufficient to alert on an attack in progress without consulting the audit table:

- Challenges issued, consumed, expired, and destroyed by attempt exhaustion
- Second-factor verifications by outcome, with TOTP and recovery-code paths distinguished
- Recovery codes consumed
- Enrollments activated and disabled
- Operator escape-hatch invocations

A sustained rise in exhausted challenges, or any operator escape-hatch invocation, is the signal
worth alerting on. Metrics shall carry no per-account labels, since unbounded cardinality on user
identifiers is both an operational and a disclosure problem.

Second-factor verification failures caused by server clock drift are indistinguishable from attacks
in these counters. The operations documentation shall note that instance time synchronization
becomes a correctness dependency once this feature is enabled.

## 11. Frontend Requirements

### 11.1 Login

The login view shall gain a second stage. Challenge state shall be held in memory in the
authentication store and shall never be written to `localStorage`, `sessionStorage`, or the URL.

The second stage shall:

- Accept a 6-digit code with numeric input affordances and paste support
- Offer an explicit switch to recovery-code entry
- Show remaining validity and handle expiry by returning the user to the password stage with an
  explanatory message
- Clear the entered code on failure, mirroring how the password field is cleared today
- Show remaining attempts before the challenge is destroyed, since failures now contribute to
  account locking per section 6.5
- Handle `Retry-After` for rate-limited responses, reusing the existing countdown behavior

Sensitive operations covered by section 5.4 shall prompt for a code inline, alongside the password
field they already present, rather than through a separate navigation step.

### 11.2 Account settings

A two-factor card shall be added to account settings, following the structure of the existing
change-password and change-email cards. It shall present:

- Current state and, when active, the confirmation date and remaining recovery-code count
- An enrollment flow: password → QR code and manual secret → confirmation code → recovery codes
- Copy and download actions for recovery codes, and a required acknowledgement before the codes
  can be dismissed
- Regenerate and disable actions with confirmation

### 11.3 Presentation details

The provisioning URI shall be generated server-side and rendered as a QR code client-side using a
small QR library.

QR encoding is Reed-Solomon error correction plus matrix layout, so it is not a candidate for
hand-writing the way RFC 6238 is; the choice is only where the dependency lives. Placing it in the
frontend keeps image handling out of the authentication service. The library becomes a production
npm dependency and therefore enters the `npm audit --omit=dev --audit-level=high` gate, so it shall
be chosen for a small transitive tree and active maintenance, and shall not be given the raw secret
beyond the URI it renders.

The manual-entry secret shall always be shown alongside the QR code, since not every user can scan
one, and it shall be presented in grouped uppercase Base32.

All new strings shall be added to both the English and German dictionaries. The recovery-code
download shall be a plain-text file with a stable, non-identifying filename.

Router guards remain navigation aids only. Enforcement of the second factor is a server-side
property of session issuance; no client-side state shall be treated as authoritative.

## 12. Configuration and Deployment

A new secret shall be introduced for the enrollment encryption key, resolvable inline or from a
file, consistent with the existing bootstrap token and session secret.

The local development instructions and the production compose instructions shall both be updated,
including generation of the new secret file alongside the existing ones and the same restrictive
file permissions.

The application shall fail fast at startup if second-factor support is enabled and the key is
missing or does not satisfy the secret policy. It shall not start with a placeholder or derived
fallback key.

### 12.1 Documentation updates

The following existing documents describe authentication and shall be revised as part of the work,
not afterwards:

| Document | Revision |
| --- | --- |
| `docs/SECURITY.md` | Second-factor model, secret handling, and the operator escape hatch |
| `docs/THREAT_MODEL.md` | Revised credential-compromise assumptions; new assets (enrollment secret, recovery codes, challenge tokens) and their attack surfaces |
| `docs/BACKUP_RESTORE.md` | Section 13 of this document |
| `docs/DATABASE.md` | New tables |
| `docs/MIGRATIONS.md` | Only if the migration warrants a worked example |
| `docs/DEVELOPMENT.md` | New required environment variable for local runs |
| `docs/UPGRADE.md` | Operator steps for an existing instance, and the breaking login-response change of section 14.1 |
| `deployment/README.md` and `.env.example` | New secret and its file-based form |
| `README.md`, `CHANGELOG.md` | Feature summary, release entry, and the breaking login-response change of section 14.1 |

The breaking-change callout is not optional prose. Any consumer of the login operation outside this
repository parses the `200` body, and section 14.1 changes its shape for every caller including
non-enrolled accounts.

The threat-model revision is the substantive one. Adding a second factor changes the document's
baseline assumption that password compromise equals account compromise, and introduces assets whose
loss has no user-recoverable path.

## 13. Backup, Restore, and Key Rotation

Backup and restore documentation shall state explicitly that a database backup alone is no longer
sufficient to restore a working instance: without the enrollment encryption key, every stored
enrollment becomes undecryptable and every enrolled user is locked out of their second factor.

The documentation shall cover:

- Storing the enrollment key with the same care as the session secret, and separately from database
  backups
- The recovery path when the key is lost: the operator escape hatch of section 8.3.1 is the only
  remaining route, since a lost key locks out every enrolled account simultaneously including all
  administrators
- Key rotation: the `key_id` column permits re-encryption under a new key; the rotation procedure
  shall be documented even if tooling is deferred
- That restoring a database backup taken before an enrollment, alongside a current key, leaves that
  account with no second factor — and that this is the expected outcome, not corruption

## 14. Compatibility

The desktop application and the portable `.glacier.json` schema-v1 contract are unaffected. No new
fields are added to portable exports, and compatibility fixtures do not change.

### 14.1 Breaking API change

The login response shape changes for every caller, enrolled or not. `POST /api/v1/auth/login`
previously returned `SessionContext` directly; it now returns `LoginOutcome`, so a field previously
read as `user` is read as `context.user`. This is not gated on enrollment and takes effect the moment
the change ships.

This is accepted rather than worked around. The only consumer is the first-party Angular client,
which is generated from the same specification and regenerated in the same change; the desktop
application exchanges portable files and does not call this API. A compatibility superset carrying
both shapes would be a permanent wart giving every future reader two ways to find the same data, and
a parallel endpoint would need its own failure mode for enrolled accounts anyway, leaving two login
paths to keep in sync.

The change shall be called out explicitly in `CHANGELOG.md` and `docs/UPGRADE.md` as a breaking API
change, since a self-hosted operator may have written scripts against the login endpoint.

## 15. Implementation Notes

RFC 6238 verification shall be implemented directly rather than taken from a library.

No TOTP dependency exists in the backend today. Verification is a small amount of code over the
JDK's `javax.crypto.Mac`, which the codebase already uses for keyed hashing, plus a Base32 codec the
JDK does not provide. The algorithm is small, frozen, and exhaustively testable against the test
vectors published in the RFC, so the usual argument for a library — that the maintainer tracks
upstream change — has little to track. Most Java TOTP libraries are thin wrappers with small
maintainer counts, and this code sits directly on the authentication path, so the supply-chain cost
outweighs the saved lines. The RFC test vectors shall be included in the test suite.

The new logic belongs in the existing authentication application package, with cryptographic
primitives in the security package, alongside the existing session token service and password
verifier. Generated contract types shall not appear in domain or persistence signatures. Transaction
boundaries belong on the application operations, not the resource layer.

## 16. Testing Requirements

### 16.1 Backend

Against a real PostgreSQL instance, with deterministic time and ID providers:

- Login for a non-enrolled user is unchanged
- Login for an enrolled user issues no session, no cookies, and no session row at the password step
- A valid code at the exact step, at ±1 step, and at ±2 steps (rejected)
- Replay of an accepted step is rejected
- Recovery code accepted once, rejected on reuse
- Challenge single-use, expiry, and attempt exhaustion
- `rememberMe` is honored from the challenge and cannot be escalated at the second step
- Wrong password produces an identical response and comparable timing whether or not the account is
  enrolled
- Enrollment requires the current password; `PENDING` enrollment does not affect login
- Disable and regenerate require both factors, and revoke other sessions
- Administrative clear is audited, revokes sessions, and discloses no secret material
- Concurrent consumption of the same challenge and the same recovery code resolves to exactly one
  success
- An account deactivated, locked, deleted, or password-changed between the two steps cannot complete
  the second step, and the response is indistinguishable from an invalid challenge
- The identifier rate-limit entry and the account failure counter are not cleared by the password
  step alone
- Repeated failed second-factor attempts lock the account on the configured threshold, and a
  successful verification resets the counter
- The per-account challenge cap evicts the oldest challenge rather than growing without bound
- Step-up is enforced on self-deletion, email change, and administrative operations for enrolled
  actors, and not required for actors without an enrollment
- Password-gated operations are rate-limited on repeated failure
- The operator escape hatch clears an enrollment, revokes sessions, is audited, rejects an invalid
  bootstrap token, and returns no secret or account content
- Notification dispatch failure does not roll back or block the underlying security operation
- The RFC 6238 published test vectors verify correctly
- A step-up grace period is scoped to one session, expires, and is reset by password change,
  re-enrollment, and revocation
- A completed second-factor login starts the grace window, and a session created without a
  second-factor step carries none
- A grace window of zero disables the grace behavior entirely, and every sensitive operation prompts
- Each tunable of section 7.6 is rejected outside its bounds, and the defaults apply to an instance
  whose settings row predates the migration
- Changing a tunable does not retroactively alter challenges or grace windows already issued
- The migration applies cleanly to a database populated by the previous schema
- A login for a non-enrolled user returns the tagged wrapper with `result: SESSION` and the same
  session payload the previous contract returned

### 16.2 Frontend

Unit coverage for the two-stage login store transitions, challenge expiry handling, enrollment
wizard state, and the recovery-code acknowledgement gate. Repository and generated-client metadata
checks, Biome, the strict production build, and the unit suite shall all pass.

### 16.3 End-to-end

The existing browser suite authenticates with a username and password from the environment. Covering
the enrolled path requires a fixture account with a known secret and a TOTP generator in the test
setup. This is new test infrastructure and shall be scoped as part of the work rather than assumed.

## 17. Rollout

1. Migration and configuration land with the feature dormant; no account is enrolled, so no login
   behavior changes.
2. Self-service enrollment becomes available, together with notifications and the operator escape
   hatch. The escape hatch shall ship no later than the first stage that allows an account to become
   enrolled, since the lockout it addresses becomes reachable at that moment.
3. Administrative visibility and clearing become available.

Each stage shall be independently releasable and shall leave the instance in a working state.

The enforcement policy of section 9.2 is a later, separately specified stage and is not part of this
rollout.

## 18. Acceptance Criteria

- A user can enroll, log in with a code, and log in with a recovery code.
- A user who exhausts or loses recovery codes and their device can be restored by an administrator,
  with the action audited.
- A sole administrator in the same situation can be restored by an operator holding the bootstrap
  token, with the action audited.
- Every second-factor lifecycle event reaches the account owner by email, and a mail failure never
  prevents the operation.
- No endpoint, log line, audit record, or error response discloses a secret, a code, or the
  enrollment state of an account to an unauthenticated caller.
- Non-enrolled accounts observe no behavioral change whatsoever.
- The full verification loop passes with no generated-code drift.

## 19. Open Decisions

None. All decisions raised in versions 0.1 and 0.2 are resolved and recorded in section 21.

One item is deliberately left to implementation rather than specification, because it is reversible
and cheap to change: the specific QR library, subject to the constraints in section 11.3.

The tunable defaults of section 7.6 are no longer open. They are administrator-configurable at
runtime, so the specified values are starting points rather than commitments, but their defaults and
bounds are now normative.

## 20. Recommended Follow-Up

Given that this introduces a new secret with backup and rotation consequences, a new
unauthenticated endpoint, a break-glass path authorized by the bootstrap token, and a deliberate
departure from Argon2 for one class of stored secret, the resulting decisions warrant an
architecture decision record alongside the existing series.

## 21. Decision Log

### 21.1 Resolved in version 0.2

| Decision | Outcome | Sections |
| --- | --- | --- |
| Email notification scope | Notify on all second-factor lifecycle events, best-effort and non-blocking | 5.7, 10.1 |
| Step-up scope beyond disable and regenerate | Extended to self-deletion, email change, and administrative operations on other accounts | 5.4, 8.2, 8.4 |
| Failed second-factor attempts and account locking | Counted toward the existing `failed_login_count` and temporary lock, on the same thresholds as password failures | 6.5, 8.4 |
| Last-administrator lockout | Operator escape hatch authorized by the existing bootstrap token | 8.3.1, 13 |

Gaps closed in the same revision without requiring a decision: account-state re-verification at
challenge consumption (5.2.1), deferral of rate-limit clearing and `last_login_at` to full
authentication (5.2.2), rate limiting of authenticated password-gated operations (5.4.1),
cancellation of a pending enrollment (5.1, 8.2), a bound on concurrent challenges (5.2), metrics
requirements (10.2), and the documentation revision list (12.1).

### 21.2 Resolved in version 0.3

| Decision | Outcome | Sections |
| --- | --- | --- |
| Login response shape | Tagged wrapper on `200` with a required `result` discriminator, over `oneOf` or a distinct `202` | 8.1 |
| TOTP implementation | Hand-written RFC 6238 over `javax.crypto.Mac`, with the RFC test vectors in the suite | 15, 16.1 |
| Enforcement policy timing | Deferred to a follow-up; not in the first release | 2.2, 7.6, 9.2, 17 |
| Second-factor rate-limit storage | Widen the existing `login_rate_limits` scope constraint rather than add a table | 6.5, 7.4 |
| QR rendering | Client-side library, given the raw provisioning URI only | 11.3 |
| Recovery-code delivery | Copyable block plus plain-text download, behind an acknowledgement gate | 5.3, 11.2 |
| Step-up ergonomics | Short server-side grace period, scoped to one session | 5.4.2, 7.5 |

The step-up grace decision introduced the only new schema change in this revision: a nullable
re-authentication timestamp on `user_sessions`.

### 21.3 Resolved in version 0.4

The version 0.3 decisions were reviewed for contradictions they introduced. Three were found and
resolved:

| Decision | Outcome | Sections |
| --- | --- | --- |
| Login response is a breaking change for non-enrolled callers | Accepted rather than worked around; documented as a breaking change instead of hidden behind a compatibility shim | 14.1, 12.1 |
| Whether a second-factor login starts the step-up grace window | It does. Login starts the window; it is never satisfied by one, and sessions created without a second-factor step carry no window | 5.4.2 |
| Where the new tunables live | `instance_settings`, alongside the existing login-throttling settings, rather than deployment configuration | 7.6, 9.1 |

The first of these is the consequential one. A tagged wrapper on `200` cannot be introduced without
changing the response body every caller already parses, and the alternatives — content negotiation,
a versioned path, or a query-parameter opt-in — each add a compatibility surface that would outlive
the migration. Accepting the break keeps one contract.

Moving the tunables into `instance_settings` made them administrator-editable at runtime, which in
turn required bounds, an admin settings surface, and the acceptance that a change never retroacts on
challenges already issued.

### 21.4 Resolved in version 0.5

Resolved while planning milestone T0, the first milestone to ship any part of this specification.

| Decision | Outcome | Sections |
| --- | --- | --- |
| Name of the `LoginOutcome` session member | `context`, not `session` | 8.1, 14.1 |
| When `MFA_REQUIRED` and `MfaChallenge` enter the contract | In T0, alongside the wrapper, though nothing emits them until T2 | 8.1 |

`SessionContext` already contains a `session` member, so naming the wrapper member `session` would
have produced `session.session.current`. The rename is confined to this specification and the
contract; no behavior depends on it.

Declaring the full target shape in T0 keeps the committed generated client changing exactly once
rather than twice, and keeps the dormant branch visible to reviewers of the contract from the outset.

### 21.5 Resolved in version 0.6

Resolved while planning milestone T2a, the milestone that made the feature reachable through the API.

| Decision | Outcome | Sections |
| --- | --- | --- |
| HTTP method for `disableTotp` | `POST /api/v1/me/mfa/totp/disable`, not `DELETE /api/v1/me/mfa/totp` | 8.2 |
| Where the operator escape hatch lives | `POST /api/v1/setup/second-factor-reset`, reusing the bootstrap-token flow | 8.3.1 |
| Whether disable ships before the second-factor code requirement | Yes, password-gated only in T2a; T3 adds the code to the same operation | 5.5, 8.2 |

Disabling carries the current password in the request body, and a `DELETE` with a body generates
awkwardly in the typescript-angular client. The house precedent for a password-carrying destructive
self-service operation is `POST /api/v1/me/deletion`, so `disableTotp` follows it.
`cancelTotpEnrollment` keeps `DELETE`, because it has no body.

Shipping disable password-gated in T2a keeps the delivery principle that each stage leaves a working
instance: without it, the only exit from an enrollment would be the break-glass path. T2 and T3
release together, so the password-only shape never reaches a release.

### 21.6 Resolved in version 0.7

Resolved while planning milestone T2b, the milestone that made the feature reachable from a browser.

| Decision | Outcome | Sections |
| --- | --- | --- |
| How a client learns the feature is disabled | A required `available` flag on `MfaStatus`, rather than inferring it from a failed enrollment attempt | 8.2, 11.1 |
| Whether the second login stage is a route | No — a stage rendered inside the login card, with the challenge token held in memory only | 6.7, 11.4 |
| Whether a rejected code is treated as an expired session | No; the client's global 401 handling excludes both login endpoints | 6.5, 11.4 |

`GET /api/v1/me/mfa` answers `NONE` both for an account that has not enrolled and for an instance
where the flag is off, which are byte-identical. Without a second field the settings card would
offer a setup button whose only possible outcome is the `503`. The flag is reported by the status
operation alone; every other operation continues to refuse outright when the feature is disabled.

Making the second stage a route would put the challenge token in a resolver or in the URL, and a
reload mid-challenge would strand the user on a page with no token. Keeping it in memory means a
reload returns to the password stage, which is the correct outcome — the challenge is short-lived by
design and the password stage is always reachable.

The third decision corrects a defect T2b exposed rather than introduced: the browser client treated
any `401` outside `POST /api/v1/auth/login` as a lost session and returned to the login page, so a
single mistyped code discarded a challenge the server was still willing to accept.
