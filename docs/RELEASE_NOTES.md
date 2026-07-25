# v0.1.0 — Version 1 release notes

This is the first tagged release of Glacier Notes Cloud (M13). Full per-milestone scope is in
`CHANGELOG.md`; acceptance criteria and verification commands are in `docs/MILESTONE_STATUS.md`.

## What's shipped

Glacier Notes Cloud is a self-hosted, multi-user notes application: owner-scoped notebooks, notes,
checklists, and labels; Markdown editing with sanitized rendering; image attachments across
filesystem, PostgreSQL, or private S3-compatible storage; full-text search; retained note version
history; portable full/notebook/note import and export compatible with a separate desktop
application; account self-service including verified email changes and retained/restorable deletion;
an administration surface covering users, invitations, instance settings, SMTP status, immutable
audit events, and gated backups; and M12's security hardening — dependency and container image
scanning, named attack-simulation tests, log-hygiene enforcement, and a documented threat model.

## Non-goals

The following are explicitly out of scope for v1 (`GLACIER_NOTES_CLOUD_MILESTONES_BIOME.md` §8) and
absent by design, not by omission:

- Offline PWA editing
- Real-time collaboration
- Shared notes or notebooks
- Public links
- OIDC, LDAP, passkeys, or MFA
- Animated GIF support
- Automatic image-backend migration
- Kubernetes support
- Official horizontal scaling
- Virus scanning
- Application-managed backup encryption
- Native mobile applications
- Desktop synchronization itself (v1 provides groundwork for it, not a partial implementation)

## Deployment prerequisites

Docker Compose v2 with a Docker-compatible daemon, three independent random secrets (database
password, bootstrap token, session secret), and PostgreSQL 18.3 (provisioned by Compose). See
[`deployment/README.md`](../deployment/README.md) for first-start instructions and reverse-proxy/HTTPS
guidance, and [`page/documentation.html`](../page/documentation.html) for the full configuration
reference. Backups are disabled by default; enabling them and handling the resulting archives safely
is covered in `docs/BACKUP_RESTORE.md`.

## Upgrade guidance

See [`docs/UPGRADE.md`](UPGRADE.md) for the operator-facing upgrade procedure and rollback approach.
As the initial release, v0.1.0 has no prior version to upgrade from.

## Verifying this release

The published image is signed and its software bill of materials is attached to the image as a
verifiable attestation (keyless cosign, GitHub OIDC — no key management required):

```bash
cosign verify ghcr.io/cracksoldier/glacier-notes-cloud:v0.1.0 \
  --certificate-identity-regexp \
    'https://github.com/Cracksoldier/glacier-notes-cloud/.github/workflows/release.yml@refs/tags/v0.1.0' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

The GitHub Release for `v0.1.0` also carries a standalone CycloneDX SBOM, its own cosign signature
bundle, the resolved image digest, and a `SHA256SUMS` file covering every downloadable asset.

## Security posture

See `docs/THREAT_MODEL.md` for the STRIDE-organized mitigation inventory and `docs/SECURITY.md` for
how to run the dependency, image, and log-hygiene scans locally and how to report a vulnerability.

## Known limitations

`docs/KNOWN_ISSUES.md` tracks the current suppressed dependency/image scan findings and their
justification. Live cross-browser/device testing beyond the automated desktop and tablet Playwright
projects, and manual load testing, are process activities outside this repository's automated gates —
they are intentionally not represented as code or CI here.
