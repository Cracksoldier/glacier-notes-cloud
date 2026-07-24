# M7 Test Results

No runtime tests have been executed for R6. The CodeRabbit run and human triage used source,
generated-contract, migration, and specification inspection only.

Future M7 verification runs must be appended here with the date, exact command, storage backend,
environment profile, result, duration, and safe artifact location. Prioritize failure injection at
every database/object-store boundary, S3 timeout/encryption checks, PostgreSQL pool-leak tests,
blocked-prefix garbage collection, request-size boundaries, concurrent upload UI state, and
keyboard-only lightbox behavior. Do not replace earlier failed runs with later results, and do not
record credentials, tokens, cookies, user content, image bytes, or unredacted logs.

## 2026-07-24 durable-storage remediation

- `./mvnw -pl backend -Dtest=ExternalStorageOperationsTest,DatabaseSchemaTest,OwnershipRepositoryTest,ImageResourceTest test`
  failed before test discovery because the Docker daemon was unavailable. No application tests ran.
- After Docker was started,
  `sg docker -c './mvnw -pl backend -Dtest=ExternalStorageOperationsTest,DatabaseSchemaTest,OwnershipRepositoryTest,ImageResourceTest test'`
  passed 12 tests in 23.726 seconds using PostgreSQL Dev Services and the filesystem image backend.
  It covered the V10 schema, reservation leases and quota accounting, safe transfer cleanup,
  owner mutation scope, blocked-prefix garbage selection, and asynchronous binary removal.
- `sg docker -c './mvnw verify'` passed the complete 57-test backend suite in 1 minute 12 seconds.
  Filesystem, PostgreSQL, and MinIO/S3 image profiles all passed, including eventual physical
  deletion. Safe reports are under `backend/target/surefire-reports/`.
- Frontend non-regression gates passed: `npm run check` (83 files), `npm run test:ci` (23 tests),
  and `npm run build:production` (2.814 seconds). The production build retained the existing bundle
  budget warnings.

## 2026-07-24 S3 policy remediation

- `sg docker -c './mvnw -pl backend -Dtest=SecretProviderTest,SecretPolicyTest,SmtpStartupValidatorTest,RequestBodyLimitPolicyTest,S3StoragePolicyTest,PingResourceTest,RequestBodyLimitTest test'`
  passed 16 tests in 20.9 seconds. S3 policy tests verified bounded call/attempt timeouts, supported
  server-side encryption values, and encryption on `PutObject` requests.
- `sg docker -c './mvnw -pl backend -Dtest=AuthenticationResourceTest,SetupResourceTest,ImageResourceTest,PostgresqlImageResourceTest,S3ImageResourceTest,TransferResourceTest test'`
  passed 15 tests in 43.526 seconds, including the MinIO/S3 profile.
- `docker compose config --quiet` passed with the S3 encryption and timeout variables wired into the
  application service.
- `sg docker -c './mvnw verify'` passed all 68 backend tests in 1 minute 19 seconds. Safe reports are
  under `backend/target/surefire-reports/`.

The S3 client now enforces explicit API-call and attempt bounds, and deployment configuration passes
the validated `AES256` or `aws:kms` server-side-encryption policy through to uploads.
