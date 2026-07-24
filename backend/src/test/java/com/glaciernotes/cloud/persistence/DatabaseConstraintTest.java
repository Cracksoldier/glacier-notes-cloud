package com.glaciernotes.cloud.persistence;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class DatabaseConstraintTest {
    @Inject
    DataSource dataSource;

    @Test
    void normalizedUsernamesAndEmailsAreUnique() throws SQLException {
        try (var connection = transaction()) {
            insertUser(connection, UUID.randomUUID(), "Alpha", "alpha", "alpha@example.test");
            assertThrows(SQLException.class, () ->
                insertUser(connection, UUID.randomUUID(), "ALPHA", "alpha", "other@example.test")
            );
            connection.rollback();
        }

        try (var connection = transaction()) {
            insertUser(connection, UUID.randomUUID(), "Bravo", "bravo", "shared@example.test");
            assertThrows(SQLException.class, () ->
                insertUser(connection, UUID.randomUUID(), "Charlie", "charlie", "SHARED@example.test")
            );
            connection.rollback();
        }
    }

    @Test
    void onlyOneDefaultNotebookIsAllowedPerOwner() throws SQLException {
        try (var connection = transaction()) {
            var owner = UUID.randomUUID();
            insertUser(connection, owner, "owner", "owner", "owner@example.test");
            insertNotebook(connection, owner, UUID.randomUUID(), true);
            assertThrows(
                SQLException.class,
                () -> insertNotebook(connection, owner, UUID.randomUUID(), true)
            );
            connection.rollback();
        }
    }

    @Test
    void crossOwnerNotebookReferencesAreRejected() throws SQLException {
        try (var connection = transaction()) {
            var ownerA = UUID.randomUUID();
            var ownerB = UUID.randomUUID();
            insertUser(connection, ownerA, "owner-a", "owner-a", "a@example.test");
            insertUser(connection, ownerB, "owner-b", "owner-b", "b@example.test");
            var notebookB = UUID.randomUUID();
            insertNotebook(connection, ownerB, notebookB, true);

            assertThrows(SQLException.class, () -> {
                try (var statement = connection.prepareStatement(
                    "insert into notes(owner_id, id, notebook_id, note_type) values (?, ?, ?, 'text')"
                )) {
                    statement.setObject(1, ownerA);
                    statement.setObject(2, UUID.randomUUID());
                    statement.setObject(3, notebookB);
                    statement.executeUpdate();
                }
            });
            connection.rollback();
        }
    }

    @Test
    void authenticationSettingsRejectUnsafeDurationsAndThresholds() throws SQLException {
        try (var connection = transaction(); var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                "update instance_settings set normal_session_duration_minutes = 14"
            ));
            connection.rollback();
        }

        try (var connection = transaction(); var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                "update instance_settings set login_lock_threshold = login_delay_threshold"
            ));
            connection.rollback();
        }

        try (var connection = transaction(); var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                "update instance_settings set invitation_expiration_hours = 0"
            ));
            connection.rollback();
        }

        try (var connection = transaction(); var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                "update instance_settings set password_reset_expiration_minutes = 1441"
            ));
            connection.rollback();
        }
    }

    @Test
    void bootstrapRateLimitsRejectNegativeFailureCounts() throws SQLException {
        try (var connection = transaction(); var statement = connection.createStatement()) {
            var failure = assertThrows(SQLException.class, () -> statement.executeUpdate("""
                insert into bootstrap_rate_limits(
                    client_key, window_started_at, failure_count, updated_at
                ) values (
                    repeat('a', 64), current_timestamp, -1, current_timestamp
                )
                """));

            assertEquals("23514", failure.getSQLState());
            connection.rollback();
        }
    }

    private Connection transaction() throws SQLException {
        var connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        return connection;
    }

    private void insertUser(
        Connection connection,
        UUID id,
        String username,
        String normalizedUsername,
        String email
    ) throws SQLException {
        try (var statement = connection.prepareStatement(
            "insert into app_users(id, username, username_normalized, email, email_normalized, role, status) "
                + "values (?, ?, ?, ?, ?, 'USER', 'ACTIVE')"
        )) {
            statement.setObject(1, id);
            statement.setString(2, username);
            statement.setString(3, normalizedUsername);
            statement.setString(4, email);
            statement.setString(5, email.toLowerCase(java.util.Locale.ROOT));
            statement.executeUpdate();
        }
    }

    private void insertNotebook(
        Connection connection,
        UUID owner,
        UUID id,
        boolean defaultNotebook
    ) throws SQLException {
        try (var statement = connection.prepareStatement(
            "insert into notebooks(owner_id, id, name, is_default) values (?, ?, 'Notes', ?)"
        )) {
            statement.setObject(1, owner);
            statement.setObject(2, id);
            statement.setBoolean(3, defaultNotebook);
            statement.executeUpdate();
        }
    }
}
