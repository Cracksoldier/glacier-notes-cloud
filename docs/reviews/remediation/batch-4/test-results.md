# Batch 4 Test Results

All runs were performed on 2026-07-24 from `c28f701` plus the Batch 4 working diff. No credentials,
cookies, tokens, user content, exports, traces, or container data are stored here.

## Regression-First Evidence

- The initial focused backend run executed 30 tests and failed with six assertions plus three
  errors. Expected failures proved the missing note limit, audit classification, export-factory
  invariant, version-hash nullability, transfer constraint, explicit save scope, and null-safe
  identifier equality. Two errors exposed test-harness cleanup/proxy issues; those harnesses were
  corrected before using their results as fix evidence.
- The pre-review completed focused set passed 38 tests covering ownership repositories, schema/upgrade
  behavior, note request boundaries, transfer contracts, audit metadata, configured base64 limits,
  download ordering, and malformed public URLs.
- `CoreContentRepositoryPagingTest` passed and proved list and ranked-search result ordering with
  exactly two prepared statements for five selected notes.
- After the finished-diff review, the 19-test `PortableTransferCodecTest`, `DatabaseSchemaTest`, and
  `OwnershipRepositoryTest` set passed. It proves field-specific streamed base64 limits, malformed
  base64 rejection, exact two-note trigger refresh, behavioral export-scope constraints, and
  persisted/reloaded composite-identifier equality.

## Module and Repository Gates

- `sg docker -c './mvnw verify'` passed all 82 backend tests with PostgreSQL Dev Services and the
  filesystem, PostgreSQL, and MinIO/S3 profiles.
- `npm ci`, the no-Prettier guard, `npm run check`, and `npm run build:production` passed. The build
  retained the existing 700 kB initial-bundle and 8 kB notes-shell CSS warnings.
- `npm run test:ci` passed 41 tests across 14 files.
- `npm audit --omit=dev --audit-level=high` reported zero production vulnerabilities.
- `./mvnw -pl backend clean generate-sources` completed successfully; only the intended generated
  OpenAPI input hash changed. `git diff --check` passed.

## Deployment and Browser Gate

An isolated `glacier-batch4` Compose project used ports `18080`/`19000` and temporary file-backed
secrets outside the repository. Application, setup-status, and readiness probes passed. Administrator
bootstrap and a persistent session survived app-container recreation; invitation activation, member
login, and session validation also passed.

`CI=1 npm run test:e2e` passed all six desktop/tablet core-note, search/history, and portable-transfer
workflows in 7.9 seconds. The isolated containers, network, and four named test volumes were removed;
an empty project listing confirmed teardown.
