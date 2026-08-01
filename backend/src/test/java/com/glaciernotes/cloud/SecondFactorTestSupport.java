package com.glaciernotes.cloud;

import com.glaciernotes.cloud.application.port.PasswordVerifier;
import com.glaciernotes.cloud.security.Base32Codec;
import com.glaciernotes.cloud.security.TotpVerifier;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared harness for the step-up suites. Every one of them needs an enrolled account, a live
 * session, and codes generated against the same clock, so the setup lives here rather than being
 * copied six times.
 */
abstract class SecondFactorTestSupport {
    protected static final String PASSWORD = "correct-horse-battery-staple-2026";
    protected static final int PERIOD_SECONDS = 30;
    protected static final int DIGITS = 6;

    @Inject
    DataSource dataSource;

    @Inject
    PasswordVerifier passwordVerifier;

    @Inject
    TotpVerifier totp;

    protected record Login(String session, String csrf) {
    }

    protected void cleanDatabase() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from audit_events");
            statement.executeUpdate("delete from endpoint_rate_limits");
            statement.executeUpdate("delete from login_rate_limits");
            statement.executeUpdate("delete from security_tokens");
            statement.executeUpdate("delete from mfa_challenges");
            statement.executeUpdate("delete from user_mfa_recovery_codes");
            statement.executeUpdate("delete from user_mfa_totp");
            statement.executeUpdate("delete from user_sessions");
            statement.executeUpdate("delete from user_settings");
            statement.executeUpdate("delete from app_users");
            // Other suites leave the administrator-editable tunables changed.
            statement.executeUpdate("""
                update instance_settings
                   set mfa_challenge_lifetime_minutes = 5,
                       mfa_challenge_attempt_limit = 5,
                       mfa_pending_enrollment_minutes = 30,
                       mfa_step_up_grace_minutes = 5,
                       login_lock_threshold = 10,
                       self_deletion_enabled = true,
                       default_language = 'en'
                """);
        }
    }

    protected UUID insertUser(String username, String email, String role) throws SQLException {
        var id = UUID.randomUUID();
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
            statement.setString(3, username.toLowerCase(Locale.ROOT));
            statement.setString(4, email);
            statement.setString(5, email.toLowerCase(Locale.ROOT));
            statement.setString(6, username);
            statement.setString(7, role);
            statement.setString(8, passwordHash);
            statement.executeUpdate();
        }
        return id;
    }

    protected Login login(String identifier) {
        var response = passwordStep(identifier);
        response.then().statusCode(200).body("result", equalTo("SESSION"));
        return new Login(response.getCookie("GLACIER_SESSION"), response.getCookie("GLACIER_CSRF"));
    }

    protected Response passwordStep(String identifier) {
        return given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Glacier Test Browser")
            .body("""
                {"identifier":"%s","password":"%s","rememberMe":false}
                """.formatted(identifier, PASSWORD))
            .when().post("/api/v1/auth/login");
    }

    /** Logs in through both stages, which is the only way a session opens with a grace window. */
    protected Login secondFactorLogin(String identifier, String code) {
        var challenge = passwordStep(identifier);
        challenge.then().statusCode(200).body("result", equalTo("MFA_REQUIRED"));
        var response = given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Glacier Test Browser")
            .body("""
                {"challengeToken":"%s","code":"%s"}
                """.formatted(challenge.jsonPath().getString("challenge.token"), code))
            .when().post("/api/v1/auth/login/mfa");
        response.then().statusCode(200);
        return new Login(response.getCookie("GLACIER_SESSION"), response.getCookie("GLACIER_CSRF"));
    }

    protected byte[] startEnrollment(Login login) {
        var response = authenticated(login)
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/mfa/totp");
        response.then().statusCode(200);
        return Base32Codec.decode(response.jsonPath().getString("secret"));
    }

    protected List<String> confirmEnrollment(Login login, byte[] secret) {
        return authenticated(login)
            .body("""
                {"code":"%s"}
                """.formatted(currentCode(secret)))
            .when().post("/api/v1/me/mfa/totp/confirm")
            .then().statusCode(200).extract().jsonPath().getList("codes", String.class);
    }

    protected byte[] enroll(Login login) {
        var secret = startEnrollment(login);
        confirmEnrollment(login, secret);
        return secret;
    }

    protected RequestSpecification authenticated(Login login) {
        return given()
            .cookie("GLACIER_SESSION", login.session())
            .cookie("GLACIER_CSRF", login.csrf())
            .header("X-CSRF-Token", login.csrf())
            .header("User-Agent", "Glacier Test Browser")
            .contentType(ContentType.JSON);
    }

    /** Puts every session back to never having proved possession, so the next call is prompted. */
    protected void closeGraceWindow() throws SQLException {
        execute("update user_sessions set second_factor_verified_at = null");
    }

    /**
     * A confirmation or a login burns its own TOTP step, so a test that needs to spend another code
     * inside the same 30-second window has to forget it first.
     */
    protected void forgetAcceptedStep() throws SQLException {
        execute("update user_mfa_totp set last_accepted_step = null");
    }

    protected String currentCode(byte[] secret) {
        return codeAtOffset(secret, 0);
    }

    protected String codeAtOffset(byte[] secret, long stepOffset) {
        awaitStableStep();
        return totp.generate(secret, Instant.now().getEpochSecond() / PERIOD_SECONDS + stepOffset, DIGITS);
    }

    private void awaitStableStep() {
        long secondsIntoStep = Instant.now().getEpochSecond() % PERIOD_SECONDS;
        if (secondsIntoStep < PERIOD_SECONDS - 2) {
            return;
        }
        try {
            Thread.sleep(2_500);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    protected void execute(String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    protected long count(String table, String condition) throws SQLException {
        return ((Number) scalar("select count(*) from " + table + " where " + condition)).longValue();
    }

    protected Object scalar(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            return rows.getObject(1);
        }
    }
}
