package com.glaciernotes.cloud.persistence;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class DatabaseSchemaTest {
    @Inject
    DataSource dataSource;

    @Test
    void flywayCreatedTheCompleteInitialSchema() throws SQLException {
        var expected = Set.of(
            "app_users", "user_sessions", "invitations", "security_tokens", "endpoint_rate_limits", "notebooks",
            "user_password_history", "notes", "checklist_items", "labels", "note_labels",
            "image_assets", "note_image_references", "note_versions",
            "note_version_image_references", "tombstones", "user_settings", "instance_state",
            "instance_settings", "audit_events", "backup_jobs", "job_locks",
            "bootstrap_rate_limits", "login_rate_limits"
        );
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "select table_name from information_schema.tables where table_schema = 'public'"
             );
             var rows = statement.executeQuery()) {
            var actual = new java.util.HashSet<String>();
            while (rows.next()) {
                actual.add(rows.getString(1));
            }
            assertTrue(actual.containsAll(expected));
            assertTrue(actual.contains("flyway_schema_history"));
        }
    }

    @Test
    void tombstonesContainNoContentColumns() throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "select column_name from information_schema.columns "
                     + "where table_schema = 'public' and table_name = 'tombstones'"
             );
             var rows = statement.executeQuery()) {
            var columns = new java.util.HashSet<String>();
            while (rows.next()) {
                columns.add(rows.getString(1));
            }
            assertEquals(
                Set.of("id", "owner_id", "entity_type", "entity_id", "deleted_at", "expires_at", "last_version"),
                columns
            );
            assertFalse(columns.contains("title"));
            assertFalse(columns.contains("content"));
        }
    }

    @Test
    void ownerFirstAndSearchIndexesExist() throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "select indexname from pg_indexes where schemaname = 'public'"
             );
             var rows = statement.executeQuery()) {
            var indexes = new java.util.HashSet<String>();
            while (rows.next()) {
                indexes.add(rows.getString(1));
            }
            assertTrue(indexes.contains("ix_notes_owner_collection"));
            assertTrue(indexes.contains("ix_notes_owner_notebook_updated"));
            assertTrue(indexes.contains("ix_notes_search_vector"));
            assertTrue(indexes.contains("uq_notebooks_one_default"));
            assertTrue(indexes.contains("ix_bootstrap_rate_limits_blocked"));
            assertTrue(indexes.contains("ix_login_rate_limits_blocked"));
            assertTrue(indexes.contains("ix_endpoint_rate_limits_blocked"));
        }
    }

    @Test
    void ownerSensitiveForeignKeysUseOwnerAwareReferences() throws SQLException {
        var expectedFragments = Set.of(
            "notebooks:foreign key (owner_id) references app_users(id)",
            "notes:foreign key (owner_id, notebook_id) references notebooks(owner_id, id)",
            "checklist_items:foreign key (owner_id, note_id) references notes(owner_id, id)",
            "labels:foreign key (owner_id) references app_users(id)",
            "note_labels:foreign key (owner_id, note_id) references notes(owner_id, id)",
            "note_labels:foreign key (owner_id, label_id) references labels(owner_id, id)",
            "image_assets:foreign key (owner_id) references app_users(id)",
            "note_image_references:foreign key (owner_id, note_id) references notes(owner_id, id)",
            "note_image_references:foreign key (owner_id, image_id) references image_assets(owner_id, id)",
            "note_versions:foreign key (owner_id, note_id) references notes(owner_id, id)",
            "note_version_image_references:foreign key (owner_id, note_version_id) references note_versions(owner_id, id)",
            "note_version_image_references:foreign key (owner_id, image_id) references image_assets(owner_id, id)",
            "tombstones:foreign key (owner_id) references app_users(id)",
            "user_settings:foreign key (user_id, last_selected_notebook_id) references notebooks(owner_id, id)"
        );

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "select conrelid::regclass::text, lower(pg_get_constraintdef(oid)) "
                     + "from pg_constraint where contype = 'f'"
             );
             var rows = statement.executeQuery()) {
            var definitions = new java.util.HashSet<String>();
            while (rows.next()) {
                definitions.add(rows.getString(1) + ":" + rows.getString(2));
            }
            for (var expected : expectedFragments) {
                assertTrue(
                    definitions.stream().anyMatch(definition -> definition.startsWith(expected)),
                    () -> "Missing owner-aware constraint: " + expected
                );
            }
        }
    }
}
