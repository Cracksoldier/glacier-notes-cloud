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
| M9–M13 | Pending | Not yet implemented |

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
