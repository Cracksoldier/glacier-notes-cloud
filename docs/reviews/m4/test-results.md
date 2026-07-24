# M4 Test Results

No runtime tests have been executed for R3. The CodeRabbit run and human triage used source and
specification inspection only.

Future M4 verification runs must be appended here with the date, exact command, environment profile,
result, duration, and safe artifact location. Do not replace earlier failed runs with later results,
and do not record credentials, tokens, cookies, user content, or unredacted logs.

## 2026-07-24 SMTP configuration remediation

- `sg docker -c './mvnw -pl backend -Dtest=SecretProviderTest,SecretPolicyTest,SmtpStartupValidatorTest,RequestBodyLimitPolicyTest,S3StoragePolicyTest,PingResourceTest,RequestBodyLimitTest test'`
  passed 16 tests in 20.9 seconds. SMTP startup validation covered unauthenticated delivery and
  rejected incomplete username/password pairs.
- `docker compose config --quiet` and `sh -n deployment/docker/entrypoint.sh` passed.
- `test "$(printf 'smtp password with spaces  \r\n' | sed -e '$s/\r$//')" = "smtp password with spaces  "`
  passed, confirming the SMTP password loader removes only a final CR line ending while preserving
  spaces.
- `sg docker -c './mvnw verify'` passed all 68 backend tests in 1 minute 19 seconds. Safe reports are
  under `backend/target/surefire-reports/`.

Authenticated SMTP now fails startup unless both credentials are present; unauthenticated SMTP
remains supported.

## 2026-07-24 Batch 3 async remediation

Admin settings coverage proved both the disabled form and the direct save guard after an initial GET
failure. The complete frontend suite passed 41 tests. See the
[Batch 3 test record](../remediation/batch-3/test-results.md) for regression-first and full-gate
evidence.

## 2026-07-24 Batch 5 frontend remediation

Lifecycle/admin regressions cover masked tokens, reset single-flight behavior, post-action user
refresh, initial-load alert visibility, and picker focus. The complete frontend suite passed 59
tests. See the [Batch 5 test record](../remediation/batch-5/test-results.md).
