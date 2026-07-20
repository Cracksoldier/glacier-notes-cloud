# Glacier Notes Cloud — Milestone Plan and Acceptance Criteria

**Document status:** Implementation planning baseline  
**Document version:** 1.0  
**Date:** 2026-07-20  
**Source specification:** Glacier Notes Cloud Product and Technical Specification 1.0  
**Target release:** Glacier Notes Cloud v1  
**Primary stack:** Angular, Quarkus, PostgreSQL, OpenAPI  
**Supported deployment:** Docker Compose  

---

## 1. Purpose

This document converts the approved Glacier Notes Cloud specification into an ordered implementation plan.

Each milestone is a delivery gate. A milestone is complete only when:

1. All mandatory deliverables exist.
2. All milestone acceptance criteria pass.
3. Required automated tests pass.
4. No unresolved blocker remains for dependent milestones.
5. Security and ownership requirements relevant to the milestone have been verified.
6. The implementation and OpenAPI contract remain aligned.

The milestones intentionally avoid calendar estimates. Scheduling may be added later after team size, availability, and delivery cadence are known.

---

## 2. Delivery Principles

### 2.1 OpenAPI First

All HTTP APIs shall be defined in the canonical OpenAPI document before implementation.

A backend endpoint is not considered complete unless:

- The OpenAPI operation exists.
- Request and response schemas are defined.
- Validation constraints are represented where possible.
- Standard error responses are documented.
- Generated code compiles.
- Contract tests pass.
- The Angular client uses generated API code rather than handwritten duplicate DTOs.

### 2.2 Vertical Slices

Whenever practical, milestones shall deliver complete vertical slices:

- Database
- Quarkus application service
- REST API
- Angular client
- Authorization
- Tests
- Documentation

Infrastructure-only foundations may be delivered earlier when required by several later slices.

### 2.3 Ownership and Privacy by Default

Every user-content feature shall be implemented with ownership isolation from the beginning.

Administrator privileges shall not imply access to user note content.

No milestone may defer ownership checks to a later “security phase.”

### 2.4 Compatibility

Portable Glacier Notes entities shall retain stable UUIDs and remain compatible with the desktop import/export model.

Cloud-specific fields shall not unnecessarily alter the portable note format.

### 2.5 No Silent Data Loss

Features involving autosave, concurrency, import, deletion, image storage, and background work shall explicitly handle failures and conflicts.

---

## 3. Milestone Overview

| ID | Milestone | Primary Outcome | Depends On |
|---|---|---|---|
| M0 | Architecture and Repository Foundation | Buildable project, canonical contracts, CI foundation | None |
| M1 | PostgreSQL Domain Foundation | Migrated schema, persistence rules, ownership groundwork | M0 |
| M2 | Deployment, Bootstrap, and Instance Initialization | Docker deployment and secure first-admin setup | M0, M1 |
| M3 | Authentication, Sessions, and Security Baseline | Secure login, cookies, CSRF, lockout, session management | M1, M2 |
| M4 | User Administration and Invitation Lifecycle | Admin-managed users, invitations, resets, activation | M3 |
| M5 | Core Notes Backend | Notebooks, notes, labels, checklist, archive, trash APIs | M1, M3 |
| M6 | Core Angular Note Application | Usable multi-user note-taking web UI | M3, M5 |
| M7 | Image Assets and Storage Backends | Secure uploads, quotas, filesystem/DB/S3 storage | M5, M6 |
| M8 | Search, Concurrency, and Version History | FTS, optimistic locking, historical note restore | M5, M6 |
| M9 | Import, Export, and Desktop Compatibility | Portable data exchange and compatibility fixtures | M5, M7, M8 |
| M10 | Account Lifecycle, User Settings, and Email Flows | Profile, email change, self-deletion, i18n, themes | M3, M4, M6 |
| M11 | Administrative Operations and Observability | Audit, metrics, health, settings, backup jobs | M4, M7, M9, M10 |
| M12 | Security Hardening and Release Candidate | Tested, documented, deployable v1 candidate | M0–M11 |
| M13 | Version 1 Release | Approved production release and tagged artifacts | M12 |

Some milestones may be developed in parallel after their shared dependencies are complete, but they shall not be accepted out of dependency order.

---

# M0 — Architecture and Repository Foundation

## Objective

Create the technical foundation needed for all further work, including the repository layout, OpenAPI-first workflow, generated clients/interfaces, build automation, Biome-based Angular quality tooling, and architectural boundaries.

## Scope

- Monorepo or coordinated repository structure
- Quarkus backend skeleton
- Angular frontend skeleton
- Canonical OpenAPI 3.1 document
- Generated Java and TypeScript code
- Build and test commands
- CI pipeline
- Biome as the Angular formatter and general-purpose linter
- Angular strict template type checking
- Angular-specific linting only where Biome does not provide equivalent rules
- Coding conventions
- Error format
- Correlation IDs
- Base package/module boundaries
- Dependency and license inventory

Prettier shall not be installed or used in the Angular project.

Biome shall be used for TypeScript, JavaScript, JSON, and CSS formatting and linting. Angular HTML formatting shall initially be enabled and validated against representative Angular templates. If the pinned Biome release cannot safely format required Angular syntax, HTML files shall be explicitly excluded from Biome formatting until support is sufficient; Prettier shall not be introduced as a fallback.

## Deliverables

1. Repository structure for frontend, backend, OpenAPI, deployment, documentation, and compatibility fixtures.
2. Quarkus project that starts locally.
3. Angular application that builds and renders a placeholder shell.
4. Canonical `openapi/glacier-notes-v1.yaml`.
5. OpenAPI generation integrated into the build.
6. Generated Angular API client.
7. Generated or contract-aligned Java API interfaces and DTOs.
8. Standard `application/problem+json` error model.
9. Request correlation-ID support.
10. An exact-version development dependency on `@biomejs/biome`.
11. A committed `frontend/biome.json` containing the approved baseline configuration.
12. Frontend package scripts for:
    - Biome validation
    - Biome safe fixes
    - Formatting
    - Formatting verification
    - Angular production build
    - Angular tests
13. Angular compiler configuration with `strictTemplates: true`.
14. Representative Angular template fixtures covering:
    - Interpolation
    - Property binding
    - Event binding
    - Two-way binding
    - Pipes
    - `@if`
    - `@for`
    - `@switch`
    - `ng-template`
    - Structural directives where used
    - Multiline component attributes
15. CI workflow for:
    - Backend compilation
    - Backend unit tests
    - Frontend Biome checks
    - Angular production compilation
    - Angular strict template checking
    - Frontend tests
    - OpenAPI validation
    - Generated-code drift detection
16. `.env.example` without secrets.
17. Initial architecture decision records.
18. Documented editor integration for Biome.
19. A documented decision on whether `angular-eslint` is retained only for selected Angular-specific rules that Biome and the Angular compiler do not cover.


## M0 Biome Baseline Configuration

The baseline configuration shall be stored at `frontend/biome.json`. The `$schema` value shall match the exact pinned Biome version used by the project.

```json
{
  "$schema": "https://biomejs.dev/schemas/2.3.11/schema.json",
  "vcs": {
    "enabled": true,
    "clientKind": "git",
    "useIgnoreFile": true
  },
  "files": {
    "includes": [
      "src/**/*.ts",
      "src/**/*.html",
      "src/**/*.css",
      "*.json",
      "!src/app/shared/generated-api/**",
      "!dist/**",
      "!coverage/**"
    ]
  },
  "formatter": {
    "enabled": true,
    "indentStyle": "space",
    "indentWidth": 2,
    "lineWidth": 100,
    "lineEnding": "lf"
  },
  "linter": {
    "enabled": true,
    "rules": {
      "preset": "recommended",
      "style": {
        "useImportType": "off"
      }
    }
  },
  "javascript": {
    "formatter": {
      "quoteStyle": "single",
      "semicolons": "always",
      "trailingCommas": "all"
    }
  },
  "css": {
    "formatter": {
      "enabled": true
    },
    "linter": {
      "enabled": true
    }
  },
  "json": {
    "formatter": {
      "enabled": true
    },
    "linter": {
      "enabled": true
    }
  },
  "html": {
    "parser": {
      "interpolation": true
    },
    "formatter": {
      "enabled": true,
      "indentStyle": "space",
      "indentWidth": 2,
      "lineWidth": 100
    },
    "linter": {
      "enabled": true
    }
  }
}
```

The `useImportType` rule is disabled because Angular dependency injection and decorator-generated metadata may require imports at runtime even when they appear to be type-only to a general TypeScript analyzer.

The generated OpenAPI Angular client is excluded from Biome checks by default to avoid reformatting generated source. Generated-code correctness shall instead be enforced through regeneration and drift detection.

If the pinned Biome version cannot safely parse or format required Angular HTML syntax, the following temporary adjustment is permitted:

```json
{
  "files": {
    "includes": [
      "src/**/*.ts",
      "src/**/*.css",
      "*.json",
      "!src/**/*.html",
      "!src/app/shared/generated-api/**",
      "!dist/**",
      "!coverage/**"
    ]
  },
  "html": {
    "formatter": {
      "enabled": false
    }
  }
}
```

That exception shall:

- Be documented in an architecture decision record.
- Include a linked upstream Biome limitation or reproducible fixture.
- Keep Angular templates protected by `strictTemplates` and Angular compilation.
- Be reviewed when the pinned Biome version is upgraded.
- Not introduce Prettier.

## Recommended Frontend Scripts

The Angular `package.json` should expose commands equivalent to:

```json
{
  "scripts": {
    "format": "biome format --write .",
    "format:check": "biome format .",
    "lint": "biome lint .",
    "check": "biome check .",
    "check:write": "biome check --write .",
    "build:production": "ng build --configuration production",
    "test:ci": "ng test --watch=false"
  }
}
```

The exact Angular test command may be adapted to the selected Angular test runner, but it shall run non-interactively in CI.

## Acceptance Criteria

### Build and Repository

- [ ] A clean checkout can build both frontend and backend using documented commands.
- [ ] Generated API code is produced automatically and is not manually edited.
- [ ] CI fails when the OpenAPI document is invalid.
- [ ] CI fails when generated code is stale relative to the OpenAPI contract.
- [ ] The backend and frontend dependency versions are pinned or reproducibly resolved.
- [ ] `@biomejs/biome` is installed as an exact-version development dependency.
- [ ] The `biome.json` schema URL matches the pinned Biome version.
- [ ] No Prettier package, plugin, configuration file, editor default, or CI command exists in the Angular project.
- [ ] No production secret is committed to the repository.
- [ ] The repository contains a clear local-development guide.

### Biome and Angular Quality Tooling

- [ ] `npm run check` passes from a clean checkout.
- [ ] `npm run check:write` followed by `npm run check` produces no further changes.
- [ ] `npm run format` is idempotent.
- [ ] TypeScript, JavaScript, JSON, and CSS files are formatted and linted by Biome.
- [ ] The generated Angular API client is excluded from rewriting or has a separately documented generated-code policy.
- [ ] `lint/style/useImportType` is disabled for Angular source because of decorator metadata requirements.
- [ ] Angular compiler options include `strict: true` and `strictTemplates: true`.
- [ ] Angular production compilation runs after Biome checks in CI.
- [ ] Angular template fixtures compile successfully after `biome check --write`.
- [ ] Biome formatting does not alter the behavior or bindings of the representative Angular template fixtures.
- [ ] If Angular HTML formatting is disabled, the exception follows the documented temporary-exclusion policy.
- [ ] Biome remains the only formatting tool; no fallback formatter is introduced.
- [ ] `angular-eslint`, if present, is limited to documented Angular-specific rules and is not used for formatting.
- [ ] Editor documentation identifies Biome as the default formatter for supported frontend files.
- [ ] CI does not depend on developer-specific editor formatting behavior.

### OpenAPI

- [ ] `/api/v1` is established as the API base path.
- [ ] The API defines a standard problem response containing status, application error code, safe detail, and correlation ID.
- [ ] Pagination conventions are documented.
- [ ] UUID and ISO-8601 timestamp conventions are documented.
- [ ] Authentication and CSRF behavior are represented or referenced in the contract.
- [ ] Stable operation IDs are assigned.
- [ ] At least one generated Angular-client operation successfully calls a backend placeholder endpoint.

### Architecture

- [ ] Generated DTOs are separated from persistence entities.
- [ ] Domain/application logic does not depend directly on Angular or transport-layer classes.
- [ ] Storage, email, password checking, time, and ID generation have replaceable interfaces where testing requires them.
- [ ] User-content authorization is designated as a service/persistence concern, not only a UI concern.
- [ ] The architecture leaves room for a desktop adapter and future synchronization.
- [ ] The frontend-tooling decision is recorded in an architecture decision record.
- [ ] The decision explicitly states that Biome replaces Prettier for the Angular project.

## Exit Gate

M0 is complete when a new developer can clone the project, run a documented build, start both applications, execute all tests, pass the Biome and Angular strict-template checks, and make a generated client call against a versioned backend endpoint.

---

# M1 — PostgreSQL Domain Foundation

## Objective

Establish the PostgreSQL persistence model, schema migrations, base repositories, ownership model, optimistic-version fields, and transaction conventions.

## Scope

- PostgreSQL as the only supported v1 database
- Migration tooling
- User and instance tables
- Session and security-token groundwork
- Notebook, note, checklist, label, image metadata, version, tombstone, audit, and job tables
- UUID conventions
- UTC timestamps
- Ownership constraints
- Database indexes
- Full-text search column groundwork
- Integration-test database

## Deliverables

1. Versioned database migrations.
2. Persistence entities and repositories.
3. Testcontainers or equivalent PostgreSQL integration-test setup.
4. Database diagram.
5. Unique normalized identity constraints.
6. Owner-scoped indexes.
7. Optimistic lock fields.
8. Tombstone schema.
9. Schema metadata/version tracking.
10. Migration and rollback/backup guidance.

## Acceptance Criteria

### Schema

- [ ] Every synchronizable entity uses a UUID.
- [ ] Every synchronizable mutable entity has `createdAt`, `updatedAt`, and `version`.
- [ ] Server timestamps are stored as `timestamptz`.
- [ ] User-owned entities contain an immutable `ownerId`.
- [ ] Unique indexes exist for normalized username and normalized email.
- [ ] A user can have only one default notebook.
- [ ] Label names are unique per owner using normalized comparison.
- [ ] Foreign keys prevent invalid note/notebook/checklist/label relationships.
- [ ] The schema supports a stable image ID independent of the selected storage backend.
- [ ] Tombstones contain no note content fields.
- [ ] Audit and operational job tables do not require access to user note content.

### Migrations

- [ ] A blank PostgreSQL database migrates to the latest schema automatically in the supported development workflow.
- [ ] Migrations are deterministic and repeatable in CI.
- [ ] Integration tests use PostgreSQL rather than substituting an incompatible in-memory database.
- [ ] Failed migrations prevent normal startup with a clear operator-facing error.
- [ ] Migration documentation warns before destructive schema changes.

### Ownership

- [ ] Repository methods for user-owned entities require an owner scope or authenticated context.
- [ ] Tests prove that the same entity ID cannot be read through another owner scope.
- [ ] Cross-user UUID collisions can exist only where the global primary-key design permits safe handling; they can never grant access or overwrite another owner’s data.
- [ ] Queries used by later collection endpoints have owner-first indexes.

## Exit Gate

M1 is complete when the full initial schema can be created in PostgreSQL, all ownership constraints are covered by integration tests, and later milestones can build repositories without redesigning core identifiers.

---

# M2 — Deployment, Bootstrap, and Instance Initialization

## Objective

Deliver the officially supported Docker Compose environment and the secure one-time first-administrator setup.

## Scope

- Application container
- PostgreSQL container
- Persistent volumes
- Angular served by Quarkus
- Environment configuration
- Initial instance status
- Bootstrap token
- First ADMIN creation
- Bootstrap lockout after initialization
- Container health behavior

## Deliverables

1. Production-oriented Dockerfile.
2. Docker Compose configuration.
3. Persistent PostgreSQL volume.
4. Persistent filesystem image volume.
5. Optional backup volume definition.
6. Environment-variable documentation.
7. `/api/v1/setup/status`.
8. `/api/v1/setup/administrator`.
9. Angular first-run setup page.
10. Setup security tests.

## Acceptance Criteria

### Deployment

- [ ] `docker compose up` starts the application and PostgreSQL using documented configuration.
- [ ] The compiled Angular application is served from the Quarkus application container.
- [ ] Application data survives container recreation.
- [ ] The application container can run without root privileges where supported.
- [ ] Database readiness is checked before accepting normal requests.
- [ ] No default production password is embedded in the image or Compose file.
- [ ] Filesystem image storage uses a persistent mounted directory by default.
- [ ] Direct local HTTP access works for development.
- [ ] Reverse-proxy configuration requirements are documented.

### Bootstrap

- [ ] An uninitialized instance reports that setup is required.
- [ ] The first administrator cannot be created without the configured bootstrap token.
- [ ] Invalid bootstrap tokens receive a generic failure and are rate-limited.
- [ ] The token never appears in logs, audit metadata, or response bodies.
- [ ] The first administrator is created with normalized username and email.
- [ ] Normal password policy is enforced.
- [ ] A default notebook and initial user settings are created transactionally.
- [ ] Instance initialization is persisted.
- [ ] The bootstrap endpoint becomes permanently unavailable after successful setup.
- [ ] Restarting with the same bootstrap token does not re-enable setup.
- [ ] Bootstrap completion creates an audit event without storing the token.
- [ ] Startup warns or fails clearly when no user exists and no bootstrap token is configured.

## Exit Gate

M2 is complete when a fresh installation can be deployed from documentation and securely initialized exactly once.

---

# M3 — Authentication, Sessions, and Security Baseline

## Objective

Provide secure username/email login, server-managed sessions, CSRF protection, password handling, throttling, lockout, and session self-management.

## Scope

- Login and logout
- Username or email identifier
- Case-insensitive normalized identity matching
- Argon2id
- Secure cookies
- CSRF
- Normal and remember-me sessions
- Session listing/revocation
- Login rate limits
- Progressive delays
- Temporary account lockout
- Security headers
- Authentication route guards

## Deliverables

1. Authentication OpenAPI operations.
2. Argon2id password service.
3. Login/logout/session backend.
4. Database-backed session store.
5. CSRF implementation.
6. Angular login and session handling.
7. Session-management page.
8. Rate-limiting and lockout implementation.
9. Core security headers.
10. Authentication integration tests.

## Acceptance Criteria

### Login

- [ ] A user can log in using username or email.
- [ ] Login and uniqueness matching are case-insensitive while original casing remains available for display.
- [ ] Display name cannot be used to log in.
- [ ] Incorrect credentials return a neutral error that does not reveal account existence.
- [ ] Deactivated, deleted, pending, and locked users cannot authenticate.
- [ ] Password hashes use Argon2id with documented parameters.
- [ ] Plaintext passwords and password hashes never appear in API responses or logs.

### Sessions

- [ ] Normal sessions expire after 12 hours by default.
- [ ] Remember-me sessions expire after 30 days by default.
- [ ] Session duration settings are validated.
- [ ] Session state survives application restart.
- [ ] Sessions are not stored only in process memory.
- [ ] Authentication cookies are `HttpOnly`.
- [ ] `Secure` is applied when the configured public URL uses HTTPS.
- [ ] Cookie `SameSite` and path settings match the documented deployment model.
- [ ] Session identifiers are rotated after login and privilege changes.
- [ ] A user can list active sessions without seeing raw tokens.
- [ ] A user can revoke an individual session.
- [ ] A user can revoke all other sessions.
- [ ] Logout revokes the current server-side session.

### CSRF and Headers

- [ ] State-changing cookie-authenticated requests without a valid CSRF token are rejected.
- [ ] The generated Angular client or interceptor sends the CSRF value correctly.
- [ ] Content Security Policy is active.
- [ ] Framing is restricted.
- [ ] MIME sniffing is disabled.
- [ ] Referrer and permissions policies are configured.
- [ ] Security-header tests run in CI.

### Throttling and Lockout

- [ ] Login attempts are limited by account identifier and IP address.
- [ ] Increasing delays begin after five failed attempts by default.
- [ ] The account is locked for 15 minutes after ten failed attempts by default.
- [ ] A successful login resets the applicable failure state.
- [ ] Lockout behavior cannot be used to enumerate account existence.
- [ ] Authentication endpoints return `429` where appropriate.
- [ ] Throttling has automated tests using deterministic clocks.

### Authorization Baseline

- [ ] Anonymous requests to protected endpoints receive the documented response.
- [ ] USER accounts cannot access ADMIN endpoints.
- [ ] ADMIN access does not automatically bypass content ownership.
- [ ] Angular route guards prevent normal navigation to unauthorized routes.
- [ ] Server authorization remains effective when UI guards are bypassed.

## Exit Gate

M3 is complete when a user can securely authenticate, maintain and revoke server-backed sessions, and all baseline browser-security controls pass automated tests.

---

# M4 — User Administration and Invitation Lifecycle

## Objective

Deliver administrator-managed onboarding, invitations, activation, password resets, role/status administration, and last-administrator protections.

## Scope

- User list and user metadata
- Invitation creation
- SMTP and no-SMTP invitation delivery
- Full-token URL acceptance
- Manual full-token entry
- Invitation revoke/resend
- Domain allowlist
- Administrative reset links
- Activation/deactivation
- Unlock
- Role changes
- Session revocation
- Last-admin rules
- Admin privacy restrictions

## Deliverables

1. Admin user-management API.
2. Invitation API and token service.
3. Invitation email template.
4. Copyable activation-link flow.
5. Invitation acceptance UI.
6. Manual token-entry UI.
7. Admin user-list and details pages.
8. Password-reset request and completion flows.
9. Admin-generated reset link.
10. Domain allowlist setting.
11. Relevant audit events.

## Acceptance Criteria

### Invitations

- [ ] Public self-registration does not exist.
- [ ] An administrator can create an invitation for a valid unique email and username.
- [ ] Invitation tokens use at least 256 bits of entropy.
- [ ] Only token hashes are persisted.
- [ ] Tokens are single-use and expire after seven days by default.
- [ ] An invitation can be accepted from a URL containing the token.
- [ ] The same full token can be pasted into the invitation page manually.
- [ ] No short human-readable code is required.
- [ ] Successful acceptance creates an active account and default notebook.
- [ ] Acceptance marks the invitation as consumed.
- [ ] Replaying a consumed token fails.
- [ ] Administrators can revoke pending invitations.
- [ ] Administrators can resend or regenerate invitations.
- [ ] Token values are not exposed in audit logs.
- [ ] When SMTP is unavailable, the admin can copy an activation link.
- [ ] Optional email-domain restrictions are enforced for new invitations.
- [ ] Existing users are not disabled when the domain allowlist changes.

### Password Reset

- [ ] A user can request a password-reset email when SMTP is configured.
- [ ] The request response does not reveal whether the account exists.
- [ ] Reset tokens are hashed, single-use, throttled, and expire after the configured period.
- [ ] An administrator can generate a copyable reset link.
- [ ] Administrators never set or see a user's password.
- [ ] Completing a reset revokes all prior sessions and reset tokens.
- [ ] Reset-link generation is audited without storing the token.

### User Administration

- [ ] Administrators can list users and view counts/usage metadata without content previews.
- [ ] Administrators can activate and deactivate users.
- [ ] Deactivation revokes all sessions immediately.
- [ ] Administrators can unlock a locked account.
- [ ] Administrators can revoke all sessions for a user.
- [ ] Administrators can change role, username, and email subject to constraints.
- [ ] The last active administrator cannot be deactivated, deleted, or demoted.
- [ ] A last administrator cannot self-demote.
- [ ] Blocked last-admin operations return a specific documented error.
- [ ] USER accounts cannot execute any user-administration operation.
- [ ] ADMIN accounts cannot retrieve note titles, bodies, checklist text, image data, or image filenames through administration endpoints.

## Exit Gate

M4 is complete when a new user can be securely invited and activated with or without SMTP, and the administrator can manage account access without gaining note-content access.

---

# M5 — Core Notes Backend

## Objective

Implement the complete owner-scoped backend domain for notebooks, notes, checklist items, labels, colors, archive, pinning, and trash.

## Scope

- Default notebook
- Notebook CRUD and reorder
- Text notes
- Checklist notes
- Label CRUD and assignments
- Pinning
- Archive
- Trash/restore/purge
- Note movement
- Text/checklist conversion
- Server timestamps
- Basic pagination and filtering
- Stable UUIDs
- API contract

## Deliverables

1. Notebook endpoints.
2. Note endpoints.
3. Label endpoints.
4. Checklist persistence and mapping.
5. Default-notebook creation.
6. Archive and trash endpoints.
7. Empty-trash operation.
8. Move and conversion operations.
9. Owner-scoped repository and service tests.
10. OpenAPI examples and errors.

## Acceptance Criteria

### Notebooks

- [ ] Every activated user has exactly one default notebook.
- [ ] The default notebook can be renamed.
- [ ] The default notebook cannot be deleted.
- [ ] Users can create, rename, recolor, and reorder notebooks.
- [ ] Notebook list responses contain note counts without exposing other users' data.
- [ ] Deleting a non-default notebook supports moving notes to the default notebook or moving them to trash.
- [ ] Notebook deletion behavior is transactional.
- [ ] A user cannot read or mutate another user's notebook by UUID.

### Notes

- [ ] Users can create text notes and checklist notes.
- [ ] Client-generated valid UUIDs are accepted when non-conflicting.
- [ ] Server-generated `createdAt`, `updatedAt`, and `version` values are returned.
- [ ] Users can update title, content, checklist, pinned state, archive state, color, labels, and notebook.
- [ ] Users can move notes between owned notebooks.
- [ ] Text-to-checklist and checklist-to-text conversion follows documented best-effort rules.
- [ ] Collection endpoints are paginated.
- [ ] Collection endpoints support notebook, note-type, pinned, archive, and trash filters.
- [ ] Notes belonging to another user return the same safe not-found behavior as nonexistent notes.

### Labels

- [ ] Users can create, rename, list, and delete labels.
- [ ] Label uniqueness is case-insensitive within one user's account.
- [ ] Different users can use the same label names.
- [ ] Deleting a label removes assignments without deleting notes.
- [ ] A note cannot be assigned another user's label.

### Trash and Archive

- [ ] Trashing sets a server timestamp and removes the note from default views.
- [ ] A trashed note can be restored.
- [ ] An individual trashed note can be permanently deleted.
- [ ] Empty trash permanently deletes eligible notes.
- [ ] Archived notes remain editable.
- [ ] Archived notes are separated from the normal notebook view.
- [ ] Retention cleanup can be added without changing the note schema.

### Ownership and Privacy

- [ ] Automated tests attempt IDOR access for every content endpoint.
- [ ] Cross-user update and deletion attempts fail.
- [ ] ADMIN users are subject to the same ownership rule on normal note endpoints.
- [ ] No repository method used by the API performs an unscoped user-content lookup.

## Exit Gate

M5 is complete when the complete note domain is available through secure, owner-scoped APIs and can be exercised without a production UI.

---

# M6 — Core Angular Note Application

## Objective

Deliver a usable browser application for the core Glacier Notes workflow using the generated API client.

## Scope

- Application shell
- Sidebar
- Notebook navigation
- Card grid
- Text-note editor
- Checklist editor
- Labels and colors
- Pin/archive/trash
- Markdown toolbar
- Markdown rendering and sanitization
- Autosave states
- Keyboard shortcuts
- Error handling
- Responsive desktop/tablet layout

## Deliverables

1. Authenticated application layout.
2. Notebook sidebar.
3. Note-card grid.
4. Text-note editor.
5. Checklist-note editor.
6. Label and color selectors.
7. Archive and trash views.
8. Markdown renderer and toolbar.
9. Autosave service.
10. Global error/problem handler.
11. Shortcut help.
12. Component and end-to-end tests.

## Acceptance Criteria

### Navigation and Layout

- [ ] Authenticated users see the main Glacier Notes layout.
- [ ] Sidebar lists notebooks, labels, archive, trash, and import/export navigation.
- [ ] Selecting a notebook loads only that user's notes.
- [ ] The layout remains usable at supported desktop and tablet widths.
- [ ] Protected routes redirect unauthenticated users to login.
- [ ] Browser refresh preserves route and session state where valid.

### Note Cards and Editor

- [ ] Notes display title and suitable text/checklist previews.
- [ ] Pinned notes appear before other notes where applicable.
- [ ] Note colors remain readable in dark and light themes.
- [ ] A user can create a text note from the UI.
- [ ] A user can create a checklist note from the UI.
- [ ] A user can edit title and content.
- [ ] Checklist items can be added, edited, toggled, deleted, and reordered.
- [ ] Notes can be moved, pinned, archived, labeled, colored, trashed, and restored.
- [ ] The UI handles safe not-found and permission-equivalent errors without exposing ownership details.

### Markdown

- [ ] Markdown headings, emphasis, lists, links, code, blockquotes, and tables render.
- [ ] Raw HTML does not execute.
- [ ] Rendered HTML is sanitized.
- [ ] External links do not retain opener access.
- [ ] Toolbar actions insert or wrap Markdown at the cursor.
- [ ] Checklist item Markdown is restricted to supported inline formatting.

### Autosave and UX

- [ ] Autosave is debounced to approximately 500 ms by default.
- [ ] The editor visibly distinguishes saving, saved, error, and conflict states.
- [ ] Failed saves retain the local draft.
- [ ] Closing the editor after a pending save does not silently discard edits.
- [ ] Lists refresh after create, update, restore, archive, and deletion.
- [ ] Lists refresh when the window regains focus as specified.
- [ ] Applicable keyboard shortcuts work.
- [ ] Browser-reserved shortcuts fail gracefully.

### Generated Client

- [ ] The frontend uses generated OpenAPI client methods for backend calls.
- [ ] Handwritten duplicate API DTOs are not used for covered operations.
- [ ] Standard problem responses are shown through consistent user-facing messages.

## Exit Gate

M6 is complete when a normal USER can perform all non-image core note operations through the browser without using API tools directly.

---

# M7 — Image Assets and Storage Backends

## Objective

Implement secure image upload, processing, references, quotas, garbage collection, and all three selectable storage backends.

## Scope

- PNG, JPEG, WebP
- File picker, drop, paste
- MIME validation
- Downscaling/re-encoding
- Thumbnails
- 10 MB default upload limit
- 1 GB user quota
- Filesystem default
- PostgreSQL binary backend
- S3-compatible backend
- Backend immutability
- Orphan cleanup
- Streaming download

## Deliverables

1. Image storage abstraction.
2. Filesystem storage implementation.
3. PostgreSQL binary implementation.
4. S3-compatible implementation.
5. Upload and authorized download API.
6. Image-processing pipeline.
7. Quota service.
8. Frontend upload/drop/paste UX.
9. Image thumbnails and full-size view.
10. Garbage-collection job.
11. Backend-specific integration tests.

## Acceptance Criteria

### Upload and Validation

- [ ] PNG, JPEG, and WebP uploads are accepted when valid.
- [ ] GIF and unsupported formats are rejected.
- [ ] Validation uses signatures and decoding rather than extension alone.
- [ ] Malformed images are rejected without excessive memory or CPU usage.
- [ ] Configured byte and pixel-dimension limits are enforced.
- [ ] Files larger than the configured incoming limit are downscaled/re-encoded when safe.
- [ ] Files are rejected if the processed result still exceeds the limit.
- [ ] Temporary files are randomly named and cleaned after success or failure.
- [ ] Original filenames cannot cause path traversal.

### Quota

- [ ] A new user has a 1 GB default image quota.
- [ ] Administrators can configure the default quota.
- [ ] Uploads that exceed remaining quota fail atomically with a documented error.
- [ ] Concurrent uploads cannot materially bypass the quota.
- [ ] Administrators can view counts and byte totals without filenames or image previews.
- [ ] User-facing quota information reflects the authenticated user's own usage.

### Storage Backends

- [ ] Filesystem is the default backend.
- [ ] Filesystem writes are atomic and use the mounted persistent volume.
- [ ] PostgreSQL storage streams binary data without requiring the complete object in memory.
- [ ] S3 objects are private.
- [ ] S3 credentials are supplied only through secret configuration.
- [ ] Each installation records its selected backend.
- [ ] Startup fails clearly if the configured backend changes after assets exist.
- [ ] No automatic migration between backends is performed.
- [ ] The same image API behavior passes against all three backends.

### Ownership and References

- [ ] Image IDs are stable UUIDs independent of storage location.
- [ ] Only the owner can retrieve an image.
- [ ] Guessing an image UUID does not reveal whether another user owns it.
- [ ] Notes can reference owned images only.
- [ ] Removing one reference does not delete an image still referenced elsewhere.
- [ ] Images referenced by retained note versions are not garbage-collected.
- [ ] Orphan assets are deleted only after the configured grace/eligibility checks.

### Frontend

- [ ] Users can add images through file picker.
- [ ] Users can add images through drag and drop.
- [ ] Users can paste supported images from the clipboard.
- [ ] Cards display thumbnails.
- [ ] The editor displays images.
- [ ] Users can open a larger image view.
- [ ] Upload progress and validation failures are understandable.

## Exit Gate

M7 is complete when images work end to end against each supported deployment-time backend and all ownership, quota, and processing tests pass.

---

# M8 — Search, Concurrency, and Version History

## Objective

Deliver PostgreSQL full-text search, conflict-safe autosave, and configurable note history.

## Scope

- PostgreSQL `simple` full-text search
- Title/content/checklist indexing
- Filters
- Ranked results
- Optimistic locking
- `409 Conflict`
- Version snapshot triggers
- Last 20/30-day default retention
- Restore history
- Cleanup jobs

## Deliverables

1. Search-vector migration and maintenance.
2. Search API and Angular search UI.
3. Optimistic-lock enforcement.
4. Conflict response model.
5. Conflict-resolution UI.
6. Note-version persistence.
7. Version-history UI.
8. Version cleanup job.
9. Search and concurrency tests.

## Acceptance Criteria

### Search

- [ ] Search uses PostgreSQL full-text search.
- [ ] The `simple` language-neutral configuration is used.
- [ ] Titles, Markdown source, and checklist text are indexed.
- [ ] Default search includes non-trashed notes owned by the user.
- [ ] Archived notes are included unless excluded by a filter.
- [ ] Trash search requires explicit scope/filter.
- [ ] Filters exist for notebook, label, archive status, trash, note type, and pinned state.
- [ ] Results are ranked and use stable secondary ordering.
- [ ] Results are paginated.
- [ ] Search never returns another user's content.
- [ ] Indexes update after note and checklist changes.
- [ ] Empty-query behavior is documented and tested.

### Optimistic Locking

- [ ] Every note update requires the last known version.
- [ ] A stale update returns `409 Conflict`.
- [ ] A stale update does not overwrite the current stored note.
- [ ] The conflict response includes the current version and safe metadata.
- [ ] The Angular editor clearly reports the conflict.
- [ ] The user can reload the server copy.
- [ ] The user can copy or preserve the local draft before reloading.
- [ ] Two-tab and two-session conflict scenarios have automated tests.

### Version History

- [ ] Default maximum history is 20 versions per note.
- [ ] Default history age is 30 days.
- [ ] Both values are administrator-configurable.
- [ ] A snapshot is created when the editor closes after meaningful changes.
- [ ] A snapshot is created after meaningful changes when at least five minutes have passed since the prior snapshot.
- [ ] A snapshot is created when a conflicting update is rejected.
- [ ] A snapshot is created before/when restoring from trash as specified.
- [ ] A snapshot is created before restoring an older version.
- [ ] Normal 500 ms autosaves do not create a version each time.
- [ ] Duplicate snapshots are avoided where content has not changed.
- [ ] Restoring a version preserves the note ID and increments the current version.
- [ ] The pre-restore state is retained.
- [ ] Image references required by retained versions remain valid.
- [ ] Cleanup enforces both count and age limits.

## Exit Gate

M8 is complete when search is production-usable and concurrent editing cannot silently overwrite newer data.

---

# M9 — Import, Export, and Desktop Compatibility

## Objective

Provide safe user import/export, blind administrative import, and verified compatibility with the Glacier Notes desktop format.

## Scope

- Full/notebook/note export
- `.glacier.json`
- Base64 images
- Import inspection
- Add as copies
- Replace by ID
- Cross-user collision safety
- Blind admin import
- Streaming and size limits
- Compatibility fixtures
- Tombstones groundwork

## Deliverables

1. Export job API.
2. Export streaming/download.
3. Import inspection API.
4. Import apply API.
5. Conflict-strategy UI.
6. Blind admin import UI.
7. Desktop-generated compatibility fixtures.
8. Cloud export compatibility tests.
9. Import cleanup and cancellation.
10. Transfer documentation.

## Acceptance Criteria

### Export

- [ ] A user can export all their content.
- [ ] A user can export one notebook.
- [ ] A user can export one note.
- [ ] Users cannot export another user's content.
- [ ] Images are included as base64 in the portable format.
- [ ] Export uses bounded memory or streaming.
- [ ] User exports can be disabled by an administrator.
- [ ] Export files contain no authentication, session, audit, or authorization data.
- [ ] Cloud metadata is optional and does not break compatible readers.
- [ ] Pending exports can be cancelled.
- [ ] Temporary export files are cleaned according to policy.

### Import Inspection

- [ ] The import validates format and schema version.
- [ ] UUID syntax and relationships are validated.
- [ ] Image MIME type and decoded size are validated.
- [ ] Maximum file size, entity count, nesting, and image totals are enforced.
- [ ] Quota impact is calculated before apply.
- [ ] Validation returns counts and actionable structural errors.
- [ ] Invalid imports do not partially alter permanent user data.
- [ ] Temporary uploads are deleted after completion, cancellation, or expiry.

### Conflict Strategies

- [ ] “Add as copies” remaps all required IDs and relationships consistently.
- [ ] “Replace existing by ID” replaces only matching entities owned by the target user.
- [ ] An ID belonging to another user is never overwritten.
- [ ] Cross-user collisions do not reveal the other user's identity or content.
- [ ] Import apply is transactional where possible and compensates binary writes on failure.
- [ ] Import retries do not create uncontrolled duplicates.

### Blind Administrative Import

- [ ] An administrator can choose a target user and upload an import.
- [ ] Admin validation output contains only counts and structural errors.
- [ ] The administrator cannot preview note titles, bodies, checklist text, images, or image filenames.
- [ ] Ownership is assigned to the target user.
- [ ] The operation is audited.
- [ ] USER accounts cannot invoke target-user imports.

### Desktop Compatibility

- [ ] A full desktop export fixture imports successfully.
- [ ] A notebook desktop export fixture imports successfully.
- [ ] A single-note desktop export fixture imports successfully.
- [ ] UUIDs are preserved when no conflict exists.
- [ ] Image references survive a desktop-to-cloud round trip.
- [ ] A cloud export can be opened by a compatible desktop client.
- [ ] The default notebook is correctly handled.
- [ ] Add-as-copies and replace-by-ID match desktop semantics.

### Tombstones

- [ ] Permanently deleted synchronizable entities can create minimal tombstones.
- [ ] Tombstones contain owner, entity type, entity ID, version, and deletion/expiry timestamps only.
- [ ] Tombstones contain no note content.
- [ ] Default tombstone retention is 30 days.
- [ ] The schema can support a future modification-time/change-feed endpoint.

## Exit Gate

M9 is complete when portable data can round-trip between compatible desktop and cloud versions without violating ownership or quota boundaries.

---

# M10 — Account Lifecycle, User Settings, and Email Flows

## Objective

Complete user-facing profile/security settings, verified email changes, account deletion, theme/language preferences, trash policy, and external email sharing.

## Scope

- Profile
- Username change
- Display-name change
- Email verification flow
- Password change
- Self-deletion
- Administrative retained deletion
- Deactivation/reactivation behavior
- Theme
- Language
- Trash preference
- Move-checked preference
- `mailto:` share

## Deliverables

1. Profile/settings pages.
2. Password-change flow.
3. Email-change request and verification.
4. Old-address notification.
5. Self-deletion UI.
6. Administrative deletion lifecycle.
7. User preference API.
8. English and German UI.
9. Dark and light themes.
10. Email share action.
11. Retention jobs for account deletion and trash.

## Acceptance Criteria

### Profile and Password

- [ ] Users can change display name.
- [ ] Users can change username subject to case-insensitive uniqueness.
- [ ] Changing password requires the current password.
- [ ] New passwords satisfy configured policy.
- [ ] Changing password revokes all sessions.
- [ ] Password-history enforcement works when enabled.
- [ ] Common-password rejection works by default and can be disabled by administrators.

### Email Change

- [ ] A user must provide the current password to request an email change.
- [ ] The new email is verified before becoming active.
- [ ] The old email remains the login address until verification completes.
- [ ] The new email cannot conflict case-insensitively with another account.
- [ ] Successful change notifies the old address when SMTP is available.
- [ ] Pending email-change tokens are hashed, expiring, single-use, and throttled.
- [ ] Self-service email change is unavailable with a clear explanation when SMTP is unavailable.
- [ ] An administrator can change an email immediately.
- [ ] Administrative email changes are audited.

### Deactivation and Deletion

- [ ] Deactivation immediately revokes sessions and blocks login.
- [ ] Deactivated user data remains and continues counting toward storage.
- [ ] Cleanup policies continue for deactivated users.
- [ ] An invitation cannot reuse the deactivated account's normalized identity.
- [ ] An administrator can reactivate a deactivated account.
- [ ] Administrative deletion uses the configured retention period by default.
- [ ] An administrator can restore an account before retained deletion completes.
- [ ] Immediate admin deletion requires destructive confirmation.
- [ ] User self-deletion requires the current password.
- [ ] The final self-deletion warning lists all permanently removed data classes.
- [ ] Self-deletion is immediate and irreversible.
- [ ] Self-deletion cancels sessions, pending exports, image processing, and security tokens.
- [ ] The last active administrator cannot self-delete.
- [ ] Permanent deletion removes user content and image binaries.
- [ ] Minimal audit records and tombstones follow their independent retention policies.

### Preferences and UI

- [ ] Dark theme is the default.
- [ ] Users can switch between dark and light themes.
- [ ] Theme persists.
- [ ] English and German can be switched at runtime.
- [ ] Supported browser locale is used for the initial language; otherwise English.
- [ ] Dates are formatted according to selected language.
- [ ] Move-checked-to-bottom can be configured.
- [ ] Trash auto-purge preference follows the administrator's policy.
- [ ] Desktop-only tray and quick-note settings do not appear.

### Email Sharing

- [ ] Share builds a `mailto:` URL using title and Markdown body.
- [ ] Checklist notes are rendered as Markdown checklist lines.
- [ ] The UI warns that images cannot be attached.
- [ ] Export is offered as an alternative.
- [ ] Excessive URL length is handled with a useful warning.
- [ ] Note content is not sent through server SMTP.

## Exit Gate

M10 is complete when user identity, preferences, deletion, and email-related self-service flows are complete and policy-compliant.

---

# M11 — Administrative Operations and Observability

## Objective

Complete runtime administration, audit logging, SMTP status, instance settings, health, metrics, scheduled cleanup, and environment-gated full backup.

## Scope

- Admin overview
- Instance settings
- SMTP status/test
- Audit events/export
- Structured logs
- Health
- Metrics
- Internal management port
- Cleanup jobs
- Backup job and dashboard
- Operational documentation

## Deliverables

1. Admin overview page.
2. Instance-settings API/UI.
3. SMTP status and connection test.
4. Audit-event service and UI.
5. CSV/JSON audit export.
6. Structured logging.
7. Liveness/readiness endpoints.
8. Internal metrics endpoint.
9. Coordinated scheduled jobs.
10. Environment-gated backup service.
11. Backup dashboard.
12. Backup/restore runbook.

## Acceptance Criteria

### Instance Settings

- [ ] Administrators can configure all approved non-secret settings.
- [ ] Settings have validated safe ranges.
- [ ] Restart-required settings are marked.
- [ ] Secret settings cannot be entered or retrieved from the dashboard.
- [ ] Setting changes generate audit events.
- [ ] Invalid settings do not leave partial configuration.
- [ ] The image backend cannot be changed through runtime settings after assets exist.

### SMTP Status

- [ ] The dashboard shows configured/not configured status.
- [ ] Sender name and address may be displayed.
- [ ] Administrators can trigger a connection/test email.
- [ ] Last successful email time is displayed.
- [ ] Generic failure status is displayed without credentials.
- [ ] SMTP password is never accepted or shown in the dashboard.

### Audit

- [ ] Required administrative and security events are recorded.
- [ ] Failed login attempts and security-sensitive login events are recorded.
- [ ] Normal successful logins are not recorded by default.
- [ ] Full IP address is stored as approved.
- [ ] Client/browser information is normalized.
- [ ] Correlation IDs connect audit events to request logs.
- [ ] Audit metadata contains no tokens, passwords, note content, or image filenames.
- [ ] Default retention is 365 days.
- [ ] Retention is configurable.
- [ ] Audit events cannot be edited or manually deleted through the dashboard.
- [ ] Administrators can export audit records as CSV and JSON.

### Logs, Health, and Metrics

- [ ] Production logs are structured.
- [ ] Routine logs contain no note content.
- [ ] Operational-log retention guidance defaults to 30 days.
- [ ] Liveness reports process health.
- [ ] Readiness checks PostgreSQL and required image storage.
- [ ] SMTP failure is reported as degraded rather than automatically blocking note use.
- [ ] Metrics are enabled by default.
- [ ] Metrics are exposed on a separate management interface.
- [ ] The management interface binds internally by default.
- [ ] Metrics can be disabled.
- [ ] Metric labels do not contain usernames, emails, note titles, image filenames, or tokens.

### Scheduled Jobs

- [ ] Jobs exist for invitation, reset-token, email-token, session, trash, history, tombstone, orphan-image, audit, deletion, and temporary-transfer cleanup.
- [ ] Jobs are safe to retry.
- [ ] Jobs do not rely solely on uncoordinated process-local scheduling.
- [ ] Two simulated application instances cannot process the same exclusive job simultaneously where duplication is unsafe.
- [ ] Job failures are logged with correlation/job IDs and recoverable states.

### Backup

- [ ] Backup operations are unavailable unless the environment feature flag is enabled.
- [ ] When enabled, an administrator can initiate a backup from the dashboard.
- [ ] The backup runs as a background job.
- [ ] The backup is written only to the configured server directory.
- [ ] The dashboard does not provide arbitrary filesystem browsing.
- [ ] The backup includes PostgreSQL data, image assets, non-secret settings, manifest, versions, timestamps, and checksums.
- [ ] Configuration values for database credentials, SMTP passwords, S3 secrets, bootstrap tokens, and cryptographic keys are not intentionally copied into the backup manifest/configuration.
- [ ] Documentation states that the database dump still contains sensitive user data and authentication hashes.
- [ ] Backup files have restrictive permissions.
- [ ] Backup creation and outcome are audited.
- [ ] Restore instructions are tested on a clean environment.
- [ ] Documentation requires external encryption and protection of the backup directory.

## Exit Gate

M11 is complete when an operator can safely administer and observe the system, and a gated backup can be created and restored using documented procedures.

---

# M12 — Security Hardening and Release Candidate

## Objective

Integrate all v1 features, close security and reliability gaps, complete cross-feature testing, and produce a release candidate.

## Scope

- Full regression
- Threat modeling
- Security review
- Performance validation
- Accessibility
- Browser compatibility
- Deployment documentation
- Upgrade/migration test
- Backup restore test
- Desktop compatibility test
- Dependency scanning
- Release candidate artifacts

## Deliverables

1. Threat model.
2. Security test report.
3. End-to-end test suite.
4. Performance test report.
5. Accessibility review.
6. Supported-browser statement.
7. Docker image candidate.
8. Docker Compose release candidate.
9. Configuration reference.
10. Security guide.
11. Backup/restore guide.
12. Import/export guide.
13. Desktop compatibility guide.
14. Upgrade test record.
15. Known-issues list.

## Acceptance Criteria

### Functional Regression

- [ ] All milestone acceptance suites pass.
- [ ] All 50 high-level specification acceptance criteria are traceable to automated or documented manual tests.
- [ ] A fresh instance can be installed, initialized, and used end to end.
- [ ] An existing release-candidate database can be migrated to the latest schema.
- [ ] Core note operations work with filesystem, PostgreSQL, and S3 image configurations.
- [ ] English and German workflows pass smoke tests.
- [ ] Dark and light themes pass visual/readability checks.

### Security

- [ ] CSRF bypass attempts fail.
- [ ] Session fixation tests fail to retain the pre-login identifier.
- [ ] IDOR tests cover all owner-scoped endpoints.
- [ ] ADMIN content-access attempts fail.
- [ ] Path-traversal attempts fail.
- [ ] Malformed and oversized image/import payloads fail safely.
- [ ] Invitation, reset, bootstrap, and email-change token replay fails.
- [ ] Token expiration and throttling pass.
- [ ] Logs and metrics are scanned for prohibited fields.
- [ ] Dependency vulnerability scanning has no unresolved critical vulnerability.
- [ ] Secrets are absent from built frontend assets.
- [ ] Production security headers meet the documented policy.
- [ ] The threat model documents remaining accepted risks.

### Reliability and Data Integrity

- [ ] Autosave failure never silently discards the current draft.
- [ ] Stale updates never silently overwrite newer notes.
- [ ] Import failure does not leave inconsistent relationships.
- [ ] Binary-write failures are compensated or recoverable.
- [ ] Scheduled cleanup is retry-safe.
- [ ] Account deletion removes all required data.
- [ ] Tombstones retain only approved minimal metadata.
- [ ] Backup restore produces a usable instance.
- [ ] Restarting during common jobs leaves recoverable states.

### Performance

- [ ] Collection endpoints remain paginated.
- [ ] Search uses indexes and does not scan all users' note rows.
- [ ] Large permitted images and exports are streamed or bounded.
- [ ] Memory usage remains bounded during maximum-size import tests.
- [ ] A documented representative small-team dataset provides acceptable interactive response behavior.
- [ ] Slow operations expose progress or asynchronous job status where designed.

### Accessibility and Browser UX

- [ ] Primary workflows are keyboard usable.
- [ ] Focus is visible.
- [ ] Dialogs use correct focus trapping and return behavior.
- [ ] Form errors are associated with inputs.
- [ ] Status is not communicated by color alone.
- [ ] The supported desktop browser matrix passes smoke tests.
- [ ] Tablet-width layout remains usable.

### Documentation and Packaging

- [ ] Docker Compose installation is reproducible from the release documentation.
- [ ] All required environment variables are documented.
- [ ] Reverse-proxy and Tailscale/private-network notes are documented.
- [ ] Backup security warnings are prominent.
- [ ] SMTP-less invitation and reset administration are documented.
- [ ] The known limitations match the version-1 non-goals.
- [ ] Version and build identifiers appear in the admin status page.
- [ ] OCI image labels and release metadata are complete.

## Exit Gate

M12 is complete when the release candidate passes the full quality gate and has no unresolved release-blocking defect.

---

# M13 — Version 1 Release

## Objective

Publish the approved Glacier Notes Cloud v1 release and establish the baseline for maintenance.

## Scope

- Final versioning
- OCI image publication
- Release notes
- Tagged source
- Checksums/SBOM where available
- Migration baseline
- Support/issue templates
- Post-release validation

## Deliverables

1. Tagged source release.
2. Versioned OCI image.
3. Versioned Docker Compose example.
4. Release notes.
5. Configuration reference.
6. Upgrade notes.
7. Known limitations.
8. Software bill of materials.
9. Checksums/signatures where supported.
10. Issue templates.
11. v1 project board closure.

## Acceptance Criteria

- [ ] The release is built from the tagged commit.
- [ ] CI for the tagged commit passes.
- [ ] The OCI image version is immutable.
- [ ] The application reports the released version.
- [ ] A clean installation from published artifacts succeeds.
- [ ] A restore from the release-candidate backup succeeds with released artifacts.
- [ ] Release notes list version-1 features and explicit non-goals.
- [ ] Default configuration contains no unsafe sample secret.
- [ ] The published Compose file uses persistent volumes.
- [ ] Documentation links resolve.
- [ ] Known issues contain no undisclosed data-loss or authorization defect.
- [ ] The release owner formally approves the v1 acceptance checklist.

## Exit Gate

M13 is complete when published v1 artifacts are reproducibly installable and the release approval is recorded.

---

## 4. Cross-Milestone Definition of Done

A feature or story is not done until all applicable conditions are satisfied.

### Contract

- [ ] OpenAPI contract updated first.
- [ ] Generated code refreshed.
- [ ] Request, response, and error behavior documented.
- [ ] No breaking API change is introduced without explicit approval.

### Backend

- [ ] Business logic implemented outside transport classes.
- [ ] Ownership and role checks implemented server-side.
- [ ] Validation and safe errors implemented.
- [ ] Database migration added when necessary.
- [ ] Unit and PostgreSQL integration tests pass.
- [ ] Logs contain no prohibited content.

### Frontend

- [ ] Generated API client is used.
- [ ] Loading, empty, success, error, and unauthorized states are handled.
- [ ] Keyboard use and accessible labels are covered.
- [ ] English and German strings are present.
- [ ] Dark and light themes remain readable.
- [ ] Frontend tests pass.

### Security

- [ ] Authentication/authorization tests cover the feature.
- [ ] CSRF behavior is correct for mutations.
- [ ] Sensitive fields are redacted.
- [ ] Rate limits are applied where tokens or credential attempts are involved.
- [ ] Content is sanitized before rendering.

### Operations

- [ ] Relevant metrics avoid personal labels.
- [ ] Relevant audit events contain only approved metadata.
- [ ] Background operations are retry-safe.
- [ ] Configuration and documentation are updated.

### Quality

- [ ] Acceptance criteria are traceable to tests.
- [ ] No open blocker or critical defect remains.
- [ ] Code review is complete.
- [ ] CI passes from a clean checkout.

---

## 5. Recommended Release Blocking Severity

### Blocker

The release cannot proceed when a defect can cause:

- Cross-user data access
- Administrator access to prohibited note content
- Authentication bypass
- Permanent unintended data loss
- Corrupt import without recovery
- Secret disclosure
- Last-administrator loss
- Unusable installation or migration
- Backup restore failure for the supported procedure

### Critical

The release normally cannot proceed when a defect causes:

- Common note edits to be lost
- Optimistic locking to fail
- Account deletion to leave substantial undeclared user content
- Quota bypass with operational risk
- Token replay or serious throttling failure
- Unsupported public exposure of management metrics
- Major desktop import incompatibility

### Major

May block depending on affected workflow:

- A core note feature is unavailable
- Search returns incorrect owned results
- One supported image backend is unusable
- A required admin action fails
- German or English UI has a major incomplete workflow

### Minor

Does not normally block release:

- Cosmetic issue without accessibility impact
- Non-critical wording problem
- Low-impact layout issue
- Rare recoverable workflow inconvenience

---

## 6. Test Traceability Matrix Requirements

Before M12 can close, a matrix shall connect each requirement to one or more of:

- Unit test
- PostgreSQL integration test
- API contract test
- Angular component test
- End-to-end test
- Security test
- Manual deployment test
- Backup/restore test
- Desktop compatibility fixture

At minimum, the matrix shall explicitly cover:

1. Ownership isolation
2. ADMIN content restrictions
3. Last-administrator protection
4. Session revocation
5. Invitation and reset token safety
6. Optimistic locking
7. Version history
8. Image quota and storage backends
9. Search isolation
10. Import conflict strategies
11. Cross-user UUID collisions
12. Account deletion
13. Audit retention
14. Backup feature gate and restore
15. Tombstone privacy
16. Desktop round-trip compatibility

---

## 7. Recommended Issue/Epic Structure

Each milestone may be represented by one project epic.

Suggested issue types:

- `architecture`
- `openapi`
- `backend`
- `frontend`
- `database`
- `security`
- `operations`
- `testing`
- `documentation`
- `compatibility`

Every implementation issue should contain:

- Requirement reference
- OpenAPI operation IDs, when applicable
- User-visible behavior
- Security/ownership implications
- Acceptance criteria
- Test expectations
- Dependencies
- Explicitly excluded behavior

---

## 8. Version 1 Scope Guard

The following shall not be pulled into a milestone unless the specification is formally amended:

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
- Desktop synchronization itself

Version 1 shall provide groundwork for future synchronization, not a partial untested sync implementation.

---

## 9. Final Release Acceptance Summary

Glacier Notes Cloud v1 is ready for release only when:

1. A new Docker Compose installation can be securely bootstrapped.
2. Invited users can activate accounts with or without SMTP.
3. Authentication, lockout, sessions, and CSRF meet the security requirements.
4. User content is strictly isolated.
5. Administrators cannot inspect user note content.
6. Core desktop note features work in the browser.
7. Images work with all supported deployment-time backends.
8. Search, autosave conflicts, and version history behave correctly.
9. Desktop-compatible import/export passes fixtures.
10. Account lifecycle and deletion rules are enforced.
11. Audit, health, metrics, cleanup jobs, and optional backup work.
12. Backup restoration succeeds.
13. Security regression tests pass.
14. Deployment and operator documentation are complete.
15. No blocker or critical release defect remains.
