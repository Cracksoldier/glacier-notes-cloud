# Batch 6 Test Results

## Regression-First Evidence

- `node scripts/repository-contracts.test.mjs` failed 3/3 before production changes: generated
  package metadata, unsafe publishing helper, and repository-facing metadata.
- `npm test -- --watch=false --include 'src/app/app.spec.ts' --include
  'src/app/core/generated-api-support.spec.ts'` failed 3/5 before production changes: startup
  diagnostics, exploded arrays, and TSV.
- After the initial fix, both commands passed (3/3 repository contracts and 5/5 focused tests).

## Complete Verification

- `./mvnw -pl backend clean generate-sources` passed repeatedly. SHA-256 manifests before and after
  regeneration were identical, and `git_push.sh` remained absent.
- The generated package installed independently, reported zero vulnerabilities, and passed
  `ng-packagr -p ng-package.json`.
- `sg docker -c './mvnw verify'` passed all 82 backend tests with PostgreSQL Dev Services and the
  filesystem, migration, request-limit, email, and MinIO/S3 profiles.
- A clean `npm ci` gate passed repository contracts, Prettier rejection, `npm run check`,
  `npm run build:production`, all 62 pre-review frontend tests, and
  `npm audit --omit=dev --audit-level=high` with zero production vulnerabilities. The existing
  bundle-budget warnings remain.
- An isolated Compose deployment on ports 18080/19000 passed application/readiness checks,
  bootstrap and remembered-session persistence across forced recreation, invitation activation,
  member login, logout, and `npm run test:e2e` (6/6 desktop/tablet tests). Its test volumes were
  removed afterward.

## Post-CodeRabbit Verification

All three valid review findings were fixed without a second review. The generated-support focused
suite passed 3/3, `npm run check` and the production build passed, the complete frontend suite
passed 63/63, and the independently installed generated package built successfully.
