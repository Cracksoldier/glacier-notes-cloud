# M0–M10 Structured Code Review and Test Plan

## Purpose and Review Rules

This plan covers the implementation delivered through M10, including the subsequent CI stabilization
commit. It is a plan only: no CodeRabbit review, build, test, container, migration, or application
command was run while preparing it.

The review must:

- Treat `openapi/glacier-notes-v1.yaml` and the two Glacier specification documents as requirements.
- Apply the conventions and security rules in `AGENTS.md`.
- Prioritize correctness, data integrity, ownership, privacy, and migration safety over style.
- Never apply CodeRabbit suggestions automatically. A developer must validate every finding first.
- Use disposable branches/worktrees; do not detach or rewrite the main development checkout.
- Store durable, redacted review evidence under `docs/reviews/<milestone-scope>/` using the policy in
  `docs/reviews/README.md`. Keep secrets, user data, logs, and large/binary artifacts outside Git.

The command templates below follow the official
[CodeRabbit CLI reference](https://docs.coderabbit.ai/cli/reference). Confirm the installed CLI
version and current reference before beginning because the CLI is still evolving.

## Review Baseline and Milestone Slices

Use a disposable worktree checked out at each target commit. Run every review against the listed base
with `--committed --base-commit <base>`. M0 and M1 share the root commit and therefore require a
temporary repository with an empty baseline commit and the `9933582` tree committed above it.

| Review | Base | Target | Primary focus |
|---|---|---|---|
| R0: M0–M1 | Synthetic empty commit | `9933582` | Repository, contract generation, schema, ownership foundation |
| R1: M2 | `9933582` | `b13b862` | Compose deployment, secrets, bootstrap transaction and lockout |
| R2: M3 | `b13b862` | `f83da35` | Authentication, sessions, cookies, CSRF, throttling |
| R3: M4 | `f83da35` | `08d3f76` | Invitations, resets, administration, last-admin protection |
| R4: M5 | `08d3f76` | `bf7e3f0` | Owner-scoped notebook/note/label APIs and trash behavior |
| R5: M6 + integration | `bf7e3f0` | `e31f9e2` | Angular UI, generated client, autosave, CI/docs integration |
| R6: M7 | `e31f9e2` | `ec36449` | Image validation, quota, storage backends, garbage collection |
| R7: M8 | `ec36449` | `2cf76f3` | Search, optimistic locking, conflict recovery, history |
| R8: M9 | `2cf76f3` | `0f02251` | Portable transfers, collision safety, desktop compatibility |
| R9: M10 | `0f02251` | `ea3cce3` | Account lifecycle, tokens, preferences, email flows |
| R10: CI stabilization | `ea3cce3` | `663cd08` | Async UI behavior, tablet navigation, E2E reliability |

After the slices, perform one current-state review at `663cd08` against the synthetic empty baseline.
If CodeRabbit rejects the scope as too large, retain its narrower-scope suggestions and split the
snapshot into contract/deployment, backend/persistence, frontend, and tests. Each split must still
include the interfaces needed to assess its boundaries.

## Planned CodeRabbit Workflow

### 1. Preflight

Record the reviewer, date, target SHA, CLI version, Java/Node versions, and the milestone slice.
Verify that the normal checkout is clean and that the review worktree contains no `.env`, Compose
secret files, credentials, databases, uploads, exports, or build output.

Create the milestone evidence directory in the primary checkout before starting. A historical review
worktree may not contain the current `docs/reviews` structure, so its output path must point back to
the primary checkout.

Planned diagnostic commands:

```bash
cr auth status
cr doctor
```

Do not place API keys on a shared command line or in review artifacts.

### 2. Run a Structured Review

Use agent output so findings can be triaged consistently:

```bash
REVIEW_EVIDENCE_DIR=<PRIMARY_CHECKOUT>/docs/reviews/<MILESTONE_SCOPE>
mkdir -p "$REVIEW_EVIDENCE_DIR"
cr review --agent --committed --base-commit <BASE_SHA> \
  -c AGENTS.md CODERABBIT_M0_M10_REVIEW_PLAN.md \
  > "$REVIEW_EVIDENCE_DIR/<REVIEW_ID>.jsonl"
```

For a fix iteration, review only the intentional local delta:

```bash
cr review --agent --uncommitted \
  -c AGENTS.md CODERABBIT_M0_M10_REVIEW_PLAN.md \
  > "$REVIEW_EVIDENCE_DIR/<REVIEW_ID>-fix.jsonl"
```

Do not combine `--committed` and `--uncommitted`. Include untracked files only when a reviewer has
confirmed they are source files and not secrets. Do not use the lighter review policy for milestone
acceptance. Before committing evidence, confirm a terminal `complete` event, ensure there is no
terminal `error` event, calculate the raw file's SHA-256, and redact any unsafe contextual output.

### 3. Validate and Triage Findings

For every CodeRabbit finding, record:

- Review ID, milestone, severity, file, and line.
- Violated specification or acceptance criterion.
- Concrete failure mode and affected actor/data.
- Whether the code path is reachable.
- Existing test coverage and the smallest missing regression test.
- Disposition: confirmed, duplicate, false positive, accepted risk, or deferred.
- Fix owner and verification evidence.

Map CodeRabbit `critical` findings to the repository's Blocker/Critical rubric after human analysis.
Treat cross-owner disclosure, authentication bypass, irreversible data loss, secret exposure,
bootstrap takeover, and unsafe migration behavior as release blockers. A false-positive disposition
requires written technical evidence; “tests pass” alone is insufficient.

## Milestone Review and Testing Matrix

| Milestone | Mandatory review questions | Planned verification |
|---|---|---|
| M0 | Is OpenAPI canonical? Can generated code drift? Are DTO/entity/domain boundaries preserved? Are problem responses and correlation IDs safe? Is Biome the sole formatter? | Regenerate contracts and require a clean diff; run backend/frontend clean builds, Biome, strict templates, unit tests, and CI workflow inspection. |
| M1 | Do all user-content queries require `OwnerId`? Are UUID, UTC, version, FK, normalization, and owner-first index rules enforced? Can migrations start from blank PostgreSQL? | Fresh-database migration; repository IDOR matrix with two owners; uniqueness/default-notebook/rollback failure cases. |
| M2 | Are secrets external, containers non-root, volumes durable, readiness accurate, and bootstrap one-time/rate-limited? Is initialization transactional? | Fresh Compose bootstrap, invalid-token attempts, restart/recreate persistence, health endpoints, and log scan for token leakage. |
| M3 | Are Argon2id, neutral login errors, durable session rotation/revocation, cookie flags, CSRF, CSP, and deterministic lockout correct? | Username/email login matrix; anonymous/USER/ADMIN authorization; missing/invalid CSRF; lockout clock tests; restart session persistence. |
| M4 | Are invitation/reset tokens random, hashed, expiring, single-use, and non-enumerating? Can any operation remove the last admin or expose content to admins? | Token replay/expiry/revoke tests; SMTP and no-SMTP flows; last-admin action matrix; admin privacy response inspection. |
| M5 | Is every content operation owner-scoped, transactional where required, safely paginated, and optimistic-version ready? | CRUD and filter matrix for two owners; foreign UUID attempts; notebook deletion strategies; archive/trash/restore/purge and conversion tests. |
| M6 | Can autosave lose a draft? Is Markdown sanitized? Are errors ownership-neutral? Are tablet layout, keyboard behavior, and generated-client usage sound? | Unit tests for save states and failures; XSS/link tests; desktop/tablet Playwright core workflow; refresh/focus and pending-close races. |
| M7 | Are signatures decoded safely? Can uploads bypass quota concurrently, traverse paths, leak cross-owner assets, or delete version-referenced images? | Valid/invalid corpus; byte/pixel/decompression limits; concurrent quota test; filesystem/PostgreSQL/S3 contract suite; orphan-GC eligibility tests. |
| M8 | Is search owner-scoped and correctly indexed? Can stale autosave overwrite data? Are snapshot triggers, restore semantics, images, count, and age retention correct? | Search/filter/ranking/pagination tests; two-tab conflict test; stale-version API test; snapshot deduplication and cleanup boundary tests. |
| M9 | Are imports bounded and atomic? Are cross-owner collisions opaque? Does ID remapping preserve every relationship? Is admin import blind? | All desktop fixtures; cloud round-trip; malformed/oversized import corpus; add/replace/retry/cancel cases; admin privacy and tombstone inspection. |
| M10 | Do identity changes revoke sessions and preserve uniqueness? Are email tokens safe? Are deletion/restore jobs complete and last-admin safe? Do preferences persist without races? | Password/session matrix; email verification replay/expiry; deactivation/reactivation; retained/immediate/self-delete tests; theme/language/trash/share UI tests. |

## Cross-Cutting Adversarial Passes

Perform these after the milestone slices:

1. **Ownership and privacy:** For every content UUID endpoint, attempt read, update, delete, export,
   image retrieval, search, history, and import replacement as a second user and as an unrelated
   administrator. Responses must not reveal existence or content.
2. **Transactions and concurrency:** Race default-notebook creation, quota updates, autosaves,
   invitation acceptance, email verification, retained deletion, import apply, and cleanup jobs.
3. **Failure recovery:** Inject database, storage, S3, SMTP, and network failures at transaction
   boundaries. Confirm retries are bounded and no partial state or silent data loss remains.
4. **Input abuse:** Exercise malformed JSON, invalid UUIDs, Unicode normalization, oversized
   Markdown, hostile URLs/HTML, image bombs, traversal names, and deeply nested/large imports.
5. **Contract consistency:** Compare OpenAPI behavior, generated Java/TypeScript models, status
   codes, CSRF requirements, pagination, and `application/problem+json` responses.
6. **Operational safety:** Inspect container user, mounts, secrets, startup failures, health
   separation, persistence, CSP/security headers, correlation IDs, and redacted logs.
7. **Regression reliability:** Run E2E tests repeatedly with two workers and clean data. Distinguish
   a product race from a test synchronization issue; never hide a product defect with forced clicks
   or arbitrary sleeps.

## Planned Test Execution Order

Only after review findings are triaged should tests be run, in this order:

1. Focused unit or integration test reproducing each confirmed defect.
2. `./mvnw verify`.
3. `cd frontend && npm run check`.
4. `cd frontend && npm run test:ci`.
5. `cd frontend && npm run build:production`.
6. Contract regeneration followed by a clean Git diff check.
7. Supported Docker Compose build, bootstrap, restart, health, authentication, and persistence flow.
8. Full Playwright suite for Chromium and tablet against a fresh deployment.
9. Backend-specific M7 suites for filesystem, PostgreSQL, and S3-compatible storage.
10. Desktop fixture import/export round trips and the cross-cutting adversarial cases above.

Capture the exact command, environment profile, result, duration, and artifact location. Redact
cookies, tokens, addresses, and imported content from reports.

## Review Deliverables

Produce:

- `docs/reviews/<scope>/<REVIEW_ID>-summary.md`: scope, SHA, reviewers, dispositions, overall
  decision, and unresolved risk.
- `docs/reviews/<scope>/<REVIEW_ID>.jsonl`: immutable raw CodeRabbit output.
- `docs/reviews/<scope>/test-results.md`: dated commands, profiles, outcomes, and links to safe
  external artifacts.
- `docs/reviews/m0-m10-traceability.md`: every acceptance criterion marked Pass, Fail, or Not Tested
  with evidence.
- Issues for deferred Major/Minor findings with owner and target milestone.

## Exit Criteria

The structured review is complete only when:

- Every M0–M10 acceptance criterion has a recorded disposition and evidence.
- No confirmed Blocker or Critical finding remains open.
- Every Major finding is fixed or explicitly accepted by the project owner with a tracked issue.
- Ownership, last-admin, token, deletion, migration, quota, conflict, and import-safety boundaries
  have automated negative tests.
- Generated-code drift, backend verification, frontend checks/tests/build, Compose deployment, and
  desktop/tablet E2E gates pass from a clean checkout and fresh database.
- A final CodeRabbit review of the fix delta introduces no new confirmed Blocker, Critical, or Major
  finding.
- The primary checkout remains clean except for deliberately accepted review changes.
