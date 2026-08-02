# Glacier Notes Cloud

Self-hosted, multi-user Glacier Notes web application. The project is an OpenAPI-first
monorepo with a Quarkus backend, Angular frontend, and PostgreSQL persistence layer.

> [!WARNING]
> **Early development.** Glacier Notes Cloud is a pre-1.0 release (v0.1.x). APIs, database schema,
> and configuration may change between versions without notice. Not production-ready — do not rely
> on it for irreplaceable data, and always keep verified backups.

## Prerequisites

- JDK 21 or newer (the build targets Java 21)
- Docker-compatible daemon with Docker Compose v2 for PostgreSQL development, integration tests,
  and the production-like environment
- OpenSSL or another secure random generator for disposable Compose secrets
- Node.js 24.15 or newer
- npm 11.12.1

## Local test environment

The recommended development workflow runs Quarkus and Angular separately. Quarkus Dev Services
starts an ephemeral PostgreSQL container, so no database URL or local PostgreSQL installation is
required. The commands below use disposable development secrets; never use them for a deployment.

### Prepare the workspace

From the repository root, generate the API contracts and install the locked frontend dependencies:

~~~bash
./mvnw -pl backend clean generate-sources
cd frontend
npm ci
cd ..
~~~

Regenerate after changing `openapi/glacier-notes-v1.yaml`. Do not manually edit files below
`backend/target/generated-sources/openapi` or `frontend/src/app/shared/generated-api`.

### Start the backend

Ensure Docker is running, then start Quarkus from the repository root:

~~~bash
export GLACIER_BOOTSTRAP_TOKEN=local-bootstrap-token-development-only-2026
export GLACIER_SECURITY_SESSION_SECRET=local-session-secret-development-only-2026
export GLACIER_IMAGE_FILESYSTEM_ROOT=/tmp/glacier-notes-dev-images
./mvnw -pl backend quarkus:dev
~~~

The API listens on `http://localhost:8080`. PostgreSQL starts automatically in Docker, and the
management interface is available only on port 9000. Readiness is
`http://localhost:9000/q/health/ready`, and metrics are exposed at
`http://localhost:9000/q/metrics` while metrics are enabled.

Backups are disabled by default. To exercise the M11 backup dashboard in development, set these
additional variables before starting Quarkus:

~~~bash
export GLACIER_BACKUP_ENABLED=true
export GLACIER_BACKUP_DIRECTORY=/tmp/glacier-notes-dev-backups
export GLACIER_BUILD_IDENTIFIER=local-dev
~~~

Use only a disposable directory for development backups. Backup archives contain user data and
authentication hashes.

### Start the frontend

In a second terminal:

~~~bash
cd frontend
npm start
~~~

Open `http://localhost:4200`. The Angular development server proxies `/api` to Quarkus. On a fresh
database, create the initial administrator and enter
`local-bootstrap-token-development-only-2026` in the bootstrap-token field.

Confirm the services independently if startup fails:

~~~bash
curl --fail --show-error http://localhost:8080/api/v1/setup/status
curl --fail --show-error http://localhost:9000/q/health/ready
~~~

Stop both development servers with `Ctrl+C`. Because Dev Services reuse is disabled, its test
database is discarded when the backend stops.

## Build and test

```bash
./mvnw verify
cd frontend
npm ci
npm run test:repository
npm run check
npm run build:production
npm run test:ci
npm audit --omit=dev --audit-level=high
```

`./mvnw -pl backend generate-sources` regenerates the Java contract and committed
Angular client from `openapi/glacier-notes-v1.yaml`. Generated files must not be edited.

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for local startup, database, generation,
and editor instructions. Implemented roadmap scope is tracked in
[docs/MILESTONE_STATUS.md](docs/MILESTONE_STATUS.md).

## Portable import and export

Signed-in users can open **Import / Export** from the notes toolbar. Exports can contain the whole
account, one notebook, or one note and download as `.glacier.json`. Imports are inspected before
apply; ID conflicts can either be added as deterministic copies or replaced only within the current
account. Administrators may disable user exports and can perform a blind import from a user's admin
page without previewing note content. See [docs/PORTABLE_TRANSFERS.md](docs/PORTABLE_TRANSFERS.md)
for format limits, job states, cleanup, and compatibility guidance.

## Install a published release

Tagged releases publish a signed, immutable image to `ghcr.io/cracksoldier/glacier-notes-cloud:vX.Y.Z`
and attach a versioned Compose file, CycloneDX SBOM, cosign signature bundle, and `SHA256SUMS` to each
[GitHub Release](https://github.com/Cracksoldier/glacier-notes-cloud/releases). This is the supported
way to run the released application without building from source:

~~~bash
mkdir -p deployment/secrets
openssl rand -base64 36 > deployment/secrets/database-password.txt
openssl rand -base64 36 > deployment/secrets/bootstrap-token.txt
openssl rand -base64 48 > deployment/secrets/session-secret.txt
chmod 600 deployment/secrets/*.txt
sudo chown 10001:10001 deployment/secrets/*.txt

curl -fLO https://github.com/Cracksoldier/glacier-notes-cloud/releases/download/v0.1.0/compose-v0.1.0.yaml

# Optional but recommended: verify the image was built by this repository's release workflow.
# Requires cosign (https://docs.sigstore.dev/cosign/system_config/installation/).
cosign verify ghcr.io/cracksoldier/glacier-notes-cloud:v0.1.0 \
  --certificate-identity-regexp \
    'https://github.com/Cracksoldier/glacier-notes-cloud/.github/workflows/release.yml@refs/tags/v0.1.0' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com

docker compose -f compose-v0.1.0.yaml up -d
~~~

Open `http://127.0.0.1:8080` and use the generated bootstrap token to create the administrator.
See [docs/UPGRADE.md](docs/UPGRADE.md) for moving between released versions and
[docs/adr/0008-release-and-signing-process.md](docs/adr/0008-release-and-signing-process.md) for the
publication and signing rationale.

## Production-like local environment

Use the following flow when testing the compiled, same-origin application from **source** rather than
from a published release. Copy the non-secret configuration template, create local secrets, then
build and start the complete environment:

~~~bash
cp .env.example .env
mkdir -p deployment/secrets
openssl rand -base64 36 > deployment/secrets/database-password.txt
openssl rand -base64 36 > deployment/secrets/bootstrap-token.txt
openssl rand -base64 48 > deployment/secrets/session-secret.txt
chmod 600 deployment/secrets/*.txt
sudo chown 10001:10001 deployment/secrets/*.txt
~~~

The `chown` is required. Compose bind-mounts these files with their host ownership and the
application container runs unprivileged as uid 10001, so a mode-600 file owned by your host account
is unreadable inside the container and the app refuses to start. Owning them by 10001 keeps the
restrictive mode while granting exactly the one uid that needs them.

The optional TOTP second factor is off by default and needs no secret. To enable it, generate its own
key and point `.env` at it *before* starting Compose — it is deliberately separate from the session
secret, because rotating the session secret only ends sessions whereas rotating this one would
invalidate every stored enrollment:

~~~bash
openssl rand -base64 48 > deployment/secrets/mfa-encryption-secret.txt
chmod 600 deployment/secrets/mfa-encryption-secret.txt
sudo chown 10001:10001 deployment/secrets/mfa-encryption-secret.txt
# in .env: GLACIER_MFA_ENABLED=true
#          GLACIER_MFA_ENCRYPTION_SECRET_FILE=./deployment/secrets/mfa-encryption-secret.txt
~~~

With `.env` and the secrets in place, build and start the environment:

~~~bash
docker compose up --build --wait
~~~

Open `http://127.0.0.1:8080` and use the generated bootstrap token to create the administrator.
The management endpoints are `http://127.0.0.1:9000/q/health/ready` and
`http://127.0.0.1:9000/q/metrics`; keep port 9000 private.

To test administrator-created backups, set `GLACIER_BACKUP_ENABLED=true` and a recognizable
`GLACIER_BUILD_IDENTIFIER` in `.env` before starting Compose. Leave
`GLACIER_BACKUP_DIRECTORY=/var/lib/glacier-notes/backups` at its default unless another writable
container path is intentional; Compose mounts the persistent `backup_data` volume at the configured
path. After bootstrap, open `/admin/backups`. Backup and clean-restore procedures are documented in
[docs/BACKUP_RESTORE.md](docs/BACKUP_RESTORE.md).

Stop the environment without deleting its data by running `docker compose down`. See
[deployment/README.md](deployment/README.md) for all configuration, SMTP, management-interface, and
secret-rotation details. Running `docker compose down --volumes` permanently removes the local
database and application volumes.

## IntelliJ IDEA development setup

### Import and toolchains

1. Open the repository root in IntelliJ IDEA and import the detected root `pom.xml` as a Maven
   project.
2. Set the Project SDK, Maven importer JDK, and Maven runner JRE to JDK 21.
3. Under **Build, Execution, Deployment | Build Tools | Maven**, select **Use Maven wrapper** so the
   IDE uses the repository's pinned Maven version. See IntelliJ's
   [Maven configuration guide](https://www.jetbrains.com/help/idea/maven-support.html) if these
   settings are not visible.
4. Run `./mvnw -pl backend clean generate-sources`, then reload all Maven projects. IntelliJ should
   recognize `backend/target/generated-sources/openapi/src/gen/java` as generated sources.
5. Configure Node.js 24.15 or newer as the project JavaScript runtime, open
   `frontend/package.json`, and run `npm ci`.

Java and Maven support are sufficient for backend development. Frontend IDE integration requires
the JavaScript/TypeScript and Node.js plugins available for your IntelliJ edition; JetBrains
documents the required plugins and runtime under
[Node.js development](https://www.jetbrains.com/help/idea/developing-node-js-applications.html).
The optional Quarkus plugin adds framework-aware inspections. Install the first-party **Biome**
plugin listed in the [Biome editor documentation](https://biomejs.dev/editors/first-party-extensions/)
for frontend formatting and lint feedback; do not configure Prettier.

### Run configurations

Create these configurations under **Run | Edit Configurations**:

- **Backend — Maven:** working directory is the repository root; command line is
  `-pl backend quarkus:dev`. Add environment variables
  `GLACIER_BOOTSTRAP_TOKEN=local-bootstrap-token-development-only-2026` and
  `GLACIER_SECURITY_SESSION_SECRET=local-session-secret-development-only-2026`. To test backups from
  the IDE, also add `GLACIER_BACKUP_ENABLED=true`,
  `GLACIER_BACKUP_DIRECTORY=/tmp/glacier-notes-idea-backups`, and
  `GLACIER_BUILD_IDENTIFIER=intellij-dev`.
- **Frontend — npm:** package file is `frontend/package.json`, command is `run`, and script is
  `start`.
- **Backend debugger — Remote JVM Debug:** attach to `localhost:5005` after starting the backend.
  [Quarkus dev mode](https://quarkus.io/guides/maven-tooling#dev-mode) enables this debug port by
  default without suspending startup.
- **Backend verification — Maven:** working directory is the repository root; command line is
  `verify`.
- **Frontend verification — npm:** create configurations for `check`, `build:production`, and
  `test:ci`; add `test:repository` when validating repository metadata and operational runbooks.

Start Docker before the backend configuration. A Compound configuration can launch **Backend** and
**Frontend** together. If generated Java types appear unresolved, rerun `generate-sources` and use
**Reload All Maven Projects** before manually changing source-root settings.
