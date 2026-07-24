# Milestone Status

This file records implemented milestone scope. Acceptance criteria remain defined in
*GLACIER_NOTES_CLOUD_MILESTONES_BIOME.md*; a milestone is marked complete here only after its
implementation and repository verification gates pass.

| Milestone | Status | Delivered scope |
| --- | --- | --- |
| M0 | Complete | Monorepo, Quarkus/Angular foundations, OpenAPI generation, and CI conventions |
| M1 | Complete | PostgreSQL domain schema, owner-aware keys, migrations, and persistence boundaries |
| M2 | Complete | Secure container deployment, bootstrap flow, secrets, and operational health checks |
| M3 | Complete | Session authentication, CSRF protection, login throttling, and security headers |
| M4 | Complete | Invitations, user lifecycle administration, password resets, settings, and email delivery |
| M5 | Complete | Owner-scoped notebooks, notes, checklists, labels, archive, trash, conversion, and pagination APIs |
| M6 | Complete | Desktop-aligned Angular notes UI, secure Markdown, checklists, autosave, conflicts, themes, shortcuts, and responsive browser tests |
| M7 | Complete | Secure image processing, owner-scoped references, quotas, thumbnails, garbage collection, and filesystem/PostgreSQL/S3 storage |
| M8 | Complete | Ranked owner-scoped PostgreSQL search, conflict-safe editing, retained note versions, restore, and cleanup policies |
| M9 | Complete | Portable full/notebook/note transfer, desktop compatibility, conflict strategies, bounded jobs, and blind administrative import |
| M10 | Complete | Account self-service, verified email changes, deletion retention, synchronized preferences, i18n, themes, and external email sharing |
| M11 | Complete | Administrative overview, safe settings, SMTP status, immutable audit, metrics, coordinated cleanup, and gated full backups |
| M12–M13 | Pending | Not yet implemented |

## M5 Verification

M5 includes the canonical OpenAPI contract and generated Angular client, Flyway V5 constraints and
indexes, transactional content services, optimistic versions, stable keyset cursors, safe
cross-owner not-found behavior, and content-free tombstones retained for 30 days.

Verify the current milestone state with:

~~~bash
./mvnw verify
cd frontend
npm run check
npm run build:production
npm run test:ci
~~~

## M6 Verification

M6 uses the generated M5 client through a cloud data-access layer. It includes routed notebook,
label, archive, and trash views; cursor pagination; a desktop-aligned responsive shell; debounced
optimistic autosave; retained drafts and explicit conflict handling; sanitized Markdown; and
Playwright coverage against the supported Compose deployment.

The standard frontend gates run component tests. With a disposable initialized Compose instance,
also run:

~~~bash
cd frontend
GLACIER_E2E_USERNAME=your-user GLACIER_E2E_PASSWORD=your-password npm run test:e2e
~~~

## M7 Verification

M7 adds signature-based PNG, JPEG, and WebP processing; normalized full-size and thumbnail assets;
owner-scoped streaming APIs; note references; per-user quotas; delayed orphan cleanup; and immutable
filesystem, PostgreSQL, or private S3-compatible storage selection. The note editor supports file
selection, drop, clipboard paste, progress, galleries, cards, and full-size viewing. Backend
integration tests run the same image flow against all three storage implementations, including a
disposable MinIO instance for S3 compatibility.

The standard backend and frontend gates above verify M7. Validate both deployment definitions with:

~~~bash
docker compose config --quiet
docker compose -f compose.yaml -f compose.minio.yaml config --quiet
~~~

## M8 Verification

M8 adds PostgreSQL `simple` full-text search across titles, Markdown, and checklist text with owned
filters, ranked cursor pagination, and explicit active/trash scope. Note conflicts return the current
version and timestamp without overwriting server state; the editor can copy the local draft, reload
the server copy, or explicitly overwrite. Semantic note snapshots support preview and restore while
retaining referenced images. Hourly cleanup enforces the administrator-configurable 20-version and
30-day defaults.

The standard backend and frontend gates above cover search isolation and ranking, checklist index
maintenance, two-session conflicts, snapshot deduplication and restore, cleanup boundaries, editor
conflict actions, and search pagination.

## M9 Verification

M9 adds asynchronous full, notebook, and note exports; streamed `.glacier.json` generation with
base64 images; bounded import inspection; quota estimates; preserve, add-as-copies, and replace-by-ID
strategies; cancellation and expiry cleanup; and administrator-controlled user exports. Blind
administrative imports expose counts and structural errors only, assign content to the selected
user, and write an audit event when applied.

Backend integration tests cover ownership isolation, all export scopes, desktop fixture imports,
round trips, image references, conflict strategies, disabled exports, and the blind-admin boundary.
The frontend production build type-checks the transfer dialog and administration controls. The
standard gates above plus both Compose validation commands verify the milestone; the optional
Playwright suite includes the browser import/export flow.

## M10 Verification

M10 adds profile and username updates; current-password-protected password and verified-email
changes; password history and configurable common-password checks; immediate self-deletion; retained,
restorable, and explicitly confirmed administrative deletion; synchronized theme, language,
checklist-order, and trash-retention preferences; and scheduled account/trash cleanup. The browser
uses English or German at runtime and composes note sharing locally through `mailto:` with Markdown
checklists, image warnings, URL-length warnings, and a Markdown export alternative.

Backend integration tests cover session revocation, password reuse, retained deletion restoration,
destructive deletion, last-administrator protection, migration constraints, and existing lifecycle
boundaries. The frontend gates type-check the account, policy, localized-date, checklist-order, and
sharing surfaces. Run the standard backend and frontend verification commands above.

## M11 Verification

M11 adds an operational administration overview; complete non-secret instance settings with atomic
validation; persisted instance logos; SMTP status and test delivery; immutable, filterable audit
events with CSV/JSON export; structured production request logs; management-port health and
Prometheus metrics; database-leased cleanup jobs; and environment-gated background backups.

Backend tests cover settings safety and audit metadata, disabled backup behavior, public logos, and
exclusive job leases. Frontend tests cover the settings load boundary, normalized audit display, and
credential-free SMTP testing. The deployment CI gate verifies readiness and metrics, creates a real
backup, validates archive and entry checksums, and restores the dump into a clean PostgreSQL
container. Run the standard gates plus:

~~~bash
docker compose config --quiet
GLACIER_BACKUP_ENABLED=true docker compose up --build --wait
~~~

Backup sensitivity, encryption requirements, checksum validation, and clean restore steps are in
`docs/BACKUP_RESTORE.md`.
