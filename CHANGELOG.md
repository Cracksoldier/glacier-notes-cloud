# Changelog

All notable changes to Glacier Notes Cloud are recorded here, grouped by milestone. This project has
not yet made a numbered release; entries are milestone-scoped until the first tagged version.

Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Full acceptance
criteria and verification commands per milestone are in `docs/MILESTONE_STATUS.md`.

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
