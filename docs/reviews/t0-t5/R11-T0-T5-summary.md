# R11 CodeRabbit Review Summary — T0–T5 (Optional Second Factor)

## Review Metadata

- Scope: the committed T0–T5 diff, reviewed as seven per-milestone runs
- Full range: `c1de83f~1..bf9b276` (16 commits)
- Command per run: `coderabbit review --agent --committed --base-commit <milestone>~1`
- CodeRabbit CLI: `0.7.1`
- Result: `review_completed` for all seven runs
- Raw findings: 36 (`18 major`, `18 minor`)

| Run | Milestone | Base | Target | Files | Findings | Raw artifact SHA-256 |
|---|---|---|---|---:|---:|---|
| R11-t0 | T0 — contract and dormant foundation | `4570b436` | `c1de83fc` | 17 | 10 | `8e068e2ecef8de278b02c3d1a28b6354fe6b48e6869ef328e78b42881286cd4e` |
| R11-t1 | T1 — schema and crypto core | `c1de83fc` | `c225e81f` | 32 | 5 | `3cafa7306e6049c22080afa81246d19d1ab90e4652a3e56c032b868f4e967d18` |
| R11-t2a | T2a — contract and backend | `a6cd7852` | `7acc9414` | 46 | 7 | `e6afa0847599db6bbe63fd46f7660115cb1d2512a800de6298bb9076cf58adcc` |
| R11-t2b | T2b — browser enrollment and login | `76b5ff14` | `6ea7a8bb` | 37 | 4 | `efe7d081de6c866698312b01d641b0d503f831222007d58175b29d2baebf54d9` |
| R11-t3a | T3a — step-up enforcement | `0b327f42` | `83c48eb1` | 49 | 1 | `53c551425493b2a79f5a91c702bc8839a2751569b4cc07e330124abfc360bf9b` |
| R11-t3b | T3b — browser step-up prompts | `8bfa5f07` | `214ec0ce` | 19 | 2 | `fda29fd5333a7a884dc534ad35cc23593dcf1c3bf991c99b65c325f2abeefe5e` |
| R11-t4 | T4 — administrative surface | `f27a5940` | `40819e3e` | 33 | 2 | `e825e8157aeed9b1f1c0587a1e7fe16002ac6e56e26ac0948ae945f3555b9867` |
| R11-t5 | T5 — hardening and documentation | `eed6f166` | `bf9b276d` | 18 | 5 | `070a91f25e882fecf396b7d0e72614dd925334ed674252cb614bab09610fd8a2` |

Every raw stream contains a terminal completion event and no error event.

### Why seven runs rather than one

A single review of the full range was attempted first and was rejected by the service with
`payload_too_large` (106 files, 8,394 insertions). The rejected stream carried a terminal error event
and was discarded rather than filed, per the rule in [README.md](../README.md). Probing established
that the ceiling sits between 46 and 106 changed files, so the range was split along its own
milestone boundaries — each run covers exactly one feature commit, excluding the interleaved
`chore(graphify)` snapshots.

The cost of reviewing snapshots rather than the final tree is that a finding may describe an
intermediate state that a later milestone already corrected. Four of 36 did; they are marked
**Superseded** below. This was measured, not assumed.

Runs did not execute in milestone order: T2a ran first as a payload probe. The service also enforces
a free-tier rate limit of roughly three reviews per 48-minute window, so the remaining runs were
driven by a retrying script over several hours. Neither affects the evidence, since each run is
pinned to its own commit pair.

## Finding Classification

Human triage compared every comment against the source at `db1c369` (the tree after the T5
documentation audit), the milestone acceptance criteria, the specification decision log, and the
prior M0–M11 review evidence.

### T0 — contract and dormant foundation

| Severity | Finding | Classification | Final status |
|---|---|---|---|
| Major | Spec §5.7 requires a lifecycle notification unconditionally, but delivery is best-effort and SMTP is optional | **Valid.** The criterion as written is unsatisfiable on an instance without SMTP. The obligation is now on the attempt, and §5.7's lead points at the best-effort provisions it already carried further down. | Resolved |
| Major | Milestone release-blocker repeats the same unconditional notification requirement | **Valid.** Same defect, mirrored into the release gate. T3's objective and exit gate now require a dispatch attempt; an instance without SMTP satisfies the gate by skipping. | Resolved |
| Major | Spec forbids disclosing enrollment state, but `MFA_REQUIRED` necessarily discloses it after a correct password | **Valid.** The prohibition must be scoped to the pre-password phase; the implementation is correct and the prose is not. Scoped in spec §18 and in the T2 criterion that mirrored it. | Resolved |
| Major | Spec §18 claims "no behavioral change whatsoever" for non-enrolled accounts | **Valid.** T0 itself changed the login response shape via the `LoginOutcome` envelope. Replaced by the narrower guarantee the code makes — never asked for a code, never refused on second-factor grounds — with new §18.1 recording the envelope change explicitly. | Resolved |
| Major | Milestone release-blocker repeats the "no behavioral change" absolute | **Valid.** Same defect in the release gate. T2's exit gate now carries the same narrowed claim and defers to §18.1. | Resolved |
| Minor | `AuthStore.login` keys session establishment off `outcome.context` truthiness rather than `result === SESSION` | **Not a defect.** The server always sends `context` with a `SESSION` result, so the added check would be redundant with the condition already written. Declined on the same ground as the `clearUserMfa` guard below: handling for a state the contract forecloses. | Closed — no change |
| Minor | Spec overstates that the Angular client is the only consumer of the login contract | **Valid.** It is the only *in-repository* consumer; the portable format and desktop client make the broader claim unsafe. §14.1 now says so and connects the qualifier to the operator call-out it already required. | Resolved |
| Minor | ADR 0009's rationale for rejecting `202` overstates client ambiguity | **Valid.** Exact status comparison does distinguish `202` from `200`; the real reason is the split response schema, which the ADR also gives. The ambiguity claim is dropped in favour of the schema argument. | Resolved |
| Minor | `MfaChallenge` schema lacks `minimum: 0` on `attemptsRemaining` and `minItems`/`uniqueItems` on `acceptedFactors` | **Valid in part.** `minimum` and `minItems` added and regenerated. `uniqueItems` rejected: the TypeScript generator turns it into `Set<…>`, which `JSON.parse` never produces, so the committed client would carry a type that lies. | Resolved |
| Minor | Milestone header still cites specification version 0.4 and a draft status | **Superseded.** Corrected before this review by the T5 documentation audit; the header now cites version 0.9 and "Delivered". | No change |

### T1 — schema and cryptographic core

| Severity | Finding | Classification | Final status |
|---|---|---|---|
| Major | `TotpVerifier.generate` formats the code without a locale | **Valid defect.** `String.format("%0Nd", …)` follows the default locale, so a host whose locale carries a non-ASCII numbering system emits non-Latin digits. `constantTimeEquals` then compares them against the user's ASCII input and every verification fails. No test covers a non-default locale. | Resolved |
| Major | `MfaChallengeEntity.recordFailure` increments in memory without concurrency control | **Valid defect, for a different reason than stated.** `MfaChallengeService` does take `PESSIMISTIC_WRITE` on the user row — but at line 112, *after* reading the challenge at line 107. Under READ COMMITTED a second transaction reads the stale count, blocks on the user lock, then writes the same value back. The lost increment lets a caller exceed `mfaChallengeAttemptLimit`. Fixed with an atomic `update … returning`, followed by `entityManager.refresh` so the later dirty flush cannot write the stale count back over it. | Resolved |
| Major | `CHANGELOG.md` implies all login behavior is unchanged | **Valid.** The `LoginOutcome` envelope changed the response shape. Same sentence as the T2a and T2b changelog findings; rewritten once to say the schema landed a release ahead of the endpoints, so upgrading to it alone changes no behaviour. | Resolved |
| Minor | `MfaStartupValidator` resolves the enrollment secret before checking whether MFA is enabled | **Valid defect.** Java evaluates both arguments at line 34, and `SecretProvider.resolve` throws when a configured file is unreadable. A stale `GLACIER_MFA_ENCRYPTION_SECRET_FILE` path therefore blocks startup even with the feature off. | Resolved |
| Minor | `README.md` sets MFA configuration after the Compose startup command | **Valid.** The order asks the reader to reconfigure a running container. The secret-generation block now precedes `docker compose up`, which moved into its own step. | Resolved |

### T2a — contract and backend

| Severity | Finding | Classification | Final status |
|---|---|---|---|
| Major | `MfaFailure.Reason.UNAVAILABLE` has no documented status or error code | **Partially superseded.** Specification §8.4 gained the `503`/`MFA_UNAVAILABLE` row in commit `564404a`. The OpenAPI operations still do not declare the status. `503` added to the three operations that call `requireEnabled`. | Resolved |
| Major | Break-glass example expands the bootstrap token into process arguments | **Valid.** `deployment/README.md` puts the token where any local process can read it from the process table. Replaced with a `0600` header file and `-H @file`, with a fallback noted for curl older than 7.55.0. | Resolved |
| Minor | `MfaEnrollmentService.confirm` lacks `dontRollbackOn` | **Valid defect.** The expired-enrollment `entityManager.remove` is discarded when `MfaFailure.notEnrolled()` is thrown. `confirm` audits only on success, so no audit row is lost — the cleanup is. Third occurrence of this trap in this feature. | Resolved |
| Minor | Break-glass `curl` example neither fails on HTTP errors nor prints the status | **Valid.** `--fail-with-body` and `--write-out` make the documented `204` verifiable. | Resolved |
| Minor | `MfaEnrollmentTest` compares a confirmation response against the grouped secret | **Valid.** The assertion should strip the display grouping before comparing. Both forms are now checked, in the confirmation body and the status body. | Resolved |
| Minor | `CHANGELOG.md` V13 entry says no endpoint reads the MFA tables | **Valid.** Stale within its own `[Unreleased]` section, which goes on to document those endpoints. Fixed with the T1 finding above — the same sentence. | Resolved |
| Minor | `docs/UPGRADE.md` describes the second factor as forthcoming | **Superseded.** Reworded during the T4 documentation pass; the file now describes it as available. | No change |

### T2b — browser enrollment and login

| Severity | Finding | Classification | Final status |
|---|---|---|---|
| Major | `AuthStore.completeSecondFactor` throws synchronously from a method returning an `Observable` | **Valid.** A subscriber's `error` handler cannot see it. Currently unreachable — the component only calls it with a challenge open — but latent. Now returns `throwError`. | Resolved |
| Major | The two-factor card renders nothing when its status load fails | **Valid.** `@if (status(); as value)` yields an empty card on a null status; the `mfaCardLoadFailed` string exists but no path reaches it, so a failed load is indistinguishable from an absent feature. The card now has an `@else if` branch that renders the message. | Resolved |
| Minor | `copyCodes()` does not handle a clipboard rejection | **Valid.** It bypasses the `run()` helper every other action uses, so a denied permission produces an unhandled rejection and silent failure. Fixed with a dedicated message pointing at the download, rather than routing through `run()` — the generic problem text would not tell the user their codes are still recoverable. | Resolved |
| Minor | `CHANGELOG.md` retains a stale groundwork statement | **Valid.** Same doc drift as the T1 and T2a entries, and the third milestone at which the same sentence was flagged. Fixed once. | Resolved |

### T3a — step-up enforcement

| Severity | Finding | Classification | Final status |
|---|---|---|---|
| Major | The administrative reset-link call passes an empty step-up request | **Superseded.** True at `83c48eb`, when the backend gate existed but no browser surface collected credentials. T3b (`214ec0c`) added the prompt; at `db1c369` the call passes `currentPassword` and `code`. | No change |

T3a is the largest backend milestone in the range — step-up enforcement across six operations,
session revocation, ten mail templates, migration V14, and seven test suites — and drew no finding
against its own backend code.

### T3b — browser step-up prompts

| Severity | Finding | Classification | Final status |
|---|---|---|---|
| Major | `admin-user-detail.fail()` consults the step-up prompt for actions that have no pending retry | **Valid.** Activate, deactivate, unlock, and revoke-sessions call `fail()` directly; a step-up refusal opens a code field attached to no queued action and suppresses the error message. `fail()` now consults the prompt only while a confirmation panel is open. | Resolved |
| Minor | `StepUpPrompt.handle` returns `true` for a repeated refusal while already open | **Valid.** The caller suppresses the error, so the user sees the code field silently cleared with no explanation. `handle` now returns `false` once the field is already open. | Resolved |

### T4 — administrative surface

| Severity | Finding | Classification | Final status |
|---|---|---|---|
| Major | `AdministrationResource.clearUserMfa` dereferences its request body without a null guard | **Not a defect.** Both bodies are declared `required: true`, which the generator emits as `@Valid @NotNull` on the interface parameter, so a literal JSON `null` is rejected during validation and never reaches the resource. Verified against a running instance: both operations answer `400` with a constraint-violation body, not `500`. A guard here would handle a case the contract already forecloses. | Closed — no change |
| Minor | Deployment alerting guidance presents rejected codes as evidence of clock drift | **Valid.** A fleet-wide spike in `glacier_mfa_verifications{outcome="rejected"}` is equally the signature of an attack; the wording teaches operators to dismiss it. Now states that a drifted clock and a credential-stuffing run produce the same curve, that drift is the cheaper hypothesis to check first, and that ruling it in does not rule an attack out. | Resolved |

### T5 — hardening and documentation

| Severity | Finding | Classification | Final status |
|---|---|---|---|
| Major | `docs/THREAT_MODEL.md` understates administrative visibility | **Valid.** T4 added per-account second-factor state to the admin user page; the threat model still says administrators see no more than aggregate enrollment counts. Split into two residual risks: enforcement is still per account, and administrators do see per-account enrollment state and confirmation time — a deliberate disclosure to the operator, bounded to exclude secrets and remaining-code counts. | Resolved |
| Major | The milestone table still titles T5 "Feature Release" | **Valid.** T5 deliberately cut no release — `pom.xml` remains at `0.2.0` and `CHANGELOG.md` under `[Unreleased]`. Retitled "Release Qualification" in both the overview table and the section heading, with the exit gate stating outright that no release is cut and why. | Resolved |
| Major | `page/documentation.html` implies enrollments self-heal after a secret rotation | **Valid.** "changing it has the same effect until those accounts enroll again" omits that the stale rows must be cleared or disabled first; rotation is not a standalone setting change. The page now says a stranded owner cannot sign in to re-enroll, gives the supported order, and mentions the startup warning. | Resolved |
| Minor | `MfaKeyRotationTest` asserts only that completion is not `200` | **Valid.** The assertion should pin the status and error code, as the other MFA login tests do. Now pins `500` / `INTERNAL_ERROR` and that the detail does not describe the key state. | Resolved |
| Minor | Milestone body cites specification version 0.4 against metadata claiming 0.9 | **Superseded.** Corrected by the T5 documentation audit before this review. | No change |

## Disposition Totals

| Disposition | Count |
|---|---:|
| Resolved in batch 1 (code, contract, and operator procedure) | 15 |
| Resolved in batch 2 (documentation) | 15 |
| Superseded by a later milestone or the T5 documentation audit | 4 |
| Not a defect | 2 |

All 36 are dispositioned; none remain open.

Four findings are executable defects in shipped backend code: the locale-dependent TOTP formatting,
the attempt-counter race, the missing `dontRollbackOn` on `confirm`, and the eager secret resolution
at startup. Five are frontend correctness issues that degrade error reporting without affecting
authorization. Two sharpen backend tests that passed for the wrong reason, three tighten the OpenAPI
contract, and one is a security-hygiene correction to a documented operator procedure. The remaining
fifteen are documentation accuracy — predominantly absolute claims in the specification and milestone
plan that the implementation does not honour, and which the traceability matrix in
[`docs/SECOND_FACTOR_TRACEABILITY.md`](../../SECOND_FACTOR_TRACEABILITY.md) did not catch because it
traces requirements to tests rather than auditing requirement wording.

Three of the fifteen were the same `CHANGELOG.md` sentence reported at three successive milestones,
and two more were milestone-plan copies of specification absolutes. That is the failure mode of a
requirement written as an unqualified "shall": it gets mirrored into gates and release notes, and
each copy has to be found separately. The three genuinely unsatisfiable requirements — unconditional
notification, unscoped enrollment-state secrecy, and "no behavioral change whatsoever" — are recorded
with their corrected wording in specification §21.11.

No finding reopens or contradicts a disposition from R0–R10 or remediation batches 3–7.

## Incidental Finding

Disproving the `clearUserMfa` null guard surfaced something CodeRabbit did not report. A request body
that fails bean validation is answered by the RESTEasy Reactive built-in violation mapper rather than
`ProblemExceptionMapper`, so the response is neither `application/problem+json` nor carries an
`errorCode` or correlation id, and it names the Java method and parameter:

```json
{"title":"Constraint Violation","status":400,
 "violations":[{"field":"clearUserMfa.adminStepUpRequest","message":"..."}]}
```

The message is also rendered in the server's default locale. This is API-wide and predates the second
factor — every `required: true` body and every validated parameter reaches it. It is recorded here
rather than fixed because remediating it changes error responses across the whole contract, which is
its own change with its own spec and documentation impact.

## Outcome

Remediation ran in two batches: code defects first, each preceded by a regression test that fails
without the fix, then documentation accuracy. Both are complete and every finding carries a final
status. Commands and results are recorded in [test-results.md](test-results.md).

Nothing here blocks a release. The one acceptance criterion still unticked — a portable round trip
through the desktop application — is a manual check outside this repository and predates the review.
