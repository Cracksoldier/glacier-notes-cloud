package com.glaciernotes.cloud;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SetupResourceTest {
    private static final String TOKEN = "test-bootstrap-token-with-at-least-32-characters";
    private static final String VALID_PASSWORD = "correct-horse-battery-staple-2026";

    @Inject
    DataSource dataSource;

    @BeforeEach
    @AfterEach
    void resetBootstrapState() throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("delete from audit_events");
            statement.executeUpdate("delete from bootstrap_rate_limits");
            statement.executeUpdate("delete from app_users");
            statement.executeUpdate("""
                update instance_state
                   set initialized = false,
                       initialized_at = null,
                       initialized_by = null,
                       updated_at = current_timestamp,
                       version = 0
                 where singleton_key = 1
                """);
        }
    }

    @Test
    void reportsThatAFreshInstanceRequiresSetup() {
        given()
            .when().get("/api/v1/setup/status")
            .then()
            .statusCode(200)
            .body("setupRequired", equalTo(true));
    }

    @Test
    void doesNotCreateAnAdministratorWithoutTheBootstrapToken() throws SQLException {
        given()
            .contentType(ContentType.JSON)
            .body(validRequest())
            .when().post("/api/v1/setup/administrator")
            .then()
            .statusCode(400);

        try (var connection = dataSource.getConnection()) {
            assertEquals(0, count(connection, "app_users", "true"));
            assertEquals(1, count(connection, "instance_state", "not initialized"));
        }
    }

    @Test
    void createsTheAdministratorAndInitialWorkspaceAtomically() throws SQLException {
        given()
            .contentType(ContentType.JSON)
            .header("X-Bootstrap-Token", TOKEN)
            .body(validRequest())
            .when().post("/api/v1/setup/administrator")
            .then()
            .statusCode(201)
            .body("initialized", equalTo(true))
            .body("initializedAt", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T.*Z$"));

        given()
            .when().get("/api/v1/setup/status")
            .then()
            .statusCode(200)
            .body("setupRequired", equalTo(false));

        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("""
                select username, username_normalized, email, email_normalized,
                       role, status, password_hash
                  from app_users
                """); var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("Admin.User", rows.getString("username"));
                assertEquals("admin.user", rows.getString("username_normalized"));
                assertEquals("Admin@Example.COM", rows.getString("email"));
                assertEquals("admin@example.com", rows.getString("email_normalized"));
                assertEquals("ADMIN", rows.getString("role"));
                assertEquals("ACTIVE", rows.getString("status"));
                assertTrue(rows.getString("password_hash").startsWith("$argon2id$"));
                assertNotEquals(VALID_PASSWORD, rows.getString("password_hash"));
                assertFalse(rows.next());
            }
            assertEquals(1, count(connection, "notebooks", "is_default"));
            assertEquals(1, count(connection, "user_settings", "theme = 'dark' and language = 'en'"));
            assertEquals(1, count(connection, "audit_events", "event_type = 'INSTANCE_BOOTSTRAPPED'"));
            assertEquals(1, count(connection, "instance_state", "initialized"));
            try (var statement = connection.prepareStatement(
                "select metadata_json::text from audit_events where event_type = 'INSTANCE_BOOTSTRAPPED'"
            ); var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertFalse(rows.getString(1).contains(TOKEN));
                assertFalse(rows.getString(1).toLowerCase().contains("token"));
            }
        }

        given()
            .contentType(ContentType.JSON)
            .header("X-Bootstrap-Token", TOKEN)
            .body(validRequest())
            .when().post("/api/v1/setup/administrator")
            .then()
            .statusCode(404)
            .body("errorCode", equalTo("ENTITY_NOT_FOUND"));
    }

    @Test
    void rejectsCommonPasswordsWithoutChangingInstanceState() throws SQLException {
        given()
            .contentType(ContentType.JSON)
            .header("X-Bootstrap-Token", TOKEN)
            .body(validRequest().replace(VALID_PASSWORD, "qwerty123456"))
            .when().post("/api/v1/setup/administrator")
            .then()
            .statusCode(422)
            .body("errorCode", equalTo("VALIDATION_FAILED"))
            .body("validationErrors[0].field", equalTo("password"));

        try (var connection = dataSource.getConnection()) {
            assertEquals(0, count(connection, "app_users", "true"));
            assertEquals(1, count(connection, "instance_state", "not initialized"));
        }
    }

    @Test
    void persistsFailedTokenAttemptsAndReturnsRetryAfter() throws SQLException {
        for (int attempt = 1; attempt < 5; attempt++) {
            given()
                .contentType(ContentType.JSON)
                .header("X-Bootstrap-Token", "incorrect-bootstrap-token-value-000000")
                .body(validRequest())
                .when().post("/api/v1/setup/administrator")
                .then()
                .statusCode(403)
                .body("errorCode", equalTo("SETUP_DENIED"))
                .body("detail", equalTo("Setup could not be completed"));
        }

        given()
            .contentType(ContentType.JSON)
            .header("X-Bootstrap-Token", "incorrect-bootstrap-token-value-000000")
            .body(validRequest())
            .when().post("/api/v1/setup/administrator")
            .then()
            .statusCode(429)
            .header("Retry-After", matchesPattern("^[1-9]\\d*$"))
            .body("errorCode", equalTo("SETUP_RATE_LIMITED"));

        given()
            .contentType(ContentType.JSON)
            .header("X-Bootstrap-Token", TOKEN)
            .body(validRequest())
            .when().post("/api/v1/setup/administrator")
            .then()
            .statusCode(429)
            .header("Retry-After", matchesPattern("^[1-9]\\d*$"));

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "select failure_count, blocked_until from bootstrap_rate_limits"
             ); var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            assertEquals(5, rows.getInt(1));
            assertTrue(rows.getObject(2) != null);
            assertFalse(rows.next());
        }
    }

    private int count(java.sql.Connection connection, String table, String condition) throws SQLException {
        try (var statement = connection.prepareStatement(
            "select count(*) from " + table + " where " + condition
        ); var rows = statement.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private String validRequest() {
        return """
            {
              "username": "Admin.User",
              "email": "Admin@Example.COM",
              "displayName": "Glacier Administrator",
              "password": "%s"
            }
            """.formatted(VALID_PASSWORD);
    }
}
