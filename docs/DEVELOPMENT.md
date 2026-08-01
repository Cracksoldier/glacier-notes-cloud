# Development guide

## Toolchain

- Java source and bytecode level: 21
- Maven: 3.9.16 through the committed wrapper
- Quarkus: 3.37.3
- PostgreSQL integration image: `postgres:18.3-alpine`
- Node.js: 24.15 or newer
- npm: 11.12.1
- Angular: 22.0.7
- Biome: 2.5.4

## Generate and build

Generate Java API interfaces and the committed Angular client:

```bash
./mvnw -pl backend clean generate-sources
git diff --exit-code -- frontend/src/app/shared/generated-api
```

Generated Java is placed below `backend/target/generated-sources/openapi`. Generated Java
and TypeScript must never be edited manually.

Run the backend tests and PostgreSQL migrations against an ephemeral PostgreSQL container:

```bash
./mvnw verify
```

Run the frontend gates:

```bash
cd frontend
npm ci
npm run check
npm run build:production
npm run test:ci
```

M6 browser tests run against the production-like Compose deployment. After creating a local user,
provide its credentials explicitly and run Chromium in desktop and tablet viewports:

```bash
docker compose up --build --wait
cd frontend
npx playwright install chromium
GLACIER_E2E_USERNAME=your-user \
GLACIER_E2E_PASSWORD=your-password \
npm run test:e2e
```

The browser suite creates uniquely named test notebooks, labels, and notes. Use a disposable local
account or database when running it outside CI.

The second-factor spec needs an instance started with `GLACIER_MFA_ENABLED=true` and a second,
dedicated account — it enrolls, signs in through both stages, and disables the factor again, so it
must not share an account with the note workflow. It is skipped unless both variables are set:

```bash
GLACIER_E2E_MFA_USERNAME=your-second-user \
GLACIER_E2E_MFA_PASSWORD=your-second-password \
npm run test:e2e
```

An interrupted run can leave that account enrolled. Since the spec only knows the password, it
cannot clean that up; clear it with the break-glass operation in `deployment/README.md`.

## Local applications

With a Docker-compatible daemon running, Quarkus Dev Services supplies PostgreSQL:

```bash
./mvnw -pl backend quarkus:dev
```

In another shell:

```bash
cd frontend
npm start
```

The Angular development server proxies `/api` to Quarkus on port 8080, so the generated client
uses the same relative URL in development and in the integrated application container introduced
in M2.

Second-factor support is off by default, so an ordinary `quarkus:dev` run needs no additional
variable — do not add one to the standard incantation. To work on it, set `GLACIER_MFA_ENABLED=true`
together with `GLACIER_MFA_ENCRYPTION_SECRET` (or `GLACIER_MFA_ENCRYPTION_SECRET_FILE`), which must
hold 32–512 non-whitespace characters. Enabling the flag without a valid secret is a startup failure
by design. The secret is deliberately separate from the session secret: rotating the session secret
only ends sessions, whereas rotating this one would invalidate stored enrollments and recovery codes.

The step-up prompt on the gated operations is easy to miss while developing: verifying a code opens
a grace window on that session, five minutes by default, and inside it the password alone is
accepted, so no code field ever appears. To exercise the prompt, close the window by setting the
step-up grace to `0` in admin settings, beside the login-throttling fields — there is no environment
variable for it, and the change takes effect on the next request.

To see the notifications a second-factor change sends, point the instance at a local mail sink —
`docker run --rm -p 1025:1025 -p 8025:8025 axllent/mailpit` — and start Quarkus with
`GLACIER_SMTP_ENABLED=true`, `GLACIER_SMTP_HOST=localhost`, `GLACIER_SMTP_PORT=1025`,
`GLACIER_SMTP_START_TLS=DISABLED`, and a `GLACIER_SMTP_SENDER_ADDRESS`. Messages then appear at
`http://localhost:8025`. They are sent after the operation commits, so a mail server that is
unreachable logs a warning and changes nothing about the operation itself; leaving
`GLACIER_SMTP_ENABLED` off skips them silently. Set the account's language in account settings to
see the German wording — the language is per recipient, falling back to the instance default.

## Formatting and editors

Biome is the only frontend formatter. The repository recommends the Biome VS Code extension
and commits workspace formatter settings. Do not install Prettier configuration or run a
second formatter over frontend files. Generated API files are excluded from Biome.

Angular templates are currently formatted by Biome and protected by the representative
template fixture, strict template compilation, and production builds.

## API conventions

- All application operations live below `/api/v1`.
- IDs are UUID strings; timestamps are ISO-8601 UTC values.
- Errors use `application/problem+json` and include an application error code and correlation ID.
- Collection endpoints added in later milestones must use explicit pagination and stable ordering.
- Synchronizable collections must remain extendable with `modifiedSince` and tombstone inclusion.
