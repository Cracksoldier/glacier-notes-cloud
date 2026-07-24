# M9 Test Results

No runtime tests have been executed for R8. The CodeRabbit run and human triage used source,
generated-contract, migration, Git-diff, fixture, and specification inspection only.

Future M9 verification runs must be appended here with the date, exact command, database/storage
profile, result, duration, and safe artifact location. Prioritize cancel/restart polling races,
keyboard-only modal operation, route-specific request limits, failed admin cancellation,
legacy checklist relocation through `REPLACE_BY_ID`, configured image-size boundaries, repeated
Playwright runs, and download cleanup races. Do not replace earlier failures with later successes,
and do not record credentials, tokens, cookies, imported content, exports, database dumps, or
unredacted logs.

## 2026-07-24 durable-transfer remediation

- `sg docker -c './mvnw -pl backend -Dtest=TransferResourceTest,LifecycleResourceTest,PostgresqlImageResourceTest test'`
  passed 10 tests in 37.548 seconds. Both portable-transfer workflows completed with journaled image
  replacement and terminal-state temporary-file cleanup; the PostgreSQL image workflow also passed.
- `sg docker -c './mvnw verify'` passed all 57 backend tests in 1 minute 12 seconds. Import image
  reservations, deterministic retry keys, transfer state transitions, and the V10 reconciliation
  migration were exercised. Safe reports are under `backend/target/surefire-reports/`.
- `npm run check`, `npm run test:ci`, and `npm run build:production` passed with 23 frontend tests;
  the production build reported only the existing bundle budget warnings.
