package com.glaciernotes.cloud.application.content;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class NoteHistoryCleanupTest {
    private static final UUID OWNER = UUID.fromString("509531e6-c66e-4524-9537-2eb00d5ef41e");
    private static final UUID NOTEBOOK = UUID.fromString("7cc48faa-f79d-4c95-9d13-98dfc7f78fc8");
    private static final UUID NOTE = UUID.fromString("4188e693-626d-40c4-9256-a30f38069a42");

    @Inject
    DataSource dataSource;

    @Inject
    ContentService contentService;

    @AfterEach
    void clean() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from notes where owner_id = '" + OWNER + "'");
            statement.executeUpdate("delete from app_users where id = '" + OWNER + "'");
            statement.executeUpdate("update instance_settings set note_version_maximum_count = 20, " +
                "note_version_retention_days = 30 where singleton_key = 1");
        }
    }

    @Test
    void cleanupEnforcesCountAndAgeBoundaries() throws SQLException {
        Instant now = Instant.now();
        UUID oldVersion = UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                    insert into app_users(id, username, username_normalized, email, email_normalized,
                      display_name, role, status, password_hash, password_changed_at, activated_at)
                    values ('%s', 'history-cleanup', 'history-cleanup', 'history-cleanup@example.test',
                      'history-cleanup@example.test', 'History Cleanup', 'USER', 'ACTIVE', 'unused',
                      current_timestamp, current_timestamp)
                    """.formatted(OWNER));
                statement.executeUpdate("""
                    insert into notebooks(owner_id, id, name, is_default, sort_order)
                    values ('%s', '%s', 'Notes', true, 0)
                    """.formatted(OWNER, NOTEBOOK));
                statement.executeUpdate("""
                    insert into notes(owner_id, id, notebook_id, note_type, title, content)
                    values ('%s', '%s', '%s', 'text', 'History', '')
                    """.formatted(OWNER, NOTE, NOTEBOOK));
                statement.executeUpdate("update instance_settings set note_version_maximum_count = 20, " +
                    "note_version_retention_days = 30 where singleton_key = 1");
            }
            insertVersion(connection, oldVersion, now.minus(45, ChronoUnit.DAYS), "Old");
            for (int index = 0; index < 22; index++) {
                insertVersion(connection, UUID.randomUUID(), now.minus(index, ChronoUnit.HOURS), "Recent " + index);
            }
        }

        contentService.cleanNoteHistory();

        assertEquals(20, count("select count(*) from note_versions where owner_id = ? and note_id = ?", NOTE));
        assertEquals(0, count("select count(*) from note_versions where owner_id = ? and id = ?", oldVersion));
    }

    private void insertVersion(java.sql.Connection connection, UUID id, Instant at, String title)
        throws SQLException {
        try (var statement = connection.prepareStatement("""
            insert into note_versions(owner_id, id, note_id, source_version, snapshot_reason,
              snapshot_at, content_payload, content_hash)
            values (?, ?, ?, 1, 'EDITOR_CLOSE', ?, cast(? as jsonb), ?)
            """)) {
            statement.setObject(1, OWNER);
            statement.setObject(2, id);
            statement.setObject(3, NOTE);
            statement.setTimestamp(4, java.sql.Timestamp.from(at));
            statement.setString(5, """
                {"schemaVersion":1,"noteType":"text","title":"%s","content":"", 
                 "checklistItems":[],"pinned":false,"archived":false,"color":null,
                 "labelIds":[],"imageIds":[]}
                """.formatted(title));
            statement.setString(6, UUID.randomUUID().toString().replace("-", ""));
            statement.executeUpdate();
        }
    }

    private long count(String sql, UUID id) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, OWNER);
            statement.setObject(2, id);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }
}
