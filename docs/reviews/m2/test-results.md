# M2 Test Results

No runtime tests have been executed for R1. The CodeRabbit run and human triage used source and
specification inspection only.

Future M2 verification runs must be appended here with the date, exact command, environment profile,
result, duration, and safe artifact location. Do not replace earlier failed runs with later results,
and do not record credentials, tokens, cookies, user content, or unredacted logs.

## 2026-07-24 security-boundary remediation

- `sg docker -c './mvnw -pl backend -Dtest=SecretProviderTest,SecretPolicyTest,SmtpStartupValidatorTest,RequestBodyLimitPolicyTest,S3StoragePolicyTest,PingResourceTest,RequestBodyLimitTest test'`
  passed 16 tests in 20.9 seconds. It covered whitespace-only and malformed secrets, generic 4xx
  problem classification, and fixed-length and chunked oversized requests.
- `sg docker -c './mvnw -pl backend -Dtest=AuthenticationResourceTest,SetupResourceTest,ImageResourceTest,PostgresqlImageResourceTest,S3ImageResourceTest,TransferResourceTest test'`
  passed 15 tests in 43.526 seconds across filesystem, PostgreSQL, and MinIO/S3 profiles.
- `sg docker -c './mvnw verify'` passed all 68 backend tests in 1 minute 19 seconds. Safe reports are
  under `backend/target/surefire-reports/`.

The bootstrap token now has one explicit header delivery path, central secret validation rejects
whitespace and invalid lengths, and generic client errors no longer become `INTERNAL_ERROR`.
Source inspection confirms unhandled server failures retain their throwable when logged.
