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
