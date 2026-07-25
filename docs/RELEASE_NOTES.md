# M12 release candidate notes

This is the release-candidate summary for M12, the final milestone before the first tagged version.
Full per-milestone scope is in `CHANGELOG.md`; acceptance criteria and verification commands are in
`docs/MILESTONE_STATUS.md`.

## What's shipped

Glacier Notes Cloud is a self-hosted, multi-user notes application: owner-scoped notebooks, notes,
checklists, and labels; Markdown editing with sanitized rendering; image attachments across
filesystem, PostgreSQL, or private S3-compatible storage; full-text search; retained note version
history; portable full/notebook/note import and export compatible with a separate desktop
application; account self-service including verified email changes and retained/restorable deletion;
an administration surface covering users, invitations, instance settings, SMTP status, immutable
audit events, and gated backups; and this milestone's security hardening — dependency and container
image scanning, named attack-simulation tests, log-hygiene enforcement, and a documented threat
model.

## Deployment prerequisites

Docker Compose v2 with a Docker-compatible daemon, three independent random secrets (database
password, bootstrap token, session secret), and PostgreSQL 18.3 (provisioned by Compose). See
[`deployment/README.md`](../deployment/README.md) for first-start instructions, configuration
variables, and reverse-proxy/HTTPS guidance. Backups are disabled by default; enabling them and
handling the resulting archives safely is covered in `docs/BACKUP_RESTORE.md`.

## Upgrade guidance

Flyway migrations apply automatically and block startup on failure; there is no separate manual
migration step. Before upgrading a deployment with existing data, take a verified backup (see
`docs/BACKUP_RESTORE.md`) — Flyway migrations do not provide automatic down scripts, so rollback
means restoring the matching prior application version and its backup. `docs/MIGRATIONS.md`
documents the upgrade-path convention this project follows, including the data-integrity tests that
must be extended when future migrations reshape any table they seed.

## Security posture

See `docs/THREAT_MODEL.md` for the STRIDE-organized mitigation inventory and `docs/SECURITY.md` for
how to run the dependency, image, and log-hygiene scans locally and how to report a vulnerability.
`docs/KNOWN_ISSUES.md` tracks the one low-severity, Windows-only development-tooling advisory that
CI currently reports; no backend dependency or container image finding is suppressed as of this
release candidate.

## Known limitations

Live cross-browser/device testing beyond the automated desktop and tablet Playwright projects,
manual load testing, and a live release-candidate sign-off meeting are process activities outside
this repository's automated gates — they are intentionally not represented as code or CI here.
