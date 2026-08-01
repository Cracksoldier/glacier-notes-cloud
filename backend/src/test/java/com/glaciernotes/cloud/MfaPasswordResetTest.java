package com.glaciernotes.cloud;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Specification §5.6: a password reset is not a way around the second factor. Proven rather than
 * implemented — the reset path never touches the enrollment, and this locks that in.
 */
@QuarkusTest
@TestProfile(SmtpTestProfile.class)
class MfaPasswordResetTest extends SecondFactorTestSupport {
    private static final String NEW_PASSWORD = "a-different-correct-horse-2026";

    @Inject
    MockMailbox mailbox;

    private byte[] secret;

    @BeforeEach
    void enrollAnAccount() throws SQLException {
        reset();
        insertUser("reset.user", "reset.user@example.com", "USER");
        secret = enroll(login("reset.user"));
        execute("delete from user_sessions");
        forgetAcceptedStep();
        mailbox.clear();
    }

    @AfterEach
    void reset() throws SQLException {
        mailbox.clear();
        cleanDatabase();
    }

    @Test
    void resettingThePasswordLeavesTheSecondFactorInPlace() throws SQLException {
        given().contentType(JSON)
            .body("""
                {"email":"reset.user@example.com"}
                """)
            .when().post("/api/v1/auth/password-reset/request")
            .then().statusCode(202);

        var mail = mailbox.getMailsSentTo("reset.user@example.com").getFirst();
        var marker = "/reset-password?token=";
        var token = mail.getText().substring(mail.getText().indexOf(marker) + marker.length())
            .lines().findFirst().orElseThrow().strip();

        given().contentType(JSON)
            .body("""
                {"token":"%s","password":"%s"}
                """.formatted(token, NEW_PASSWORD))
            .when().post("/api/v1/auth/password-reset/complete")
            .then().statusCode(204);

        assertEquals(1, count("user_mfa_totp", "status = 'ACTIVE'"));
        assertEquals(10, count("user_mfa_recovery_codes", "used_at is null"));

        // The new password still only gets as far as the challenge.
        given().contentType(JSON)
            .header("User-Agent", "Glacier Test Browser")
            .body("""
                {"identifier":"reset.user","password":"%s","rememberMe":false}
                """.formatted(NEW_PASSWORD))
            .when().post("/api/v1/auth/login")
            .then().statusCode(200).body("result", equalTo("MFA_REQUIRED"));
    }

    @Test
    void aChallengeIssuedBeforeTheResetCannotBeCompletedAfterIt() throws SQLException {
        var challenge = passwordStep("reset.user");
        challenge.then().statusCode(200).body("result", equalTo("MFA_REQUIRED"));
        var challengeToken = challenge.jsonPath().getString("challenge.token");

        execute("update app_users set password_changed_at = current_timestamp "
            + "where username_normalized = 'reset.user'");

        given().contentType(JSON)
            .body("""
                {"challengeToken":"%s","code":"%s"}
                """.formatted(challengeToken, currentCode(secret)))
            .when().post("/api/v1/auth/login/mfa")
            .then().statusCode(401).body("errorCode", equalTo("AUTH_MFA_CHALLENGE_INVALID"));
    }
}
