# M11 Test Results

Evidence date: 2026-07-24. Test artifacts contain no committed credentials, cookies, database dumps,
or backup archives.

## Regression Red Phase

- `npm run test:repository` failed the two new repository contracts before the fix: the Compose
  backup directory/volume target was not override-safe, and the restore guide had no executable
  filesystem-image restore.
- `./mvnw -q -pl backend
  -Dtest=OperationalJobsTest,BackupTransactionTest,AdministrationTransactionBoundaryTest test`
  exposed stale lease release, historical-failure readiness, CSV formula injection, and missing
  shared admin transaction boundaries. Test compilation also demonstrated the absent renewal and
  bounded-process APIs. A test-only backup fixture was corrected to use a nullable actor so its
  foreign key did not obscure the intended transaction assertion.

## Focused and Module Verification

- `./mvnw -q -pl backend
  -Dtest=OperationalJobsTest,BackupTransactionTest,AdministrationTransactionBoundaryTest test`
  passed after the fixes.
- `npm run test:repository` passed all 5 repository contracts.
- `docker compose config --quiet` passed.
- `./mvnw -q -pl backend test` passed the complete backend module.
- `npm run check && npm run build:production && npm run test:ci` passed Biome over 96 files, the
  strict production build, and 65 frontend tests. Only the established bundle-budget warnings
  remained.

## CI-Equivalent Verification

- `./mvnw -q verify` passed the complete Maven reactor.
- `npm ci && npm run test:repository && test ! -e .prettierrc &&
  ! npm ls prettier --depth=0 && npm run check && npm run build:production &&
  npm run test:ci && npm audit --omit=dev --audit-level=high` passed. The production dependency
  audit reported zero vulnerabilities.
- A clean temporary copy of `frontend/src/app/shared/generated-api` passed `npm install
  --ignore-scripts --package-lock=false` and `npm run build`.

## Deployment and Restore Exercise

`GLACIER_BACKUP_ENABLED=true GLACIER_BUILD_IDENTIFIER=m11-coderabbit-remediation docker compose up
--build --wait --wait-timeout 180` built and started a healthy disposable deployment. The root,
setup-status, and management readiness endpoints responded successfully; readiness reported
PostgreSQL, image storage, scheduled jobs, and database connections as `UP`.

After disposable bootstrap and admin login, `POST /api/v1/admin/backups` produced backup
`0288ac8e-0aa5-4500-80ef-272dfa0b6ddb` in `SUCCEEDED` state:

- archive size: 17,578 bytes;
- API and local SHA-256:
  `360000731b0c47aa213a1ca0d64eee46d26a8217a07bbb8f5d035b51c79860e8`;
- archive entries: `database.dump`, `instance-settings.json`, and `manifest.json`;
- manifest schema version: 12;
- manifest build identifier: `m11-coderabbit-remediation`.

The dump restored with `pg_restore --exit-on-error --no-owner --no-privileges` into a clean
`postgres:18.3-alpine` container. Queries confirmed 12 successful Flyway migrations and the M11
`job_locks.run_id` column. The Compose deployment, database volume, restore container, temporary
secrets, session cookies, dump, and backup archive were removed after verification.
