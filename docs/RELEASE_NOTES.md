# v0.3.1 — Deployable second factor

Patch release on top of v0.3.0. No schema change, no API change, no new configuration. It fixes two
deployment defects that made v0.3.0's headline feature unreachable for an operator who followed the
documentation. The full change summary is in `CHANGELOG.md`; the operator-facing steps are in
`docs/UPGRADE.md`.

## What's fixed

- **The Compose file attached to a release can now enable the second factor.** `compose-v0.3.0.yaml`
  was rendered from a template that had not been updated since v0.1.0, so it carried neither
  `GLACIER_MFA_ENABLED` nor the mount for the encryption secret. Setting them in `.env` had no
  effect, because the container never received them — an operator deploying the released artifact
  had no way to turn on the feature the release was named for. Deploy against `compose-v0.3.1.yaml`.
- **Following the documented secret setup no longer produces a container that never starts.** Every
  operator document prescribed `chmod 600 deployment/secrets/*.txt`, but Compose bind-mounts those
  files with their host ownership and the application runs unprivileged as uid 10001, so the
  container could not read them. The symptom was a bare `sed: … Permission denied` on a restart
  loop. The documentation now pairs the `chmod` with `chown 10001:10001`, keeping the restrictive
  mode while granting the one uid that needs it, and the application explains the problem at startup
  instead of failing opaquely.

## Why this was not caught

Both defects lived in what continuous integration never actually ran. CI writes its secrets at the
runner's permissive default, so the documented permissions were never exercised; and its deployment
job boots the repository Compose file rather than the rendered release template, so drift between
the two was invisible. Both workflows now apply the documented `chmod` and `chown`, and a
repository-contract test fails if the template's environment variables or mounts diverge from
`compose.yaml` again.

## Verification

`./mvnw verify`, `npm run check`, `npm run test:repository`, `npm run test:ci`,
`npm run build:production`, `npm audit --omit=dev --audit-level=high` (0 vulnerabilities), and the
Playwright suite against a Compose deployment. The entrypoint change was verified against the built
image both ways: a mode-600 host-owned secret exits with the explanatory message, and the same file
owned by uid 10001 boots normally.

---

# v0.3.0 — Optional second authentication factor

Feature release on top of v0.2.0, and the first one carrying a breaking API change. Per-milestone
context and acceptance criteria remain in `docs/MILESTONE_STATUS.md`; the full change summary is in
`CHANGELOG.md`.

## What's shipped

- An optional TOTP second factor, off unless the operator sets `GLACIER_MFA_ENABLED=true`. An
  account enrolls from its settings page — password confirmation, a QR code rendered in the browser,
  a manual key for authenticators that cannot scan, and ten single-use recovery codes it must
  acknowledge before continuing — and signs in afterwards in two stages, accepting either an
  authenticator code or a recovery code. Available in English and German. With the flag off neither
  the card nor the second stage appears, and login behaves exactly as it did in v0.2.0.
- Step-up verification on the six operations that could hand an attacker an account: disabling the
  factor, regenerating recovery codes, changing an email address, deleting an account, and an
  administrator deleting another account or minting a password-reset link for one. Verifying opens a
  grace window on that one session, five minutes by default, so a run of operations is not prompted
  repeatedly. An account without an enrollment sees the password check it always saw.
- Two recovery paths for a lost authenticator. An administrator clears another account's factor from
  the admin interface, which ends every session that account holds and mails it that an administrator
  did so; when the locked-out account is the last administrator, a bootstrap-token break-glass
  operation remains. Both are audited; neither ever discloses the secret, the recovery codes, or how
  many are left.
- The four tunables governing the feature — challenge lifetime, challenge attempt limit,
  pending-enrollment window, and step-up grace — on the admin settings page, bounded server-side and
  effective without a restart. They were previously reachable only by editing `instance_settings` by
  hand.
- Notifications for every second-factor event, and all lifecycle mail — invitations, password resets,
  email-change verification, and the new notices — now written in the recipient's language rather
  than always in English.
- `glacier_mfa_*` counters covering recovery codes consumed, challenges abandoned unused, break-glass
  resets, and administrative clears. None carries a per-account label.
- A startup warning when stored enrollments were encrypted under a different
  `GLACIER_MFA_ENCRYPTION_SECRET` than the one now configured. It reports a count only — no
  usernames, no user ids, no key material — so a mistaken secret swap is visible at boot rather than
  at someone's next sign-in.
- Four fixes to paths only a browser exercises: `/settings` and `/verify-email-change` answered `404`
  when opened directly, a stale session cookie made every page unreachable, the admin settings page
  could not save, and every step-up prompt signed the user out instead of asking for a code. See the
  `Fixed` section of `CHANGELOG.md`.

## Breaking change

`POST /api/v1/auth/login` now returns a `LoginOutcome` envelope rather than a bare `SessionContext`:
what used to be the top-level object is now nested under `context`, beside a `result` tag. The
bundled web interface is updated with it, so an upgrade using only the browser needs no action — but
any script, monitoring probe, or integration that logs in against the API must be updated.
`docs/UPGRADE.md` has the before/after payloads and the `jq` translation.
`POST /api/v1/admin/users/{userId}/password-reset` also now requires a JSON body; `{}` suffices for
an administrator without an enrollment. `GET /api/v1/auth/session` is unchanged.

## Non-goals

The non-goals list from v0.1.0 still applies. In addition, and deliberately for this release:
instance-wide enforcement of the second factor is out of scope — enrollment is per-account and
voluntary — and there is no re-encryption tooling for the enrollment secret. Both are recorded in
`GLACIER_NOTES_CLOUD_2FA_SPECIFICATION.md` §9.2 and `docs/THREAT_MODEL.md`.

## Deployment prerequisites

No new *required* environment variables — an instance that leaves the feature off upgrades with no
configuration change. Enabling it requires `GLACIER_MFA_ENABLED=true` together with
`GLACIER_MFA_ENCRYPTION_SECRET` or `GLACIER_MFA_ENCRYPTION_SECRET_FILE`, validated at startup. That
secret is deliberately separate from the session secret, and it becomes part of your backup set: a
database restored without it still contains every enrollment but can decrypt none of them. See
[`docs/BACKUP_RESTORE.md`](BACKUP_RESTORE.md) for the rotation procedure and
[`page/documentation.html`](../page/documentation.html) for the full configuration reference.

Enabling the feature also makes host clock accuracy a correctness dependency — TOTP codes are
derived from wall time and only the current 30-second step and its two neighbours are accepted. Keep
NTP running.

The browser bundle gains one runtime dependency, `uqr`, used to render the enrollment QR code
client-side; it is loaded lazily and only by the two-factor card, so the code never leaves the
browser.

## Upgrade guidance

See [`docs/UPGRADE.md`](UPGRADE.md). Migrations `V13` and `V14` run on startup and are purely
additive — three new empty tables, nullable and defaulted columns, and two widened check constraints.
Nothing is dropped or narrowed, so no operator action is needed beyond the standard image pull and
recreate. Read the breaking change above before upgrading an instance with API clients.

## Verifying this release

The published image is signed and its software bill of materials is attached to the image as a
verifiable attestation (keyless cosign, GitHub OIDC — no key management required):

```bash
cosign verify ghcr.io/cracksoldier/glacier-notes-cloud:v0.3.0 \
  --certificate-identity-regexp \
    'https://github.com/Cracksoldier/glacier-notes-cloud/.github/workflows/release.yml@refs/tags/v0.3.0' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

The GitHub Release for `v0.3.0` also carries a standalone CycloneDX SBOM, its own cosign signature
bundle, the resolved image digest, and a `SHA256SUMS` file covering every downloadable asset.

## Security posture

`docs/THREAT_MODEL.md` gains the assets this feature introduced — the stored enrollment secret, the
recovery codes, and the challenge tokens — each with its exposure and the mitigation in code, along
with the residual risks accepted with it: TOTP is phishable in real time, and loss of the encryption
secret is unrecoverable by design. The Quarkus platform moved to 3.37.4 to clear four Netty CVEs at
CVSS ≥ 7.0. One new dependency-scan suppression is recorded in `docs/KNOWN_ISSUES.md`,
`CVE-2026-56816`, for an HTTP/3 frame codec that exists only on Netty's 4.2.x line and in no artifact
this build resolves.

## Known limitations

`docs/KNOWN_ISSUES.md` continues to track suppressed dependency and image scan findings with their
justification.

# v0.2.0 — Internationalization and admin polish

Feature release on top of v0.1.0. Per-milestone context and acceptance criteria remain in
`docs/MILESTONE_STATUS.md`; per-release change summaries are in `CHANGELOG.md`.

## What's shipped

- Full runtime English/German localization across every user-facing surface: notes shell and editor,
  admin sub-pages (overview, users, invitations, settings, SMTP, audit, status, backups, user
  detail), auth flows (login, password reset, invitation acceptance, email verification), setup, and
  the app shell.
- Problem toasts, admin, auth, and setup strings now flow through `I18nService`, so language changes
  take effect without reloading localized surfaces.
- Admin area redesigned on the global design tokens (`--color-accent`, `--color-surface`,
  `--color-surface-elevated`, `--color-border`, `--color-shadow`, `--color-text*`) so it follows the
  app-wide dark/light theme, with Font Awesome icons on sidebar navigation, status/overview cards,
  and primary action buttons. Introduces primary/secondary/danger button variants and a mobile
  sidebar that collapses to an icon strip.
- Admin `.button-danger` hover regression fixed so destructive actions keep their red foreground on
  hover.

## Non-goals

The non-goals list from v0.1.0 still applies — see the v0.1.0 section below or
`GLACIER_NOTES_CLOUD_MILESTONES_BIOME.md` §8. v0.2.0 does not narrow or widen that list.

## Deployment prerequisites

Unchanged from v0.1.0. See [`deployment/README.md`](../deployment/README.md) for first-start
instructions and [`page/documentation.html`](../page/documentation.html) for the full configuration
reference. No new required environment variables in v0.2.0.

## Upgrade guidance

See [`docs/UPGRADE.md`](UPGRADE.md). v0.2.0 has no schema changes, no new required environment
variables, and no operator-facing configuration changes — the standard image pull + recreate
procedure applies. Flyway runs its usual validation on startup.

## Verifying this release

The published image is signed and its software bill of materials is attached to the image as a
verifiable attestation (keyless cosign, GitHub OIDC — no key management required):

```bash
cosign verify ghcr.io/cracksoldier/glacier-notes-cloud:v0.2.0 \
  --certificate-identity-regexp \
    'https://github.com/Cracksoldier/glacier-notes-cloud/.github/workflows/release.yml@refs/tags/v0.2.0' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

The GitHub Release for `v0.2.0` also carries a standalone CycloneDX SBOM, its own cosign signature
bundle, the resolved image digest, and a `SHA256SUMS` file covering every downloadable asset.

## Security posture

Unchanged from v0.1.0. `docs/THREAT_MODEL.md` and `docs/SECURITY.md` remain the authoritative
references; v0.2.0 adds no new dependency, image, or log-hygiene suppressions.

## Known limitations

Unchanged from v0.1.0. `docs/KNOWN_ISSUES.md` continues to track suppressed dependency/image scan
findings and their justification.

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
