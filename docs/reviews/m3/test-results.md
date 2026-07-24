# M3 Test Results

No runtime tests have been executed for R2. The CodeRabbit run and human triage used source and
specification inspection only.

Future M3 verification runs must be appended here with the date, exact command, environment profile,
result, duration, and safe artifact location. Do not replace earlier failed runs with later results,
and do not record credentials, tokens, cookies, user content, or unredacted logs.

## 2026-07-24 Batch 3 async remediation

Delayed-restoration guard coverage passed and the deployment E2E suite verified authenticated
navigation in desktop and tablet browsers. The complete frontend suite passed 41 tests; all 68
backend tests also passed. See the [Batch 3 test record](../remediation/batch-3/test-results.md) for
the exact gates, pre-fix failures, deployment profile, and CodeRabbit follow-up evidence.

## 2026-07-24 Batch 5 frontend remediation

Admin-status failure and associated login-validation regressions failed before the fixes and passed
afterward. The complete frontend suite passed 59 tests, all 82 backend tests passed, and all six
browser workflows passed. See the [Batch 5 test record](../remediation/batch-5/test-results.md).

## 2026-07-24 Batch 7 coverage closure

The isolated supported deployment rejected invalid-CSRF logout with `403 CSRF_INVALID`, preserved
the active session, and then completed valid logout. All six browser workflows passed. See the
[Batch 7 test record](../remediation/batch-7/test-results.md).
