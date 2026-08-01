# Database migrations

Flyway migrations run and validate before normal Quarkus startup. A migration failure prevents
the application from becoming ready. Hibernate validates mappings but never creates or updates
the schema.

## Rules

1. Never edit a migration that has reached a shared environment.
2. Add a new, monotonically versioned migration for every schema change.
3. Test both a blank database and every supported prior release state.
4. Prefer additive, backward-compatible changes and staged removal.
5. Before a destructive migration, stop writes and create a verified PostgreSQL backup.

Flyway migrations do not provide automatic down scripts. Rollback means restoring the matching
application version and its verified database backup. Document data conversion, downtime, backup,
and restore steps in the migration change that introduces a destructive operation.

## Upgrade data-integrity tests

Two tests under `backend/src/test/java/com/glaciernotes/cloud/persistence/` migrate an isolated,
randomly-named schema to a fixed prior version, seed rows through raw JDBC, migrate to head, and
assert the seeded data survived:

- `MigrationUpgradeTest` seeds a minimal note/version chain and asserts the V11 `content_hash`
  backfill produced a 64-character hash.
- `SchemaUpgradeDataIntegrityTest` seeds one row across every content, ownership, and audit table
  that exists at the baseline (`notebooks`, `notes`, `checklist_items`, `labels`, `note_labels`,
  `image_assets`, `image_asset_blobs`, `note_image_references`, `note_versions`,
  `note_version_image_references`, `user_sessions`, `audit_events`) and asserts every row, plus its
  foreign-key joins, survives the upgrade to head.

Both currently target `target("10")` (the `external_storage_operations` migration) as the prior
baseline. When a new milestone adds a migration that changes a seeded table's shape, bump this
baseline forward to the newest version before that change so the test keeps exercising a real
upgrade path rather than a no-op.

The second-factor migrations `V13__multi_factor_authentication` and `V14__second_factor_step_up` need
no such bump: both are additive — new tables plus new nullable or defaulted columns — and both are
already crossed by the existing `target("10")` baseline, so the seeded rows go through them on the
way to head. `V13` does widen one existing constraint, `login_rate_limits_scope_check`, but only to
admit a superset of its former values, which every seeded row already satisfies.

