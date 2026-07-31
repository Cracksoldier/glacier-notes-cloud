# Upgrading a deployment

This document covers the operator-facing mechanics of moving a running Glacier Notes Cloud
deployment to a newer released version. For how migrations themselves are authored and tested, see
`docs/MIGRATIONS.md`. For creating and restoring backups, see `docs/BACKUP_RESTORE.md`.

## Procedure

1. **Take a verified backup.** Follow `docs/BACKUP_RESTORE.md` to create a backup and confirm its
   manifest checksums validate. Do not proceed on an unverified backup.
2. **Review the new release's notes.** Check the target version's entry below (or its GitHub Release)
   for any version-specific steps beyond the default flow.
3. **Pull the new image tag** and recreate the `app` service:
   ```bash
   docker compose pull app
   docker compose up -d
   ```
   If you deployed from a versioned release Compose file (`compose-vX.Y.Z.yaml`, attached to each
   GitHub Release), download the new version's file and repeat the same commands against it instead.
4. **Flyway migrates automatically** on startup and blocks the application from becoming ready if a
   migration fails — there is no separate manual migration step. Watch `docker compose logs app` and
   `GET /q/health/ready` on the management port until the container reports healthy.
5. **Confirm the reported version** on the admin status page (or `GET /api/v1/admin/status`) matches
   the version you intended to deploy.

## Rollback

Flyway does not provide automatic down scripts (`docs/MIGRATIONS.md`). If an upgrade fails or
introduces a regression, rollback means:

1. Stop the `app` service.
2. Restore the verified backup taken in step 1 above, following `docs/BACKUP_RESTORE.md`.
3. Redeploy the prior image tag.

There is no in-place schema downgrade — restoring the backup is the supported rollback path.

## Version history

### Unreleased

Contains a breaking API change to `POST /api/v1/auth/login`. The bundled web interface is updated
with it, so an upgrade using only the browser needs no action beyond the procedure above. No new
required environment variables and no schema changes — Flyway runs its usual validation on startup.

If you have a script, monitoring probe, or integration that logs in against the API, it must be
updated. The endpoint previously returned the session object directly:

```json
{"user": {"id": "…", "username": "…", "role": "ADMIN"}, "session": {"current": true}}
```

It now returns that object nested under `context`, inside an envelope with a `result` tag:

```json
{"result": "SESSION", "context": {"user": {"id": "…", "username": "…", "role": "ADMIN"}, "session": {"current": true}}}
```

Concretely, a `jq` expression reading `.user.username` becomes `.context.user.username`. The status
code, the `Set-Cookie` headers, and the `401`/`429` error responses are unchanged, so a script that
only checks the status code or extracts the `GLACIER_SESSION` cookie needs no change.
`GET /api/v1/auth/session` is unchanged and still returns the object in its previous shape.

### v0.2.0

Feature release. Ships full English/German runtime localization across every user-facing surface
and a design-token refresh of the admin area with Font Awesome icons. No new required environment
variables, no schema changes, and no operator-facing configuration changes — the default upgrade
procedure above applies unchanged. Flyway runs its usual validation on startup.

### v0.1.0

Initial release. No prior version to upgrade from.
