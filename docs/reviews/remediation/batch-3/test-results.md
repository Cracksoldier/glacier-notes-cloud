# Batch 3 Test Results

All runs were performed on 2026-07-24 from
`b5bbb0aeae58e60e18746ddbefaaf383e13e69e6` plus the Batch 3 working diff. No credentials, cookies,
tokens, user content, exports, traces, or container data are stored here.

## Regression-First Evidence

- The original focused frontend run failed 14 tests across seven files and produced the expected
  unhandled initialization rejection before the fixes.
- After the original fixes, the focused set passed 20 tests across seven files.
- A later checklist-update/trash regression failed before the queue fix because trash was called
  immediately with version `0`; the focused `NotesStore` suite then passed 8 tests.
- CodeRabbit follow-up regressions initially failed 5 of 17 tests across five files: stale history
  response, late export, late import, error-state transfer close, and stalled preference queue.
- After the follow-up fixes,
  `npm run test:ci -- --include=...` passed all 17 focused tests in five files.

## Module and Repository Gates

- `npm run check` passed all 87 files. No Prettier configuration or dependency is present.
- `npm run build:production` passed. It retained the existing 700 kB initial-bundle warning
  (784.06 kB total) and 8 kB notes-shell CSS warning (8.90 kB total).
- `npm run test:ci` passed 41 tests across 14 files.
- `npm audit --omit=dev --audit-level=high` reported 0 vulnerabilities.
- `sg docker -c './mvnw verify'` passed all 68 backend tests in 1 minute 18 seconds using PostgreSQL
  Dev Services plus filesystem, PostgreSQL, and MinIO/S3 storage profiles.
- Contract regeneration produced no tracked generated-client drift. `git diff --check` passed.

## Deployment and Browser Gate

An isolated Compose project used ports `18080`/`19000` and temporary file-backed secrets outside the
repository. The application root, setup status, and readiness endpoint passed; administrator
bootstrap and invited-user activation succeeded.

The first post-review two-worker run exited successfully with five direct passes and one M8 retry.
Its trace showed search starting before the editor's asynchronous post-close list refresh completed.
After synchronizing on that GET response, the CI-equivalent command
`npm run test:e2e -- --workers=2` passed all six desktop/tablet workflows without retries in 16.2
seconds. The isolated containers, network, and four named test volumes were then removed.
