# M10 Test Results

No runtime tests have been executed for R9. The CodeRabbit run and human triage used source,
generated-contract, migration, Git-diff, specification, and official PostgreSQL syntax inspection
only.

Future M10 verification runs must be appended here with the date, exact command, database/storage
profile, result, duration, and safe artifact location. Prioritize delayed preference GET/PUT races,
keyboard-only share warnings, localization coverage, email-change URL hygiene, permanent-deletion
failure injection at every object/database boundary, retained-deletion scheduling, and password
history transitions. Do not replace earlier failures with later successes, and do not record
credentials, tokens, cookies, email addresses, user content, exports, database dumps, or
unredacted logs.

## 2026-07-24 durable-deletion remediation

- `sg docker -c './mvnw -pl backend -Dtest=TransferResourceTest,LifecycleResourceTest,PostgresqlImageResourceTest test'`
  passed 10 tests in 37.548 seconds. The seven lifecycle cases included permanent deletion with
  deliberately unsafe binary and transfer locations: logical account deletion succeeded while both
  physical cleanup intents remained durably recorded for operator handling.
- `sg docker -c './mvnw verify'` passed all 57 backend tests in 1 minute 12 seconds across PostgreSQL
  Dev Services, filesystem images, PostgreSQL image blobs, and MinIO/S3. Safe reports are under
  `backend/target/surefire-reports/`.
- `npm run check`, `npm run test:ci`, and `npm run build:production` passed; 23 frontend tests ran,
  and the production build emitted only the existing bundle budget warnings.

## 2026-07-24 Batch 3 async remediation

Preference tests proved concurrent-load coalescing, GET/PATCH serialization, and queue recovery
after a 30-second request timeout. The complete frontend suite passed 41 tests and production
dependency audit found no vulnerabilities. See the
[Batch 3 test record](../remediation/batch-3/test-results.md).

## 2026-07-24 Batch 5 frontend remediation

German translation and keyboard-modal regressions cover share headings, reasons, privacy/actions,
focus restoration, and notes settings text. The complete frontend suite passed 59 tests and the
production audit found zero vulnerabilities. See the
[Batch 5 test record](../remediation/batch-5/test-results.md).
