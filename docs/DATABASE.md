# PostgreSQL domain model

Portable content is keyed by `(owner_id, id)`. This allows two accounts to import the same
desktop UUIDs without collision while making cross-owner foreign keys structurally invalid.

```mermaid
erDiagram
  APP_USERS ||--o{ USER_SESSIONS : owns
  APP_USERS ||--o{ INVITATIONS : creates
  APP_USERS ||--o{ NOTEBOOKS : owns
  APP_USERS ||--o{ LABELS : owns
  APP_USERS ||--o{ IMAGE_ASSETS : owns
  APP_USERS ||--|| USER_SETTINGS : configures
  NOTEBOOKS ||--o{ NOTES : contains
  NOTES ||--o{ CHECKLIST_ITEMS : contains
  NOTES ||--o{ NOTE_LABELS : tagged
  LABELS ||--o{ NOTE_LABELS : assigned
  NOTES ||--o{ NOTE_IMAGE_REFERENCES : embeds
  IMAGE_ASSETS ||--o{ NOTE_IMAGE_REFERENCES : referenced
  NOTES ||--o{ NOTE_VERSIONS : snapshots
  NOTE_VERSIONS ||--o{ NOTE_VERSION_IMAGE_REFERENCES : retains
  IMAGE_ASSETS ||--o{ NOTE_VERSION_IMAGE_REFERENCES : retained
  APP_USERS ||--o{ TOMBSTONES : owns
  APP_USERS ||--o{ SECURITY_TOKENS : secures
  APP_USERS ||--o{ AUDIT_EVENTS : acts
  APP_USERS ||--o{ BACKUP_JOBS : starts
```

All synchronizable mutable rows use UUIDs, `timestamptz` creation/update timestamps, and a
non-negative optimistic `BIGINT` version. PostgreSQL check constraints protect string-backed
enums without coupling future migrations to PostgreSQL enum types.

Invitation and password-reset rows persist only domain-separated token hashes. Persistent endpoint
rate-limit rows are keyed by scope and a keyed client/identifier hash; raw addresses, reset tokens,
and invitation tokens are not stored.

The note search vector uses the language-neutral `simple` configuration for title and content.
M8 may extend vector maintenance to relational checklist and label text without changing note IDs.
