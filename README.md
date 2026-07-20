# Glacier Notes Cloud

Self-hosted, multi-user Glacier Notes web application. The project is an OpenAPI-first
monorepo with a Quarkus backend, Angular frontend, and PostgreSQL persistence layer.

## Prerequisites

- JDK 21 or newer (the build targets Java 21)
- Docker-compatible daemon for PostgreSQL integration tests
- Node.js 24.15 or newer
- npm 11.12.1

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
and editor instructions.

## Docker Compose deployment

The production-oriented image serves the compiled Angular application from Quarkus and runs as an
unprivileged user. Follow [deployment/README.md](deployment/README.md) to create secret files and run:

```bash
docker compose up --build -d
```

The application binds to `127.0.0.1:8080` and its management health endpoint to
`127.0.0.1:9000` by default. PostgreSQL, image, and backup data are stored in named volumes.
