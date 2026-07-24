# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Glacier Notes Cloud is a self-hosted, multi-user notes web application: an OpenAPI-first monorepo
with a Quarkus (Java 21) backend, an Angular frontend, and PostgreSQL persistence. `openapi/glacier-notes-v1.yaml`
is the canonical contract — it is the source for transport DTOs and API interfaces on both sides.
Backend persistence entities and domain types stay independent of the generated contract.

## Commands

### Generate contracts (run after any `openapi/glacier-notes-v1.yaml` change)

```bash
./mvnw -pl backend clean generate-sources
git diff --exit-code -- frontend/src/app/shared/generated-api   # verify no drift
```

Never hand-edit `backend/target/generated-sources/openapi` or `frontend/src/app/shared/generated-api`.

### Backend (from repo root)

```bash
./mvnw verify                        # full reactor build + JUnit tests against Docker-backed PostgreSQL
./mvnw -pl backend quarkus:dev        # run Quarkus with PostgreSQL Dev Services (needs Docker running)
./mvnw -pl backend test -Dtest=ClassName#methodName   # run a single test
```

Required env vars for `quarkus:dev`: `GLACIER_BOOTSTRAP_TOKEN`, `GLACIER_SECURITY_SESSION_SECRET`,
`GLACIER_IMAGE_FILESYSTEM_ROOT`. Add `GLACIER_BACKUP_ENABLED=true`, `GLACIER_BACKUP_DIRECTORY`, and
`GLACIER_BUILD_IDENTIFIER` to exercise the admin backup dashboard locally.

### Frontend (from `frontend/`)

```bash
npm ci                     # install locked dependencies
npm start                  # ng serve, proxies /api to Quarkus on :8080
npm run check              # Biome format + lint (the only frontend formatter — never add Prettier)
npm run check:write        # apply safe Biome fixes
npm run test:repository    # validates repository/generated-client metadata (scripts/repository-contracts.test.mjs)
npm run build:production   # strict Angular production build
npm run test:ci            # Vitest unit suite, non-watch
npx vitest run path/to/file.spec.ts -t "test name"   # run a single test
npm run test:e2e           # Playwright, against a running Compose deployment (needs GLACIER_E2E_USERNAME/PASSWORD)
```

### Production-like local environment

```bash
cp .env.example .env
mkdir -p deployment/secrets
openssl rand -base64 36 > deployment/secrets/database-password.txt
openssl rand -base64 36 > deployment/secrets/bootstrap-token.txt
openssl rand -base64 48 > deployment/secrets/session-secret.txt
chmod 600 deployment/secrets/*.txt
docker compose up --build --wait
docker compose down          # stop, preserve volumes (add --volumes to delete data permanently)
```

Full verification loop (mirrors CI):

```bash
./mvnw verify
cd frontend && npm ci && npm run test:repository && npm run check && npm run build:production && npm run test:ci
npm audit --omit=dev --audit-level=high
```

## Architecture

### Backend package boundaries (`backend/src/main/java/com/glaciernotes/cloud/`)

- `generated` — build-generated REST contracts and DTOs (from the OpenAPI spec)
- `api` — HTTP adapters, `application/problem+json` mapping, correlation IDs
- `application` — use cases and transaction boundaries, organized by domain area (`content`,
  `transfer`, `image`, `operations`, `lifecycle`, `auth`, `storage`, `port`)
- `domain` — ownership-aware models and repository ports (e.g. `OwnerId`, `TimeProvider`, `IdGenerator`)
- `persistence` — PostgreSQL mappings (`entity`) and owner-scoped repository adapters (`repository`)
- `security`, `infrastructure`, `configuration` — replaceable infrastructure boundaries

Transaction boundaries belong on application operations or repository writes, never in UI/API code.
Time and ID generation are injected interfaces specifically so tests can substitute deterministic
implementations; binary storage and password hashing are likewise application ports (filesystem,
PostgreSQL, or S3-compatible storage are interchangeable backends).

**Ownership is the load-bearing abstraction of this codebase.** Portable content is keyed by
composite `(owner_id, id)` identity (ADR 0004) — the same UUID can exist in two different accounts
(e.g. after importing a shared desktop export) without ever granting cross-owner access. Every owned
repository operation accepts an `OwnerId`; there is no unscoped content lookup in the repository
interface. Administrators are bound by the same ownership rules as normal users for their own
content — admin-only operations work through separate, explicitly audited paths (e.g. blind import),
never by bypassing ownership checks. `OwnerId` is the single highest-degree node in the codebase
graph, touching nearly every application-layer service.

Flyway owns all schema creation and runs before Quarkus becomes ready; Hibernate is validation-only
and never creates/updates schema (ADR 0005). Never edit a migration that has reached a shared
environment — add a new, monotonically versioned one instead, and prefer additive/backward-compatible
changes over destructive ones (see `docs/MIGRATIONS.md`).

### Frontend structure (`frontend/src/app/`)

Standalone Angular components (no NgModules) organized by feature: `auth`, `account`, `admin`,
`notes`, `sessions`, `setup`, `home`, `transfer`, plus `core` (cross-cutting services like i18n) and
`shared/generated-api` (committed OpenAPI-generated client — never hand-edit). Router guards are
navigation aids only, not an authorization boundary — Quarkus enforces `USER`/`ADMIN` roles
server-side regardless of what the Angular router allows.

Authentication uses opaque random session tokens (only keyed hashes persisted in `user_sessions`);
state-changing requests use a session-bound double-submit CSRF token. Future note features should
depend on platform-neutral data-access interfaces so the web client stays compatible with a
separate Electron desktop adapter that shares the portable `.glacier.json` format.

### Portable transfer format

Full/notebook/note export and import use a desktop-compatible schema-v1 `.glacier.json` format
(`format`, `schemaVersion`, `exportedAt`, `notebooks`, `notes`, `labels`, `images`, `scope`; images
are base64, referenced from note content as `glacier-img://<uuid>`). Compatibility fixtures live in
`compatibility-fixtures/desktop-schema-v1` — after changing the portable contract, re-verify all
fixtures and confirm a fresh cloud export is still readable by the desktop app (see
`docs/PORTABLE_TRANSFERS.md`). Import conflicts are resolved by deterministic ID remap ("add as
copies") or "replace existing by ID" scoped to the importing account; an ID owned by another account
is never accessed or disclosed. Blind administrative imports (from a user's admin page) surface only
counts, conflicts, and structural errors — never note content, titles, or filenames.

### Request body limits (ADR 0007)

Ordinary API requests are capped at 10 MiB via an early Vert.x body handler. Image uploads and
portable imports get their own configured maximum plus a 1 MiB multipart-envelope allowance. The
Quarkus global limits are kept as the absolute outer ceiling — raising the portable-import maximum
requires raising that ceiling too, by at least the multipart allowance.

## Coding conventions

- Four spaces for Java, two for frontend/data files. Java types `UpperCamelCase`, members
  `lowerCamelCase`, tests end in `Test`. Angular filenames are lowercase kebab-case; tests end in
  `.spec.ts`.
- Add an OpenAPI operation before implementing a new HTTP endpoint; regenerate and commit the
  generated output together with the spec change. Keep generated DTOs out of domain/persistence
  signatures.
- Never log note content, checklist text, filenames, passwords, or tokens.
- Normalize identity values before persistence while preserving original display casing.
- Use server-authoritative UTC timestamps and optimistic versions for mutable content.
- IDs are UUID strings; timestamps are ISO-8601 UTC. Errors use `application/problem+json` with an
  application error code and correlation ID. Paginated/synchronizable collections must support
  `modifiedSince` and tombstone inclusion.

## Testing

Backend: JUnit 5, REST Assured, Quarkus Test, against real PostgreSQL via Dev Services (no
in-memory substitute) — see ADR 0005. Frontend: Vitest through Angular CLI for unit tests; Playwright
(desktop + tablet projects) for browser coverage. There is no numeric coverage threshold; cover
changed behavior, ownership boundaries, constraints, migrations, transaction failure paths, and
template syntax. Run focused tests first, then the affected-module and full CI gates.

## Commits & PRs

Imperative Conventional Commit subjects (`feat(operations): ...`, `fix(frontend): ...`, `test: ...`,
`docs(readme): ...`). Keep commits focused. PRs describe behavior and migration impact, link issues,
list verification commands, and include screenshots for visible UI changes. CI must pass with no
generated-code drift.

## Security & operations

Never commit credentials, tokens, cookies, user content, dumps, or backups. Keep the management port
(9000) private. Preserve `application/problem+json`, correlation IDs, and owner-equivalent
not-found behavior (a cross-owner lookup must look identical to a genuinely missing record — never
leak existence). Follow `docs/BACKUP_RESTORE.md` for backup/restore; keep raw CodeRabbit JSONL
review evidence immutable.

## graphify

This project has a knowledge graph at `graphify-out/` with god nodes, community structure, and
cross-file relationships.

- For codebase questions, first run `graphify query "<question>"` when `graphify-out/graph.json`
  exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for
  focused concepts. These return a scoped subgraph, usually much smaller than `GRAPH_REPORT.md` or
  raw grep output.
- Dirty `graphify-out/` files are expected after hooks or incremental updates; that is not a reason
  to skip graphify. Only skip it if the task is about stale/incorrect graph output, or the user
  explicitly says not to use it.
- If `graphify-out/wiki/index.md` exists, use it for broad navigation instead of raw source browsing.
- Read `graphify-out/GRAPH_REPORT.md` only for broad architecture review or when query/path/explain
  do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- When the user types `/graphify`, use the installed graphify skill or instructions before doing
  anything else.
