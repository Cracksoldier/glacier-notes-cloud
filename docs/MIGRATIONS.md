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

