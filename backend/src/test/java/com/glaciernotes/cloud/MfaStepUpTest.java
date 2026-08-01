package com.glaciernotes.cloud;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(SmtpTestProfile.class)
class MfaStepUpTest extends SecondFactorTestSupport {
    @Inject
    MockMailbox mailbox;

    private UUID userId;
    private Login login;
    private byte[] secret;

    @BeforeEach
    void enrollAnAccount() throws SQLException {
        reset();
        userId = insertUser("step.up", "step.up@example.com", "USER");
        login = login("step.up");
        secret = enroll(login);
        // Confirmation is itself a verification: it opens the window and burns its own step.
        closeGraceWindow();
        forgetAcceptedStep();
        mailbox.clear();
    }

    @AfterEach
    void reset() throws SQLException {
        mailbox.clear();
        cleanDatabase();
    }

    @Test
    void refusesEverySelfServiceOperationWhenOnlyThePasswordIsSupplied() throws SQLException {
        for (var operation : new String[] {
            "/api/v1/me/mfa/totp/disable",
            "/api/v1/me/mfa/recovery-codes",
            "/api/v1/me/deletion"
        }) {
            closeGraceWindow();
            authenticated(login)
                .body("""
                    {"currentPassword":"%s"}
                    """.formatted(PASSWORD))
                .when().post(operation)
                .then().statusCode(401).body("errorCode", equalTo("AUTH_MFA_STEP_UP_REQUIRED"));
        }
        closeGraceWindow();
        emailChange(null).then()
            .statusCode(401).body("errorCode", equalTo("AUTH_MFA_STEP_UP_REQUIRED"));

        // Refused means refused: nothing was disabled, deleted, replaced, or issued.
        assertEquals(1, count("user_mfa_totp", "status = 'ACTIVE'"));
        assertEquals(10, count("user_mfa_recovery_codes", "used_at is null"));
        assertEquals(1, count("app_users", "id = '" + userId + "' and status = 'ACTIVE'"));
        assertEquals(0, count("security_tokens", "token_type = 'EMAIL_CHANGE'"));
    }

    @Test
    void acceptsTheOperationWhenAFreshCodeIsSupplied() throws SQLException {
        emailChange(currentCode(secret)).then().statusCode(202);

        assertEquals(1, count("security_tokens", "token_type = 'EMAIL_CHANGE' and revoked_at is null"));
        assertEquals(1, count("audit_events", "event_type = 'MFA_STEP_UP_SUCCEEDED'"));
        assertEquals(0, count("audit_events", "event_type = 'MFA_STEP_UP_FAILED'"));
    }

    @Test
    void leavesAnAccountWithoutAnEnrollmentEntirelyUnchanged() throws SQLException {
        insertUser("plain.user", "plain.user@example.com", "USER");
        var plain = login("plain.user");

        authenticated(plain)
            .body("""
                {"currentPassword":"%s","newEmail":"plain.new@example.com"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/email-change")
            .then().statusCode(202);

        assertEquals(0, count("audit_events", "event_type like 'MFA_STEP_UP%'"));
    }

    @Test
    void refusesAWrongCodeAndRecordsTheAttempt() throws SQLException {
        emailChange("000000").then()
            .statusCode(401).body("errorCode", equalTo("AUTH_MFA_INVALID_CODE"));

        assertEquals(1, count("audit_events", "event_type = 'MFA_STEP_UP_FAILED'"));
        assertEquals(0, count("security_tokens", "token_type = 'EMAIL_CHANGE'"));
        assertEquals(1, count("endpoint_rate_limits", "scope = 'STEP_UP_USER'"));
        assertEquals(1, count("endpoint_rate_limits", "scope = 'STEP_UP_IP'"));
    }

    @Test
    void refusesTheWrongPasswordBeforeItEverLooksAtTheCode() throws SQLException {
        authenticated(login)
            .body("""
                {"currentPassword":"not-the-right-password","newEmail":"step.new@example.com","code":"%s"}
                """.formatted(currentCode(secret)))
            .when().post("/api/v1/me/email-change")
            .then().statusCode(403).body("errorCode", equalTo("CURRENT_PASSWORD_INVALID"));

        assertEquals(1, count("audit_events", "event_type = 'MFA_REAUTHENTICATION_FAILED'"));
        // The code was never examined, so it is still spendable.
        emailChange(currentCode(secret)).then().statusCode(202);
    }

    @Test
    void refusesACodeThatWasAlreadySpentOnALogin() throws SQLException {
        execute("delete from user_sessions");
        var code = currentCode(secret);
        var second = secondFactorLogin("step.up", code);
        closeGraceWindow();

        authenticated(second)
            .body("""
                {"currentPassword":"%s","newEmail":"step.new@example.com","code":"%s"}
                """.formatted(PASSWORD, code))
            .when().post("/api/v1/me/email-change")
            .then().statusCode(401).body("errorCode", equalTo("AUTH_MFA_INVALID_CODE"));
    }

    @Test
    void acceptsARecoveryCodeAndSpendsItOnlyOnce() throws SQLException {
        var recoveryCode = regenerateWith(currentCode(secret)).getFirst();
        closeGraceWindow();

        emailChange(recoveryCode).then().statusCode(202);
        assertEquals(1, count("user_mfa_recovery_codes", "used_at is not null"));

        closeGraceWindow();
        execute("delete from security_tokens");
        emailChange(recoveryCode).then()
            .statusCode(401).body("errorCode", equalTo("AUTH_MFA_INVALID_CODE"));
    }

    @Test
    void endsInARateLimitAfterRepeatedWrongCodes() throws SQLException {
        for (int attempt = 0; attempt < 10; attempt++) {
            closeGraceWindow();
            disableWith("000000").then().statusCode(401);
        }

        // The attempt past the ceiling is refused as rate limited and reported with a wait.
        closeGraceWindow();
        disableWith("000000").then()
            .statusCode(429)
            .body("errorCode", equalTo("LIFECYCLE_RATE_LIMITED"))
            .header("Retry-After", org.hamcrest.Matchers.matchesPattern("^\\d+$"));

        // A correct code no longer helps.
        closeGraceWindow();
        disableWith(currentCode(secret)).then()
            .statusCode(429).body("errorCode", equalTo("LIFECYCLE_RATE_LIMITED"));
        assertEquals(1, count("user_mfa_totp", "status = 'ACTIVE'"));
    }

    @Test
    void endsInARateLimitAfterRepeatedWrongPasswords() throws SQLException {
        for (int attempt = 0; attempt < 10; attempt++) {
            authenticated(login)
                .body("""
                    {"currentPassword":"not-the-right-password"}
                    """)
                .when().post("/api/v1/me/mfa/totp/disable")
                .then().statusCode(403);
        }

        authenticated(login)
            .body("""
                {"currentPassword":"not-the-right-password"}
                """)
            .when().post("/api/v1/me/mfa/totp/disable")
            .then().statusCode(429).body("errorCode", equalTo("LIFECYCLE_RATE_LIMITED"));
    }

    private Response disableWith(String code) {
        return authenticated(login)
            .body("""
                {"currentPassword":"%s","code":"%s"}
                """.formatted(PASSWORD, code))
            .when().post("/api/v1/me/mfa/totp/disable");
    }

    private java.util.List<String> regenerateWith(String code) {
        return authenticated(login)
            .body("""
                {"currentPassword":"%s","code":"%s"}
                """.formatted(PASSWORD, code))
            .when().post("/api/v1/me/mfa/recovery-codes")
            .then().statusCode(200).extract().jsonPath().getList("codes", String.class);
    }

    private Response emailChange(String code) {
        var body = code == null
            ? """
              {"currentPassword":"%s","newEmail":"step.new@example.com"}
              """.formatted(PASSWORD)
            : """
              {"currentPassword":"%s","newEmail":"step.new@example.com","code":"%s"}
              """.formatted(PASSWORD, code);
        return authenticated(login).body(body).when().post("/api/v1/me/email-change");
    }
}
