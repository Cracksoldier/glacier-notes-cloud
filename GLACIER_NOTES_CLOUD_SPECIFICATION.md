# Glacier Notes Cloud — Product and Technical Specification

**Status:** Approved for implementation planning  
**Specification version:** 1.0  
**Date:** 2026-07-20  
**Product:** Glacier Notes Cloud  
**Primary deployment model:** Self-hosted, single-instance web application  
**Backend:** Quarkus  
**Frontend:** Angular  
**Database:** PostgreSQL  
**API approach:** OpenAPI-first  
**Desktop client:** Glacier Notes Electron application  
**Desktop repository:** `https://github.com/Cracksoldier/glacier-notes`

---

## 1. Document Purpose

This document defines the product, functional, security, data, API, deployment, compatibility, and operational requirements for Glacier Notes Cloud.

Glacier Notes Cloud is a self-hostable, multi-user web version of the Glacier Notes desktop application. It carries forward the desktop application's note-taking capabilities while adding:

- Full user authentication
- Administrative user and invitation management
- Per-user content isolation
- Server-side persistence
- Server-side search
- Session management
- Account lifecycle management
- Audit logging
- Operational backup support
- Future compatibility with desktop synchronization

This specification is intended to be sufficiently complete for:

- Architecture and implementation planning
- OpenAPI contract design
- Database schema design
- Frontend feature planning
- Security review
- Test planning
- Docker-based deployment
- Acceptance testing

---

## 2. Source Requirements and Compatibility Baseline

The cloud product is derived from the existing Glacier Notes desktop specification and implementation.

The desktop compatibility baseline includes:

- UUID-based notebooks, notes, labels, checklist items, and image assets
- Text notes and checklist notes
- Markdown source content
- Image references through stable asset IDs
- Labels, colors, pinning, archive, and trash
- Dark and light themes
- English and German localization
- Stable `.glacier.json` export and import
- Typed data-access operations currently exposed through the Electron preload API

The cloud product shall preserve the portable content model wherever possible. Cloud-specific data such as ownership, access control, server versions, session data, and audit data shall not be inserted into portable note content in a way that breaks desktop import compatibility.

The existing desktop client is a separate product in version 1. Cloud synchronization is not part of version 1, but the cloud API and database model shall be designed so that desktop synchronization can be added later without replacing core identifiers or redesigning all content endpoints.

---

## 3. Product Vision

Glacier Notes Cloud shall provide a private, self-hostable note system similar in interaction style to Google Keep while remaining suitable for families, small teams, and single organizations that want to control their own data.

Each installation represents one organization or trusted group. The installation has a shared user directory but no workspaces or multi-tenant organizations inside the instance.

Users shall have private notes, notebooks, labels, images, preferences, and exports. Administrators shall manage accounts and system settings without gaining access to note content.

---

## 4. Goals

### 4.1 Product Goals

1. Provide a browser-based version of Glacier Notes.
2. Preserve the desktop application's core note-taking behavior.
3. Support multiple users with secure authentication.
4. Ensure strict ownership isolation between users.
5. Allow administrators to invite and create users through a small dashboard.
6. Provide a simple Docker Compose deployment.
7. Use PostgreSQL as the only supported version-1 database.
8. Maintain desktop-compatible import and export.
9. Define all application APIs with OpenAPI before implementation.
10. Prepare the data and API architecture for future desktop synchronization.
11. Keep the application useful without external SaaS dependencies.
12. Support optional SMTP but remain administrable without SMTP.

### 4.2 Technical Goals

1. Use Quarkus for backend services.
2. Use Angular for the web frontend.
3. Serve the compiled Angular application from the Quarkus application container.
4. Store relational application data in PostgreSQL.
5. Support filesystem, PostgreSQL, or S3-compatible image storage through one storage abstraction.
6. Use database-backed sessions and jobs rather than process-local state.
7. Use optimistic locking for concurrent edits.
8. Provide structured logs, health endpoints, and optional metrics exposure.
9. Keep secrets outside normal application settings and outside backups.
10. Generate the Angular API client from the OpenAPI contract.

---

## 5. Non-Goals for Version 1

The following are explicitly outside version 1:

- Offline web editing
- Progressive Web App synchronization
- Real-time collaborative editing
- Real-time cross-device note updates
- Shared notes
- Shared notebooks
- Public share links
- Note ownership transfer
- External OpenID Connect authentication
- LDAP or Active Directory authentication
- Passkeys
- Multi-factor authentication
- CAPTCHA
- Animated GIF uploads
- Automatic migration between image storage backends
- Horizontal scaling as an officially supported deployment
- Kubernetes as an officially supported deployment
- Plain JVM, systemd, NAS-app-store, or Windows-service packaging
- In-application email composition and delivery of note content
- Virus scanning
- Application-managed database encryption
- Application-managed encrypted backups
- Mandatory application-level HTTPS enforcement
- Built-in Prometheus or Grafana deployment
- Mobile-native applications
- Multiple organizations or workspaces in one installation
- Administrator access to user note content

Future requirements are described in Section 32.

---

## 6. User Types and Authorization Roles

### 6.1 Roles

Version 1 shall support exactly two roles.

#### USER

A user may:

- Authenticate
- Manage their own profile
- Change their own password
- Request an email-address change
- View and revoke their own sessions
- Manage their own notebooks
- Manage their own notes
- Manage their own checklists
- Manage their own labels
- Upload and remove their own images
- Search their own content
- Import data into their own account
- Export their own data when exports are enabled
- Configure their own supported preferences
- Delete their own account when self-deletion is enabled

#### ADMIN

An administrator has all USER capabilities and may additionally:

- View the user directory
- Invite users
- Create activation links
- Revoke or resend invitations
- Activate and deactivate users
- Initiate account deletion
- Immediately delete an account when explicitly confirmed
- Change a user's role
- Change a user's username or email address
- Generate password-reset links
- Unlock locked accounts
- Revoke all sessions for a user
- View storage-usage totals
- Change non-secret instance settings
- View SMTP status and run a connection test
- View application and health status
- View and export administrative audit events
- Initiate a full server backup when enabled
- Perform blind imports into a selected user account

### 6.2 Administrator Content Restrictions

Administrators shall not be authorized to:

- View another user's note titles
- View another user's note bodies
- View another user's checklist text
- View another user's images
- Search another user's notes
- Preview another user's imports
- Download another user's regular export
- Edit another user's note content
- Use impersonation to access another user's content

Administrative statistics shall expose counts and byte totals only.

### 6.3 Last-Administrator Protection

The system shall always retain at least one active administrator.

The last active administrator shall not be:

- Deactivated
- Deleted
- Changed to USER
- Allowed to self-delete

An administrator shall not be allowed to remove their own ADMIN role when they are the last active administrator.

Blocked actions shall return a clear business error and the dashboard shall explain the reason.

---

## 7. Tenancy and Ownership Model

A Glacier Notes Cloud installation represents one organization, family, or team.

There are no workspace entities in version 1.

Every user-owned content record shall have an immutable `ownerId`.

Ownership shall be enforced in the service and persistence layers. Access control shall never rely only on client-supplied filters.

For every user-content lookup, update, delete, restore, download, and search operation, the effective query shall include the authenticated user's ownership scope.

A UUID collision with an entity owned by another user shall never permit access to or replacement of that entity.

---

## 8. Authentication

### 8.1 Login Identifier

Users shall log in with one input field that accepts either:

- Username
- Email address

### 8.2 Username Rules

- Every user shall have one username.
- Usernames shall be unique across the installation.
- Original casing shall be preserved for display.
- Login and uniqueness checks shall be case-insensitive.
- `Andreas`, `andreas`, and `ANDREAS` shall be treated as the same username.
- Usernames shall be normalized before comparison.
- Leading and trailing whitespace shall be removed.
- The allowed username character policy shall be configurable only through a future version; version 1 shall use a documented fixed policy.
- Recommended fixed policy: 3–64 characters using Unicode letters, Unicode numbers, `.`, `_`, and `-`.
- Usernames shall not contain whitespace.
- Reserved route names and security-sensitive names may be rejected.

The database shall store both:

- `username`
- `usernameNormalized`

A unique constraint shall exist on `usernameNormalized`.

### 8.3 Email Rules

- Every user shall have one email address.
- Email addresses shall be unique across the installation.
- Original casing shall be preserved for display.
- Login and uniqueness checks shall be case-insensitive.
- Leading and trailing whitespace shall be removed.
- The database shall store both `email` and `emailNormalized`.
- A unique constraint shall exist on `emailNormalized`.

### 8.4 Display Name

- Display name is separate from username.
- Display names need not be unique.
- Users may change their own display name.
- Display names shall not be accepted as login identifiers.

### 8.5 Password Storage

Passwords shall be hashed with Argon2id.

The system shall never:

- Store plaintext passwords
- Log plaintext passwords
- Return password hashes through APIs
- Allow administrators to view user passwords
- Include password hashes in user-facing exports
- Include password hashes in routine diagnostic output

Argon2id parameters shall be configurable through deployment configuration, with secure documented defaults.

### 8.6 Password Policy

Default password rules:

- Minimum length: 12 characters
- Maximum accepted length: at least 128 characters
- Passwords shall not contain whitespace
- No mandatory uppercase requirement
- No mandatory lowercase requirement
- No mandatory number requirement
- No mandatory symbol requirement
- Common or compromised passwords shall be rejected by default
- The compromised-password check may be disabled by an administrator
- Password-history enforcement is disabled by default
- Password-history enforcement may be enabled
- When enabled, the previous five password hashes shall be checked

Password validation shall use generic error responses that do not expose sensitive implementation details.

### 8.7 Authentication Failure Behavior

The login endpoint shall not reveal whether the supplied username or email exists.

Default failure policy:

- Rate-limit by normalized account identifier
- Rate-limit by client IP address
- Introduce increasing delays after five failed attempts
- Temporarily lock the account for 15 minutes after ten failed attempts
- Reset the account failure counter after successful login
- Permit administrators to unlock an account
- Apply separate limits to login, invitation, password-reset, and token-validation endpoints

The thresholds and lock duration shall be administrator-configurable within safe bounds.

### 8.8 Sessions

The application shall use server-managed sessions.

Default session durations:

- Normal session: 12 hours
- Remember-me session: 30 days

Session records shall be stored in PostgreSQL or another shared persistence layer, not in Quarkus process memory.

Each session shall have:

- ID
- User ID
- Creation time
- Last-seen time
- Absolute expiration time
- Remember-me flag
- Revocation time
- Hashed session token or equivalent secure server-side representation
- Client IP address or security metadata as needed
- Normalized client/browser description

Users shall be able to:

- View their active sessions
- Revoke an individual session
- Revoke all other sessions

Administrators shall be able to revoke all sessions for a selected user.

A password change, account deactivation, account deletion, or security reset shall revoke all existing sessions for that user.

### 8.9 Browser Cookie Requirements

Authentication cookies shall be:

- `HttpOnly`
- `SameSite=Lax` by default
- Scoped to the application path
- Marked `Secure` when the public application URL uses HTTPS
- Rotated after authentication and privilege changes
- Protected against fixation

State-changing requests shall use CSRF protection.

The production deployment documentation shall strongly recommend TLS through a reverse proxy. Application-level forced HTTPS redirection is not a version-1 requirement.

---

## 9. Initial Administrator Bootstrap

A new installation shall expose a one-time setup flow only while no user account exists.

The setup flow shall:

1. Require a cryptographically random bootstrap token supplied through an environment variable or mounted secret.
2. Accept creation of the first ADMIN account.
3. Apply normal username, email, and password validation.
4. Mark the instance as initialized in persistent storage.
5. Permanently disable the bootstrap endpoint after successful creation.
6. Reject further use even if the bootstrap token remains configured.
7. Record the bootstrap completion as a security audit event.

The application shall warn or fail startup when an uninitialized instance has no bootstrap token configured.

The bootstrap token shall never be written to logs, audit records, or API responses.

---

## 10. Invitations and Manual Account Creation

### 10.1 Registration Policy

Public self-registration shall not exist.

Accounts may be created only through:

- Administrator invitation
- Administrator-generated activation flow

Administrators shall not assign or communicate temporary passwords.

### 10.2 Invitation Creation

An administrator shall provide:

- Email address
- Username, optionally preselected
- Role
- Optional display name

The server shall create:

- A cryptographically random token with at least 256 bits of entropy
- A stored token hash
- Creation time
- Expiration time
- Creator administrator ID
- Target email
- Target role
- Invitation status

Default expiration shall be seven days and shall be configurable.

Invitation tokens shall be:

- Single-use
- Revocable
- Opaque
- Stored hashed
- Excluded from logs
- Excluded from audit metadata except through a non-secret invitation ID

### 10.3 Invitation Delivery

When SMTP is configured:

- The system shall send an invitation email.
- The email shall contain an activation URL based on the configured public base URL.
- Administrators may resend the invitation.

When SMTP is not configured:

- The dashboard shall show a copyable activation URL.
- The dashboard shall allow copying the raw full invitation token.
- The token shall be shown only at creation or explicit regeneration where feasible.

### 10.4 Invitation Acceptance

Two acceptance methods shall be supported:

1. Open `/accept-invitation?token=<token>`.
2. Open the invitation page and paste the same full token manually.

No short human-readable invitation code is required in version 1.

The user shall choose or confirm:

- Username
- Display name
- Password

After successful acceptance:

- The invitation shall be marked accepted.
- The account shall become active immediately.
- The invitation token shall become unusable.
- An audit event shall be recorded.
- The user may log in.

### 10.5 Domain Restrictions

Administrators may optionally configure an allowlist of email domains.

Default behavior:

- No domain restriction

When enabled:

- New invitations shall be limited to allowed domains.
- Manual account activation shall follow the same rule unless an explicit protected administrative override is later added.
- Existing users shall not be automatically disabled when the allowlist changes.

### 10.6 Pending Invitation Management

Administrators shall be able to:

- List pending invitations
- Filter by status
- Revoke an invitation
- Resend an invitation
- Generate a replacement token
- Copy the activation link
- View creation and expiration dates

The dashboard shall not display accepted or expired token values.

---

## 11. Password Reset

### 11.1 User-Initiated Reset

When SMTP is configured, users may request a password-reset email.

The request endpoint shall always return a neutral response regardless of account existence.

The reset token shall be:

- Cryptographically random
- Single-use
- Stored hashed
- Short-lived
- Revocable through successful password change
- Subject to separate throttling

Recommended default expiration: 60 minutes.

### 11.2 Administrative Reset Link

Administrators may generate a copyable password-reset link for a user.

Administrators shall not:

- Set a temporary password
- View the resulting password
- Complete the reset on behalf of the user

Generating a reset link shall:

- Invalidate previous reset tokens for the user
- Be recorded in the audit log
- Avoid including the token itself in the audit event

### 11.3 Password Change

An authenticated user changing their password shall:

- Enter the current password
- Satisfy the current password policy
- Cause all active sessions to be revoked
- Receive a new session only after reauthentication or explicit session rotation
- Generate a security audit event

---

## 12. Email Address Changes

### 12.1 User-Initiated Email Change

A user changing their own email shall:

1. Enter their current password.
2. Enter the new email address.
3. Pass uniqueness and format validation.
4. Receive a verification email at the new address.
5. Continue using the old email as the active login address until verification succeeds.
6. Receive a notification at the old address after the change completes.
7. Have all pending email-change tokens invalidated after success.

If SMTP is unavailable, self-service email changes shall be unavailable unless a future verified alternative is implemented.

Recommended token expiration: 24 hours.

### 12.2 Administrator Email Change

An administrator may change a user's email address immediately.

The change shall:

- Require explicit confirmation
- Enforce uniqueness
- Be recorded in the audit log
- Notify the affected user at the old address when SMTP is available
- Revoke pending email-change tokens

The administrator shall not gain access to user content as a result.

---

## 13. Account Status and Lifecycle

### 13.1 Account States

A user account may be:

- `PENDING_ACTIVATION`
- `ACTIVE`
- `LOCKED`
- `DEACTIVATED`
- `PENDING_DELETION`
- `DELETED`

Authentication shall be allowed only for active, non-locked accounts.

### 13.2 Deactivation

When an administrator deactivates a user:

- All sessions shall be revoked immediately.
- Login shall be rejected.
- Data shall remain stored.
- Stored data shall continue to count toward quota and usage.
- Trash cleanup and normal retention jobs shall continue.
- Administrators shall still be unable to inspect content.
- Another invitation using the same normalized email or username shall be rejected.
- The administrator may reactivate the account.

### 13.3 Administrator-Initiated Deletion

Administrators may initiate deletion with:

- Configured retention period
- Explicit immediate-deletion override

Default administrative deletion retention: 30 days, configurable.

During pending deletion:

- Sessions shall be revoked.
- Login shall be rejected.
- Data shall remain inaccessible.
- The user may be restored by an administrator before permanent deletion.
- Deletion date and initiator shall be recorded.

Immediate deletion shall require an explicit destructive confirmation.

### 13.4 User Self-Deletion

Self-deletion shall be immediate when enabled.

The user shall:

1. Be authenticated.
2. Re-enter their password.
3. Be shown a clear irreversible-deletion warning.
4. Explicitly confirm permanent deletion.

The confirmation shall state that the following are permanently removed:

- Notes
- Notebooks
- Checklists
- Labels
- Images
- User settings
- Sessions
- Version history
- Import state
- Export state

Self-deletion shall:

- Revoke all sessions immediately.
- Cancel pending exports.
- Cancel pending image processing.
- Cancel pending email changes and reset tokens.
- Be irreversible.
- Be blocked for the last active administrator.

An administrator may disable self-deletion through instance settings.

### 13.5 Permanent Deletion

Permanent deletion shall remove user-owned content and associated binary data.

The system shall retain only legally and technically necessary minimal records, including:

- Non-content administrative audit records until audit retention expires
- Minimal deletion tombstones for synchronization compatibility
- Aggregate operational metrics without personal content

Tombstones shall not include:

- Note titles
- Note bodies
- Checklist text
- Image filenames
- Image data

---

## 14. Core Note-Taking Features

Version 1 shall carry forward the following desktop capabilities.

### 14.1 Notebooks

Users may:

- Create notebooks
- Rename notebooks
- Assign an optional notebook color
- Reorder notebooks
- Delete notebooks
- Move notes between notebooks
- View note counts

Every user shall have a default notebook.

The default notebook:

- Is created during account activation
- May be renamed
- Cannot be deleted

When deleting a non-default notebook, the user shall be offered:

- Move contained notes to the default notebook
- Move contained notes to trash

Notebook IDs shall remain stable UUIDs.

### 14.2 Notes

Supported note types:

- `text`
- `checklist`

Each note supports:

- Title
- Markdown content for text notes
- Checklist items for checklist notes
- Images
- Pinning
- Archive status
- Color
- Labels
- Trash state
- Creation time
- Update time
- Optimistic version

Users may:

- Create notes
- Edit notes
- Convert text notes to checklist notes
- Convert checklist notes to text notes
- Move notes between notebooks
- Pin and unpin notes
- Archive and unarchive notes
- Move notes to trash
- Restore notes
- Permanently delete notes
- Restore an older version

### 14.3 Card Grid

The default note view shall use a responsive card grid similar to the desktop product.

Cards shall display appropriate previews:

- Title
- Text excerpt
- Checklist excerpt
- Image thumbnail
- Labels
- Pin state
- Archive state
- Color

Pinned notes shall be shown before other notes when applicable.

### 14.4 Editor

The editor may be implemented as a modal or detail route but shall support:

- Title editing
- Markdown toolbar
- Note color
- Labels
- Images
- Pinning
- Archive
- Move
- Delete
- Autosave
- Save-and-close
- Optimistic conflict handling
- Version history

### 14.5 Autosave

Autosave shall be debounced.

Recommended default debounce: 500 milliseconds after the last meaningful edit.

Autosave shall:

- Send the currently known optimistic version
- Update the current record without creating a history snapshot every time
- Clearly indicate saving, saved, offline/error, and conflict states
- Avoid silent data loss

### 14.6 Trash

Deleting a note shall move it to trash rather than permanently deleting it.

Trash behavior:

- `deletedAt` is set by the server.
- Trashed notes are excluded from default search.
- Users may restore notes.
- Users may permanently purge individual notes.
- Users may empty trash.
- Automatic trash cleanup uses the effective user/system retention setting.

The administrator defines the default trash-retention period.

Users may disable automatic purge for their own notes unless the administrator has configured a policy that forbids disabling it.

### 14.7 Archive

Archived notes shall:

- Be hidden from the default notebook view
- Remain searchable by default when archive filtering allows
- Be available through an Archive view
- Remain editable

### 14.8 Labels

Users may:

- Create labels
- Rename labels
- Delete labels
- Assign multiple labels to a note
- Filter notes by label

Label names shall be unique per user using case-insensitive normalized comparison.

Deleting a label shall remove its assignments without deleting notes.

### 14.9 Note Colors

Notes shall support a fixed color palette.

The palette shall:

- Work in dark and light themes
- Maintain readable contrast
- Preserve portable color identifiers where possible
- Avoid allowing arbitrary unsafe CSS values from API input

### 14.10 Checklists

Checklist items shall support:

- UUID
- Text
- Checked state
- Sort order
- Inline Markdown
- Add
- Edit
- Delete
- Reorder
- Toggle from card
- Toggle from editor

Users may enable “move checked items to bottom” as a preference.

### 14.11 Markdown

Text notes shall support Markdown including:

- Headings
- Bold
- Italic
- Lists
- Links
- Code spans
- Code blocks
- Blockquotes
- Tables
- Images through Glacier asset references

Raw HTML shall not be rendered by default.

Rendered HTML shall be sanitized.

External links shall open in a new browser context with protections against opener access.

Checklist text shall support inline Markdown only.

### 14.12 Keyboard Shortcuts

The web application shall support applicable desktop shortcuts:

| Shortcut | Action |
|---|---|
| `Ctrl/Cmd+N` | New text note |
| `Ctrl/Cmd+Shift+N` | New checklist note |
| `Ctrl/Cmd+F` | Focus search |
| `Ctrl/Cmd+Enter` | Save and close editor |
| `Esc` | Close editor or dialog |
| `Ctrl/Cmd+B` | Bold |
| `Ctrl/Cmd+I` | Italic |
| `Ctrl/Cmd+E` | Open import/export |
| `Ctrl/Cmd+,` | Open settings |
| `Ctrl/Cmd+/` | Show shortcut help |

Browser-reserved shortcuts shall degrade gracefully.

Desktop-only tray, global shortcut, and always-on-top quick-note features are removed from the cloud product.

---

## 15. Images and Binary Assets

### 15.1 Supported Formats

Version 1 shall support:

- PNG
- JPEG
- WebP

Animated GIF is not supported in version 1.

Unsupported formats shall be rejected before storage.

### 15.2 Upload Methods

The Angular application shall support:

- File picker
- Drag and drop
- Clipboard paste

### 15.3 Size Limits

Defaults:

- Maximum incoming image size: 10 MB
- Per-user total storage quota: 1 GB

Both values shall be administrator-configurable.

### 15.4 Image Processing

When an image exceeds the configured limit:

1. The server shall attempt safe downscaling or re-encoding.
2. The processed image shall be validated.
3. The upload shall be rejected if the result remains over the limit.

Image processing shall enforce:

- Pixel-dimension limits
- Decode time limits
- Memory limits
- MIME signature validation
- Rejection of malformed input
- Metadata removal where appropriate

### 15.5 Asset Model

Each image shall have a stable UUID independent of physical storage.

Metadata shall include:

- ID
- Owner ID
- MIME type
- Original filename, optional
- Byte size
- Width
- Height
- Content hash
- Storage backend
- Storage key
- Creation time

Image filenames shall not be exposed through metrics.

### 15.6 Reference and Garbage Collection

Notes shall reference images by stable ID.

Removing a reference shall not immediately delete an image if another owned note still references it.

An image shall be eligible for deletion when:

- No note owned by the same user references it
- No retained note version references it
- No active import or export job references it
- The configured orphan grace period has expired

### 15.7 Storage Quota

Quota accounting shall include:

- Active image binaries
- Images referenced only by retained note versions
- Temporary imported image data after validation, where practical

Quota checks shall be atomic enough to prevent concurrent uploads from substantially exceeding the configured limit.

Administrators may view byte totals and counts but not image contents or filenames.

---

## 16. Image Storage Backends

### 16.1 Supported Backends

Exactly one image backend shall be active per installation:

- Filesystem
- PostgreSQL binary storage
- S3-compatible object storage

Filesystem shall be the default.

### 16.2 Deployment-Time Selection

The backend shall be selected through deployment configuration.

The selected backend shall be stored as part of persistent instance metadata.

After the first image has been stored:

- The configured backend shall be treated as immutable in version 1.
- Startup shall fail with a clear configuration error if the configured backend differs from persistent instance metadata.
- The application shall not attempt automatic migration.

### 16.3 Filesystem Backend

The filesystem backend shall:

- Use a persistent mounted volume
- Avoid user-controlled path segments
- Derive paths from server-generated storage keys
- Use atomic writes
- Prevent path traversal
- Support streaming reads
- Maintain appropriate permissions
- Keep temporary processing files separate

### 16.4 PostgreSQL Backend

The PostgreSQL backend shall:

- Store binary data in a dedicated asset table or supported large-object strategy
- Avoid loading large binaries fully into memory
- Keep metadata relationally queryable
- Participate in deletion integrity
- Be documented as potentially increasing backup size and database I/O

### 16.5 S3-Compatible Backend

The S3 backend shall support configuration for:

- Endpoint
- Region
- Bucket
- Access key through secret configuration
- Secret key through secret configuration
- Path-style access where required
- TLS behavior
- Optional server-side object-storage encryption settings

Objects shall not be publicly readable.

Downloads shall be authorized by the application. Pre-signed URLs may be used only with short expiration and ownership checks.

### 16.6 Future Migration

Storage migration tooling is a future feature.

Version 1 documentation shall state that switching backends requires an external or future migration process.

---

## 17. Search

### 17.1 Search Engine

Version 1 shall use PostgreSQL full-text search.

No external search engine is required.

### 17.2 Indexed Content

Search shall index:

- Note title
- Markdown source content
- Checklist item text

Search shall be language-neutral by using a language-neutral PostgreSQL text-search configuration such as `simple`.

### 17.3 Default Scope

Default search shall include:

- All non-trashed notes owned by the current user
- Archived notes unless excluded by a filter

Default search shall exclude:

- Trashed notes
- Other users' content

### 17.4 Filters

Search shall support filters for:

- Notebook
- Label
- Archived status
- Trash status
- Note type
- Pinned status

The UI shall allow switching to a trash search explicitly.

### 17.5 Additional Matching

Labels and image filenames may be filterable or matched through explicit search options, but the primary ranked full-text index is defined for title, Markdown, and checklist text.

### 17.6 Search Results

Results shall support:

- Pagination
- Relevance ranking
- Stable secondary ordering
- Match highlighting based on safe server-provided fragments or client-side matching
- Ownership isolation
- Appropriate empty-query behavior

---

## 18. Concurrent Editing and Optimistic Locking

### 18.1 Version Field

Each mutable synchronizable entity shall include an optimistic `version` value.

Note update requests shall include the version last read by the client.

### 18.2 Conflict Behavior

When the stored version differs from the submitted version:

- The server shall reject the update.
- The server shall return HTTP `409 Conflict`.
- The response shall include non-sensitive conflict metadata and the current entity version.
- The frontend shall tell the user that the note changed elsewhere.
- The frontend shall offer reload and copy-current-draft actions.
- The system shall not silently apply last-write-wins behavior.

A version-history snapshot shall be created when a conflicting update is rejected, subject to deduplication.

### 18.3 Cross-Device Refresh

Real-time updates are not required.

The frontend shall refresh relevant lists:

- After successful save
- After import
- After restore or deletion
- When the browser window regains focus
- On explicit user refresh

WebSockets and Server-Sent Events are not required in version 1.

---

## 19. Note Version History

### 19.1 Retention

Defaults:

- Maximum retained versions per note: 20
- Maximum retention age: 30 days

Both values shall be administrator-configurable.

The effective cleanup policy shall remove versions exceeding either configured boundary.

### 19.2 Snapshot Triggers

A version snapshot shall be created when:

1. A note editor is explicitly closed after meaningful changes.
2. At least five minutes have passed since the previous stored version and meaningful changes are committed.
3. A conflicting update is rejected.
4. A note is restored from trash.
5. An older version is restored.

Normal debounced autosaves shall update the current note without creating a new snapshot for every request.

### 19.3 Version Contents

A note version shall contain the restorable note state:

- Type
- Title
- Content
- Checklist
- Image references
- Pin state
- Archive state
- Color
- Labels
- Notebook reference where appropriate
- Source note version
- Snapshot reason
- Snapshot time

Versions shall be owned by the same user as the note.

### 19.4 Restore

Restoring a version shall:

- Require the current optimistic note version
- Create a snapshot of the pre-restore current state
- Restore the selected historical content
- Increment the current note version
- Preserve the note ID
- Generate an audit/security-neutral application event where useful

---

## 20. Import and Export

### 20.1 Compatibility Requirement

The cloud application shall remain compatible with the desktop `.glacier.json` schema version 1.

Portable entity structures shall remain compatible for:

- Notebooks
- Notes
- Checklist items
- Labels
- Images
- Default notebook metadata
- Export scope metadata

Cloud-only metadata shall be placed in optional envelope extensions and shall not make the file unreadable by compatible desktop versions that ignore unknown fields.

### 20.2 User Export

Users may export only their own content.

Export availability shall be administrator-configurable.

Supported scopes:

- Full user data
- One notebook
- One note

Images shall be base64 encoded in the portable JSON format.

Export generation shall stream data where possible and shall not require loading the complete export into application memory.

### 20.3 User Import

Normal users shall import into their own account.

The import process shall:

1. Receive the file through a bounded upload.
2. Store it temporarily outside permanent asset storage.
3. Validate format and schema version.
4. Validate UUIDs.
5. Validate relationships.
6. Validate image MIME types and payloads.
7. Validate quota impact.
8. Report counts and structural errors.
9. Detect ID conflicts inside the target user's ownership scope.
10. Request a conflict strategy when required.
11. Apply changes transactionally where practical.
12. Roll back or compensate on failure.
13. Remove temporary files.

### 20.4 Conflict Strategies

The cloud application shall support the same choices as the desktop client:

- **Add as copies:** Imported entities receive remapped IDs as necessary.
- **Replace existing by ID:** Matching entities owned by the target user are replaced.

Cross-user UUID collisions shall never replace another user's entity.

A collision with another user's UUID shall be:

- Remapped when using add-as-copies
- Rejected or safely remapped according to the import operation
- Never exposed as information about the other user

### 20.5 Administrator Blind Import

Administrators may import into a selected target account.

This shall be a blind operation:

- The administrator selects the target user.
- The administrator uploads the file.
- Validation reports only structure, counts, and generic errors.
- The administrator cannot preview titles, note bodies, checklist text, images, or filenames.
- The action is recorded in the administrative audit log.
- Ownership is assigned to the target user.

### 20.6 Import Safety

Import shall enforce:

- Maximum upload size
- Maximum entity counts
- Maximum decoded image bytes
- Maximum nesting/depth
- Streaming parsing where possible
- Timeouts
- Cancellation
- Quota validation
- Safe rollback

### 20.7 Portable Envelope

The portable envelope shall continue to identify the Glacier export format and schema version.

A cloud export may add an optional extension similar to:

```json
{
  "format": "glacier-notes-export",
  "schemaVersion": 1,
  "exportedAt": "2026-07-20T10:00:00Z",
  "scope": {
    "type": "full"
  },
  "notebooks": [],
  "notes": [],
  "labels": [],
  "images": [],
  "cloudMetadata": {
    "source": "glacier-notes-cloud",
    "apiVersion": "v1"
  }
}
```

Portable entities shall not contain authentication or authorization data.

---

## 21. Email Sharing

Note sharing shall continue to use the user's external mail client through a `mailto:` URL.

The frontend shall:

- Use note title as the suggested subject
- Use Markdown text as the body
- Render checklist items as Markdown checklist lines
- Warn that images are not attached
- Offer export as an alternative for image-bearing notes
- Handle browser and operating-system URL length limitations gracefully

The server shall not send note content through SMTP in version 1.

SMTP shall be reserved for:

- Invitations
- Password reset
- Email verification
- Security notifications
- Administrative test messages

---

## 22. User Preferences

Each user shall have settings including:

- Theme: `dark` or `light`
- Language: `en` or `de`
- Move checked items to bottom
- Trash auto-purge preference, subject to policy
- Last selected notebook
- Optional UI density or layout state when later defined

Desktop-only settings shall not be part of cloud user settings:

- Close to tray
- Quick-note global shortcut
- Window bounds

The default theme shall remain dark.

The initial language shall use browser locale when it is German or English; otherwise English.

---

## 23. Instance Settings

Administrators shall be able to configure non-secret settings.

Version-1 settings include:

- Instance name
- Instance logo
- Default language
- Allowed upload types within implemented formats
- Maximum image size
- Per-user storage quota
- Default trash retention
- Whether users may disable auto-purge
- Administrative account-deletion retention
- Invitation expiration
- Password-reset expiration
- Email-change verification expiration
- Normal session duration
- Remember-me duration
- Whether user exports are enabled
- Whether self-account deletion is enabled
- Public base URL
- SMTP sender name
- SMTP sender address
- Optional allowed email domains
- Note-version maximum count
- Note-version retention days
- Audit retention days
- Operational-log retention guidance
- Login-lockout thresholds
- Common-password check enabled
- Password-history enabled

Settings shall have:

- Validation
- Safe minimum and maximum bounds
- Change audit events
- Clear distinction between runtime-changeable and restart-required settings

### 23.1 Secret Settings

Secrets shall not be entered or displayed through the dashboard in version 1.

Secrets include:

- SMTP password
- Database password
- S3 secret key
- Bootstrap token
- Cryptographic signing keys
- Session encryption or signing keys

Secrets shall be supplied through:

- Environment variables
- Mounted secret files
- Container orchestration secrets where available

---

## 24. Administrative Dashboard

### 24.1 Dashboard Pages

Version 1 shall include:

1. Overview
2. User list
3. User details
4. Create user or invitation
5. Pending invitations
6. Storage usage
7. Instance settings
8. SMTP status
9. Application status
10. Audit log
11. Backup jobs when enabled

### 24.2 User List

The user list may display:

- Username
- Display name
- Email
- Role
- Account status
- Created time
- Last successful login time, when retained
- Storage byte total
- Note count
- Notebook count
- Image count

It shall not display content previews.

### 24.3 User Administration Actions

Supported actions:

- Invite
- Resend invitation
- Revoke invitation
- Activate
- Deactivate
- Unlock
- Change role
- Change username
- Change email
- Generate reset link
- Revoke sessions
- Begin retained deletion
- Permanently delete
- Blind import

Destructive actions shall require explicit confirmation.

### 24.4 SMTP Status

The dashboard may show:

- Configured or not configured
- Sender name
- Sender address
- Connection-test result
- Last successful email time
- Last generic failure category

The dashboard shall not:

- Show SMTP passwords
- Accept SMTP passwords
- Return secret connection strings

### 24.5 Application Status

The dashboard may show:

- Application version
- Build identifier
- Database connectivity
- Selected image backend
- Storage connectivity
- SMTP status
- Backup feature status
- Health summary
- Metrics feature status

---

## 25. Administrative Audit Log

### 25.1 Audit Scope

Audit events shall include:

- First-admin bootstrap
- User creation
- Invitation creation
- Invitation resend
- Invitation revocation
- Invitation acceptance
- User activation
- User deactivation
- Role changes
- Username changes by administrator
- Email changes by administrator
- Password-reset link generation
- Account unlock
- Session revocation by administrator
- Account deletion initiation
- Account restoration
- Immediate account deletion
- Instance-setting changes
- SMTP test
- Blind import
- Backup initiation
- Backup completion or failure
- Failed login attempts
- Security-sensitive login events
- Access-control violations where appropriate

Normal successful user logins do not need an audit record by default.

### 25.2 Audit Record Fields

An audit record shall contain:

- Event ID
- Event type
- Event time
- Actor user ID, when applicable
- Target user ID or entity ID, when applicable
- Result
- Full client IP address
- Normalized client/browser description
- Request correlation ID
- Non-secret metadata

Audit metadata shall not contain:

- Passwords
- Session tokens
- Invitation tokens
- Reset tokens
- Note titles
- Note bodies
- Checklist text
- Image filenames
- Image data
- SMTP credentials

### 25.3 Retention

Default administrative and security audit retention: 365 days.

Retention shall be administrator-configurable within safe bounds.

Audit records:

- Cannot be edited through the dashboard
- Cannot be manually deleted through the dashboard
- May be exported as CSV or JSON by administrators
- Are deleted only by the retention process or direct database administration outside the product

### 25.4 Operational Logs

Routine operational logs have a separate default retention target of 30 days.

Log retention may be controlled by container or host logging infrastructure.

---

## 26. Full Server Backup

### 26.1 Feature Gate

Full server backup shall be disabled unless explicitly enabled through an environment variable.

### 26.2 Trigger

When enabled, an administrator may initiate a backup through the dashboard.

The dashboard request shall create a background backup job.

### 26.3 Destination

The backup shall be written to a configured server directory on a persistent mounted volume.

Version 1 shall not download the full backup directly through the browser.

### 26.4 Contents

A backup shall contain:

- PostgreSQL data in a documented restorable format
- Image assets
- Non-secret instance settings
- Manifest
- Application version
- Schema version
- Backup creation time
- Checksums

A backup shall not contain an intentionally collected copy of:

- Database credentials
- SMTP password
- S3 secret key
- Bootstrap token
- Application cryptographic keys

Database data will necessarily contain authentication hashes and user content; documentation shall classify the archive as highly sensitive.

### 26.5 Encryption

Application-managed backup encryption is not included in version 1.

Documentation shall require operators to:

- Restrict file permissions
- Store backups on protected media
- Encrypt backup storage externally
- Apply retention externally
- Test restoration
- Avoid exposing the backup directory through a web server

### 26.6 Backup Job Behavior

A backup job shall have:

- ID
- Initiating administrator
- State
- Start time
- Completion time
- Output path or non-sensitive identifier
- Result size
- Checksum
- Generic error message

The dashboard shall not expose arbitrary filesystem browsing.

Backup creation shall be audited.

---

## 27. API Architecture

### 27.1 OpenAPI-First Requirement

The REST API shall be defined in OpenAPI before endpoint implementation.

The canonical contract shall be stored in the source repository, for example:

```text
openapi/
└── glacier-notes-v1.yaml
```

The OpenAPI document shall define:

- Paths
- HTTP methods
- Operation IDs
- Request schemas
- Response schemas
- Authentication
- CSRF requirements where representable
- Standard error schemas
- Pagination
- Validation constraints
- Examples
- Tags
- Deprecation markers

### 27.2 Code Generation

The build shall generate:

- Angular TypeScript API client
- TypeScript DTOs
- Java API interfaces or server stubs where practical
- Java DTOs where appropriate

Generated code shall not be manually edited.

Generated code shall be isolated from:

- Domain entities
- Persistence entities
- Business services
- Security rules

The backend may implement generated interfaces while mapping generated DTOs to internal domain models.

### 27.3 Contract Stability

All endpoint operation IDs shall be stable.

Breaking changes shall require:

- A new API version
- A documented migration
- Or an explicitly backward-compatible schema evolution

Version 1 shall use:

```text
/api/v1
```

### 27.4 API Style

The API shall use:

- JSON for normal resources
- Multipart upload for images and import files
- Streaming responses for downloads and large exports
- `application/problem+json` for standardized errors
- ISO 8601 UTC timestamps
- UUID strings
- Explicit pagination
- `409 Conflict` for optimistic-lock failures
- `422 Unprocessable Entity` for validly parsed but semantically invalid data where appropriate
- `413 Payload Too Large` for upload limits
- `429 Too Many Requests` for throttling

### 27.5 Pagination

Collection endpoints shall support cursor or page-based pagination.

For future synchronization, synchronizable listing endpoints shall also support:

- `modifiedSince`
- Stable ordering
- Page continuation
- Inclusion of deletion tombstones where authorized

### 27.6 Idempotency

Idempotency support shall be considered for:

- Client-generated note creation
- Image upload finalization
- Import apply
- Backup start
- Future synchronization writes

Client-generated UUIDs may make entity creation naturally retryable when ownership and payload are validated.

---

## 28. Proposed API Surface

The exact OpenAPI contract is a separate deliverable, but version 1 shall cover the following logical operations.

### 28.1 Setup

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/setup/status` | Return whether bootstrap is required |
| `POST` | `/api/v1/setup/administrator` | Create first administrator |

### 28.2 Authentication

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Log in with username/email and password |
| `POST` | `/api/v1/auth/logout` | Revoke current session |
| `GET` | `/api/v1/auth/session` | Return current user/session |
| `POST` | `/api/v1/auth/password-reset/request` | Request reset email |
| `POST` | `/api/v1/auth/password-reset/complete` | Complete reset |
| `POST` | `/api/v1/auth/invitations/accept` | Accept invitation |
| `POST` | `/api/v1/auth/invitations/inspect` | Validate token without revealing excess data |

### 28.3 Current User

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/me` | Current profile |
| `PATCH` | `/api/v1/me` | Change display name or username |
| `POST` | `/api/v1/me/password` | Change password |
| `POST` | `/api/v1/me/email-change` | Begin email change |
| `POST` | `/api/v1/me/email-change/verify` | Verify new email |
| `DELETE` | `/api/v1/me` | Immediate self-deletion |
| `GET` | `/api/v1/me/sessions` | List own sessions |
| `DELETE` | `/api/v1/me/sessions/{sessionId}` | Revoke own session |
| `DELETE` | `/api/v1/me/sessions` | Revoke other/all sessions |
| `GET` | `/api/v1/me/settings` | Read preferences |
| `PATCH` | `/api/v1/me/settings` | Change preferences |
| `GET` | `/api/v1/me/storage` | Own quota usage |

### 28.4 Notebooks

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/notebooks` | List notebooks |
| `POST` | `/api/v1/notebooks` | Create notebook |
| `GET` | `/api/v1/notebooks/{id}` | Read notebook |
| `PATCH` | `/api/v1/notebooks/{id}` | Update notebook |
| `DELETE` | `/api/v1/notebooks/{id}` | Delete notebook with strategy |
| `POST` | `/api/v1/notebooks/reorder` | Reorder notebooks |
| `GET` | `/api/v1/notebooks/default` | Read default notebook |

### 28.5 Notes

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/notes` | List/filter notes |
| `POST` | `/api/v1/notes` | Create note |
| `GET` | `/api/v1/notes/{id}` | Read note |
| `PATCH` | `/api/v1/notes/{id}` | Update with optimistic version |
| `POST` | `/api/v1/notes/{id}/trash` | Move to trash |
| `POST` | `/api/v1/notes/{id}/restore` | Restore from trash |
| `DELETE` | `/api/v1/notes/{id}` | Permanently delete |
| `POST` | `/api/v1/notes/{id}/move` | Move notebook |
| `GET` | `/api/v1/notes/{id}/versions` | List versions |
| `GET` | `/api/v1/notes/{id}/versions/{versionId}` | Read version |
| `POST` | `/api/v1/notes/{id}/versions/{versionId}/restore` | Restore version |
| `DELETE` | `/api/v1/notes/trash` | Empty trash |

### 28.6 Labels

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/labels` | List labels |
| `POST` | `/api/v1/labels` | Create label |
| `PATCH` | `/api/v1/labels/{id}` | Rename label |
| `DELETE` | `/api/v1/labels/{id}` | Delete label |

### 28.7 Images

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/images` | Upload/process image |
| `GET` | `/api/v1/images/{id}` | Stream authorized image |
| `GET` | `/api/v1/images/{id}/metadata` | Read owned metadata |
| `DELETE` | `/api/v1/images/{id}` | Delete if unreferenced |

### 28.8 Search

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/search/notes` | Ranked full-text search |

### 28.9 Transfer

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/exports` | Create user export |
| `GET` | `/api/v1/exports/{id}` | Export status |
| `GET` | `/api/v1/exports/{id}/download` | Download own export |
| `DELETE` | `/api/v1/exports/{id}` | Cancel/delete export |
| `POST` | `/api/v1/imports/inspect` | Upload and inspect |
| `POST` | `/api/v1/imports/{id}/apply` | Apply strategy |
| `DELETE` | `/api/v1/imports/{id}` | Cancel import |

### 28.10 Administration

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/admin/users` | List users |
| `POST` | `/api/v1/admin/invitations` | Create invitation |
| `GET` | `/api/v1/admin/invitations` | List invitations |
| `POST` | `/api/v1/admin/invitations/{id}/resend` | Resend/regenerate |
| `DELETE` | `/api/v1/admin/invitations/{id}` | Revoke |
| `GET` | `/api/v1/admin/users/{id}` | Read administrative metadata |
| `PATCH` | `/api/v1/admin/users/{id}` | Change safe account fields |
| `POST` | `/api/v1/admin/users/{id}/activate` | Activate |
| `POST` | `/api/v1/admin/users/{id}/deactivate` | Deactivate |
| `POST` | `/api/v1/admin/users/{id}/unlock` | Unlock |
| `POST` | `/api/v1/admin/users/{id}/password-reset` | Generate reset link |
| `DELETE` | `/api/v1/admin/users/{id}/sessions` | Revoke sessions |
| `POST` | `/api/v1/admin/users/{id}/deletion` | Schedule deletion |
| `DELETE` | `/api/v1/admin/users/{id}` | Immediate permanent deletion |
| `POST` | `/api/v1/admin/users/{id}/imports/inspect` | Blind import inspect |
| `POST` | `/api/v1/admin/users/{id}/imports/{importId}/apply` | Blind import apply |
| `GET` | `/api/v1/admin/settings` | Read instance settings |
| `PATCH` | `/api/v1/admin/settings` | Change instance settings |
| `GET` | `/api/v1/admin/audit-events` | List audit events |
| `GET` | `/api/v1/admin/audit-events/export` | Export audit events |
| `GET` | `/api/v1/admin/status` | Application status |
| `POST` | `/api/v1/admin/smtp/test` | Test SMTP |
| `POST` | `/api/v1/admin/backups` | Start backup |
| `GET` | `/api/v1/admin/backups` | List backup jobs |
| `GET` | `/api/v1/admin/backups/{id}` | Backup status |

---

## 29. Data Model

### 29.1 Common Rules

Synchronizable entities shall use UUIDs.

Every synchronizable entity shall include:

- `id`
- `createdAt`
- `updatedAt`
- `version`

Server timestamps are authoritative.

Client-generated UUIDs shall be accepted for portable content entities when valid and non-conflicting.

All timestamps shall be stored as PostgreSQL `timestamptz`.

### 29.2 User

Suggested fields:

- `id`
- `username`
- `username_normalized`
- `email`
- `email_normalized`
- `display_name`
- `role`
- `status`
- `password_hash`
- `password_changed_at`
- `failed_login_count`
- `locked_until`
- `created_at`
- `updated_at`
- `activated_at`
- `deactivated_at`
- `pending_deletion_at`
- `deletion_due_at`
- `last_login_at`
- `version`

### 29.3 User Session

Suggested fields:

- `id`
- `user_id`
- `token_hash`
- `remember_me`
- `created_at`
- `last_seen_at`
- `expires_at`
- `revoked_at`
- `ip_address`
- `client_description`

### 29.4 Invitation

Suggested fields:

- `id`
- `email`
- `email_normalized`
- `proposed_username`
- `role`
- `display_name`
- `token_hash`
- `status`
- `created_by`
- `created_at`
- `expires_at`
- `accepted_at`
- `revoked_at`

### 29.5 Security Tokens

Password-reset and email-change tokens shall use dedicated tables or a strongly typed generalized token table.

Stored fields shall include:

- Token ID
- User ID
- Token hash
- Token type
- Created time
- Expiration time
- Consumed time
- Revoked time
- Target email where applicable

### 29.6 Notebook

Portable fields:

- `id`
- `name`
- `color`
- `created_at`
- `updated_at`
- `sort_order`

Cloud fields:

- `owner_id`
- `is_default`
- `version`

Constraints:

- Owner exists
- Only one default notebook per owner
- Default notebook cannot be deleted

### 29.7 Note

Portable fields:

- `id`
- `notebook_id`
- `type`
- `title`
- `content`
- `pinned`
- `archived`
- `color`
- `deleted_at`
- `created_at`
- `updated_at`

Cloud fields:

- `owner_id`
- `version`
- Full-text search vector
- Optional last snapshot time

Checklist items, labels, and images may be represented relationally rather than embedded in the persistence entity while preserving the portable API representation.

### 29.8 Checklist Item

Fields:

- `id`
- `note_id`
- `owner_id`
- `text`
- `checked`
- `sort_order`
- `created_at`
- `updated_at`
- `version`

### 29.9 Label

Fields:

- `id`
- `owner_id`
- `name`
- `name_normalized`
- `created_at`
- `updated_at`
- `version`

Unique constraint:

- `(owner_id, name_normalized)`

### 29.10 Note Label

Fields:

- `note_id`
- `label_id`
- `owner_id`

Ownership consistency shall be validated.

### 29.11 Image Asset

Metadata fields:

- `id`
- `owner_id`
- `mime_type`
- `original_file_name`
- `byte_size`
- `width`
- `height`
- `content_hash`
- `storage_backend`
- `storage_key`
- `created_at`
- `updated_at`
- `version`

Binary data is stored according to the configured backend.

### 29.12 Note Image Reference

Fields:

- `note_id`
- `image_id`
- `owner_id`
- `sort_order` or reference metadata where required

### 29.13 Note Version

Fields:

- `id`
- `note_id`
- `owner_id`
- `source_version`
- `snapshot_reason`
- `snapshot_at`
- Restorable content payload
- Referenced image IDs

The content payload may use structured JSONB when it simplifies faithful restoration.

### 29.14 Tombstone

Fields:

- `id`
- `owner_id`
- `entity_type`
- `entity_id`
- `deleted_at`
- `expires_at`
- `last_version`

Tombstones shall contain no content.

### 29.15 User Settings

Fields:

- `user_id`
- `theme`
- `language`
- `move_checked_to_bottom`
- `trash_auto_purge_days`
- `last_selected_notebook_id`
- `updated_at`
- `version`

### 29.16 Instance Settings

Instance settings may be stored as typed columns, a typed settings table, or validated JSONB.

Secrets shall not be stored in this table.

### 29.17 Audit Event

Fields:

- `id`
- `event_type`
- `occurred_at`
- `actor_user_id`
- `target_user_id`
- `target_entity_type`
- `target_entity_id`
- `result`
- `ip_address`
- `client_description`
- `correlation_id`
- `metadata_json`

### 29.18 Backup Job

Fields:

- `id`
- `created_by`
- `state`
- `created_at`
- `started_at`
- `completed_at`
- `output_identifier`
- `byte_size`
- `checksum`
- `error_code`
- `error_message`

---

## 30. Desktop Compatibility and Future Synchronization

### 30.1 Version-1 Relationship

The cloud application is a separate web application in version 1.

The existing Electron application remains local-first and offline.

No automatic synchronization is implemented in version 1.

### 30.2 Compatibility Principles

The cloud implementation shall:

- Preserve client-generated UUIDs
- Preserve portable note structures
- Preserve image asset IDs
- Preserve export schema compatibility
- Use server-authoritative timestamps
- Add optimistic versions
- Use soft deletion and tombstones
- Support modification-time filtering
- Version the API under `/api/v1`

### 30.3 Data-Access Adapter Strategy

The Angular note UI should use a platform-neutral data-access interface.

Conceptual structure:

```text
Angular feature components
          |
          v
Glacier data-access interfaces
          |
          +-- Desktop adapter -> window.glacierApi -> Electron repositories
          |
          +-- Cloud adapter -> generated OpenAPI Angular client -> Quarkus
```

Shared interfaces may cover:

- Notebooks
- Notes
- Labels
- Images
- Settings
- Import/export

Cloud-only interfaces may cover:

- Authentication
- Sessions
- User profile
- Administration
- Audit
- Backup

### 30.4 IPC-to-REST Mapping

The current desktop operations map naturally to cloud endpoints:

| Desktop capability | Cloud equivalent |
|---|---|
| `notebooks.list` | `GET /api/v1/notebooks` |
| `notebooks.create` | `POST /api/v1/notebooks` |
| `notebooks.update` | `PATCH /api/v1/notebooks/{id}` |
| `notebooks.delete` | `DELETE /api/v1/notebooks/{id}` |
| `notes.list` | `GET /api/v1/notes` |
| `notes.get` | `GET /api/v1/notes/{id}` |
| `notes.create` | `POST /api/v1/notes` |
| `notes.update` | `PATCH /api/v1/notes/{id}` |
| `notes.trash` | `POST /api/v1/notes/{id}/trash` |
| `notes.restore` | `POST /api/v1/notes/{id}/restore` |
| `notes.purge` | `DELETE /api/v1/notes/{id}` |
| `notes.move` | `POST /api/v1/notes/{id}/move` |
| `labels.*` | `/api/v1/labels` |
| `images.add` | `POST /api/v1/images` |
| `images.getDataUrl` | Authorized image URL or blob fetch |
| `images.delete` | `DELETE /api/v1/images/{id}` |
| `settings.get/set` | `/api/v1/me/settings` |
| `transfer.*` | `/api/v1/imports` and `/api/v1/exports` |
| `share.emailNote` | Browser-side `mailto:` composition |

Desktop-only system, tray, quick-note-window, and IPC event operations do not map to the cloud API.

### 30.5 Synchronization Readiness

Future synchronization shall be able to query changes using:

- Entity type
- Modification timestamp
- Stable ordering
- Pagination cursor
- Owner scope
- Tombstones

The version-1 schema shall not require implementing the change-feed endpoint, but shall retain the fields needed to add it.

### 30.6 Tombstone Retention

After permanent deletion of a synchronizable entity, a minimal tombstone shall be retained for 30 days.

The period shall initially be fixed for synchronization readiness or made deployment-configurable later.

Tombstones shall be accessible only to the owning user and authorized future sync client.

---

## 31. Frontend Architecture

### 31.1 Angular Application

The cloud frontend shall use Angular and should align with the desktop client's Angular major version at implementation time to maximize code reuse.

The application shall use:

- Standalone components
- Angular Router
- Reactive forms
- Generated OpenAPI client
- Central authentication/session state
- Route guards
- HTTP interceptors for CSRF/error handling
- Runtime English/German translations
- CSS custom properties for themes

### 31.2 Feature Areas

Suggested structure:

```text
src/app/
├── core/
│   ├── api/
│   ├── auth/
│   ├── guards/
│   ├── interceptors/
│   ├── error-handling/
│   └── state/
├── features/
│   ├── auth/
│   ├── notebooks/
│   ├── notes/
│   ├── search/
│   ├── labels/
│   ├── transfer/
│   ├── profile/
│   ├── settings/
│   └── admin/
└── shared/
    ├── markdown/
    ├── dialogs/
    ├── forms/
    ├── layout/
    └── generated-api/
```

### 31.3 Authentication UX

The frontend shall include:

- Login
- Invitation acceptance
- Manual token entry
- Password-reset request
- Password-reset completion
- First-admin setup
- Locked/deactivated account messages
- Session-expiration handling

### 31.4 Responsive Behavior

The application shall primarily target desktop browsers but remain usable on tablets and narrow browser windows.

Mobile-native behavior is not required.

### 31.5 Accessibility

Interactive controls shall:

- Be keyboard accessible
- Have accessible labels
- Preserve visible focus
- Avoid color-only status communication
- Use semantic dialog behavior
- Provide appropriate error association

---

## 32. Backend Architecture

### 32.1 Quarkus Application Layers

Suggested modules or packages:

```text
backend/
├── api-generated/
├── api/
├── application/
├── domain/
├── persistence/
├── security/
├── storage/
├── search/
├── transfer/
├── admin/
├── audit/
├── jobs/
└── configuration/
```

### 32.2 Layer Responsibilities

- **Generated API:** OpenAPI-generated contracts and DTOs
- **API adapters:** HTTP mapping and validation
- **Application services:** Use cases and transaction boundaries
- **Domain:** Ownership, lifecycle, and business rules
- **Persistence:** PostgreSQL repositories and mappings
- **Security:** Authentication, sessions, CSRF, rate limiting
- **Storage:** Filesystem/PostgreSQL/S3 image abstraction
- **Search:** PostgreSQL full-text indexing and queries
- **Transfer:** Streaming import/export
- **Admin:** User and instance administration
- **Audit:** Append-only audit events
- **Jobs:** Cleanup, backups, retention, image processing
- **Configuration:** Environment and validated instance settings

### 32.3 Database Migrations

Database schema changes shall be managed through versioned migrations.

Migrations shall:

- Run automatically at controlled startup or through a documented command
- Be tested from supported prior versions
- Avoid destructive changes without backup guidance
- Record schema version

### 32.4 Transactions

Transactions shall protect:

- User activation
- Note updates
- Notebook deletion strategy
- Import apply
- Account deletion scheduling
- Role changes
- Last-administrator checks
- Session revocation
- Version restore

Binary storage operations that cannot share a database transaction shall use compensation and job recovery.

---

## 33. Security Requirements

### 33.1 Authorization

Every endpoint shall define required authentication and role.

Every user-content service shall verify ownership.

Administrative endpoints shall use ADMIN authorization and must still avoid content access.

### 33.2 CSRF

Cookie-authenticated state-changing requests shall require CSRF protection.

The Angular client shall automatically send the CSRF token using a documented header/cookie pattern.

### 33.3 Content Security Policy

The web application shall use a restrictive CSP.

The policy shall avoid:

- Remote scripts
- `unsafe-eval`
- Unrestricted inline script
- Untrusted framing
- Arbitrary object embedding

Image sources shall be limited to required self/blob sources.

### 33.4 Security Headers

The application or recommended reverse proxy configuration shall provide:

- Content-Security-Policy
- X-Content-Type-Options
- Referrer-Policy
- Permissions-Policy
- Frame-ancestors restriction
- Appropriate cache controls
- HSTS when deployed over HTTPS and explicitly enabled at the TLS boundary

### 33.5 Upload Security

Uploads shall be validated using file signatures and decode validation, not filename extension alone.

Temporary files shall have:

- Random names
- Restricted permissions
- Size bounds
- Cleanup on success, failure, and startup recovery

### 33.6 Secret Handling

Secrets shall:

- Enter through environment variables or files
- Be redacted from configuration output
- Be redacted from logs
- Never be returned by APIs
- Never be included in normal backups as configuration values

### 33.7 Input Validation

All API inputs shall have:

- Length limits
- Count limits
- Enum validation
- UUID validation
- Timestamp validation
- Safe error reporting

Markdown shall be treated as untrusted content and sanitized at render time.

### 33.8 Privacy

Metrics and logs shall not include:

- Note titles
- Note bodies
- Checklist text
- Image filenames
- Email addresses unless specifically required in protected audit records
- Usernames in metrics labels
- Tokens
- Passwords

---

## 34. Observability and Operations

### 34.1 Structured Logs

Application logs shall be structured, preferably JSON in production.

Logs shall include:

- Timestamp
- Severity
- Logger/category
- Correlation ID
- Request path template
- Status code
- Duration
- Generic error code

Logs shall avoid user content.

### 34.2 Health Endpoints

Health endpoints shall cover:

- Liveness
- Readiness
- PostgreSQL connectivity
- Image storage connectivity
- Required job subsystem state

SMTP failure shall normally be reported as degraded status rather than making the note application unready, unless an operator chooses stricter behavior.

### 34.3 Metrics

Metrics shall be enabled by default.

Metrics shall be exposed on a separate management interface.

The management interface shall bind to:

- Localhost, or
- Internal container network

It shall not be publicly exposed by default.

Metrics may be disabled through deployment configuration.

Metrics may include:

- HTTP request counts and durations
- Login failure counts
- Active session counts
- Database pool metrics
- Job durations
- Import/export counts
- Image-processing counts
- Storage byte totals aggregated at instance level

Metrics labels shall not contain personal data or content.

### 34.4 Correlation IDs

Every request shall have a correlation ID.

The server shall:

- Accept a valid incoming correlation ID where policy allows
- Generate one otherwise
- Return it in responses
- Include it in logs and audit records

### 34.5 Scheduled Jobs

Version 1 jobs include:

- Trash cleanup
- Note-version cleanup
- Invitation expiration
- Password-reset token cleanup
- Email-change token cleanup
- Session expiration cleanup
- Pending account deletion
- Tombstone cleanup
- Orphan image cleanup
- Audit retention cleanup
- Import/export temporary-file cleanup

Jobs shall use database coordination or locking so future multi-instance support is not blocked.

---

## 35. Deployment

### 35.1 Supported Deployment

The officially supported version-1 deployment is Docker Compose.

The deployment shall contain:

- Glacier Notes application container
- PostgreSQL container
- Persistent database volume
- Persistent image volume when filesystem storage is used
- Persistent backup volume when backup is enabled

### 35.2 Application Container

The application container shall:

- Run Quarkus
- Serve the compiled Angular application
- Expose the application HTTP port
- Expose or bind the management port separately
- Run as a non-root user where practical
- Use a writable temporary directory
- Use mounted persistent directories only where required

### 35.3 Reverse Proxy

The application shall support deployment behind:

- Traefik
- Caddy
- Nginx
- Similar reverse proxies

The application shall correctly process trusted forwarded headers when explicitly configured.

Operators shall configure:

- Public base URL
- Trusted proxy behavior
- Cookie security
- Upload-size limits
- TLS at the reverse proxy

### 35.4 Direct and VPN Access

Direct local HTTP access shall be supported for development and trusted private networks.

Use through a private VPN such as Tailscale shall be supported as a normal reverse-proxy or direct-network scenario.

### 35.5 Horizontal Scaling

Horizontal scaling is not officially supported in version 1.

However, the application shall avoid:

- In-memory-only sessions
- In-memory-only token state
- Uncoordinated local scheduled jobs
- Process-local ownership locks

This preserves a path to future scaling.

---

## 36. Configuration Model

Configuration shall have three categories.

### 36.1 Required Deployment Configuration

Examples:

- PostgreSQL URL
- PostgreSQL username
- PostgreSQL password or secret file
- Session/signing secret
- Public base URL
- Image backend
- Filesystem/S3 settings
- Bootstrap token for uninitialized instances

### 36.2 Optional Secret Configuration

Examples:

- SMTP password
- S3 secret key
- Backup feature enable flag
- Backup directory
- Metrics enable flag
- Management bind address

### 36.3 Runtime Instance Settings

Non-secret values managed by administrators are defined in Section 23.

Restart-required changes shall be clearly marked.

The image backend shall not be runtime-changeable after assets exist.

---

## 37. Error Handling

The API shall return standardized problem documents containing:

- Type identifier
- Title
- HTTP status
- Safe detail
- Instance/request path where appropriate
- Correlation ID
- Application error code
- Validation errors when applicable

Examples of application error codes:

- `AUTH_INVALID_CREDENTIALS`
- `AUTH_ACCOUNT_LOCKED`
- `AUTH_ACCOUNT_DEACTIVATED`
- `AUTH_SESSION_EXPIRED`
- `CSRF_INVALID`
- `ENTITY_NOT_FOUND`
- `ENTITY_VERSION_CONFLICT`
- `USERNAME_ALREADY_EXISTS`
- `EMAIL_ALREADY_EXISTS`
- `LAST_ADMIN_PROTECTED`
- `STORAGE_QUOTA_EXCEEDED`
- `IMAGE_FORMAT_UNSUPPORTED`
- `IMPORT_SCHEMA_UNSUPPORTED`
- `IMPORT_CONFLICT`
- `BACKUP_DISABLED`

Errors shall not reveal:

- Whether another user owns a UUID
- Whether an unknown email exists during reset
- Internal filesystem paths
- SQL details
- Tokens
- Password-policy internals beyond actionable user validation

---

## 38. Performance and Reliability Requirements

Version 1 has no formal enterprise-scale SLA.

The implementation shall nevertheless:

- Paginate collection endpoints
- Stream images and exports
- Use indexed ownership queries
- Maintain full-text indexes
- Avoid loading all notes for global search
- Avoid loading complete exports into memory
- Use bounded background executors
- Apply upload and processing timeouts
- Use database connection pooling
- Use atomic or compensating import behavior
- Recover abandoned temporary imports on startup
- Avoid duplicate scheduled-job execution

Autosave shall remain responsive under normal family or small-team use.

---

## 39. Testing Requirements

### 39.1 Backend Tests

Tests shall cover:

- Authentication
- Username/email normalization
- Password policy
- Lockout and throttling
- Session expiration and revocation
- Last-administrator protection
- Ownership isolation
- Administrator content restrictions
- Invitation lifecycle
- Password-reset lifecycle
- Email-change lifecycle
- Account deactivation and deletion
- Optimistic locking
- Version-history triggers
- Search isolation and ranking
- Image quota and validation
- Each image backend
- Import conflict strategies
- Cross-user UUID collision handling
- Backup feature gate
- Audit retention
- Tombstone retention

### 39.2 Frontend Tests

Tests shall cover:

- Login and logout
- Route guards
- Invitation token paste flow
- Editor autosave states
- Conflict UI
- Notes, notebooks, labels, archive, and trash
- Markdown sanitization
- Image upload error handling
- Import/export flows
- User settings
- Admin dashboard
- Last-admin errors
- Localization
- Keyboard shortcuts

### 39.3 Contract Tests

The build shall verify:

- OpenAPI validity
- Generated client compatibility
- Backend implementation conformance
- Stable operation IDs
- No undocumented endpoints where policy forbids them

### 39.4 Desktop Compatibility Tests

Compatibility fixtures shall be created from the desktop client.

Tests shall verify:

- Desktop full export imports into cloud
- Desktop notebook export imports into cloud
- Desktop note export imports into cloud
- Cloud portable export can be read by a compatible desktop client
- ID preservation
- Add-as-copies remapping
- Replace-by-ID behavior
- Image round-trip
- Default notebook restoration

### 39.5 Security Tests

Tests shall include:

- CSRF rejection
- Session fixation prevention
- Cookie flags
- IDOR/ownership attempts
- Admin content-access attempts
- Path traversal
- Malformed image payloads
- Oversized import payloads
- Token replay
- Token expiration
- Rate limiting
- Log redaction

---

## 40. Acceptance Criteria

Version 1 is accepted when all of the following are true.

### 40.1 Installation and Bootstrap

1. The application starts through the documented Docker Compose deployment.
2. PostgreSQL migrations complete successfully.
3. An uninitialized installation requires the bootstrap token.
4. The first administrator can be created.
5. The bootstrap endpoint cannot be reused.

### 40.2 Authentication

6. Users can log in using either username or email.
7. Username and email matching is case-insensitive while original casing is preserved.
8. Passwords are stored using Argon2id.
9. Normal and remember-me sessions use the configured lifetimes.
10. Users and administrators can revoke sessions as specified.
11. Password changes revoke existing sessions.
12. Lockout and throttling behave according to configured defaults.

### 40.3 User Management

13. Administrators can invite users.
14. Invitations work through both URL and pasted full token.
15. Invitations can be revoked and resent.
16. Activation works without SMTP through a copyable link.
17. Administrators can activate, deactivate, unlock, and delete users.
18. The last active administrator cannot be removed.
19. Administrators cannot inspect user note content.

### 40.4 Notes

20. Users can create and manage notebooks.
21. Users can create text and checklist notes.
22. Markdown is rendered and sanitized.
23. Labels, colors, pinning, archive, and trash work.
24. Images can be added through picker, drag/drop, and clipboard.
25. Unsupported images and oversized payloads are safely rejected.
26. Search returns only the authenticated user's content.
27. Search supports the required filters.
28. Autosave works without creating a version on every request.
29. Version conflicts return `409` and do not silently overwrite changes.
30. Version history follows the configured retention and trigger rules.

### 40.5 Transfer and Compatibility

31. A desktop `.glacier.json` export can be imported.
32. Cloud exports preserve compatible portable structures.
33. Add-as-copies and replace-by-ID both work.
34. Cross-user UUID collisions cannot overwrite another user's content.
35. Administrators can perform blind imports without previewing content.

### 40.6 Operations

36. Structured logs are emitted without note content.
37. Health endpoints report application readiness.
38. Metrics are available on the internal management interface and can be disabled.
39. Administrative and security events are audited.
40. Audit records are retained for 365 days by default.
41. Full backups can be initiated only when enabled.
42. Backups are written to the configured directory and exclude configured secret values.
43. Filesystem is the default image backend.
44. PostgreSQL and S3 image backends can be selected at deployment.
45. The backend cannot be switched after assets exist without an external migration.

### 40.7 User Interface

46. The application supports dark and light themes.
47. English and German can be switched at runtime.
48. Applicable keyboard shortcuts work.
49. The UI remains usable on desktop and tablet-sized browser windows.
50. Desktop-only tray and quick-note-window features are absent.

---

## 41. Suggested Repository Structure

One possible monorepo structure:

```text
glacier-notes-cloud/
├── README.md
├── docker-compose.yml
├── .env.example
├── openapi/
│   └── glacier-notes-v1.yaml
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       ├── main/resources/
│       │   ├── application.properties
│       │   └── db/migration/
│       └── test/
├── frontend/
│   ├── package.json
│   ├── angular.json
│   └── src/
├── compatibility-fixtures/
│   ├── desktop-schema-v1/
│   └── expected-results/
├── deployment/
│   ├── docker/
│   ├── reverse-proxy-examples/
│   └── backup/
└── docs/
    ├── CONFIGURATION.md
    ├── SECURITY.md
    ├── BACKUP_RESTORE.md
    ├── IMPORT_EXPORT.md
    └── DESKTOP_COMPATIBILITY.md
```

A separate repository is acceptable, but shared portable TypeScript schemas or generated clients should be published or synchronized through a controlled process.

---

## 42. Future Requirements

The architecture shall leave room for the following without implementing them in version 1.

### 42.1 Authentication

- Optional TOTP for users
- Mandatory TOTP for administrators
- OpenID Connect
- Keycloak
- Authentik
- LDAP
- Passkeys

### 42.2 Sharing

- Share a note with named users
- Shared notebooks
- Viewer, editor, and owner permissions
- Re-share permission
- Expiring public links
- No requirement for password-protected public links

No unused sharing tables are required in version 1. Future migrations may add grants and memberships.

### 42.3 Desktop Synchronization

- Device registration
- Incremental change feed
- Push/pull synchronization
- Conflict merge UI
- Offline mutation queue
- Per-device cursors
- Attachment synchronization
- Selective sync

### 42.4 Storage and Operations

- Image-backend migration command
- Animated GIF handling
- Virus scanning
- Encrypted backup archives
- Kubernetes deployment
- Plain JVM deployment
- systemd packaging
- Unraid/TrueNAS/Synology packaging
- Windows service
- Official horizontal scaling
- Prometheus/Grafana stack templates

### 42.5 Security

- CAPTCHA
- Mandatory application-level HTTPS enforcement
- Database encryption guidance or integration
- More advanced anomaly detection

---

## 43. Decisions Log

| Decision | Version-1 choice |
|---|---|
| Relationship to desktop | Separate web app now; synchronization later |
| Offline web mode | Not supported |
| Tenancy | One installation, no workspaces |
| Backend | Quarkus |
| Frontend | Angular |
| Database | PostgreSQL only |
| API | OpenAPI-first with generated code |
| Login | Username or email plus password |
| Registration | Invitation/admin activation only |
| SMTP | Optional; copyable links when unavailable |
| MFA | Future |
| Sessions | 12 hours; remember me 30 days |
| Roles | USER and ADMIN |
| Admin content access | Forbidden |
| Sharing | Future only |
| Core desktop features | Included except desktop integrations |
| Image backends | Filesystem default; PostgreSQL or S3 optional |
| Backend switching | Not supported after assets exist |
| Image quota | 10 MB per image; 1 GB per user defaults |
| GIF | Future |
| Search | PostgreSQL language-neutral full-text search |
| Concurrent editing | Optimistic conflict rejection |
| Versions | Last 20, 30 days, configurable |
| Real-time updates | Not in version 1 |
| User export | Own content only |
| Admin full backup | Environment-gated, dashboard-triggered, server directory |
| Email sharing | `mailto:` |
| Deployment | Docker Compose |
| Frontend/backend container | One application container plus PostgreSQL |
| Reverse proxy | Expected for production TLS |
| Horizontal scaling | Future, but architecture shall not block it |
| Password hash | Argon2id |
| Audit retention | 365 days |
| Operational log retention | 30 days |
| Metrics | Enabled by default on internal management interface |
| Self-deletion | Immediate after password and final confirmation |
| Tombstones | 30 days after permanent deletion |
| First admin | One-time setup page with bootstrap token |

---

## 44. Implementation Follow-On Deliverables

The next implementation-planning documents should be produced from this specification:

1. OpenAPI 3.1 contract for `/api/v1`
2. PostgreSQL logical and physical schema
3. Quarkus module/package design
4. Angular route and component map
5. Authentication and session sequence diagrams
6. Invitation and password-reset sequence diagrams
7. Import/export compatibility fixtures
8. Docker Compose and environment-variable contract
9. Threat model
10. Milestone and issue breakdown
11. Backup and restore runbook
12. Desktop synchronization design proposal for a future release

---

## 45. Approval State

All material version-1 product requirements raised during requirements gathering are resolved.

No additional requirements question is required before:

- OpenAPI design
- Database modeling
- Milestone planning
- Initial project scaffolding

Changes after this point should be tracked as specification amendments or explicit implementation decisions.
