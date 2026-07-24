# Backup and Restore Runbook

Server backups are disabled by default. Set `GLACIER_BACKUP_ENABLED=true`, recreate the application
container, and use **Administration → Backups** to start a job. The resulting ZIP is written only to
the `backup_data` volume at `/var/lib/glacier-notes/backups`; the web application never provides
filesystem browsing or backup downloads.

## Protect the backup

A backup contains the PostgreSQL database, including user content and authentication hashes, plus
image objects, non-secret instance settings, a manifest, version information, timestamps, and
SHA-256 checksums. It intentionally excludes database credentials, SMTP passwords, S3 credentials,
bootstrap tokens, and cryptographic keys. Treat it as highly sensitive:

- copy completed archives to encrypted, access-controlled storage;
- encrypt archives before moving them outside the host;
- restrict both the backup volume and external copies to operators;
- retain an independent copy of the deployment secrets needed by the restored instance.

Copy an archive from Compose and verify its recorded archive checksum against the admin dashboard:

```bash
docker compose cp app:/var/lib/glacier-notes/backups/glacier-notes-BACKUP_ID.zip ./backup.zip
sha256sum backup.zip
unzip -p backup.zip manifest.json | jq .
```

Extract into an empty directory and verify every manifest entry before restoring:

```bash
mkdir restore
unzip backup.zip -d restore
jq -r '.checksums | to_entries[] | [.value, .key] | @tsv' restore/manifest.json |
  while IFS="$(printf '\t')" read -r expected path; do
    printf '%s  %s\n' "$expected" "restore/$path" | sha256sum --check -
  done
```

## Restore a clean Compose environment

Stop Glacier Notes and confirm that the target is a new, empty deployment. Never mix a dump with an
existing database or image volume. Preserve the original archive until validation is complete.

1. Create the three deployment secret files and start only PostgreSQL:

   ```bash
   docker compose up -d postgres
   docker compose exec -T postgres pg_isready -U glacier_notes -d glacier_notes
   ```

2. Restore the database:

   ```bash
   docker compose cp restore/database.dump postgres:/tmp/glacier-database.dump
   docker compose exec -T postgres pg_restore --clean --if-exists --no-owner --no-privileges \
     -U glacier_notes -d glacier_notes /tmp/glacier-database.dump
   docker compose exec -T postgres rm /tmp/glacier-database.dump
   ```

3. For the `FILESYSTEM` backend, copy `restore/images/` into a clean `image_data` volume while
   preserving the paths below `images/`. PostgreSQL-backed image bytes are already in the dump. For
   S3, upload the verified objects to the configured private bucket before starting Glacier Notes.

4. Configure the same `GLACIER_IMAGE_BACKEND`, supply new or restored deployment secrets, then start
   and validate the application:

   ```bash
   docker compose up -d app
   curl --fail http://127.0.0.1:9000/q/health/ready
   curl --fail http://127.0.0.1:8080/api/v1/setup/status
   ```

Sign in, inspect representative notes and images, and test an export before retiring the old
environment. The CI deployment gate creates an enabled backup, verifies its manifest checksums, and
restores its dump into a separate clean PostgreSQL container.
