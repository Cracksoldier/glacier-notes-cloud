package com.glaciernotes.cloud.security;

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
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Named regression tests for attack classes named in the M12 threat model: session fixation and
 * session-token replay after logout or a forced revocation (password change).
 */
@QuarkusTest
class SecurityAttackSimulationTest {
    private static final String PASSWORD = "correct-horse-battery-staple-2026";
    private static final UUID USER_ID = UUID.fromString("d3f0a6f2-7b3e-4c3b-9c1a-2c9e6a3f5b11");

    @Inject
    DataSource dataSource;

    @Inject
    PasswordVerifier passwordVerifier;

    @BeforeEach
    void createUser() throws SQLException {
        reset();
        insertUser(USER_ID, "fixation.target", "fixation@example.com", "Fixation Target");
    }

    @AfterEach
    void reset() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from audit_events");
            statement.executeUpdate("delete from endpoint_rate_limits");
            statement.executeUpdate("delete from login_rate_limits");
            statement.executeUpdate("delete from security_tokens");
            statement.executeUpdate("delete from invitations");
            statement.executeUpdate("delete from user_sessions");
            statement.executeUpdate("delete from app_users");
        }
    }

    @Test
    void loginNeverAdoptsAnAttackerSuppliedSessionCookie() {
        var attackerChosenToken = "attacker-fixed-session-token-value";

        var login = given()
            .cookie("GLACIER_SESSION", attackerChosenToken)
            .contentType(ContentType.JSON)
            .header("User-Agent", "Glacier Test Browser")
            .body("""
                {"identifier":"fixation.target","password":"%s","rememberMe":false}
                """.formatted(PASSWORD))
            .when().post("/api/v1/auth/login");
        login.then().statusCode(200);

        var issuedToken = login.getCookie("GLACIER_SESSION");
        assertNotNull(issuedToken);
        assertNotEquals(attackerChosenToken, issuedToken);

        given().cookie("GLACIER_SESSION", attackerChosenToken)
            .when().get("/api/v1/auth/session")
            .then().statusCode(401).body("errorCode", org.hamcrest.Matchers.equalTo("AUTH_SESSION_EXPIRED"));
    }

    @Test
    void sessionTokenIsRejectedAfterLogout() {
        var session = login();
        var token = session.getCookie("GLACIER_SESSION");
        var csrf = session.getCookie("GLACIER_CSRF");

        given().cookie("GLACIER_SESSION", token).cookie("GLACIER_CSRF", csrf)
            .header("X-CSRF-Token", csrf)
            .when().post("/api/v1/auth/logout")
            .then().statusCode(204);

        given().cookie("GLACIER_SESSION", token)
            .when().get("/api/v1/auth/session")
            .then().statusCode(401).body("errorCode", org.hamcrest.Matchers.equalTo("AUTH_SESSION_EXPIRED"));

        given().cookie("GLACIER_SESSION", token)
            .when().get("/api/v1/me/sessions")
            .then().statusCode(401);
    }

    @Test
    void sessionTokenIsRejectedAfterForcedRevocationOnPasswordChange() {
        var session = login();
        var token = session.getCookie("GLACIER_SESSION");
        var csrf = session.getCookie("GLACIER_CSRF");

        given().cookie("GLACIER_SESSION", token).cookie("GLACIER_CSRF", csrf)
            .header("X-CSRF-Token", csrf)
            .contentType(ContentType.JSON)
            .body("""
                {"currentPassword":"%s","newPassword":"a-different-strong-passphrase-2026"}
                """.formatted(PASSWORD))
            .when().put("/api/v1/me/password")
            .then().statusCode(204);

        given().cookie("GLACIER_SESSION", token)
            .when().get("/api/v1/auth/session")
            .then().statusCode(401).body("errorCode", org.hamcrest.Matchers.equalTo("AUTH_SESSION_EXPIRED"));
    }

    private Response login() {
        var response = given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Glacier Test Browser")
            .body("""
                {"identifier":"fixation.target","password":"%s","rememberMe":false}
                """.formatted(PASSWORD))
            .when().post("/api/v1/auth/login");
        response.then().statusCode(200);
        return response;
    }

    private void insertUser(UUID id, String username, String email, String displayName) throws SQLException {
        var passwordHash = passwordVerifier.hash(PASSWORD.toCharArray());
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 insert into app_users(
                     id, username, username_normalized, email, email_normalized,
                     display_name, role, status, password_hash, password_changed_at, activated_at
                 ) values (?, ?, ?, ?, ?, ?, 'USER', 'ACTIVE', ?, current_timestamp, current_timestamp)
                 """)) {
            statement.setObject(1, id);
            statement.setString(2, username);
            statement.setString(3, username.toLowerCase(java.util.Locale.ROOT));
            statement.setString(4, email);
            statement.setString(5, email.toLowerCase(java.util.Locale.ROOT));
            statement.setString(6, displayName);
            statement.setString(7, passwordHash);
            statement.executeUpdate();
        }
    }
}
