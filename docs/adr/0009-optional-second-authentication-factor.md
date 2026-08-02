# ADR 0009: Optional second authentication factor

- Status: Accepted
- Date: 2026-07-31

## Context

Authentication today is a single factor: an Argon2id-verified password exchanged for an opaque random
session token. `GLACIER_NOTES_CLOUD_2FA_SPECIFICATION.md` adds an optional, per-account TOTP second
factor (RFC 6238) with single-use recovery codes, delivered across milestones T0–T5 in
`GLACIER_NOTES_CLOUD_MILESTONES_2FA.md`.

Four questions were load-bearing enough that answering them differently would change the shape of
the whole feature: where the key that protects enrollment secrets comes from, how recovery codes are
stored, how a half-authenticated caller talks to the server, and what happens when the only
administrator locks themselves out. A fifth question — how the login operation reports that a second
factor is needed — is the only one that changes an existing contract, so it ships first, alone, in
T0.

`GLACIER_NOTES_CLOUD_MILESTONES_BIOME.md` section 8 listed MFA among the items excluded from version
1. This ADR is the formal amendment that removes it from that guard; every other item in the guard
stands.

## Decision

**A dedicated deployment secret encrypts enrolled TOTP secrets** — not the existing session secret.
The two have incompatible rotation semantics. Rotating the session secret today invalidates active
sessions, which is a recoverable inconvenience: users log in again. If that same secret protected
enrollments, rotating it would destroy every enrollment on the instance and lock out every enrolled
user at once, turning a routine operational action into an outage. Separating the secrets keeps
session rotation as cheap as it is now.

**Recovery codes are stored as keyed HMAC digests, not Argon2 hashes.** Argon2 exists to make
low-entropy human-chosen passwords expensive to guess; recovery codes are machine-generated with
enough entropy that brute force is not the threat. Verifying a submitted code means comparing against
up to ten stored digests, and that happens on a path reachable without a session. Ten sequential
Argon2 verifications per request is a denial-of-service amplifier — a small amount of attacker work
buys a large amount of server work. A keyed HMAC gives the property that actually matters here (a
database disclosure does not yield usable codes, because the key is not in the database) at constant,
negligible cost.

**Challenge exchange is a new unauthenticated endpoint, authorized by the challenge token rather
than by CSRF.** A caller that has passed the password step but not the second factor has no session,
so there is no session-bound CSRF token to double-submit and no identity to attach the request to.
The short-lived, single-use, attempt-capped challenge token is the authorization. This mirrors how
login itself is already exempt from CSRF (`security: []`), and it avoids the alternative of issuing a
real but degraded session, which would put a partially-authenticated principal into the session
table and require every authorization check to learn about a second privilege level.

**A locked-out sole administrator recovers through a break-glass path authorized by the bootstrap
token.** Every other recovery route assumes a second actor: another administrator to reset the
enrollment, or an operator with an account to log into. A single-administrator instance — the common
self-hosted shape — has neither. The bootstrap token is already the deployment's out-of-band proof of
physical control over the instance, already used to create the first account, and already held by
whoever can read the deployment secrets. Requiring the escape hatch to ship no later than the first
milestone that permits enrollment (specification section 17) is part of this decision, not a
follow-up.

**The login operation returns a tagged wrapper**, `LoginOutcome`, whose `result` discriminates
between `SESSION` (with a `context`) and `MFA_REQUIRED` (with a `challenge`). Two alternatives were
rejected. A `oneOf` schema models the union more precisely, but the typescript-angular generator's
output for `oneOf` would need hand-editing, and the generated client is committed to this repository
— a contract shape that cannot round-trip through code generation is not usable here. A distinct
`202` status for the challenge case keeps the `200` body unchanged, but it splits one logical
operation across two response schemas that the generator types independently, leaving callers to
switch on a status code to decide which type they hold. A client comparing the status exactly can
tell `202` from `200`; the cost is not ambiguity but that the distinction lives outside the payload,
where neither the schema nor the generated type can carry it.

The wrapper member is named `context`, not `session`: `SessionContext` already contains a `session`
member, so the latter would produce the path `session.session.current`.

## Consequences

The `200` body of `POST /api/v1/auth/login` changes shape for **every** caller, including accounts
that never enroll — a field previously read as `user` is now `context.user`. This is a breaking
change and is documented as one in `CHANGELOG.md` and `docs/UPGRADE.md` rather than hidden behind a
compatibility shim. It lands in T0, isolated from any security behavior, so that it can be reverted
without unpicking authentication logic if the shape proves wrong.

The dedicated enrollment secret becomes backup-critical in a way the session secret is not: an
instance restored without it has its database intact but every enrollment unreadable. Backup and
rotation procedures must cover it explicitly.

The recovery-code HMAC key shares that property — it is not derivable from the database, so losing it
invalidates every outstanding recovery code.

`MFA_REQUIRED` and `MfaChallenge` are defined in the contract from T0 onward but nothing emits them
until T2. The dormant branch is deliberate: the contract and the committed generated client change
exactly once.
