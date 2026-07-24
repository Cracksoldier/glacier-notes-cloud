# Batch 7 Remaining Coverage Closure

## Scope

- Base commit: `1b1b712`
- Original findings: four remaining Minor coverage gaps from R1/M2, R2/M3, and R3/M4
- Milestone gates: persistent bootstrap constraints, mutation CSRF rejection, neutral password-reset
  responses, and complete invitation/reset tokens
- CodeRabbit: CLI 0.7.0, one `--uncommitted` review of the staged finished diff

All original findings and their acceptance criteria were re-read and verified against the base.
They described absent assertions around behavior that was already correct, not production defects.
The pre-fix evidence is therefore the missing coverage itself; intentionally breaking working
production behavior to manufacture failing tests was neither necessary nor safe.

## Original Finding Resolution

| Original finding | Root cause and resolution | Final status |
|---|---|---|
| Negative `bootstrap_rate_limits.failure_count` lacks a behavioral migration test | Schema tests stopped at catalog presence. `DatabaseConstraintTest` now proves a negative insert is rejected with PostgreSQL check-violation SQLSTATE `23514`. The second raw finding is a duplicate. | Resolved |
| Deployment CI does not attempt invalid-CSRF logout | Backend enforcement existed, but the deployed-container smoke covered only valid logout. CI now asserts `403 CSRF_INVALID` and proves the rejected mutation leaves the session active before continuing the valid workflow. | Resolved |
| Password-reset neutrality does not compare known and unknown accounts | The lifecycle test exercised only an absent address. It now compares status, content type, and empty body for known and unknown accounts while retaining per-identifier throttling coverage. | Resolved |
| SMTP tests assert only lifecycle-link prefixes | The test did not prove complete bearer tokens reached either message. It now extracts both tokens and requires the canonical 43-character URL-safe shape. | Resolved |

## Finished-Diff Review

The immutable [raw JSONL](B7-COVERAGE-CLOSURE.jsonl) has 6 records, a terminal
`review_completed` event, 4 reviewed files, no findings, and SHA-256
`580c3f58a047a7e807a346f0d15890665d87803fec51d928ff9e627ecfe641f2`.
No post-review code change was required and CodeRabbit was not rerun.

Focused tests, all 83 backend tests, all 63 frontend tests, repository and generated-package gates,
the production build, Biome, the production dependency audit, the isolated deployment smoke, and
all 6 desktop/tablet browser tests passed. Complete command evidence is in
[test-results.md](test-results.md).
