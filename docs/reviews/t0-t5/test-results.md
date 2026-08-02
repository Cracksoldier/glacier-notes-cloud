# R11 Remediation — Verification Record

Batch 1 covers the executable defects. Every fix was preceded by a test that failed against the
unfixed code; the failure is recorded below so the test can be trusted to still be load-bearing.

## Batch 1 — code

| Finding | Regression test | Observed before the fix |
|---|---|---|
| `TotpVerifier.generate` formats without a locale | `TotpVerifierTest.generatesAsciiDigitsUnderALocaleWithItsOwnNumberingSystem` | `expected: <94287082> but was: <९४२८७०८२>` |
| Attempt counter increments in memory | `MfaLoginTest.countsEveryParallelFailedAttemptAgainstTheCap` | `expected: <1> but was: <0>` — no challenge reached `attempt_count = 2` |
| `MfaEnrollmentService.confirm` lacks `dontRollbackOn` | `MfaEnrollmentTest.confirmingAnExpiredPendingEnrollmentDiscardsIt` | `expected: <0> but was: <1>` — the expired row survived the refusal |
| `MfaStartupValidator` resolves the secret eagerly | `MfaStartupValidatorTest.disabledSecondFactorSupportNeverResolvesTheSecret` | the supplier threw during `validate(false, …)` |
| `AuthStore.completeSecondFactor` throws synchronously | `auth.store.spec.ts` — "reports a missing challenge through the observable rather than synchronously" | `Error: No second-factor challenge is in progress.` escaped past the subscriber |
| The two-factor card renders nothing on a failed status load | `two-factor-card.component.spec.ts` — "says the status could not be loaded rather than rendering an empty card" | no `[role="alert"]` element existed |
| `copyCodes()` ignores a clipboard rejection | `two-factor-card.component.spec.ts` — "points at the download when the clipboard refuses the codes" | `Error: denied` propagated out of `copyCodes()` |
| `StepUpPrompt.handle` swallows a repeated refusal | `step-up.spec.ts` — "surfaces a repeated refusal so the field does not clear without explanation" | `expected true to be false` |
| `admin-user-detail.fail()` prompts with no pending action | `admin-user-detail.component.spec.ts` — "reports a step-up refusal from an ungated action instead of opening the code field" | `expected true to be false` — the code field opened |

Two findings were test-quality rather than behaviour, so they have no red-then-green record:
`MfaEnrollmentTest.neverReturnsStoredSecondFactorMaterialOrLeavesPlaintextBehind` now compares
against the ungrouped secret as well as the grouped one, and
`MfaKeyRotationTest.refusesToCompleteALoginAgainstAnEnrollmentUnderAnotherKey` pins `500` /
`INTERNAL_ERROR` and asserts the response does not describe the key state, instead of merely
asserting the status is not `200`.

One finding was disproved rather than fixed — see the Incidental Finding section of
[R11-T0-T5-summary.md](R11-T0-T5-summary.md).

## Batch 2 — documentation

Batch 2 changes prose only; no behaviour changed, so there is no red-then-green record. The
verification is that the corrected statements match the code, checked against it rather than against
the plan that described it.

| Claim corrected | Checked against |
|---|---|
| Notification is an obligation on the attempt, not on delivery | `SecondFactorNotifications` dispatch sites and the best-effort provisions already in specification §5.7 |
| Enrollment state is secret only before the correct password | The `MFA_REQUIRED` branch of the login resource, which is reached only after password verification |
| A non-enrolled account sees the `LoginOutcome` envelope like everyone else | `openapi/glacier-notes-v1.yaml` — one response schema for both results |
| Administrators see per-account enrollment state and confirmation time, and nothing more | The admin user-detail response fields, which carry no secret, no codes, and no remaining count |
| A stranded enrollment cannot re-enroll itself | `EnrollmentSecretCipher.decrypt` hard-fails on a foreign `key_id`, so the second login stage never completes |
| T5 cut no release | `pom.xml` still `0.2.0`; `CHANGELOG.md` still under `[Unreleased]` |

## Commands

```
./mvnw verify                                  BUILD SUCCESS
bash backend/scripts/check-log-hygiene.sh      no prohibited-field log calls across 10 file(s)
./mvnw -pl backend clean generate-sources      committed client unchanged apart from its checksum
cd frontend
npm run check                                  111 files, 2 pre-existing CSS specificity warnings
npm run test:repository                        pass
npm run test:ci                                26 files, 112 tests, all passing
npm run build:production                       pass (pre-existing notes-shell.component.css budget warning)
```

The contract change — `minimum: 0` on `MfaChallenge.attemptsRemaining`, `minItems: 1` on
`acceptedFactors`, and `503` on the three operations that can raise `MFA_UNAVAILABLE` — regenerates
to `@Min(0)` and `@Size(min=1)` on the backend model and leaves the committed Angular client
byte-identical. `uniqueItems: true` was considered and rejected: the TypeScript generator turns it
into `Set<…>`, a type `JSON.parse` never produces.
