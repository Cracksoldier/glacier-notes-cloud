# Repository Guidelines

## Project Structure & Module Organization

`openapi/glacier-notes-v1.yaml` is canonical; generator customizations live in `openapi/templates`.
Backend Java, Flyway migrations, and JUnit tests are under `backend/src`. Angular code is in
`frontend/src/app`, browser tests in `frontend/e2e`, repository checks in `frontend/scripts`, and
assets in `frontend/public`. Never hand-edit generated API sources. Keep deployment files in
`deployment/`, runbooks and review evidence in `docs/`, and desktop fixtures in
`compatibility-fixtures/`.

## Build, Test, and Development Commands

- `./mvnw -pl backend clean generate-sources` regenerates Java and Angular contracts.
- `./mvnw verify` builds the Maven reactor and runs backend tests with Docker-backed PostgreSQL.
- `./mvnw -pl backend quarkus:dev` starts Quarkus with PostgreSQL Dev Services.
- `cd frontend && npm ci` installs locked Node.js dependencies.
- `npm run check` validates Biome formatting/lint; `npm run check:write` applies safe fixes.
- `npm run test:repository` checks repository and generated-client metadata.
- `npm run build:production && npm run test:ci` runs the strict Angular build and unit suite.
- `npm run test:e2e` runs Playwright against a production-like deployment.
- `docker compose up --build --wait` starts the same-origin application; use `docker compose down`
  to preserve volumes.

## Coding Style & Naming Conventions

Use four spaces for Java and two for frontend and data files. Java types use `UpperCamelCase`;
members use `lowerCamelCase`; tests end in `Test`. Angular filenames use lowercase kebab-case and
tests end in `.spec.ts`. Biome 2.5.4 is the only frontend formatter; do not add Prettier. Change
OpenAPI first, regenerate, and commit generated output with its source. Keep generated DTOs, domain
models, and entities separate. User-content repository operations require an `OwnerId`.

## Testing Guidelines

Backend tests use JUnit 5, REST Assured, Quarkus Test, and PostgreSQL Dev Services. Frontend tests
use Vitest through Angular CLI; browser coverage uses Playwright desktop and tablet projects. No
numeric coverage threshold exists. Cover changed behavior, ownership, constraints, migrations,
transaction failure, and template syntax. Run focused tests, then affected-module and CI gates.

## Commits & Pull Requests

History follows imperative Conventional Commit subjects: `feat(operations): ...`,
`fix(frontend): ...`, `test: ...`, and `docs(readme): ...`. Keep commits focused. Pull requests
should describe behavior and migration impact, link issues, list verification commands, and include
screenshots for visible UI changes. CI must pass without generated-code drift.

## Security & Operations

Copy `.env.example` to `.env`; never commit credentials, tokens, cookies, user content, dumps, or
backups. Keep management port 9000 private and follow `docs/BACKUP_RESTORE.md`. Preserve
`application/problem+json`, correlation IDs, owner-equivalent not-found behavior, and immutable raw
CodeRabbit JSONL evidence.
