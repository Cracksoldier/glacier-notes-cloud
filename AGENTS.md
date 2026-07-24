# Repository Guidelines

## Project Structure & Module Organization

`openapi/glacier-notes-v1.yaml` is the canonical HTTP contract. The Quarkus backend lives in
`backend/`; code is under `src/main/java`, Flyway migrations under `src/main/resources/db/migration`,
and tests under `src/test/java`. Angular code is in `frontend/src/app`, static assets in `public`, and
generated API code in `frontend/src/app/shared/generated-api`. Keep operational artifacts in `deployment/`,
ADRs in `docs/adr`, and desktop samples in `compatibility-fixtures/`.

## Build, Test, and Development Commands

- `./mvnw verify` builds the backend and runs tests against ephemeral PostgreSQL via Docker.
- `./mvnw -pl backend quarkus:dev` starts Quarkus with PostgreSQL Dev Services.
- `./mvnw -pl backend clean generate-sources` regenerates Java contracts and the Angular client.
- `cd frontend && npm ci` installs the locked frontend dependency tree.
- `npm run check` runs Biome formatting and lint validation.
- `npm run check:write` applies safe Biome fixes.
- `npm run build:production` performs the strict Angular production build.
- `npm run test:ci` runs frontend tests once; `npm start` starts the proxied development server.

## Coding Style & Naming Conventions

Use four-space indentation for Java and two spaces for TypeScript, HTML, CSS, JSON, and YAML.
Java types use `UpperCamelCase`, methods and fields use `lowerCamelCase`, and tests end in `Test`.
Angular files use lowercase kebab-case and tests end in `.spec.ts`. Biome 2.5.4 is the only frontend
formatter; do not introduce Prettier. Never manually edit generated API files. Add or change the
OpenAPI operation first, then regenerate. Keep generated DTOs, domain models, and entities separate.
Every user-content repository operation must require an `OwnerId`.

## Testing Guidelines

Backend tests use JUnit 5, REST Assured, Quarkus Test, and PostgreSQL Dev Services. Frontend
tests use Vitest through Angular CLI. No numeric coverage threshold is configured; cover changed
behavior, ownership boundaries, constraints, migrations, and Angular template syntax. Run both
backend and frontend gates before submitting changes.

## Commit & Pull Request Guidelines

The repository has no commit history yet, so no established convention exists. Use imperative
subjects, preferably Conventional Commit form such as `feat(api): add notebook contract`.
Keep generated contract changes in the same commit as their OpenAPI source. Pull requests should
describe behavior and migration impact, link relevant issues, list verification commands, and add
screenshots for visible UI changes. CI must pass with no generated-code drift.

## Security & Configuration

Copy `.env.example` for local configuration and never commit real credentials, tokens, or user
content. Preserve `application/problem+json` errors and correlation IDs without exposing SQL,
filesystem paths, or cross-owner resource existence.
