package com.glaciernotes.cloud;

import com.glaciernotes.cloud.application.port.PasswordVerifier;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthenticationResourceTest {
    private static final String PASSWORD = "correct-horse-battery-staple-2026";
    private static final UUID ADMIN_ID = UUID.fromString("a3c12303-7962-4ee7-a83b-68aa5d9e1c4a");
    private static final UUID USER_ID = UUID.fromString("660dfd47-1fa8-42ac-8571-b43fe5775c85");

    @Inject
    DataSource dataSource;

    @Inject
    PasswordVerifier passwordVerifier;

    @BeforeEach
    void createUsers() throws SQLException {
        reset();
        insertUser(ADMIN_ID, "Admin.User", "Admin@Example.COM", "Administrator", "ADMIN");
        insertUser(USER_ID, "normal.user", "user@example.com", "Normal User", "USER");
    }

    @AfterEach
    void reset() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from audit_events");
            statement.executeUpdate("delete from login_rate_limits");
            statement.executeUpdate("delete from user_sessions");
            statement.executeUpdate("delete from app_users");
            statement.executeUpdate("update instance_settings set public_base_url = null where singleton_key = 1");
        }
    }

    @Test
    void logsInByNormalizedEmailAndCreatesOnlyHashedServerState() throws SQLException {
        var login = login("ADMIN@example.com", PASSWORD, false);
        login.then()
            .statusCode(200)
            .body("user.id", equalTo(ADMIN_ID.toString()))
            .body("user.username", equalTo("Admin.User"))
            .body("user.email", equalTo("Admin@Example.COM"))
            .body("user.role", equalTo("ADMIN"))
            .body("session.current", equalTo(true))
            .body("session.rememberMe", equalTo(false))
            .header("Content-Security-Policy", matchesPattern(".*frame-ancestors 'none'.*"))
            .header("X-Frame-Options", equalTo("DENY"))
            .header("X-Content-Type-Options", equalTo("nosniff"));

        var sessionCookie = login.getCookie("GLACIER_SESSION");
        var csrfCookie = login.getCookie("GLACIER_CSRF");
        assertNotNull(sessionCookie);
        assertNotNull(csrfCookie);
        assertTrue(setCookieHeaders(login).stream().anyMatch(value ->
            value.toLowerCase(java.util.Locale.ROOT).startsWith("glacier_session=")
                && value.toLowerCase(java.util.Locale.ROOT).contains("httponly")
                && value.toLowerCase(java.util.Locale.ROOT).contains("samesite=lax")
                && value.toLowerCase(java.util.Locale.ROOT).contains("path=/")
                && !value.toLowerCase(java.util.Locale.ROOT).contains("secure")
        ), setCookieHeaders(login).toString());
        assertTrue(setCookieHeaders(login).stream().anyMatch(value ->
            value.toLowerCase(java.util.Locale.ROOT).startsWith("glacier_csrf=")
                && !value.toLowerCase(java.util.Locale.ROOT).contains("httponly")
        ), setCookieHeaders(login).toString());

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "select token_hash, extract(epoch from expires_at - created_at) from user_sessions"
             ); var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            assertNotEquals(sessionCookie, rows.getString(1));
            assertTrue(rows.getString(1).matches("^[0-9a-f]{64}$"));
            assertTrue(rows.getLong(2) == 43_200L);
            assertFalse(rows.next());
        }

        given().cookie("GLACIER_SESSION", sessionCookie)
            .when().get("/api/v1/auth/session")
            .then().statusCode(200).body("session.current", equalTo(true));
        given().cookie("GLACIER_SESSION", sessionCookie)
            .when().get("/api/v1/admin/status")
            .then().statusCode(200).body("database", equalTo("up"));
    }

    @Test
    void credentialFailuresAreNeutralAndDisplayNamesCannotAuthenticate() {
        login("Admin.User", "wrong-password-value", false).then()
            .statusCode(401)
            .body("errorCode", equalTo("AUTH_INVALID_CREDENTIALS"));
        login("Administrator", PASSWORD, false).then()
            .statusCode(401)
            .body("errorCode", equalTo("AUTH_INVALID_CREDENTIALS"));
        login("missing@example.com", PASSWORD, false).then()
            .statusCode(401)
            .body("errorCode", equalTo("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void enforcesCsrfAndSupportsSessionSelfManagement() {
        var first = login("Admin.User", PASSWORD, false);
        var second = login("Admin.User", PASSWORD, true);
        var currentToken = second.getCookie("GLACIER_SESSION");
        var csrf = second.getCookie("GLACIER_CSRF");

        given().cookie("GLACIER_SESSION", currentToken)
            .when().get("/api/v1/me/sessions")
            .then().statusCode(200).body("$", hasSize(2));

        given().cookie("GLACIER_SESSION", currentToken)
            .when().delete("/api/v1/me/sessions")
            .then().statusCode(403).body("errorCode", equalTo("CSRF_INVALID"));

        given()
            .cookie("GLACIER_SESSION", currentToken)
            .cookie("GLACIER_CSRF", csrf)
            .header("X-CSRF-Token", csrf)
            .when().delete("/api/v1/me/sessions")
            .then().statusCode(204);

        given().cookie("GLACIER_SESSION", first.getCookie("GLACIER_SESSION"))
            .when().get("/api/v1/auth/session")
            .then()
            .statusCode(401)
            .contentType("application/problem+json")
            .body("errorCode", equalTo("AUTH_SESSION_EXPIRED"));

        given()
            .cookie("GLACIER_SESSION", currentToken)
            .cookie("GLACIER_CSRF", csrf)
            .header("X-CSRF-Token", csrf)
            .when().post("/api/v1/auth/logout")
            .then().statusCode(204);

        given().cookie("GLACIER_SESSION", currentToken)
            .when().get("/api/v1/auth/session")
            .then()
            .statusCode(401)
            .body("errorCode", equalTo("AUTH_SESSION_EXPIRED"));
    }

    @Test
    void appliesProgressiveCooldownAndTemporaryAccountLock() throws SQLException {
        for (int attempt = 1; attempt <= 10; attempt++) {
            var result = login("Admin.User", "wrong-password-value", false);
            if (attempt < 5) {
                result.then().statusCode(401);
            } else {
                result.then()
                    .statusCode(429)
                    .header("Retry-After", matchesPattern("^[1-9]\\d*$"))
                    .body("errorCode", equalTo("AUTH_RATE_LIMITED"));
                expireCooldowns();
            }
        }

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "select status, failed_login_count, locked_until from app_users where id = ?"
             )) {
            statement.setObject(1, ADMIN_ID);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertTrue("LOCKED".equals(rows.getString(1)));
                assertTrue(rows.getInt(2) >= 10);
                assertNotNull(rows.getObject(3));
            }
        }
    }

    @Test
    void userRoleCannotAccessAdministrativeEndpointAndHttpsEnablesSecureCookies() throws SQLException {
        var userLogin = login("normal.user", PASSWORD, false);
        given().cookie("GLACIER_SESSION", userLogin.getCookie("GLACIER_SESSION"))
            .when().get("/api/v1/admin/status")
            .then().statusCode(403);

        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(
                "update instance_settings set public_base_url = 'https://notes.example.test' "
                    + "where singleton_key = 1"
            );
        }
        var secureLogin = login("Admin.User", PASSWORD, true);
        assertTrue(setCookieHeaders(secureLogin).stream().allMatch(value -> value.contains("Secure")));
        assertTrue(setCookieHeaders(secureLogin).stream().allMatch(value -> value.contains("Max-Age")));
    }

    private Response login(String identifier, String password, boolean rememberMe) {
        return given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Glacier Test Browser")
            .body("""
                {"identifier":"%s","password":"%s","rememberMe":%s}
                """.formatted(identifier, password, rememberMe))
            .when().post("/api/v1/auth/login");
    }

    private List<String> setCookieHeaders(Response response) {
        return response.getHeaders().getValues("Set-Cookie");
    }

    private void expireCooldowns() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(
                "update login_rate_limits set blocked_until = current_timestamp - interval '1 second'"
            );
        }
    }

    private void insertUser(
        UUID id,
        String username,
        String email,
        String displayName,
        String role
    ) throws SQLException {
        var passwordHash = passwordVerifier.hash(PASSWORD.toCharArray());
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 insert into app_users(
                     id, username, username_normalized, email, email_normalized,
                     display_name, role, status, password_hash, password_changed_at, activated_at
                 ) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, current_timestamp, current_timestamp)
                 """)) {
            statement.setObject(1, id);
            statement.setString(2, username);
            statement.setString(3, username.toLowerCase(java.util.Locale.ROOT));
            statement.setString(4, email);
            statement.setString(5, email.toLowerCase(java.util.Locale.ROOT));
            statement.setString(6, displayName);
            statement.setString(7, role);
            statement.setString(8, passwordHash);
            statement.executeUpdate();
        }
    }
}
