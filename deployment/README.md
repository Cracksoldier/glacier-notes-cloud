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

Set `GLACIER_PUBLIC_BASE_URL` to the externally visible origin. When it starts with `https://`,
authentication and CSRF cookies receive the `Secure` flag. A value stored later in instance settings
takes precedence. The supported browser deployment is same-origin behind a reverse proxy; cookies
use `SameSite=Lax` and `Path=/`.

| Variable | Purpose |
| --- | --- |
| `GLACIER_DATABASE_URL`, `GLACIER_DATABASE_USERNAME` | PostgreSQL JDBC connection |
| `GLACIER_DATABASE_PASSWORD_FILE` | PostgreSQL password file read by the container entrypoint |
| `GLACIER_BOOTSTRAP_TOKEN_FILE` | One-time setup token file; overrides `GLACIER_BOOTSTRAP_TOKEN` |
| `GLACIER_SECURITY_SESSION_SECRET_FILE` | HMAC/session secret file; overrides the direct value |
| `GLACIER_PUBLIC_BASE_URL` | External origin used for secure-cookie policy; default `http://localhost:8080` |
| `GLACIER_BOOTSTRAP_FAILURE_LIMIT` | Invalid attempts per window; default `5` |
| `GLACIER_BOOTSTRAP_WINDOW_SECONDS`, `GLACIER_BOOTSTRAP_BLOCK_SECONDS` | Rate-limit window and block duration; defaults `900` |
| `GLACIER_PASSWORD_ARGON2_MEMORY_KIB` | Argon2id memory cost; minimum/default `19456` |
| `GLACIER_PASSWORD_ARGON2_ITERATIONS`, `GLACIER_PASSWORD_ARGON2_PARALLELISM` | Argon2id time and parallelism; minimum/default `2` and `1` |
| `GLACIER_PASSWORD_ARGON2_HASH_LENGTH`, `GLACIER_PASSWORD_SALT_LENGTH` | Hash and salt bytes; minimum/default `32` and `16` |
| `GLACIER_SMTP_ENABLED` | Enables invitation and password-reset email; default `false` |
| `GLACIER_SMTP_HOST`, `GLACIER_SMTP_PORT` | SMTP endpoint; defaults `localhost:587` |
| `GLACIER_SMTP_START_TLS`, `GLACIER_SMTP_TLS` | STARTTLS mode and implicit TLS switch |
| `GLACIER_SMTP_USERNAME`, `GLACIER_SMTP_PASSWORD_FILE` | SMTP login name and host path to its password file |
| `GLACIER_SMTP_SENDER_NAME`, `GLACIER_SMTP_SENDER_ADDRESS` | Display name and envelope sender used by lifecycle email |
| `GLACIER_IMAGE_BACKEND` | `FILESYSTEM` (default), `POSTGRESQL`, or `S3`; immutable after the first upload |
| `GLACIER_IMAGE_FILESYSTEM_ROOT` | Filesystem object root; Compose uses the persistent `image_data` volume |
| `GLACIER_S3_ENDPOINT`, `GLACIER_S3_REGION`, `GLACIER_S3_BUCKET` | Private S3-compatible endpoint and bucket configuration |
| `GLACIER_S3_PATH_STYLE` | Force path-style addressing for MinIO and compatible providers |
| `GLACIER_S3_ACCESS_KEY_FILE`, `GLACIER_S3_SECRET_KEY_FILE` | Read-only credential files for S3 storage |
| `GLACIER_S3_SERVER_SIDE_ENCRYPTION` | Optional `AES256` or `aws:kms` object encryption setting |
| `GLACIER_S3_API_CALL_TIMEOUT_SECONDS`, `GLACIER_S3_API_CALL_ATTEMPT_TIMEOUT_SECONDS` | Total and per-attempt S3 timeouts; defaults `30` and `10` |
| `GLACIER_TRANSFER_MAXIMUM_UPLOAD_BYTES` | Maximum portable import upload; default 1.5 GiB |
| `GLACIER_TRANSFER_MAXIMUM_DECODED_IMAGE_BYTES` | Maximum combined decoded image data per import; default 1 GiB |
| `GLACIER_TRANSFER_RETENTION_HOURS` | Lifetime of staged imports and downloadable exports; default 24 hours |
| `GLACIER_HTTP_DEFAULT_MAXIMUM_BODY_BYTES` | Maximum ordinary API body; default 10 MiB |
| `GLACIER_HTTP_MULTIPART_OVERHEAD_BYTES` | Multipart envelope allowance added to image and transfer file limits; default 1 MiB |
| `GLACIER_HTTP_ABSOLUTE_MAXIMUM_BODY_BYTES` | Server hard ceiling; default 1537 MiB |

Image uploads accept PNG, JPEG, and WebP and are normalized before storage. The administration UI
controls the stored-image limit, per-user quota, accepted types, and orphan grace period. The raw
processing envelope remains 40 MB and 40 megapixels. S3 objects must be private; Glacier streams
authorized objects and does not use public or presigned URLs. S3 call and attempt timeouts must be
positive, and the attempt timeout cannot exceed the total timeout.

Ordinary API requests are limited to 10 MiB. Image and portable-import routes receive their
configured file limit plus the multipart overhead allowance. The absolute HTTP ceiling must cover
all three route limits; startup fails if it is smaller. When increasing the portable-import limit,
increase `GLACIER_HTTP_ABSOLUTE_MAXIMUM_BODY_BYTES` by at least the configured overhead as well.

The selected backend is recorded in PostgreSQL. Once an image exists, changing
`GLACIER_IMAGE_BACKEND` intentionally prevents startup because M7 does not provide backend
migration. Back up image objects and PostgreSQL metadata from the same point in time.

For disposable local S3-compatible testing, start the MinIO overlay before any image has been
uploaded to another backend:

```bash
openssl rand -hex 16 > deployment/secrets/s3-access-key.txt
openssl rand -base64 36 > deployment/secrets/s3-secret-key.txt
chmod 600 deployment/secrets/s3-*.txt
docker compose -f compose.yaml -f compose.minio.yaml up --build -d
```

The overlay creates a private bucket and exposes the MinIO API on `127.0.0.1:9002` and console on
`127.0.0.1:9003`. Credentials are read only from the mounted secret files. Set
`GLACIER_S3_ACCESS_KEY_FILE` and `GLACIER_S3_SECRET_KEY_FILE` in `.env` to use different host paths.

SMTP is optional. With SMTP disabled, administrators receive copyable invitation and password-reset
links in the dashboard; user-requested password resets remain neutral no-ops. To enable delivery,
create a password file outside the repository, set its host path in `GLACIER_SMTP_PASSWORD_FILE`, and
set `GLACIER_SMTP_ENABLED=true` plus the SMTP variables above. The password is bind-mounted read-only
and is not exposed through the administration API. Username and password must either both be set or
both be absent; unauthenticated SMTP remains supported. Password-file spaces and tabs are preserved,
while its terminal line ending is removed. Ensure `GLACIER_PUBLIC_BASE_URL` is the browser origin
users can reach before issuing links.

For a reverse proxy, keep the application and management ports on loopback or a private Docker
network, terminate TLS at the proxy, and forward only port 8080. Enable and restrict Quarkus forwarded
header processing with `QUARKUS_HTTP_PROXY_PROXY_ADDRESS_FORWARDING=true` only when the proxy
addresses are trusted. Never publish port 9000 publicly.

The `transfer_data` volume contains short-lived uploads and generated exports. It is not a backup:
jobs expire automatically, and successful imports remove their upload. Restrict the volume like user
content because files may contain complete note libraries.

Back up the `postgres_data`, `image_data`, and `backup_data` volumes together. Restores must preserve
database and filesystem data from the same point in time.
