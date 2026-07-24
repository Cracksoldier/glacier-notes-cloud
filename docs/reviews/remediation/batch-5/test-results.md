# Batch 5 Test Results

All runs were performed on 2026-07-24 from `7514d1b` plus the Batch 5 working diff. No credentials,
cookies, tokens, user content, imports, exports, traces, screenshots, or container data are stored
here.

## Regression-First Evidence

The initial focused command was:

```bash
npm test -- --watch=false \
  --include login \
  --include public-lifecycle \
  --include setup \
  --include admin-status \
  --include admin-user-detail \
  --include note-editor \
  --include transfer-dialog \
  --include i18n
```

After correcting one test-harness DOM typing error, the pre-fix run executed 33 tests in eight files:
15 failed and 18 passed, with the expected unhandled admin-status request. The failures proved
missing validation/error associations, stale and concurrent admin/token state, invalid setup CSS,
malformed block code, shifting upload identity, incomplete dialog focus behavior, and missing
English/German messages. The same focused set passed 33/33 after the fixes. A later focused run of
the three expanded modal/admin suites passed 21/21.

## Module and Repository Gates

- `npm run check` passed all 90 files.
- `npm run test:ci` passed 59 tests across 16 files.
- `npm run build:production` passed. It retained the existing non-blocking 700 kB initial-bundle
  and 8 kB notes-shell stylesheet warnings.
- The exact frontend CI sequence—`npm ci`, no-Prettier guard, Biome check, production build, unit
  suite, and `npm audit --omit=dev --audit-level=high`—passed; the production audit found zero
  vulnerabilities.
- `sg docker -c './mvnw verify'` passed all 82 backend tests and the Quarkus build in 1 minute
  23 seconds. Contract generation produced no generated-source drift.
- `git diff --check` passed.

## Deployment and Browser Gate

An isolated `glacier-batch5` Compose project used ports `18080`/`19000` and temporary file-backed
secrets outside the repository. The application, setup-status, and readiness probes passed.
Administrator bootstrap, session validation, invitation creation, and member activation prepared a
throwaway browser-test account.

`CI=1 npm run test:e2e` passed all six desktop/tablet core-note, search/history, and
portable-transfer workflows in 7.2 seconds. The containers, network, and four named volumes were
removed; an empty project listing confirmed teardown.

## Finished-Diff Review

One CodeRabbit 0.7.0 run completed with one Minor suggestion. Source comparison proved that its
proposed upload completion order would not reproduce the original shifting-index bug, so it was
classified not applicable and no source change was made. The raw stream was not rerun or modified.
