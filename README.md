# Glacier Notes Cloud

Self-hosted, multi-user Glacier Notes web application. The project is an OpenAPI-first
monorepo with a Quarkus backend, Angular frontend, and PostgreSQL persistence layer.

## Prerequisites

- JDK 21 or newer (the build targets Java 21)
- Docker-compatible daemon for PostgreSQL development and integration tests
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
readiness endpoint is available at `http://localhost:9000/q/health/ready`.

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
npm run check
npm run build:production
npm run test:ci
```

`./mvnw -pl backend generate-sources` regenerates the Java contract and committed
Angular client from `openapi/glacier-notes-v1.yaml`. Generated files must not be edited.

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for local startup, database, generation,
and editor instructions. Implemented roadmap scope is tracked in
[docs/MILESTONE_STATUS.md](docs/MILESTONE_STATUS.md).

## Production-like local environment

Use Docker Compose when testing the compiled, same-origin application rather than live development
servers. Create local secrets, then build and start the complete environment:

~~~bash
mkdir -p deployment/secrets
openssl rand -base64 36 > deployment/secrets/database-password.txt
openssl rand -base64 36 > deployment/secrets/bootstrap-token.txt
openssl rand -base64 48 > deployment/secrets/session-secret.txt
chmod 600 deployment/secrets/*.txt
docker compose up --build --wait
~~~

Open `http://127.0.0.1:8080` and use the generated bootstrap token to create the administrator.
The management endpoint is `http://127.0.0.1:9000/q/health/ready`. Stop the environment without
deleting its data by running `docker compose down`. See
[deployment/README.md](deployment/README.md) for configuration, SMTP, secret rotation, and backup
instructions. Running `docker compose down --volumes` permanently removes the local database and
application volumes.

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
  `GLACIER_SECURITY_SESSION_SECRET=local-session-secret-development-only-2026`.
- **Frontend — npm:** package file is `frontend/package.json`, command is `run`, and script is
  `start`.
- **Backend debugger — Remote JVM Debug:** attach to `localhost:5005` after starting the backend.
  [Quarkus dev mode](https://quarkus.io/guides/maven-tooling#dev-mode) enables this debug port by
  default without suspending startup.
- **Backend verification — Maven:** working directory is the repository root; command line is
  `verify`.
- **Frontend verification — npm:** create configurations for `check`, `build:production`, and
  `test:ci`.

Start Docker before the backend configuration. A Compound configuration can launch **Backend** and
**Frontend** together. If generated Java types appear unresolved, rerun `generate-sources` and use
**Reload All Maven Projects** before manually changing source-root settings.
