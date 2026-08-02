# Second-factor traceability matrix

This document satisfies section 6 of [`GLACIER_NOTES_CLOUD_MILESTONES_2FA.md`](../GLACIER_NOTES_CLOUD_MILESTONES_2FA.md),
which requires each specification requirement of the optional TOTP second factor to be connected to
at least one unit test, PostgreSQL integration test, API contract test, Angular component test,
end-to-end test, security test, or documented manual procedure before the milestone track closes.

Section numbers refer to [`GLACIER_NOTES_CLOUD_2FA_SPECIFICATION.md`](../GLACIER_NOTES_CLOUD_2FA_SPECIFICATION.md).
Backend tests live under `backend/src/test/java/com/glaciernotes/cloud/`; component specs under
`frontend/src/app/`; the browser round trip in `frontend/e2e/second-factor.spec.ts`.

Every entry below names a specific test method, not just a class, so that a reader can confirm the
named test would fail without the behaviour it claims to pin. Where no test exists, the row says so
rather than pointing at something adjacent — see [Known gaps](#known-gaps).

## Matrix

### 1. A non-enrolled account is never asked for a second factor (§5.2, §18, §18.1)

| Kind | Evidence |
| --- | --- |
| PostgreSQL integration | `MfaStepUpTest#leavesAnAccountWithoutAnEnrollmentEntirelyUnchanged` |
| PostgreSQL integration | `MfaDisabledTest#reportsTheFeatureAsUnavailableSoTheClientOffersNoEnrollment`, `#refusesToStartAnEnrollmentWhileTheFeatureIsOff` |
| API contract | `AuthenticationResourceTest` — the pre-existing login suite passes unmodified except for the T0 response-envelope assertion |
| Angular component | `two-factor-card.component.spec.ts` — "renders nothing at all when the instance does not offer the feature" |

### 2. Enumeration resistance across both steps (§6.1)

| Kind | Evidence |
| --- | --- |
| PostgreSQL integration | `MfaLoginTest#answersAWrongPasswordIdenticallyWhetherOrNotTheAccountIsEnrolled` |
| PostgreSQL integration | `MfaLoginTest#treatsAnAccountChangedBetweenTheStepsAsAnUnknownChallenge` — deactivation, lock, deletion, and password change all collapse to the generic challenge-invalid response |
| PostgreSQL integration | `SecondFactorResetTest#doesNotRevealWhetherAnAccountExistedOrCarriedASecondFactor` |
| PostgreSQL integration | `MfaEnrollmentTest#neverReturnsStoredSecondFactorMaterialOrLeavesPlaintextBehind` |

Response *identity* is pinned; response *timing* is not — see [Known gaps](#known-gaps).

### 3. TOTP correctness against the RFC vectors, including the step window and replay rejection (§6.4)

| Kind | Evidence |
| --- | --- |
| Unit | `TotpVerifierTest#acceptsThePublishedRfc6238Vectors` — the six HMAC-SHA1 vectors of RFC 6238 appendix B |
| Unit | `TotpVerifierTest#acceptsTheNeighbouringStepsAndRejectsAnythingWider` — ±1 accepted, ±2 refused |
| Unit | `TotpVerifierTest#rejectsAnAlreadyAcceptedStepAndEverythingBeforeIt`, `#rejectsCodesOfTheWrongLength` |
| Unit | `Base32CodecTest#arbitraryBytesRoundTrip`, `#decodingToleratesLowercaseMissingPaddingAndGroupingSeparators`, `#charactersOutsideTheAlphabetAreRejected` |
| PostgreSQL integration | `MfaLoginTest#acceptsTheNeighbouringStepsRejectsWiderOnesAndNeverRepeatsAStep` — the same window enforced through the endpoint |

### 4. Challenge single-use, expiry, attempt exhaustion, and concurrent-consumption safety (§5.2, §6.5, §7.3)

| Kind | Evidence |
| --- | --- |
| PostgreSQL integration | `MfaLoginTest#refusesAnExpiredOrAlreadyConsumedChallenge` |
| PostgreSQL integration | `MfaLoginTest#exhaustsTheChallengeAndEventuallyLocksTheAccount` |
| PostgreSQL integration | `MfaLoginTest#resolvesConcurrentCompletionsToASingleSession` — two threads on one barrier; exactly one 200 and one `user_sessions` row |
| PostgreSQL integration | `MfaLoginTest#keepsAtMostThreeOpenChallengesPerAccount` — the cap evicts the oldest rather than growing |
| Security | `MfaTokenServiceTest#challengeTokensCarryAtLeast256BitsOfEntropy`, `#challengeTokensAreUnpredictable`, `#hashingIsDeterministicAndDoesNotRevealTheToken` |
| Observability | `MfaAdministrativeClearTest#expiredAndConsumedChallengesAreCountedApart` |

### 5. Recovery-code single-use and regeneration (§5.3, §6.3)

| Kind | Evidence |
| --- | --- |
| PostgreSQL integration | `MfaLoginTest#acceptsEachRecoveryCodeExactlyOnce` |
| PostgreSQL integration | `MfaLoginTest#resolvesConcurrentCompletionsToASingleSession` — its second half races one recovery code across two challenges |
| PostgreSQL integration | `MfaEnrollmentTest#regenerationReplacesEveryPreviousRecoveryCode` |
| PostgreSQL integration | `MfaStepUpTest#acceptsARecoveryCodeAndSpendsItOnlyOnce` |
| Security | `MfaTokenServiceTest#recoveryCodesAreDistinctAndUseAnUnambiguousAlphabet`, `#recoveryCodesAreNormalizedBeforeHashingSoTranscriptionVariantsMatch`, `#challengeAndRecoveryHashesAreSeparatedByTheirDomainPrefix` |
| Angular component | `two-factor-card.component.spec.ts` — "keeps the recovery codes on screen until they are acknowledged", "reports the remaining recovery codes while the factor is active" |

### 6. Account-state re-verification between the two steps (§5.2.1)

| Kind | Evidence |
| --- | --- |
| PostgreSQL integration | `MfaLoginTest#treatsAnAccountChangedBetweenTheStepsAsAnUnknownChallenge` |
| PostgreSQL integration | `MfaPasswordResetTest#aChallengeIssuedBeforeTheResetCannotBeCompletedAfterIt` |
| PostgreSQL integration | `MfaPasswordResetTest#resettingThePasswordLeavesTheSecondFactorInPlace` (§5.6) |

### 7. Rate-limit and lock-counter interaction with the second step (§6.5)

| Kind | Evidence |
| --- | --- |
| PostgreSQL integration | `MfaLoginTest#exhaustsTheChallengeAndEventuallyLocksTheAccount` — failed second-factor attempts feed the existing account lockout |
| PostgreSQL integration | `MfaLoginTest#thePasswordStepIssuesNoSessionAndClearsNoLoginState` — the password step alone clears neither the rate-limit entry nor the failure counter |
| PostgreSQL integration | `MfaStepUpTest#endsInARateLimitAfterRepeatedWrongCodes`, `#endsInARateLimitAfterRepeatedWrongPasswords` (§5.4.1) |
| Unit | `LoginThrottlePolicyTest#appliesDeterministicProgressiveDelaysAndLockout` |

### 8. Step-up coverage and the grace window's scope, expiry, and reset conditions (§5.4, §5.4.2)

| Kind | Evidence |
| --- | --- |
| PostgreSQL integration | `MfaStepUpTest#refusesEverySelfServiceOperationWhenOnlyThePasswordIsSupplied`, `#acceptsTheOperationWhenAFreshCodeIsSupplied`, `#refusesAWrongCodeAndRecordsTheAttempt`, `#refusesTheWrongPasswordBeforeItEverLooksAtTheCode`, `#refusesACodeThatWasAlreadySpentOnALogin` |
| PostgreSQL integration | `MfaAdminStepUpTest#anEnrolledAdministratorMustSupplyACodeForDeletionAndForTheResetLink`, `#anEnrolledAdministratorIsToldWhichCredentialIsMissing`, `#anAdministratorWithoutAnEnrollmentSuppliesThePasswordAlone`, `#theUngatedAdministrativeOperationsAreUnchanged`, `#aPlainUserIsStillRefusedOutrightWhateverItSupplies` |
| PostgreSQL integration | `MfaGraceWindowTest#aSecondFactorLoginOpensTheWindowAndAPasswordOnlyLoginDoesNot`, `#confirmingAnEnrollmentOpensTheWindowSoTheNextOperationIsNotPromptedAgain`, `#anExpiredWindowPromptsAgain`, `#aWindowOfZeroPromptsEveryTime`, `#theWindowNeverTransfersToAnotherSession`, `#disablingTheFactorClearsEveryWindow`, `#changingThePasswordLeavesNoWindowBehind`, `#reEnrollingClearsEveryWindowBeforeOpeningItsOwn` |
| Angular component | `account-settings.component.spec.ts` — "asks for a code on the email form only after the server demands one", "confirms self-deletion once, then only adds the code on the retry", "leaves no code field behind when an email change is accepted straight away" |
| Angular component | `two-factor-card.component.spec.ts` — "reveals a code field and keeps the password when the server asks to step up" |
| Angular component | `admin-user-detail.component.spec.ts` — "sends the administrator password, then adds the code the server asks for" |
| End-to-end | `second-factor.spec.ts` — the final phase closes the instance-wide grace tunable and confirms the browser is asked for a code it was not asked for while the window was open |

### 9. Session revocation on every second-factor change (§5.5)

| Kind | Evidence |
| --- | --- |
| PostgreSQL integration | `MfaSessionRevocationTest#enablingTheFactorRevokesEveryOtherSession`, `#disablingTheFactorRevokesEveryOtherSession`, `#regeneratingRecoveryCodesRevokesEveryOtherSession` |
| PostgreSQL integration | `MfaAdministrativeClearTest#clearingAnEnrolledAccountEndsItsSessionsAndDisclosesNothingBeyondTheState` |
| PostgreSQL integration | `SecondFactorResetTest#clearsTheEnrollmentEndsItsSessionsAndRestoresASingleStepLogin` |
| Security | `SecurityAttackSimulationTest#sessionTokenIsRejectedAfterForcedRevocationOnPasswordChange` |

### 10. Administrative clear disclosing nothing (§8.3, §9.1)

| Kind | Evidence |
| --- | --- |
| PostgreSQL integration | `MfaAdministrativeClearTest#clearingAnEnrolledAccountEndsItsSessionsAndDisclosesNothingBeyondTheState` |
| PostgreSQL integration | `MfaAdministrativeClearTest#anAccountWithoutAFactorIsRejectedRatherThanSilentlyAccepted`, `#aPlainUserIsRefusedOutright`, `#anEnrolledAdministratorMustProvePossessionAndItsRefusalIsAudited` |
| Angular component | `admin-user-detail.component.spec.ts` — "shows the enrollment state and offers the clear action only when a factor is active", "clears the second factor through the confirmation panel and adds the code on demand" |

### 11. Operator escape hatch, including invalid-token rejection (§8.3.1)

| Kind | Evidence |
| --- | --- |
| PostgreSQL integration | `SecondFactorResetTest#clearsTheEnrollmentEndsItsSessionsAndRestoresASingleStepLogin` |
| PostgreSQL integration | `SecondFactorResetTest#deniesAndThrottlesAWrongBootstrapTokenWithoutTouchingTheEnrollment`, `#requiresTheBootstrapTokenHeader` |
| PostgreSQL integration | `SecondFactorResetTest#doesNotRevealWhetherAnAccountExistedOrCarriedASecondFactor` |
| Manual | `docs/BACKUP_RESTORE.md` — the break-glass procedure for a sole-administrator lockout |

### 12. Notification non-blocking behaviour (§5.7)

| Kind | Evidence |
| --- | --- |
| PostgreSQL integration | `MfaNotificationTest#aBrokenMailerNeitherFailsNorRollsBackTheOperation` |
| PostgreSQL integration | `MfaNotificationTest#sendsExactlyOneMessageForEachLifecycleEvent`, `#notifiesWhenARecoveryCodeIsSpentOnALogin`, `#notifiesWhenAnOperatorClearsTheFactor` |
| PostgreSQL integration | `MfaNotificationTest#neverCarriesASecretACodeOrALink` (§6.6) |
| PostgreSQL integration | `MfaNotificationTest#writesGermanWhenTheAccountAsksForIt` — both dictionaries |

### 13. Secret-at-rest encryption, key identification, and rotation (§6.2, §13)

| Kind | Evidence |
| --- | --- |
| Security | `EnrollmentSecretCipherTest#encryptedSecretsRoundTripExactly`, `#everyRecordGetsItsOwnNonceAndDiffersOnTheWire`, `#ciphertextCarriesTheKeyIdentifierThatProducedIt` |
| Security | `EnrollmentSecretCipherTest#decryptionUnderTheWrongKeyFailsClosed`, `#tamperedCiphertextIsRejectedByTheAuthenticationTag`, `#anAbsentSecretIsRefusedRatherThanSubstituted` |
| Security | `MfaTokenServiceTest#hashesAreBoundToTheEnrollmentSecret` — rotating the session secret cannot affect enrollment-derived hashes |
| PostgreSQL integration | `MfaKeyRotationTest#refusesToCompleteALoginAgainstAnEnrollmentUnderAnotherKey` |
| PostgreSQL integration | `MfaKeyRotationTest#countsOnlyTheEnrollmentsLeftBehindByASecretSwap` — the count behind the startup warning |
| Manual | `docs/BACKUP_RESTORE.md` — the rotation procedure and the `select key_id, count(*) from user_mfa_totp group by key_id` verification query |

### 14. Tunable bounds and default application to a pre-existing settings row (§7.6)

| Kind | Evidence |
| --- | --- |
| PostgreSQL integration | `SecondFactorMigrationTest#secondFactorTunablesLandOnThePreExistingSettingsRowAndTheScopeCheckOnlyWidens` |
| PostgreSQL integration | `MfaAdministrativeClearTest#theTunablesRoundTripAndAnOutOfBoundsValueChangesNothing` — server-side rejection leaves no partial configuration |
| PostgreSQL integration | `MfaLoginTest#appliesARetunedLifetimeOnlyToNewChallengesAndTheAttemptCapImmediately` |
| PostgreSQL integration | `MfaGraceWindowTest#aWindowOfZeroPromptsEveryTime` — zero is a valid value meaning "always prompt" |
| Angular component | `admin-settings.component.spec.ts` — "loads the second-factor tunables into the form and sends them back on save" |

### 15. Migration on a populated database (§7)

| Kind | Evidence |
| --- | --- |
| PostgreSQL integration | `SchemaUpgradeDataIntegrityTest#seededRowsAcrossAllContentTablesSurviveUpgradeToHeadWithReferentialIntegrityIntact` — baselines at V10 and therefore crosses V13 and V14 with data in place |
| PostgreSQL integration | `SecondFactorMigrationTest#secondFactorTunablesLandOnThePreExistingSettingsRowAndTheScopeCheckOnlyWidens` — additive only; the `login_rate_limits` scope check widens rather than narrows |
| Startup | Hibernate validate-only schema check on every `@QuarkusTest` boot (ADR 0005) |

### 16. Portable-format exclusion and desktop fixture compatibility (§14)

| Kind | Evidence |
| --- | --- |
| PostgreSQL integration | `PortableTransferCodecTest#inspectsEveryDesktopSchemaV1ScopeWithDeterministicCounts` — all three `compatibility-fixtures/desktop-schema-v1` fixtures |
| By construction | No file under `application/transfer` references any `user_mfa_*` or `mfa_challenges` table, so no second-factor field can reach a `.glacier.json` (ADR 0009). Not asserted by a test — see below. |

## Known gaps

Three requirements are satisfied in substance without a test that would fail if they regressed. They
are recorded here rather than papered over.

1. **Portable-format exclusion is structural, not asserted.** Nothing fails if a future change starts
   writing second-factor state into an export. A regression test would need to enroll an account and
   assert the absence of any such key in its export.
2. **Enumeration timing is not measured.** Row 2 pins that the two responses are byte-identical.
   Comparable *timing* (§6.1) holds because both paths run the same Argon2 verification before
   branching, but no test asserts it, and a timing assertion would be inherently flaky in CI.
3. **The desktop round trip is manual and outside this repository.** The codec and all three fixtures
   pass here; reading a fresh cloud export back into the desktop application needs that application.
   This is the corresponding open acceptance criterion in the milestone plan.

A fourth limitation is structural rather than a missing test. This matrix answers "does a test cover
this requirement", never "is this requirement satisfiable". Review R11 found three that were not —
notification stated as an obligation on delivery, enrollment-state secrecy stated without a
pre-password scope, and "no behavioral change whatsoever" — each of which had a green row here while
the wording above it could not be met. A requirement that cannot fail is not traceable evidence.
Corrected wording is recorded in specification §21.11; the dispositions are in
[`docs/reviews/t0-t5/R11-T0-T5-summary.md`](reviews/t0-t5/R11-T0-T5-summary.md).

## Keeping this current

Test method names are cited exactly. Renaming a test breaks a claim in this file without breaking a
build, so update the row in the same change as the rename. When a new second-factor requirement is
specified, add its row here before the work is considered done.
