package com.glaciernotes.cloud.persistence;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class SecondFactorMigrationTest {
    @Inject
    DataSource dataSource;

    @Test
    void secondFactorTunablesLandOnThePreExistingSettingsRowAndTheScopeCheckOnlyWidens() throws Exception {
        String schema = "mfa_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            migrate(schema, "12");

            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                assertEquals(1, count(statement, "select count(*) from " + schema + ".instance_settings"));
                statement.executeUpdate(insertRateLimit(schema, "IDENTIFIER", 'a'));
                statement.executeUpdate(insertRateLimit(schema, "IP", 'b'));
                assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate(insertRateLimit(schema, "MFA_IP", 'c')),
                    "MFA_IP must not be accepted before V13 widens the constraint"
                );
            }

            migrate(schema, null);

            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                assertEquals(5, tunable(statement, schema, "mfa_challenge_lifetime_minutes"));
                assertEquals(5, tunable(statement, schema, "mfa_challenge_attempt_limit"));
                assertEquals(30, tunable(statement, schema, "mfa_pending_enrollment_minutes"));
                assertEquals(5, tunable(statement, schema, "mfa_step_up_grace_minutes"));

                assertBounds(statement, schema, "mfa_challenge_lifetime_minutes", 1, 30);
                assertBounds(statement, schema, "mfa_challenge_attempt_limit", 3, 10);
                assertBounds(statement, schema, "mfa_pending_enrollment_minutes", 5, 120);
                assertBounds(statement, schema, "mfa_step_up_grace_minutes", 0, 60);

                assertEquals(
                    2,
                    count(statement, "select count(*) from " + schema + ".login_rate_limits"),
                    "widening the scope check must not discard existing rows"
                );
                statement.executeUpdate(insertRateLimit(schema, "MFA_IP", 'c'));
                assertEquals(3, count(statement, "select count(*) from " + schema + ".login_rate_limits"));
                assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate(insertRateLimit(schema, "SOMETHING_ELSE", 'd'))
                );
            }
        } finally {
            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("drop schema if exists " + schema + " cascade");
            }
        }
    }

    private void migrate(String schema, String target) {
        var configuration = Flyway.configure()
            .dataSource(dataSource)
            .schemas(schema)
            .defaultSchema(schema)
            .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    /**
     * The bound is inclusive, so the boundary itself must be accepted and one step beyond rejected.
     */
    private void assertBounds(Statement statement, String schema, String column, int minimum, int maximum)
        throws SQLException {
        set(statement, schema, column, minimum);
        assertEquals(minimum, tunable(statement, schema, column));
        set(statement, schema, column, maximum);
        assertEquals(maximum, tunable(statement, schema, column));

        assertThrows(SQLException.class, () -> set(statement, schema, column, minimum - 1), column + " below bound");
        assertThrows(SQLException.class, () -> set(statement, schema, column, maximum + 1), column + " above bound");
    }

    private void set(Statement statement, String schema, String column, int value) throws SQLException {
        statement.executeUpdate(
            "update " + schema + ".instance_settings set " + column + " = " + value + " where singleton_key = 1"
        );
    }

    private int tunable(Statement statement, String schema, String column) throws SQLException {
        return count(statement, "select " + column + " from " + schema + ".instance_settings where singleton_key = 1");
    }

    private int count(Statement statement, String query) throws SQLException {
        try (var rows = statement.executeQuery(query)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private String insertRateLimit(String schema, String scope, char fill) {
        return """
            insert into %s.login_rate_limits(
              scope,key_hash,window_started_at,failure_count,updated_at
            ) values (
              '%s','%s',CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP
            )
            """.formatted(schema, scope, String.valueOf(fill).repeat(64));
    }
}
