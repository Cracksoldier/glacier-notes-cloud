# Changelog

All notable changes to Glacier Notes Cloud are recorded here, grouped by milestone. Entries before
v0.1.0 are milestone-scoped, since the project had not yet made a numbered release.

Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Full acceptance
criteria and verification commands per milestone are in `docs/MILESTONE_STATUS.md`.

## [Unreleased]

### Added

- Groundwork for the optional TOTP second factor: migration `V13` adds `user_mfa_totp`,
  `user_mfa_recovery_codes`, and `mfa_challenges`, a `user_sessions.second_factor_verified_at`
  column, four bounded `instance_settings` tunables (challenge lifetime, attempt cap,
  pending-enrollment expiry, step-up grace window), and an `MFA_IP` rate-limit scope. No endpoint
  reads or writes these tables yet and login behavior is unchanged.
- Optional TOTP second factor, reachable through the API and off unless `GLACIER_MFA_ENABLED` is
  set. An account can enroll (`POST /api/v1/me/mfa/totp`), confirm with a code from its
  authenticator app and receive ten single-use recovery codes
  (`POST /api/v1/me/mfa/totp/confirm`), inspect its state (`GET /api/v1/me/mfa`), abandon a pending
  enrollment (`DELETE /api/v1/me/mfa/totp/pending`), regenerate recovery codes
  (`POST /api/v1/me/mfa/recovery-codes`), and turn the factor off
  (`POST /api/v1/me/mfa/totp/disable`). Enrolled accounts complete login in two stages, the second
  being `POST /api/v1/auth/login/mfa`.
- A browser interface for the second factor. Account settings gains a two-factor card that walks
  through enrollment — password confirmation, a QR code rendered in the browser, a manual key for
  authenticators that cannot scan, and the recovery codes, which cannot be dismissed until they are
  acknowledged — reports how many recovery codes are left, and turns the factor off again. Signing
  in with an enrolled account now shows a second stage on the same page, accepting either a code
  from the authenticator app or a recovery code. The flow is available in English and German, and
  the card stays hidden on instances where the feature is off.
- `available` on `GET /api/v1/me/mfa`, reporting whether the instance permits enrollment at all.
  Without it a disabled instance is indistinguishable from an account that simply has not enrolled,
  and the settings card would offer a button that could only fail.
- `POST /api/v1/setup/second-factor-reset`, a break-glass operation authenticated with the
  bootstrap token that clears an account's second factor and revokes its sessions when its
  authenticator is lost. It answers `204` whether or not the account existed, so it cannot be used
  to enumerate accounts, and it is throttled by the same limiter as initial setup.
- Step-up verification on the operations that could hand an attacker an account. An enrolled
  account must supply a fresh authenticator or recovery code alongside its password to disable the
  factor, regenerate its recovery codes, change its email address, or delete itself, and an enrolled
  administrator must do the same to delete another account or to mint a password-reset link for one.
  A request that omits the code is answered `401 AUTH_MFA_STEP_UP_REQUIRED`; the browser prompts
  arrive in the next milestone. An account without an enrollment sees the password check it always
  saw. Verifying opens a grace window on that one session — `mfa_step_up_grace_minutes`, five
  minutes by default and disabled by `0` — so a run of operations is not prompted repeatedly.
  Migration `V14` adds the `STEP_UP_USER` and `STEP_UP_IP` rate-limit scopes.
- Notifications for every second-factor event: enrollment started, factor enabled, factor disabled,
  recovery codes regenerated, a recovery code spent on a login, and an operator clearing the factor.
  They carry the time and the coarse device description already kept for the session list, never a
  secret, a code, a link, or how many recovery codes are left, and they are sent after the operation
  commits, so a mail server that is down cannot undo a security change.
- `GLACIER_MFA_ENABLED` (default `false`) and `GLACIER_MFA_ENCRYPTION_SECRET`
  / `GLACIER_MFA_ENCRYPTION_SECRET_FILE`. The secret is validated at startup only when the flag is
  enabled, so existing deployments upgrade without new configuration. It is kept separate from the
  session secret so that rotating session keys cannot invalidate stored enrollments.

### Changed

- **Breaking:** `POST /api/v1/admin/users/{userId}/password-reset` now requires a JSON body. It
  carries the administrator's own `currentPassword` and, when they have enrolled a second factor,
  their `code`; both fields are optional within it, so `{}` remains a valid request for an
  administrator without an enrollment. `AdminDeletionRequest` gains the same two optional fields.
  An enrolled administrator who sends neither is answered `401 AUTH_STEP_UP_PASSWORD_REQUIRED`
  rather than being told the password was wrong.
- All lifecycle mail — invitations, password resets, email-change verification, and the new
  second-factor notices — is now written in the recipient's language, taken from their account
  setting and falling back to the instance default for recipients who have no account yet.
- Enabling the second factor, disabling it, and regenerating recovery codes now end every other
  session of that account, since none of them ever proved possession of the factor.
- **Breaking:** `POST /api/v1/auth/login` now returns a `LoginOutcome` envelope instead of a
  `SessionContext`. A successful login responds with `{"result": "SESSION", "context": {…}}`, where
  `context` is the object the endpoint previously returned at the top level — a field previously read
  as `user` is now read as `context.user`, and `session` as `context.session`. This affects every
  caller regardless of whether the account uses a second factor.
  `GET /api/v1/auth/session` is unchanged and still returns a bare `SessionContext`.
  An account that has enrolled a second factor now receives `{"result": "MFA_REQUIRED",
  "challenge": {…}}` and no cookies until it completes `POST /api/v1/auth/login/mfa`; an account
  without one is unaffected. See `docs/adr/0009-optional-second-authentication-factor.md`.

## v0.2.0 — Internationalization and admin polish

### Added

- Full runtime English/German localization across the notes shell and editor, admin sub-pages
  (overview, users, invitations, settings, SMTP, audit, status, backups, user detail), auth flows
  (login, password reset, invitation acceptance, email verification), setup, and the app shell.
- `I18nService` now backs problem toasts and every admin, auth, and setup string, so language changes
  no longer require a reload of localized surfaces.
- Admin area redesigned on the global design tokens (`--color-accent`, `--color-surface`,
  `--color-surface-elevated`, `--color-border`, `--color-shadow`, `--color-text*`) so it follows the
  app-wide dark/light theme, with Font Awesome icons on sidebar navigation, status/overview cards,
  and primary action buttons. Adds primary/secondary/danger button variants and a mobile sidebar
  that collapses to an icon strip.

### Fixed

- Preserved the red foreground on `.button-danger` hover by excluding it from the
  `button[type="button"]:not(.button-primary)` catch-all in the admin stylesheet.

### Docs

- Early-development disclaimer added to the README and landing page.
- Documented the published-release install path in the README and landing page.
- Marked M13 complete in `docs/MILESTONE_STATUS.md` after the v0.1.0 release.

## v0.1.0 — M13: Version 1 release

### Added

- Tag-triggered release workflow (`.github/workflows/release.yml`) publishing a signed, immutable
  image to `ghcr.io/cracksoldier/glacier-notes-cloud`.
- CycloneDX software bill of materials (Syft) generated against the published image and attached as
  a signed cosign attestation, alongside a standalone signed SBOM file and `SHA256SUMS`.
- Keyless image signing via cosign/GitHub OIDC — see `docs/adr/0008-release-and-signing-process.md`.
- A versioned, immutable-image Docker Compose release artifact
  (`deployment/docker/compose.release-template.yaml`, rendered per release).
- `docs/UPGRADE.md`, the operator-facing upgrade and rollback procedure.
- GitHub issue and pull request templates.
- Finalized `docs/RELEASE_NOTES.md` for the v0.1.0 release, including an explicit non-goals section.

## M12 — Security hardening and release candidate

### Added

- Backend dependency vulnerability scanning (`org.owasp:dependency-check-maven`, CVSS ≥ 7.0 blocks
  CI) with a documented suppression workflow.
- Application container image vulnerability and secret scanning (Trivy), plus OCI image labels
  (`org.opencontainers.image.*`) carrying the git revision, application version, and build date.
- Named security-attack-simulation tests: session fixation, session token replay after logout,
  session token replay after forced revocation, and storage-key path traversal against both the
  image store and the backup archive builder.
- CI-enforced log-hygiene rule preventing note content, checklist text, filenames, passwords, or
  tokens from reaching a `LOG.*` call site.
- A broader upgrade/migration data-integrity test seeding every content, ownership, and audit table
  and asserting referential integrity across a full schema upgrade.
- `docs/THREAT_MODEL.md`, `docs/SECURITY.md`, `docs/DESKTOP_COMPATIBILITY.md`,
  `docs/KNOWN_ISSUES.md`, `docs/RELEASE_NOTES.md`, and this changelog.

## M11 — Administration, audit, and backups

Operational administration overview; complete non-secret instance settings with atomic validation;
persisted instance logos; SMTP status and test delivery; immutable, filterable audit events with
CSV/JSON export; structured production request logs; management-port health and Prometheus metrics;
database-leased cleanup jobs; environment-gated background backups with checksummed archives.

## M10 — Account self-service and preferences

Profile and username updates; current-password-protected password and verified-email changes;
password history and configurable common-password checks; immediate self-deletion; retained,
restorable, and explicitly confirmed administrative deletion; synchronized theme, language,
checklist-order, and trash-retention preferences; scheduled account/trash cleanup; runtime English/
German localization; local Markdown note sharing via `mailto:`.

## M9 — Portable import and export

Asynchronous full, notebook, and note exports; streamed `.glacier.json` generation with base64
images; bounded import inspection; quota estimates; preserve, add-as-copies, and replace-by-ID
conflict strategies; cancellation and expiry cleanup; administrator-controlled user exports; blind
administrative import limited to counts and structural errors.

## M8 — Search, conflicts, and version history

PostgreSQL `simple` full-text search across titles, Markdown, and checklist text with owned filters
and ranked cursor pagination; conflict-safe editing that returns current server state without
overwriting it; retained note version snapshots with preview and restore; administrator-configurable
version-count and retention cleanup.

## M7 — Image handling

Signature-based PNG, JPEG, and WebP processing; normalized full-size and thumbnail assets;
owner-scoped streaming APIs; note image references; per-user quotas; delayed orphan cleanup;
interchangeable filesystem, PostgreSQL, and private S3-compatible storage backends.

## M6 — Notes web UI

Desktop-aligned Angular notes UI: routed notebook, label, archive, and trash views; cursor
pagination; debounced optimistic autosave with retained drafts and explicit conflict handling;
sanitized Markdown rendering; responsive desktop and tablet Playwright coverage.

## M5 — Core content APIs

Owner-scoped notebooks, notes, checklists, and labels; archive and trash with content-free
tombstones; optimistic versioning; stable keyset pagination; safe cross-owner not-found behavior.

## M4 — Accounts and lifecycle

Invitations, user lifecycle administration, password resets, instance settings, and email delivery
with a copyable-link fallback when SMTP is unavailable.

## M3 — Authentication and session security

Opaque, keyed-hash session tokens; session-bound double-submit CSRF protection; progressive login
throttling; security response headers.

## M2 — Deployment foundation

Secure container deployment, bootstrap flow, secrets handling, and operational health checks.

## M1 — Domain schema

PostgreSQL domain schema with owner-aware composite keys, Flyway-owned migrations, and
persistence-layer boundaries.

## M0 — Project foundations

Monorepo layout, Quarkus/Angular foundations, OpenAPI-first contract generation, and CI conventions.
