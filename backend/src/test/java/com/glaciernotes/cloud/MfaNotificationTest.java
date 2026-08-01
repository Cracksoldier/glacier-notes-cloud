package com.glaciernotes.cloud;

import com.glaciernotes.cloud.application.lifecycle.LifecycleEmailService;
import com.glaciernotes.cloud.application.lifecycle.MailMessages;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.mailer.Mail;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every second-factor event reaches the owner, in their own language, carrying nothing an attacker
 * reading the mailbox could use.
 */
@QuarkusTest
@TestProfile(SmtpTestProfile.class)
class MfaNotificationTest extends SecondFactorTestSupport {
    private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token-with-at-least-32-characters";
    private static final String ADDRESS = "notify.user@example.com";

    @Inject
    MockMailbox mailbox;

    private UUID userId;
    private Login login;

    @BeforeEach
    void createAnAccount() throws SQLException {
        reset();
        userId = insertUser("notify.user", ADDRESS, "USER");
        login = login("notify.user");
        mailbox.clear();
    }

    @AfterEach
    void reset() throws SQLException {
        mailbox.clear();
        cleanDatabase();
    }

    @Test
    void sendsExactlyOneMessageForEachLifecycleEvent() throws SQLException {
        var secret = startEnrollment(login);
        assertEquals(1, inbox().size());
        assertEquals(MailMessages.SECOND_FACTOR_ENROLLMENT_STARTED.subject("en"), subject(0));

        confirmEnrollment(login, secret);
        assertEquals(2, inbox().size());
        assertEquals(MailMessages.SECOND_FACTOR_ENABLED.subject("en"), subject(1));

        regenerate(null).then().statusCode(200);
        assertEquals(3, inbox().size());
        assertEquals(MailMessages.RECOVERY_CODES_REGENERATED.subject("en"), subject(2));

        disable(null).then().statusCode(204);
        assertEquals(4, inbox().size());
        assertEquals(MailMessages.SECOND_FACTOR_DISABLED.subject("en"), subject(3));

        assertEquals(4, mailbox.getTotalMessagesSent());
    }

    @Test
    void notifiesWhenARecoveryCodeIsSpentOnALogin() throws SQLException {
        var secret = startEnrollment(login);
        var recoveryCodes = confirmEnrollment(login, secret);
        execute("delete from user_sessions");
        mailbox.clear();

        secondFactorLogin("notify.user", recoveryCodes.getFirst());

        assertEquals(1, inbox().size());
        assertEquals(MailMessages.RECOVERY_CODE_USED.subject("en"), subject(0));
    }

    @Test
    void notifiesWhenAnOperatorClearsTheFactor() throws SQLException {
        enroll(login);
        mailbox.clear();

        given().contentType(JSON)
            .header("X-Bootstrap-Token", BOOTSTRAP_TOKEN)
            .body("""
                {"identifier":"notify.user"}
                """)
            .when().post("/api/v1/setup/second-factor-reset")
            .then().statusCode(204);

        assertEquals(1, inbox().size());
        assertEquals(MailMessages.SECOND_FACTOR_CLEARED_BY_OPERATOR.subject("en"), subject(0));
    }

    @Test
    void neverCarriesASecretACodeOrALink() throws SQLException {
        var start = authenticated(login)
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/mfa/totp");
        start.then().statusCode(200);
        var secret = com.glaciernotes.cloud.security.Base32Codec.decode(start.jsonPath().getString("secret"));
        var recoveryCodes = confirmEnrollment(login, secret);
        var replacements = regenerate(null).then().statusCode(200)
            .extract().jsonPath().getList("codes", String.class);

        for (var mail : inbox()) {
            var body = mail.getText();
            assertFalse(body.contains(start.jsonPath().getString("secret")));
            assertFalse(body.contains("otpauth://"));
            assertFalse(body.toLowerCase(java.util.Locale.ROOT).contains("http"));
            for (var code : recoveryCodes) {
                assertFalse(body.contains(code));
            }
            for (var code : replacements) {
                assertFalse(body.contains(code));
            }
            // The device line carries the coarse description kept for audit, never the raw agent.
            assertTrue(body.contains("Other browser / Other platform"));
        }
    }

    @Test
    void writesGermanWhenTheAccountAsksForIt() throws SQLException {
        execute("insert into user_settings(user_id, language) values ('" + userId + "', 'de')");

        startEnrollment(login);

        assertEquals(MailMessages.SECOND_FACTOR_ENROLLMENT_STARTED.subject("de"), subject(0));
        assertTrue(inbox().getFirst().getText().contains("Zeitpunkt:"));
    }

    @Test
    void aBrokenMailerNeitherFailsNorRollsBackTheOperation() throws SQLException {
        QuarkusMock.installMockForType(new ThrowingEmailService(), LifecycleEmailService.class);

        authenticated(login)
            .body("""
                {"currentPassword":"%s"}
                """.formatted(PASSWORD))
            .when().post("/api/v1/me/mfa/totp")
            .then().statusCode(200);

        assertEquals(0, mailbox.getTotalMessagesSent());
        assertEquals(1, count("user_mfa_totp", "status = 'PENDING'"));
    }

    private io.restassured.response.Response regenerate(String code) {
        return post("/api/v1/me/mfa/recovery-codes", code);
    }

    private io.restassured.response.Response disable(String code) {
        return post("/api/v1/me/mfa/totp/disable", code);
    }

    private io.restassured.response.Response post(String path, String code) {
        var body = code == null
            ? """
              {"currentPassword":"%s"}
              """.formatted(PASSWORD)
            : """
              {"currentPassword":"%s","code":"%s"}
              """.formatted(PASSWORD, code);
        return authenticated(login).body(body).when().post(path);
    }

    private List<Mail> inbox() {
        return mailbox.getMailsSentTo(ADDRESS);
    }

    private String subject(int index) {
        return inbox().get(index).getSubject();
    }

    /** Proves the observer swallows a dispatch failure rather than propagating it into the commit. */
    private static class ThrowingEmailService extends LifecycleEmailService {
        ThrowingEmailService() {
            super(null, null, null, null);
        }

        @Override
        public String languageOf(UUID userId) {
            return "en";
        }

        @Override
        public boolean sendNotification(UUID userId, String recipient, MailMessages message,
                                        Object... arguments) {
            throw new IllegalStateException("mail server unreachable");
        }
    }
}
