# Batch 7 Test Results

## Regression-First Evidence

Source inspection on base `1b1b712` confirmed that `DatabaseSchemaTest` checked only catalog
presence, deployment CI attempted only valid-CSRF logout, the reset test used only an unknown
account, and SMTP tests matched only `?token=` prefixes. Since each production behavior already
satisfied its milestone criterion, the added assertions passed without a production-code change.

## Focused and Module Verification

- `sg docker -c './mvnw -pl backend
  -Dtest=DatabaseConstraintTest,LifecycleResourceTest,LifecycleEmailTest test'` passed 14/14 tests.
- `sg docker -c './mvnw verify'` passed all 83 backend tests with PostgreSQL Dev Services and the
  filesystem, migration, request-limit, email, and MinIO/S3 profiles.
- Contract generation during Maven verification produced no tracked generated-client drift.

## CI-Equivalent Verification

- A clean `npm ci` passed Prettier rejection, `npm run check`, the production build, all 63 frontend
  tests, and `npm audit --omit=dev --audit-level=high` with zero production vulnerabilities.
- `npm run test:repository` passed 3/3 repository contracts.
- The generated Angular package installed independently with zero vulnerabilities and passed its
  `ng-packagr` build. The first sandboxed install was canceled after network isolation prevented
  progress; the authorized retry passed.
- An isolated Compose deployment on ports 18080/19000 passed application/readiness checks,
  bootstrap persistence across forced recreation, administrator login, invalid-CSRF logout
  rejection with `403 CSRF_INVALID`, preservation of the rejected session, invitation activation,
  member login, valid logout, and `npm run test:e2e` (6/6 desktop/tablet tests).
- Two sandboxed local `curl` attempts could not reach the published port; the same checks executed
  with authorized local-network access passed. Test volumes and temporary secret files were
  removed afterward.

## Finished-Diff Review

`coderabbit review --agent --uncommitted` ran exactly once against the staged four-file
implementation. It completed with 4 reviewed files and zero findings, so no post-review changes or
additional test run were required.
