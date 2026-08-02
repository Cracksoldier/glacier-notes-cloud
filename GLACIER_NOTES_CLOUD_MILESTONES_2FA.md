# Glacier Notes Cloud — Two-Factor Authentication Milestone Plan

- **Document status:** Delivered — T0 through T5 are implemented and verified. See
  [`docs/MILESTONE_STATUS.md`](docs/MILESTONE_STATUS.md) for the recorded scope.
- **Document version:** 1.1
- **Last updated:** 2026-08-01
- **Source specification:** [`GLACIER_NOTES_CLOUD_2FA_SPECIFICATION.md`](GLACIER_NOTES_CLOUD_2FA_SPECIFICATION.md) version 0.9
- **Baseline release:** Glacier Notes Cloud v0.2.0
- **Primary stack:** Angular, Quarkus, PostgreSQL, OpenAPI

---

## 1. Purpose

This document converts version 0.4 of the two-factor authentication specification into an ordered
implementation plan. It adds no requirements. Where a milestone states a behavior, that behavior is
traceable to a numbered section of the specification, and the specification remains authoritative if
the two ever disagree.

Each milestone is a delivery gate. A milestone is complete only when:

1. All mandatory deliverables exist.
2. All milestone acceptance criteria pass.
3. Required automated tests pass.
4. No unresolved blocker remains for dependent milestones.
5. The security requirements of specification section 6 relevant to the milestone are verified.
6. The implementation and the OpenAPI contract remain aligned with no generated-code drift.

There are no calendar estimates. Scheduling may be added once availability is known.

### 1.1 Relationship to the v1 milestone plan

[`GLACIER_NOTES_CLOUD_MILESTONES_BIOME.md`](GLACIER_NOTES_CLOUD_MILESTONES_BIOME.md) section 8 lists
MFA under the version 1 scope guard, which states that such items "shall not be pulled into a
milestone unless the specification is formally amended."

This plan is therefore **post-v1 work**, not a v1 milestone. Adopting it constitutes the formal
amendment that section 8 requires. That amendment shall be recorded explicitly — by revising the
scope guard entry rather than silently contradicting it — as part of T0. This plan does not
renumber, reorder, or block the M0–M13 sequence.

---

## 2. Delivery Principles

These are the v1 delivery principles, restated only where the feature changes their application.

### 2.1 OpenAPI first

Every operation is defined in `openapi/glacier-notes-v1.yaml` before implementation, regenerated
with `./mvnw -pl backend clean generate-sources`, and committed together with the generated
frontend client. Generated sources are never hand-edited. This applies with particular force here:
the decision to use a tagged wrapper rather than `oneOf` (specification section 21.2) exists
specifically because the generator's `oneOf` output would otherwise require hand-editing.

### 2.2 Fail closed

Every failure mode of this feature shall deny rather than allow. An unreadable enrollment secret, a
missing key, an expired challenge, an exhausted attempt budget, and an ambiguous account state all
resolve to a denied authentication, never to a session.

### 2.3 No enumeration regression

The existing login path performs constant work against a dummy hash when no account matches. No
milestone may introduce a branch, a timing difference, an error code, or a status code that lets a
caller *who has not supplied the correct password* distinguish an enrolled account from a
non-enrolled one, or an existing account from a missing one (specification section 6.1). Past a
correct password the distinction is unavoidable, because the challenge is the response.

### 2.4 Non-enrolled accounts are never asked for a second factor

Until an account enrolls, its login behavior is byte-for-byte unchanged apart from the response
envelope of section 14.1, and no operation available to it is refused on second-factor grounds.
Every milestone carries a regression test asserting this.

### 2.5 Secrets are never logged, audited, exported, or backed up in the clear

Enrollment secrets, recovery codes, challenge tokens, provisioning URIs, and the enrollment
encryption key are prohibited from log lines, `metadata_json`, API responses beyond their single
point of issuance, the portable `.glacier.json` format, and backup manifests.

### 2.6 Each stage leaves a working instance

Rollout stages are independently releasable. No stage may leave an instance in a state where an
account can enroll but cannot recover, or where an operator has no path back from a lockout.

---

## 3. Milestone Overview

| ID | Milestone | Primary Outcome | Depends On | Rollout Stage | Status |
|---|---|---|---|---|---|
| T0 | Decision Record, Contract, and Dormant Foundation | Amended scope guard, ADR, breaking login contract landed with no behavior change | None | 1 | Complete |
| T1 | Schema and Cryptographic Core | V13 migration, dedicated key, TOTP and recovery-code primitives, tunables | T0 | 1 | Complete |
| T2 | Enrollment, Two-Stage Login, and Recovery Codes | A user can enroll, log in with a code, and recover | T1 | 2 | Complete |
| T3 | Step-Up, Notifications, and Operator Escape Hatch | Sensitive operations gated, owner notified, lockout recoverable | T2 | 2 | Complete |
| T4 | Administrative Surface, Tunables, and Observability | Admin visibility, admin clear, runtime tunables, metrics | T3 | 3 | Complete |
| T5 | Hardening, Documentation, and Release Qualification | Threat model, end-to-end coverage, operator documentation | T4 | 3 | Complete |

### 3.1 Rollout stage mapping

Stages correspond to specification section 17.

- **Stage 1 (T0, T1)** — the feature is dormant. No account can enroll, so no login behavior changes
  beyond the response envelope. Releasable on its own.
- **Stage 2 (T2, T3)** — self-service enrollment. **T2 and T3 shall be released together.** T2 is
  the first point at which an account can become enrolled, and specification section 17 requires the
  operator escape hatch to ship no later than that moment. T2 therefore carries the escape hatch
  itself; T3 completes the stage with step-up coverage and notifications, and the stage is not
  released until both are accepted.
- **Stage 3 (T4, T5)** — administrative visibility, runtime tunables, and release qualification.

### 3.2 Out of plan

Instance-wide enforcement is deferred (specification sections 2.2 and 9.2) and is not part of any
milestone here. Its intended shape is recorded in the specification so the follow-up has a starting
point; nothing in this plan forecloses it.

---

# T0 — Decision Record, Contract, and Dormant Foundation

## Objective

Land the architecture decision record, the formal scope-guard amendment, and the breaking login
contract change — with no functional change to authentication whatsoever. This milestone exists so
that the one unavoidable compatibility break is delivered in isolation, where it can be reviewed and
reverted without entangling any security behavior.

## Scope

- Amendment of the v1 scope guard
- Architecture decision record 0009
- The `LoginOutcome` tagged wrapper on the login operation
- Regeneration of backend interfaces and the committed Angular client
- Frontend adaptation to the new response envelope
- Changelog and upgrade-note callouts for the breaking change

## Deliverables

1. Revised scope-guard entry in `GLACIER_NOTES_CLOUD_MILESTONES_BIOME.md` section 8 recording that
   optional TOTP two-factor authentication is now specified post-v1 work.
2. ADR 0009 covering the decisions of specification section 20: a new deployment secret with backup
   and rotation consequences, a new unauthenticated endpoint, a break-glass path authorized by the
   bootstrap token, and the deliberate departure from Argon2 for recovery codes.
3. `LoginOutcome` schema added to `openapi/glacier-notes-v1.yaml` with a required `result`
   discriminator (`SESSION`, `MFA_REQUIRED`) and optional `context` and `challenge` members, per
   specification section 8.1. `MfaChallenge` is defined in this milestone too, even though nothing
   emits it until T2, so the contract and the committed generated client change exactly once.
4. The login operation's `200` response changed from `SessionContext` to `LoginOutcome`.
5. Regenerated backend sources and committed frontend client, with drift verification.
6. `AuthenticationResource.login` returning `result: SESSION` with the existing session payload; the
   `MFA_REQUIRED` branch is unreachable in this milestone.
7. Frontend authentication store and login component updated to read the wrapper.
8. `CHANGELOG.md` and `docs/UPGRADE.md` entries flagging the breaking response-shape change per
   specification sections 14.1 and 12.1. `CHANGELOG.md` gains an `## [Unreleased]` section, which
   T1–T5 append to until the feature release cuts a version heading.

## Acceptance Criteria

### Contract

- [x] The OpenAPI document defines `LoginOutcome` with `result` required.
- [x] `./mvnw -pl backend clean generate-sources` followed by
      `git diff --exit-code -- frontend/src/app/shared/generated-api` reports no drift.
- [x] No file under `backend/target/generated-sources/openapi` or
      `frontend/src/app/shared/generated-api` was hand-edited.
- [x] The generated Angular client exposes a single typed success return for the login operation.

### Behavior

- [x] A successful login returns `result: SESSION` and a `context` member identical in content to
      the previous `SessionContext` body.
- [x] Cookie issuance, CSRF issuance, session persistence, `last_login_at`, and rate-limit clearing
      are unchanged.
- [x] Failed logins are unchanged in status, error code, body, and timing.
- [x] No `challenge` member is ever populated in this milestone.

### Documentation

- [x] The breaking change is described in `CHANGELOG.md` and `docs/UPGRADE.md` in terms an external
      API consumer can act on, not only in terms of internal refactoring.
- [x] ADR 0009 is committed and follows the existing ADR series conventions.
- [x] The v1 scope-guard amendment is committed in the same change as the ADR.

### Quality

- [x] Existing authentication integration tests pass with only the response-envelope assertion
      updated.
- [x] Frontend unit tests, Biome, the strict production build, and the repository-contract check
      pass.

## Exit Gate

T0 is complete when the login contract has changed shape, every existing authentication test still
passes, the break is documented for external consumers, and no user-observable authentication
behavior has changed.

---

# T1 — Schema and Cryptographic Core

## Objective

Deliver the persistence, key management, and cryptographic primitives the feature needs, with no
endpoint exposing them. At the end of this milestone the tables exist and are empty, the key is
required at startup, and the primitives are proven against published test vectors.

## Scope

- Flyway migration V13
- The dedicated enrollment encryption key and startup validation
- Hand-written RFC 6238 TOTP verification
- RFC 4648 Base32 encoding and the provisioning URI
- AES-256-GCM enrollment-secret encryption with a key identifier
- Keyed-HMAC recovery-code storage
- Challenge token generation and hashing
- The four `instance_settings` tunables with bounds
- Widening of the `login_rate_limits` scope constraint

## Deliverables

1. `V13` migration creating `user_mfa_totp`, `user_mfa_recovery_codes`, and `mfa_challenges`;
   widening the `login_rate_limits` scope check; adding the nullable re-authentication timestamp to
   `user_sessions`; and adding the four tunables to `instance_settings` with their defaults
   (specification sections 7.1 through 7.6).
2. Configuration for the enrollment encryption key, resolvable inline or from a file, consistent with
   the existing bootstrap token and session secret (specification section 12).
3. Startup validation that fails fast when second-factor support is enabled and the key is missing or
   does not satisfy the secret policy, with no placeholder or derived fallback.
4. A TOTP verifier over `javax.crypto.Mac` implementing RFC 6238, with a configurable step window.
5. Base32 encoding and provisioning-URI construction.
6. An enrollment-secret cipher using AES-256-GCM, writing and honoring `key_id`.
7. Recovery-code generation and keyed-HMAC storage and lookup (specification section 6.3).
8. Challenge token generation and hashing following the existing `SessionTokenService` pattern,
   including its domain-prefixed HMAC and constant-time comparison.
9. Repository adapters for the three new tables, owner-scoped where the row is owned.
10. An extended `LoginRateLimiter` supporting the new scope without breaking its existing callers.
11. `docs/DATABASE.md` and `docs/DEVELOPMENT.md` updates for the new tables and the new required
    local environment variable.

## Acceptance Criteria

### Migration

- [x] The migration applies cleanly to a database populated by the V12 schema.
- [x] The migration is additive; no existing column or constraint is dropped or narrowed.
- [x] Hibernate schema validation passes against the migrated schema.
- [x] An `instance_settings` row that predates the migration receives the specified defaults.
- [x] The `login_rate_limits` primary key and existing `IDENTIFIER` and `IP` behavior are unchanged.

### Cryptography

- [x] The RFC 6238 published test vectors verify correctly.
- [x] A code is accepted at the exact step and at ±1 step, and rejected at ±2 steps.
- [x] An enrollment secret encrypted and decrypted round-trips exactly, and a ciphertext written
      under one `key_id` is identifiable as such.
- [x] Decryption under the wrong key fails closed rather than yielding usable output.
- [x] Recovery-code lookup is a single indexed lookup and uses constant-time comparison.
- [x] Challenge tokens carry at least 256 bits of entropy and only their hashes are persisted.

### Configuration

- [x] The application refuses to start when the feature is enabled and the key is absent, short, or a
      placeholder.
- [x] The key is accepted both inline and from a file with restrictive permissions.
- [x] The key never appears in logs, startup banners, health output, metrics, or error messages.
- [x] Rotating the session secret has no effect on stored enrollments.

### Tunables

- [x] Each of the four tunables is rejected outside its documented bounds.
- [x] A grace window of zero is accepted and means the grace behavior is disabled.

### Isolation

- [x] No HTTP endpoint reads or writes any of the three new tables in this milestone.
- [x] Login behavior is unchanged from T0.

## Exit Gate

T1 is complete when the schema, key management, and primitives are in place and tested, the instance
still cannot enroll anyone, and login behavior is identical to T0.

---

# T2 — Enrollment, Two-Stage Login, and Recovery Codes

## Objective

Make the feature usable: a user can enroll a second factor, log in with a code, log in with a
recovery code, and — if everything else fails — be recovered by an operator holding the bootstrap
token.

This is the first milestone at which an account can become locked out of its own credentials. The
escape hatch is therefore in scope here rather than later.

## Scope

- Enrollment start, confirmation, and cancellation
- Recovery-code issuance and consumption
- The `MFA_REQUIRED` login branch and challenge lifecycle
- Challenge re-verification at consumption
- Rate limiting and account-lock integration for the second step
- The operator escape hatch
- Frontend second login stage and account-settings enrollment card

## Deliverables

1. OpenAPI operations for enrollment start, enrollment confirmation, enrollment cancellation,
   challenge verification, and recovery-code regeneration (specification section 8.2), with the error
   codes of section 8.4.
2. The operator escape-hatch operation authorized by the existing bootstrap token, per specification
   section 8.3.1.
3. Enrollment requiring the current password, producing a `PENDING` record with the expiry of
   section 7.6, and activating only on a verified code.
4. Recovery-code issuance at activation, presented exactly once, with consumption marking a single
   code used.
5. Login returning `result: MFA_REQUIRED` with a challenge for enrolled accounts, issuing no session,
   no cookie, and no `user_sessions` row at the password step (specification section 5.2).
6. Challenge single-use semantics, expiry, per-challenge attempt cap, per-account concurrent-challenge
   cap with oldest-first eviction, and replay rejection via the highest-accepted-step record.
7. Re-verification of account state at challenge consumption, resolving every adverse state to the
   generic invalid-challenge response (specification sections 5.2.1 and 8.4).
8. Deferral of rate-limit clearing and `last_login_at` to full authentication (specification section
   5.2.2).
9. Second-factor failures contributing to `failed_login_count` and the existing lock thresholds, and
   the new rate-limit scope (specification section 6.5).
10. Frontend second login stage: numeric code entry with paste support, recovery-code switch,
    remaining validity, remaining attempts, expiry recovery, and `Retry-After` handling
    (specification section 11.1).
11. Frontend two-factor card in account settings with the enrollment wizard, client-side QR
    rendering, grouped uppercase manual secret, copy and download actions, and the acknowledgement
    gate (specification sections 11.2 and 11.3).
12. English and German strings for every new surface.

## Acceptance Criteria

### Enrollment

- [x] Starting enrollment requires the current password and is audited on success and failure.
- [x] A `PENDING` enrollment does not affect login in any way.
- [x] Confirmation requires a valid code for the pending secret and activates the enrollment.
- [x] Enrollment can be cancelled, and an expired pending enrollment is discarded.
- [x] The provisioning URI is generated server-side; the QR is rendered client-side from that URI and
      the library receives nothing else.
- [x] The manual-entry secret is always shown alongside the QR code.

### Login

- [x] A non-enrolled user's login is unchanged from T0.
- [x] An enrolled user's password step issues no session, no cookie, and no session row.
- [x] A valid code at the exact step and at ±1 step completes login; ±2 steps does not.
- [x] Replaying an already-accepted step is rejected.
- [x] `rememberMe` is carried from the challenge and cannot be escalated at the second step.
- [x] A wrong password produces an identical response and comparable timing whether or not the
      account is enrolled.
- [x] An account deactivated, locked, deleted, or password-changed between the two steps cannot
      complete the second step, and the response is indistinguishable from an invalid challenge.
- [x] Challenges are single-use, expire, and are destroyed on attempt exhaustion.
- [x] The per-account challenge cap evicts the oldest challenge rather than growing without bound.
- [x] Concurrent consumption of the same challenge resolves to exactly one success.

### Recovery codes

- [x] Codes are issued once at activation and are never retrievable afterward.
- [x] A code is accepted once and rejected on reuse.
- [x] Concurrent consumption of the same code resolves to exactly one success.
- [x] Regeneration invalidates all prior codes.
- [x] The dismissal acknowledgement is required before the codes leave the screen.
- [x] The plain-text download uses a stable, non-identifying filename.

### Abuse resistance

- [x] Repeated failed second-factor attempts lock the account on the configured threshold, and a
      successful verification resets the counter.
- [x] The identifier rate-limit entry and the account failure counter are not cleared by the password
      step alone.
- [x] Rate-limited responses carry `Retry-After`.

### Operator escape hatch

- [x] It clears an enrollment, revokes the account's sessions, and is audited.
- [x] It rejects an invalid bootstrap token generically and is rate-limited.
- [x] It returns no secret material and no account content.
- [x] Its invocation is counted in metrics.

### Disclosure

- [x] No response, log line, or audit record discloses a secret, a code, or a challenge token.
      Enrollment state is not disclosed to a caller who has not supplied the correct password; past
      that point the challenge is itself the disclosure (specification section 18).
- [x] Challenge state is held only in memory on the client and never reaches `localStorage`,
      `sessionStorage`, or the URL.

## Exit Gate

T2 is complete when a user can enroll, log in with a code, log in with a recovery code, and be
recovered by an operator — and when a non-enrolled account is still never asked for a second factor
and never refused on second-factor grounds. The `LoginOutcome` envelope introduced in T0 is visible
to every account and is out of scope for this gate (specification section 18.1).

---

# T3 — Step-Up, Notifications, and Operator Escape Hatch Completion

## Objective

Extend the second factor to the sensitive operations that a stolen session would otherwise reach,
and notify the account owner of every lifecycle event where the instance has mail configured. This milestone completes rollout
stage 2 and is released together with T2.

## Scope

- Step-up verification on sensitive operations
- The session-scoped step-up grace window
- Rate limiting of authenticated password-gated operations
- Session revocation semantics on second-factor changes
- Interaction with password reset
- Best-effort, non-blocking email notification of every lifecycle event

## Deliverables

1. Step-up enforcement on disable, regenerate, self-deletion, email change, and administrative
   operations on other accounts, for actors that have an enrollment (specification section 5.4).
2. The session-scoped grace window written to the `user_sessions` re-authentication timestamp, started
   by a completed second-factor login, expiring on the configured window, and reset by password
   change, re-enrollment, and revocation (specification section 5.4.2).
3. Rate limiting of authenticated password-gated operations (specification section 5.4.1).
4. Session revocation on disable, regenerate, and administrative or operator clear (specification
   section 5.5).
5. Password-reset interaction per specification section 5.6.
6. Email notifications for every second-factor lifecycle event, dispatched best-effort and outside
   the security operation's transaction (specification section 5.7), in English and German.
7. Inline code prompts on the sensitive-operation surfaces, alongside the password field they already
   present rather than through a separate navigation step (specification section 11.1).

## Acceptance Criteria

### Step-up

- [x] Step-up is enforced on self-deletion, email change, disable, regenerate, and administrative
      operations for enrolled actors.
- [x] Step-up is not required for actors without an enrollment.
- [x] Disable and regenerate require both factors and revoke other sessions.
- [x] Password-gated operations are rate-limited on repeated failure.
- [x] Step-up failures are audited.

### Grace window

- [x] A completed second-factor login starts the window.
- [x] Login starts the window but never satisfies a step-up prompt on its own.
- [x] A session created without a second-factor step carries no window.
- [x] The window is scoped to one session and does not transfer to another.
- [x] The window expires and is reset by password change, re-enrollment, and revocation.
- [x] A configured window of zero causes every sensitive operation to prompt.
- [x] Changing the tunable does not retroactively alter windows already issued.

### Notifications

- [x] Every lifecycle event of specification section 5.7 produces a notification attempt.
- [x] A dispatch failure neither rolls back nor blocks the underlying security operation.
- [x] No notification contains a secret, a code, a challenge token, or a provisioning URI.
- [x] Notifications exist in both dictionaries.

### Stage completion

- [x] Rollout stage 2 is releasable: an account can enroll, authenticate, recover, be notified, and
      be restored by an operator.

## Exit Gate

T3 is complete when a stolen session cannot perform a sensitive operation on an enrolled account
without a fresh factor, every lifecycle event dispatches a notification attempt, and stage 2 can be
released. Delivery itself is best-effort: an instance without SMTP satisfies this gate by skipping.

---

# T4 — Administrative Surface, Tunables, and Observability

## Objective

Give administrators the visibility and controls the feature requires, and give operators the signals
needed to detect an attack without reading the audit table.

## Scope

- Administrative second-factor state on the user detail page
- Administrative clear
- The four tunables on the instance settings page
- Audit events
- Metrics

## Deliverables

1. Administrative visibility of whether a user has an active second factor and when it was confirmed
   — and nothing more (specification section 9.1).
2. The administrative clear operation of specification section 8.3, revoking sessions and audited
   with both actor and target.
3. The four tunables of specification section 7.6 exposed on the instance settings page, validated on
   both sides and rejected server-side regardless of what the client permits.
4. The audit event types of specification section 10.1, with counts and states only in
   `metadata_json`.
5. The metric counters of specification section 10.2, with no per-account labels.
6. Operations documentation noting that instance time synchronization becomes a correctness
   dependency once the feature is enabled.

## Acceptance Criteria

### Administrative visibility

- [x] An administrator sees active state and confirmation date on the user detail page.
- [x] An administrator cannot see the secret, the recovery codes, or the remaining code count.
- [x] Administrative clear revokes sessions, is audited, and discloses no secret material.
- [x] A `USER` account cannot invoke any administrative second-factor operation.
- [x] The last-administrator protections are unaffected by this surface.

### Tunables

- [x] All four tunables are editable by an administrator and validated against their bounds.
- [x] A server-side rejection occurs even when the client permits an out-of-bounds value.
- [x] Setting changes generate audit events, consistent with existing instance settings.
- [x] An invalid submission leaves no partial configuration.

### Audit and metrics

- [x] Every event type in specification section 10.1 is emitted with the correct result values.
- [x] No secret material appears in `metadata_json`.
- [x] Counters exist for challenges issued, consumed, expired, and exhausted; verifications by
      outcome with TOTP and recovery paths distinguished; recovery codes consumed; enrollments
      activated and disabled; and escape-hatch invocations.
- [x] No metric label contains a username, email, account identifier, or token.
- [x] A sustained rise in exhausted challenges is observable without querying the audit table.

## Exit Gate

T4 is complete when an administrator can see and clear second-factor state without gaining access to
any secret, can tune the feature's bounds at runtime, and an operator can alert on an attack from
metrics alone.

---

# T5 — Hardening, Documentation, and Release Qualification

## Objective

Qualify the feature for release: complete the end-to-end coverage that requires new test
infrastructure, revise the security and threat documentation, and confirm the backup, restore, and
rotation story.

## Scope

- End-to-end test infrastructure for an enrolled account
- Threat model and security documentation revision
- Backup, restore, and key rotation documentation
- Deployment and upgrade documentation
- Portable-format and desktop-compatibility confirmation
- Release qualification

## Deliverables

1. A Playwright fixture account with a known secret and a TOTP generator in the test setup,
   explicitly scoped as new infrastructure rather than assumed (specification section 16.3).
2. End-to-end coverage of enrollment, two-stage login, recovery-code login, and a step-up prompt.
3. `docs/THREAT_MODEL.md` revision replacing the baseline assumption that password compromise equals
   account compromise, and adding the enrollment secret, recovery codes, and challenge tokens as
   assets with their attack surfaces.
4. `docs/SECURITY.md` revision covering the second-factor model, secret handling, and the operator
   escape hatch.
5. `docs/BACKUP_RESTORE.md` revision per specification section 13, stating explicitly that a database
   backup alone is no longer sufficient to restore a working instance.
6. Documented key rotation procedure using the `key_id` column, even if tooling is deferred.
7. `docs/UPGRADE.md`, `docs/MIGRATIONS.md`, `deployment/README.md`, `.env.example`, `README.md`, and
   `CHANGELOG.md` revisions per specification section 12.1.
8. Confirmation that the portable `.glacier.json` format is unchanged and that all
   `compatibility-fixtures/desktop-schema-v1` fixtures still pass.
9. `docs/MILESTONE_STATUS.md` entry for this plan.

## Acceptance Criteria

### Testing

- [x] The full backend suite of specification section 16.1 passes against real PostgreSQL with
      deterministic time and ID providers.
- [x] Frontend unit coverage exists for the two-stage login store transitions, challenge expiry
      handling, enrollment wizard state, and the acknowledgement gate.
- [x] The end-to-end suite covers an enrolled account without embedding a real user's secret in the
      repository.
- [x] The full verification loop passes with no generated-code drift.
- [x] `npm audit --omit=dev --audit-level=high` passes with the QR library included.

### Compatibility

- [x] No second-factor field appears in any exported `.glacier.json`.
- [x] All desktop schema-v1 compatibility fixtures still import successfully.
- [ ] A fresh cloud export is still readable by a compatible desktop client. *(Not verified here: the
      portable codec and all three `desktop-schema-v1` fixtures pass, but a round trip through the
      desktop application is a manual check outside this repository.)*

### Operations

- [x] Backup documentation states that losing the enrollment key locks out every enrolled account
      simultaneously, including all administrators, and that the escape hatch is the only remaining
      route.
- [x] Restoring a pre-enrollment database alongside a current key is documented as leaving that
      account with no second factor, and as expected rather than corrupt.
- [x] The upgrade path for an existing instance is documented and tested on a populated database.
- [x] The new secret is generated the same way as the existing ones, with the same restrictive
      permissions, in both the local and production instructions.

### Release readiness

- [x] Every acceptance criterion in specification section 18 is satisfied and traceable to a test,
      through the section 6 matrix in
      [`docs/SECOND_FACTOR_TRACEABILITY.md`](docs/SECOND_FACTOR_TRACEABILITY.md).
- [x] The threat model records any remaining accepted risk.
- [x] No release-blocking defect remains.

## Exit Gate

T5 is complete when the feature is fully tested, an operator can deploy, back up, restore, and rotate
without ambiguity, and no unresolved blocker stands between the feature and a release.

T5 cuts no release. `pom.xml` stays at `0.2.0` and `CHANGELOG.md` stays under `[Unreleased]`;
bumping the version, writing the release notes, and tagging v0.3.0 are a separately approved step
(specification section 21.10). A milestone that both produced the evidence and tagged the release it
justifies would leave the tag unreviewable.

---

## 4. Cross-Milestone Definition of Done

A change in this plan is not done until every applicable condition holds.

### Contract

- [x] The OpenAPI operation exists before the implementation.
- [x] Generated backend and frontend code is refreshed and committed in the same change.
- [x] Generated DTOs do not appear in domain or persistence signatures.
- [x] No further breaking API change is introduced beyond the one accepted in T0.

### Backend

- [x] Logic lives in the application layer, not in the resource class.
- [x] Transaction boundaries are on application operations or repository writes.
- [x] Time and ID generation come from the injected providers.
- [x] Every owned lookup is owner-scoped.
- [x] A new migration is added rather than an applied one edited.
- [x] PostgreSQL integration tests pass.

### Frontend

- [x] The generated client is used; no handwritten duplicate DTO is introduced.
- [x] Loading, empty, success, error, expired, rate-limited, and unauthorized states are handled.
- [x] English and German strings are present.
- [x] Dark and light themes remain readable.
- [x] Keyboard use and accessible labels are covered.
- [x] Router guards are treated as navigation aids only.

### Security

- [x] The change cannot be used to distinguish an enrolled account from a non-enrolled one.
- [x] The change fails closed.
- [x] Rate limits apply wherever a credential or token attempt is possible.
- [x] No secret reaches a log, an audit record, a metric label, an export, or a backup manifest.
- [x] Constant-time comparison is used for every secret comparison.

### Operations

- [x] Audit events carry only approved metadata.
- [x] Metrics carry no per-account labels.
- [x] Notification dispatch cannot roll back or block a security operation.
- [x] Configuration and documentation are updated in the same change.

---

## 5. Release-Blocking Severity

### Blocker

- A caller who has not supplied the correct password can determine whether an account exists or is
  enrolled.
- The second factor can be bypassed by any path other than a valid code, a valid recovery code, an
  administrative clear, or the operator escape hatch.
- A challenge or recovery code can be consumed more than once.
- An enrollment secret, recovery code, or the enrollment key is disclosed or logged.
- A non-enrolled account is asked for a second factor, or is refused an operation on second-factor
  grounds. The `LoginOutcome` envelope of section 14.1 is the one accepted exception and is not a
  blocker.
- An enrolled account has no recovery path.
- The migration fails on a populated database.

### Critical

- Second-factor failures do not contribute to lockout, or the second step is otherwise
  unrate-limited.
- A step-up prompt can be satisfied without a fresh factor.
- The grace window leaks across sessions or survives a password change.
- Session revocation does not occur on disable, regenerate, or clear.
- Backup or rotation documentation omits the key-loss consequence.

### Major

- A lifecycle notification is not sent.
- An administrator cannot see or clear second-factor state.
- A tunable cannot be adjusted or is not validated.
- A required metric or audit event is missing.
- A new surface is missing from one of the two dictionaries.

### Minor

- Cosmetic issues in the enrollment wizard without accessibility impact.
- Wording problems that do not mislead about security consequences.

---

## 6. Traceability Requirements

Before T5 can close, a matrix shall connect each specification requirement to at least one of: a
unit test, a PostgreSQL integration test, an API contract test, an Angular component test, an
end-to-end test, a security test, or a documented manual procedure.

At minimum the matrix shall explicitly cover:

1. Non-enrolled login is unchanged
2. Enumeration resistance across both steps
3. TOTP correctness against the RFC vectors, including the step window and replay rejection
4. Challenge single-use, expiry, attempt exhaustion, and concurrent-consumption safety
5. Recovery-code single-use and regeneration
6. Account-state re-verification between the two steps
7. Rate-limit and lock-counter interaction with the second step
8. Step-up coverage and the grace window's scope, expiry, and reset conditions
9. Session revocation on every second-factor change
10. Administrative clear disclosing nothing
11. Operator escape hatch, including invalid-token rejection
12. Notification non-blocking behavior
13. Secret-at-rest encryption, key identification, and rotation
14. Tunable bounds and default application to a pre-existing settings row
15. Migration on a populated database
16. Portable-format exclusion and desktop fixture compatibility

**Satisfied by** [`docs/SECOND_FACTOR_TRACEABILITY.md`](docs/SECOND_FACTOR_TRACEABILITY.md), which
covers all sixteen areas and cites individual test methods rather than classes. It records three
requirements that hold without a test that would fail on regression: the portable-format exclusion is
structural rather than asserted, enumeration *timing* is not measured, and the desktop round trip is
manual and outside this repository.

---

## 7. Recommended Issue Structure

Each milestone may be one epic. Suggested issue types match the existing plan: `architecture`,
`openapi`, `backend`, `frontend`, `database`, `security`, `operations`, `testing`, `documentation`,
`compatibility`.

Every implementation issue should reference the specification section it satisfies, the OpenAPI
operation IDs it touches, the user-visible behavior, the security implications, the acceptance
criteria, the test expectations, and the behavior explicitly excluded.

---

## 8. Approval

This plan describes work that has not been authorized. Nothing in it shall be implemented until the
specification is approved for implementation and the v1 scope-guard amendment of section 1.1 is
accepted.
