# M0–M1 Test Results

The original R0 review used source inspection only. Later verification runs are appended here
without altering the immutable CodeRabbit output.

## 2026-07-24 API security remediation

- `./mvnw -pl backend clean generate-sources` passed and regenerated both Java contracts and the
  Angular client from the canonical OpenAPI document.
- `npm run check`, `npm run test:ci`, and `npm run build:production` passed. The frontend suite
  passed 24 tests, including a regression proving read-only session requests omit the CSRF header.
  The production build retained the existing bundle-budget warnings.
- `sg docker -c './mvnw verify'` passed all 68 backend tests in 1 minute 19 seconds using PostgreSQL
  Dev Services and the filesystem, PostgreSQL, and MinIO/S3 image profiles. Safe reports are under
  `backend/target/surefire-reports/`.

The global OpenAPI default now requires only the session cookie. Authenticated mutations explicitly
require the session cookie and CSRF header together, and public operations explicitly opt out.
