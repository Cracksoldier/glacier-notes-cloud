package com.glaciernotes.cloud;

import com.glaciernotes.cloud.application.port.PasswordVerifier;
import com.glaciernotes.cloud.security.Base32Codec;
import com.glaciernotes.cloud.security.TotpVerifier;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SecondFactorResetTest {
    private static final String TOKEN = "test-bootstrap-token-with-at-least-32-characters";
    private static final String WRONG_TOKEN = "incorrect-bootstrap-token-value-000000";
    private static final String PASSWORD = "correct-horse-battery-staple-2026";
    private static final String USERNAME = "Locked.Out";
    private static final UUID USER_ID = UUID.fromString("d41d0f2a-9c5b-4a1e-8f77-2b6d3c9e5a41");
    private static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;

    @Inject
    DataSource dataSource;

    @Inject
    PasswordVerifier passwordVerifier;

    @Inject
    TotpVerifier totp;

    @BeforeEach
    void createUser() throws SQLException {
        reset();
        insertUser();
    }

    @AfterEach
    void reset() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from audit_events");
            statement.executeUpdate("delete from endpoint_rate_limits");
            statement.executeUpdate("delete from login_rate_limits");
            statement.executeUpdate("delete from bootstrap_rate_limits");
            statement.executeUpdate("delete from mfa_challenges");
            statement.executeUpdate("delete from user_mfa_recovery_codes");
            statement.executeUpdate("delete from user_mfa_totp");
            statement.executeUpdate("delete from user_sessions");
            statement.executeUpdate("delete from app_users");
        }
    }

    @Test
    void clearsTheEnrollmentEndsItsSessionsAndRestoresASingleStepLogin() throws SQLException {
        enroll();
        passwordStep().then().statusCode(200).body("result", equalTo("MFA_REQUIRED"));
        assertEquals(1, count("user_sessions", "revoked_at is null"));
        assertEquals(1, count("mfa_challenges", "true"));

        reset("locked.out@example.COM").then().statusCode(204).body(equalTo(""));

        assertEquals(0, count("user_mfa_totp", "true"));
        assertEquals(0, count("user_mfa_recovery_codes", "true"));
        assertEquals(0, count("mfa_challenges", "true"));
        assertEquals(0, count("user_sessions", "revoked_at is null"));
        assertEquals(1, count(
            "audit_events",
            "event_type = 'MFA_OPERATOR_RESET' and actor_user_id is null"
                + " and target_user_id = '" + USER_ID + "'"
                + " and metadata_json->>'matched' = 'true'"
        ));

        passwordStep().then().statusCode(200).body("result", equalTo("SESSION"));
    }

    @Test
    void doesNotRevealWhetherAnAccountExistedOrCarriedASecondFactor() throws SQLException {
        var unknown = reset("nobody@example.com");
        var withoutEnrollment = reset(USERNAME);
        enroll();
        var withEnrollment = reset(USERNAME);

        for (var response : new Response[]{unknown, withoutEnrollment, withEnrollment}) {
            response.then().statusCode(204).body(equalTo(""));
            assertTrue(response.getHeaders().getValues("Set-Cookie").isEmpty());
        }
        assertEquals(1, count(
            "audit_events",
            "event_type = 'MFA_OPERATOR_RESET' and metadata_json->>'matched' = 'false'"
        ));
        assertEquals(2, count(
            "audit_events",
            "event_type = 'MFA_OPERATOR_RESET' and metadata_json->>'matched' = 'true'"
        ));
    }

    @Test
    void deniesAndThrottlesAWrongBootstrapTokenWithoutTouchingTheEnrollment() throws SQLException {
        enroll();

        for (int attempt = 1; attempt < 5; attempt++) {
            resetWith(WRONG_TOKEN, USERNAME).then()
                .statusCode(403)
                .body("errorCode", equalTo("SETUP_DENIED"));
        }
        resetWith(WRONG_TOKEN, USERNAME).then()
            .statusCode(429)
            .header("Retry-After", matchesPattern("^[1-9]\\d*$"))
            .body("errorCode", equalTo("SETUP_RATE_LIMITED"));
        // The cooldown applies to the correct token as well, so a leaked one cannot be probed.
        reset(USERNAME).then().statusCode(429);

        assertEquals(1, count("user_mfa_totp", "status = 'ACTIVE'"));
        assertEquals(10, count("user_mfa_recovery_codes", "true"));
        assertEquals(0, count("audit_events", "event_type = 'MFA_OPERATOR_RESET'"));
        assertEquals(1, count("bootstrap_rate_limits", "failure_count = 5 and blocked_until is not null"));
    }

    @Test
    void requiresTheBootstrapTokenHeader() throws SQLException {
        enroll();

        given()
            .contentType(ContentType.JSON)
            .body("""
                {"identifier":"%s"}
                """.formatted(USERNAME))
            .when().post("/api/v1/setup/second-factor-reset")
            .then().statusCode(400);

        assertEquals(1, count("user_mfa_totp", "status = 'ACTIVE'"));
    }

    private Response reset(String identifier) {
        return resetWith(TOKEN, identifier);
    }

    private Response resetWith(String bootstrapToken, String identifier) {
        return given()
            .contentType(ContentType.JSON)
            .header("X-Bootstrap-Token", bootstrapToken)
            .body("""
                {"identifier":"%s"}
                """.formatted(identifier))
            .when().post("/api/v1/setup/second-factor-reset");
    }

    private Response passwordStep() {
        return given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Glacier Test Browser")
            .body("""
                {"identifier":"%s","password":"%s","rememberMe":false}
                """.formatted(USERNAME, PASSWORD))
            .when().post("/api/v1/auth/login");
    }

    /** Drives the real enrollment API so the stored secret is genuine ciphertext. */
    private void enroll() {
        var login = passwordStep();
        login.then().statusCode(200).body("result", equalTo("SESSION"));
        var session = login.getCookie("GLACIER_SESSION");
        var csrf = login.getCookie("GLACIER_CSRF");

        var start = given()
            .cookie("GLACIER_SESSION", session).cookie("GLACIER_CSRF", csrf)
            .header("X-CSRF-Token", csrf).contentType(ContentType.JSON)
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/mfa/totp");
        start.then().statusCode(200);
        var secret = Base32Codec.decode(start.jsonPath().getString("secret"));

        given()
            .cookie("GLACIER_SESSION", session).cookie("GLACIER_CSRF", csrf)
            .header("X-CSRF-Token", csrf).contentType(ContentType.JSON)
            .body("""
                {"code":"%s"}
                """.formatted(totp.generate(secret, Instant.now().getEpochSecond() / PERIOD_SECONDS, DIGITS)))
            .when().post("/api/v1/me/mfa/totp/confirm")
            .then().statusCode(200);
    }

    private long count(String table, String condition) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "select count(*) from " + table + " where " + condition
             ); var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private void insertUser() throws SQLException {
        var passwordHash = passwordVerifier.hash(PASSWORD.toCharArray());
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 insert into app_users(
                     id, username, username_normalized, email, email_normalized,
                     display_name, role, status, password_hash, password_changed_at, activated_at
                 ) values (?, ?, ?, ?, ?, ?, 'USER', 'ACTIVE', ?,
                           current_timestamp - interval '1 day', current_timestamp)
                 """)) {
            statement.setObject(1, USER_ID);
            statement.setString(2, USERNAME);
            statement.setString(3, "locked.out");
            statement.setString(4, "Locked.Out@Example.com");
            statement.setString(5, "locked.out@example.com");
            statement.setString(6, "Locked Out");
            statement.setString(7, passwordHash);
            statement.executeUpdate();
        }
    }
}
