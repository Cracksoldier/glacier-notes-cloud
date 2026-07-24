package com.glaciernotes.cloud.persistence;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            "bootstrap_rate_limits", "login_rate_limits", "external_storage_operations"
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
            assertTrue(indexes.contains("ix_notes_owner_type_collection"));
            assertTrue(indexes.contains("ix_notes_owner_notebook_updated"));
            assertTrue(indexes.contains("ix_notes_search_vector"));
            assertTrue(indexes.contains("uq_notebooks_one_default"));
            assertTrue(indexes.contains("ix_bootstrap_rate_limits_blocked"));
            assertTrue(indexes.contains("ix_login_rate_limits_blocked"));
            assertTrue(indexes.contains("ix_endpoint_rate_limits_blocked"));
            assertTrue(indexes.contains("ix_external_storage_operations_claim"));
            assertTrue(indexes.contains("ix_external_storage_operations_owner_reservations"));
        }
    }

    @Test
    void searchVectorAndChecklistMaintenanceUseLanguageNeutralPostgresqlFts() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("""
                select generation_expression from information_schema.columns
                where table_schema = 'public' and table_name = 'notes' and column_name = 'search_vector'
                """); var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                String expression = rows.getString(1);
                assertTrue(expression.contains("to_tsvector('simple'"));
                assertTrue(expression.contains("checklist_search_text"));
            }
            try (var statement = connection.prepareStatement("""
                select count(*) from information_schema.triggers
                where event_object_schema = 'public' and event_object_table = 'checklist_items'
                  and trigger_name like 'trg_checklist_search_%'
                """); var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(3, rows.getInt(1));
            }
            try (var statement = connection.prepareStatement("""
                select is_nullable from information_schema.columns
                where table_schema = 'public' and table_name = 'note_versions'
                  and column_name = 'content_hash'
                """); var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("NO", rows.getString(1));
            }
        }
    }

    @Test
    void checklistRelocationRefreshesBothAffectedSearchDocuments() throws SQLException {
        UUID owner = UUID.randomUUID();
        UUID firstNotebook = UUID.randomUUID();
        UUID secondNotebook = UUID.randomUUID();
        UUID firstNote = UUID.randomUUID();
        UUID secondNote = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("""
                insert into app_users(id,username,username_normalized,email,email_normalized,role,status)
                values (?, ?, ?, ?, ?, 'USER', 'ACTIVE')
                """)) {
                statement.setObject(1, owner);
                statement.setString(2, "schema-" + owner);
                statement.setString(3, "schema-" + owner);
                statement.setString(4, owner + "@example.test");
                statement.setString(5, owner + "@example.test");
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                insert into notebooks(owner_id,id,name,is_default,sort_order)
                values (?,?,'First',true,0),(?,?,'Second',false,1)
                """)) {
                statement.setObject(1, owner);
                statement.setObject(2, firstNotebook);
                statement.setObject(3, owner);
                statement.setObject(4, secondNotebook);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                insert into notes(owner_id,id,notebook_id,note_type,title,content)
                values (?,?,?,'checklist','First',''),(?,?,?,'checklist','Second','')
                """)) {
                statement.setObject(1, owner);
                statement.setObject(2, firstNote);
                statement.setObject(3, firstNotebook);
                statement.setObject(4, owner);
                statement.setObject(5, secondNote);
                statement.setObject(6, secondNotebook);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                insert into checklist_items(owner_id,id,note_id,text,sort_order)
                values (?,?,?,'relocated phrase',0)
                """)) {
                statement.setObject(1, owner);
                statement.setObject(2, item);
                statement.setObject(3, firstNote);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                update checklist_items set note_id=? where owner_id=? and id=?
                """)) {
                statement.setObject(1, secondNote);
                statement.setObject(2, owner);
                statement.setObject(3, item);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                select id, checklist_search_text from notes where owner_id=? order by id
                """)) {
                statement.setObject(1, owner);
                try (var rows = statement.executeQuery()) {
                    var searchTextByNote = new HashMap<UUID, String>();
                    while (rows.next()) {
                        searchTextByNote.put(rows.getObject(1, UUID.class), rows.getString(2));
                    }
                    assertEquals(
                        Map.of(firstNote, "", secondNote, "relocated phrase"),
                        searchTextByNote
                    );
                }
            } finally {
                try (var statement = connection.prepareStatement("delete from notes where owner_id=?")) {
                    statement.setObject(1, owner);
                    statement.executeUpdate();
                }
                try (var statement = connection.prepareStatement("delete from app_users where id=?")) {
                    statement.setObject(1, owner);
                    statement.executeUpdate();
                }
            }
        }
    }

    @Test
    void transferScopeConstraintRejectsInvalidResourceCombinations() throws SQLException {
        UUID owner = UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("""
                insert into app_users(id,username,username_normalized,email,email_normalized,role,status)
                values (?, ?, ?, ?, ?, 'USER', 'ACTIVE')
                """)) {
                statement.setObject(1, owner);
                statement.setString(2, "transfer-" + owner);
                statement.setString(3, "transfer-" + owner);
                statement.setString(4, owner + "@example.test");
                statement.setString(5, owner + "@example.test");
                statement.executeUpdate();
            }
            try {
                insertExportJob(connection, owner, "ALL", null);
                insertExportJob(connection, owner, "NOTEBOOK", UUID.randomUUID());
                insertExportJob(connection, owner, "NOTE", UUID.randomUUID());

                assertThrows(
                    SQLException.class,
                    () -> insertExportJob(connection, owner, "ALL", UUID.randomUUID())
                );
                assertThrows(
                    SQLException.class,
                    () -> insertExportJob(connection, owner, "NOTEBOOK", null)
                );
                assertThrows(
                    SQLException.class,
                    () -> insertExportJob(connection, owner, "NOTE", null)
                );
            } finally {
                try (var statement = connection.prepareStatement(
                    "delete from transfer_jobs where requested_by=?"
                )) {
                    statement.setObject(1, owner);
                    statement.executeUpdate();
                }
                try (var statement = connection.prepareStatement("delete from app_users where id=?")) {
                    statement.setObject(1, owner);
                    statement.executeUpdate();
                }
            }
        }
    }

    private void insertExportJob(Connection connection, UUID owner, String scope, UUID scopeId)
        throws SQLException {
        try (var statement = connection.prepareStatement("""
            insert into transfer_jobs(
              id,job_kind,phase,state,requested_by,target_user_id,scope_kind,scope_entity_id,expires_at
            ) values (?,'EXPORT','GENERATE','QUEUED',?,?,?,?,current_timestamp + interval '1 hour')
            """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, owner);
            statement.setObject(3, owner);
            statement.setString(4, scope);
            statement.setObject(5, scopeId);
            statement.executeUpdate();
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
