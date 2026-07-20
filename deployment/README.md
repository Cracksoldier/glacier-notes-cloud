# Docker Compose Deployment

The supported deployment runs the compiled Angular application and Quarkus API in one non-root
container, backed by PostgreSQL 18.3. Database, image, and backup data use named volumes.

## First start

Create three independent random secrets. These files are ignored by Git and should be readable only
by the operator:

```bash
mkdir -p deployment/secrets
openssl rand -base64 36 > deployment/secrets/database-password.txt
openssl rand -base64 36 > deployment/secrets/bootstrap-token.txt
openssl rand -base64 48 > deployment/secrets/session-secret.txt
chmod 600 deployment/secrets/*.txt
docker compose up --build -d
docker compose ps
```

Open `http://127.0.0.1:8080`, create the administrator with the bootstrap token, and then retain or
rotate the token file according to your secret-management policy. Keep a file at the configured path
so Compose can recreate the secret mount. Persisted initialization prevents the endpoint from being
enabled again. Readiness is available only on the separately bound management port at
`http://127.0.0.1:9000/q/health/ready`.

Use `docker compose logs app` for startup failures and `docker compose down` to stop the deployment.
Do not use `down --volumes` unless permanent deletion of all application data is intended.

## Configuration

Copy `.env.example` to `.env` to change bind addresses, ports, or secret file locations. Secret files
take precedence over direct bootstrap and session environment values. Secrets must contain 32–512
non-whitespace characters. Argon2id defaults are 19,456 KiB memory, 2 iterations, parallelism 1, a
32-byte hash, and a 16-byte salt.

| Variable | Purpose |
| --- | --- |
| `GLACIER_DATABASE_URL`, `GLACIER_DATABASE_USERNAME` | PostgreSQL JDBC connection |
| `GLACIER_DATABASE_PASSWORD_FILE` | PostgreSQL password file read by the container entrypoint |
| `GLACIER_BOOTSTRAP_TOKEN_FILE` | One-time setup token file; overrides `GLACIER_BOOTSTRAP_TOKEN` |
| `GLACIER_SECURITY_SESSION_SECRET_FILE` | HMAC/session secret file; overrides the direct value |
| `GLACIER_BOOTSTRAP_FAILURE_LIMIT` | Invalid attempts per window; default `5` |
| `GLACIER_BOOTSTRAP_WINDOW_SECONDS`, `GLACIER_BOOTSTRAP_BLOCK_SECONDS` | Rate-limit window and block duration; defaults `900` |
| `GLACIER_PASSWORD_ARGON2_MEMORY_KIB` | Argon2id memory cost; minimum/default `19456` |
| `GLACIER_PASSWORD_ARGON2_ITERATIONS`, `GLACIER_PASSWORD_ARGON2_PARALLELISM` | Argon2id time and parallelism; minimum/default `2` and `1` |
| `GLACIER_PASSWORD_ARGON2_HASH_LENGTH`, `GLACIER_PASSWORD_SALT_LENGTH` | Hash and salt bytes; minimum/default `32` and `16` |

For a reverse proxy, keep the application and management ports on loopback or a private Docker
network, terminate TLS at the proxy, and forward only port 8080. Enable and restrict Quarkus forwarded
header processing with `QUARKUS_HTTP_PROXY_PROXY_ADDRESS_FORWARDING=true` only when the proxy
addresses are trusted. Never publish port 9000 publicly.

Back up the `postgres_data`, `image_data`, and `backup_data` volumes together. Restores must preserve
database and filesystem data from the same point in time.
