package com.glaciernotes.cloud;

import com.glaciernotes.cloud.application.port.PasswordVerifier;
import com.glaciernotes.cloud.security.Base32Codec;
import com.glaciernotes.cloud.security.TotpVerifier;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MfaEnrollmentTest {
    private static final String PASSWORD = "correct-horse-battery-staple-2026";
    private static final UUID USER_ID = UUID.fromString("2a0a1b1c-6d2f-4f0e-9a37-6c5f0f2d9b11");
    private static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;

    @Inject
    DataSource dataSource;

    @Inject
    PasswordVerifier passwordVerifier;

    @Inject
    TotpVerifier totp;

    private String sessionCookie;
    private String csrfToken;

    @BeforeEach
    void createUserAndLogIn() throws SQLException {
        reset();
        insertUser();
        var login = given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Glacier Test Browser")
            .body("""
                {"identifier":"totp.user","password":"%s","rememberMe":false}
                """.formatted(PASSWORD))
            .when().post("/api/v1/auth/login");
        login.then().statusCode(200).body("result", equalTo("SESSION"));
        sessionCookie = login.getCookie("GLACIER_SESSION");
        csrfToken = login.getCookie("GLACIER_CSRF");
    }

    @AfterEach
    void reset() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from audit_events");
            statement.executeUpdate("delete from endpoint_rate_limits");
            statement.executeUpdate("delete from login_rate_limits");
            statement.executeUpdate("delete from mfa_challenges");
            statement.executeUpdate("delete from user_mfa_recovery_codes");
            statement.executeUpdate("delete from user_mfa_totp");
            statement.executeUpdate("delete from user_sessions");
            statement.executeUpdate("delete from app_users");
        }
    }

    @Test
    void walksTheEnrollmentLifecycleAndReportsStatusAtEachStage() throws SQLException {
        status().statusCode(200)
            .body("status", equalTo("NONE"))
            .body("available", equalTo(true))
            .body("confirmedAt", nullValue())
            .body("recoveryCodesRemaining", nullValue());

        var secret = startEnrollment();
        status().statusCode(200)
            .body("status", equalTo("PENDING"))
            .body("available", equalTo(true))
            .body("confirmedAt", nullValue())
            .body("recoveryCodesRemaining", nullValue());

        var codes = confirm(currentCode(secret)).statusCode(200)
            .body("codes", hasSize(10))
            .body("generatedAt", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T.*Z$"))
            .extract().jsonPath().getList("codes", String.class);

        status().statusCode(200)
            .body("status", equalTo("ACTIVE"))
            .body("available", equalTo(true))
            .body("confirmedAt", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T.*Z$"))
            .body("recoveryCodesRemaining", equalTo(10));

        assertEquals(10, codes.stream().distinct().count());
        assertTrue(codes.stream().allMatch(code -> code.matches("^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$")));
        assertEquals(1, count("user_mfa_totp", "status = 'ACTIVE' and confirmed_at is not null"));
        assertEquals(10, count("user_mfa_recovery_codes", "used_at is null"));
        // The confirming code burns its own step, so it cannot be replayed at the login stage.
        assertNotNull(scalar("select last_accepted_step from user_mfa_totp"));
    }

    @Test
    void refusesEveryMutationWithoutTheCurrentPasswordOrTheCsrfHeader() throws SQLException {
        given().cookie("GLACIER_SESSION", sessionCookie)
            .cookie("GLACIER_CSRF", csrfToken)
            .contentType(ContentType.JSON)
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/mfa/totp")
            .then().statusCode(403).body("errorCode", equalTo("CSRF_INVALID"));

        authenticated().body("""
                {"currentPassword":"not-the-right-password"}
                """)
            .when().post("/api/v1/me/mfa/totp")
            .then().statusCode(403).body("errorCode", equalTo("CURRENT_PASSWORD_INVALID"));

        assertEquals(0, count("user_mfa_totp", "true"));
        assertEquals(1, count("audit_events", "event_type = 'MFA_REAUTHENTICATION_FAILED'"));
    }

    @Test
    void refusesASecondEnrollmentAndOperationsThatNeedAnActiveOne() throws SQLException {
        regenerateRequest().then().statusCode(409).body("errorCode", equalTo("MFA_NOT_ENROLLED"));
        disableRequest(PASSWORD).then().statusCode(409).body("errorCode", equalTo("MFA_NOT_ENROLLED"));

        var secret = startEnrollment();
        // A pending record is not an enrollment yet.
        regenerateRequest().then().statusCode(409).body("errorCode", equalTo("MFA_NOT_ENROLLED"));
        confirm(currentCode(secret)).statusCode(200);

        startRequest().then().statusCode(409).body("errorCode", equalTo("MFA_ALREADY_ENROLLED"));
        confirm(currentCode(secret)).statusCode(409).body("errorCode", equalTo("MFA_ALREADY_ENROLLED"));
        assertEquals(1, count("user_mfa_totp", "status = 'ACTIVE'"));
    }

    @Test
    void rejectsAWrongConfirmationCodeAndKeepsTheEnrollmentPending() throws SQLException {
        var secret = startEnrollment();

        confirm("000000").statusCode(401).body("errorCode", equalTo("AUTH_MFA_INVALID_CODE"));
        confirm(codeAtOffset(secret, -2)).statusCode(401)
            .body("errorCode", equalTo("AUTH_MFA_INVALID_CODE"));
        assertEquals(1, count("user_mfa_totp", "status = 'PENDING'"));
        assertEquals(0, count("user_mfa_recovery_codes", "true"));

        confirm(currentCode(secret)).statusCode(200);
    }

    /**
     * The refusal and the cleanup share a transaction, so the row only disappears if the rollback is
     * suppressed for the failure that carries the refusal.
     */
    @Test
    void confirmingAnExpiredPendingEnrollmentDiscardsIt() throws SQLException {
        var secret = startEnrollment();
        agePendingEnrollment();

        confirm(currentCode(secret)).statusCode(409)
            .body("errorCode", equalTo("MFA_NOT_ENROLLED"));

        assertEquals(0, count("user_mfa_totp", "true"));
    }

    @Test
    void restartingDiscardsThePreviousPendingSecret() throws SQLException {
        var abandoned = startEnrollment();
        var replacement = startEnrollment();

        assertFalse(java.util.Arrays.equals(abandoned, replacement));
        assertEquals(1, count("user_mfa_totp", "true"));
        confirm(currentCode(abandoned)).statusCode(401)
            .body("errorCode", equalTo("AUTH_MFA_INVALID_CODE"));
        confirm(currentCode(replacement)).statusCode(200);
    }

    @Test
    void cancelDiscardsAPendingEnrollmentButNeverAnActiveOne() throws SQLException {
        startEnrollment();
        cancelRequest().then().statusCode(204);
        status().statusCode(200).body("status", equalTo("NONE"));
        assertEquals(0, count("user_mfa_totp", "true"));

        // Cancelling with nothing pending is a no-op rather than an error.
        cancelRequest().then().statusCode(204);

        var secret = startEnrollment();
        confirm(currentCode(secret)).statusCode(200);
        cancelRequest().then().statusCode(204);
        status().statusCode(200).body("status", equalTo("ACTIVE"));
        assertEquals(1, count("user_mfa_totp", "status = 'ACTIVE'"));
    }

    @Test
    void regenerationReplacesEveryPreviousRecoveryCode() throws SQLException {
        var secret = startEnrollment();
        var original = confirm(currentCode(secret)).statusCode(200)
            .extract().jsonPath().getList("codes", String.class);

        var replacement = regenerateRequest().then().statusCode(200)
            .body("codes", hasSize(10))
            .extract().jsonPath().getList("codes", String.class);

        assertTrue(replacement.stream().noneMatch(original::contains));
        assertEquals(10, count("user_mfa_recovery_codes", "used_at is null"));
        status().statusCode(200).body("recoveryCodesRemaining", equalTo(10));
    }

    @Test
    void disableRemovesTheEnrollmentAndEverythingDerivedFromIt() throws SQLException {
        var secret = startEnrollment();
        confirm(currentCode(secret)).statusCode(200);

        disableRequest("not-the-right-password").then()
            .statusCode(403).body("errorCode", equalTo("CURRENT_PASSWORD_INVALID"));
        assertEquals(1, count("user_mfa_totp", "true"));

        disableRequest(PASSWORD).then().statusCode(204);
        status().statusCode(200).body("status", equalTo("NONE"));
        assertEquals(0, count("user_mfa_totp", "true"));
        assertEquals(0, count("user_mfa_recovery_codes", "true"));
        assertEquals(1, count("audit_events", "event_type = 'MFA_DISABLED'"));
    }

    @Test
    void neverReturnsStoredSecondFactorMaterialOrLeavesPlaintextBehind() throws SQLException {
        var start = startRequest();
        start.then().statusCode(200)
            .body("digits", equalTo(DIGITS))
            .body("periodSeconds", equalTo(PERIOD_SECONDS))
            .body("secret", matchesPattern("^[A-Z2-7]{4}( [A-Z2-7]{1,4})+$"))
            .body("provisioningUri", matchesPattern("^otpauth://totp/.*secret=[A-Z2-7]+.*$"));
        var secret = Base32Codec.decode(start.jsonPath().getString("secret"));

        // The start response groups the secret for display; a leak would carry the ungrouped
        // Base32, so comparing against the grouped form alone can never fail.
        var grouped = start.jsonPath().getString("secret");
        var ungrouped = grouped.replace(" ", "");
        var confirmation = confirm(currentCode(secret)).extract().response();
        var codes = confirmation.jsonPath().getList("codes", String.class);
        assertFalse(confirmation.asString().contains(grouped));
        assertFalse(confirmation.asString().contains(ungrouped));

        var statusBody = statusResponse().asString();
        assertFalse(statusBody.contains("codes"));
        assertFalse(statusBody.contains("secret"));
        assertFalse(statusBody.contains(ungrouped));
        assertFalse(statusBody.contains("provisioningUri"));
        for (var code : codes) {
            assertFalse(statusBody.contains(code));
            assertEquals(0, count(
                "user_mfa_recovery_codes", "code_hash = '" + code.replace("'", "''") + "'"
            ));
        }

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "select encode(secret_ciphertext, 'hex'), key_id from user_mfa_totp"
             ); var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            assertFalse(rows.getString(1).contains(
                java.util.HexFormat.of().formatHex(secret)
            ));
            assertNotNull(rows.getString(2));
        }
        assertEquals(0, count(
            "audit_events",
            "metadata_json::text ilike '%secret%' or metadata_json::text ilike '%code%'"
        ));
    }

    private byte[] startEnrollment() {
        var response = startRequest();
        response.then().statusCode(200);
        return Base32Codec.decode(response.jsonPath().getString("secret"));
    }

    private Response startRequest() {
        return authenticated()
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/mfa/totp");
    }

    private io.restassured.response.ValidatableResponse confirm(String code) {
        return authenticated()
            .body("""
                {"code":"%s"}
                """.formatted(code))
            .when().post("/api/v1/me/mfa/totp/confirm")
            .then();
    }

    private Response cancelRequest() {
        return authenticated().when().delete("/api/v1/me/mfa/totp/pending");
    }

    private Response disableRequest(String password) {
        return authenticated()
            .body("""
                {"currentPassword":"%s"}
                """.formatted(password))
            .when().post("/api/v1/me/mfa/totp/disable");
    }

    private Response regenerateRequest() {
        return authenticated()
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/mfa/recovery-codes");
    }

    private io.restassured.response.ValidatableResponse status() {
        return statusResponse().then();
    }

    private Response statusResponse() {
        return given().cookie("GLACIER_SESSION", sessionCookie).when().get("/api/v1/me/mfa");
    }

    private RequestSpecification authenticated() {
        return given()
            .cookie("GLACIER_SESSION", sessionCookie)
            .cookie("GLACIER_CSRF", csrfToken)
            .header("X-CSRF-Token", csrfToken)
            .contentType(ContentType.JSON);
    }

    private String currentCode(byte[] secret) {
        return codeAtOffset(secret, 0);
    }

    private String codeAtOffset(byte[] secret, long stepOffset) {
        awaitStableStep();
        return totp.generate(secret, Instant.now().getEpochSecond() / PERIOD_SECONDS + stepOffset, DIGITS);
    }

    /**
     * Codes are generated against the client clock and verified against the server clock, so a code
     * produced within a second of a step boundary can land in a neighbouring window.
     */
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

    private void agePendingEnrollment() throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "update user_mfa_totp set created_at = created_at - interval '30 days'"
             )) {
            statement.executeUpdate();
        }
    }

    private long count(String table, String condition) throws SQLException {
        return ((Number) scalar("select count(*) from " + table + " where " + condition)).longValue();
    }

    private Object scalar(String sql) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            return rows.getObject(1);
        }
    }

    private void insertUser() throws SQLException {
        var passwordHash = passwordVerifier.hash(PASSWORD.toCharArray());
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 insert into app_users(
                     id, username, username_normalized, email, email_normalized,
                     display_name, role, status, password_hash, password_changed_at, activated_at
                 ) values (?, ?, ?, ?, ?, ?, 'USER', 'ACTIVE', ?, current_timestamp, current_timestamp)
                 """)) {
            statement.setObject(1, USER_ID);
            statement.setString(2, "totp.user");
            statement.setString(3, "totp.user".toLowerCase(Locale.ROOT));
            statement.setString(4, "totp@example.com");
            statement.setString(5, "totp@example.com");
            statement.setString(6, "Totp User");
            statement.setString(7, passwordHash);
            statement.executeUpdate();
        }
    }
}
