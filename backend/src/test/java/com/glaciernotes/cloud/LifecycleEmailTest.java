package com.glaciernotes.cloud;

import com.glaciernotes.cloud.application.lifecycle.LifecycleService;
import com.glaciernotes.cloud.application.lifecycle.AccountService;
import com.glaciernotes.cloud.application.port.PasswordVerifier;
import com.glaciernotes.cloud.generated.model.EmailChangeRequest;
import com.glaciernotes.cloud.generated.model.InvitationCreateRequest;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(LifecycleEmailTest.SmtpProfile.class)
class LifecycleEmailTest {
    private static final UUID ADMIN_ID = UUID.fromString("767bca30-9ecf-4f89-83cc-ac025d520323");
    private static final String PASSWORD = "correct-horse-battery-staple-2026";

    @Inject LifecycleService lifecycle;
    @Inject AccountService accounts;
    @Inject PasswordVerifier passwords;
    @Inject MockMailbox mailbox;
    @Inject DataSource dataSource;

    @BeforeEach
    void prepare() throws SQLException {
        reset();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 insert into app_users(id, username, username_normalized, email, email_normalized,
                    role, status, password_hash, password_changed_at, activated_at)
                 values (?, 'admin', 'admin', 'admin@example.com', 'admin@example.com', 'ADMIN', 'ACTIVE',
                    ?, current_timestamp, current_timestamp)
                 """)) {
            statement.setObject(1, ADMIN_ID);
            statement.setString(2, passwords.hash(PASSWORD.toCharArray()));
            statement.executeUpdate();
        }
    }

    @AfterEach
    void reset() throws SQLException {
        mailbox.clear();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from audit_events");
            statement.executeUpdate("delete from endpoint_rate_limits");
            statement.executeUpdate("delete from security_tokens");
            statement.executeUpdate("delete from invitations");
            statement.executeUpdate("delete from user_sessions");
            statement.executeUpdate("delete from app_users");
        }
    }

    @Test
    void configuredSmtpDeliversInvitationsAndPasswordResetsWithoutReturningTokens() {
        var invitation = lifecycle.createInvitation(
            new InvitationCreateRequest("invited@example.com", InvitationCreateRequest.RoleEnum.USER),
            ADMIN_ID,
            "smtp-test"
        );

        assertEquals("EMAIL_SENT", invitation.getDelivery().toString());
        assertNull(invitation.getToken());
        assertNull(invitation.getActivationUrl());
        var invitationMail = mailbox.getMailsSentTo("invited@example.com").getFirst();
        assertTrue(tokenFrom(invitationMail.getText(), "/accept-invitation?token=")
            .matches("[A-Za-z0-9_-]{43}"));

        lifecycle.requestPasswordReset("admin@example.com", "127.0.0.1", "smtp-test");

        var resetMail = mailbox.getMailsSentTo("admin@example.com").getFirst();
        assertTrue(tokenFrom(resetMail.getText(), "/reset-password?token=")
            .matches("[A-Za-z0-9_-]{43}"));
        assertEquals(2, mailbox.getTotalMessagesSent());
    }

    @Test
    void emailChangeIsVerifiedHashedSingleUseAndNotifiesTheOldAddress() throws SQLException {
        accounts.requestEmailChange(ADMIN_ID,
            new EmailChangeRequest(PASSWORD, "Admin.New@Example.com"), "127.0.0.1", "email-change-test");

        var verification = mailbox.getMailsSentTo("Admin.New@Example.com").getFirst();
        var marker = "/verify-email-change?token=";
        var raw = verification.getText().substring(verification.getText().indexOf(marker) + marker.length()).lines()
            .findFirst().orElseThrow().strip();
        try (var connection = dataSource.getConnection(); var rows = connection.createStatement().executeQuery(
            "select token_hash, target_email_normalized from security_tokens where token_type='EMAIL_CHANGE'")) {
            assertTrue(rows.next());
            assertEquals(64, rows.getString(1).length());
            assertTrue(!raw.equals(rows.getString(1)));
            assertEquals("admin.new@example.com", rows.getString(2));
        }

        accounts.completeEmailChange(raw, "127.0.0.1", "email-change-test");
        assertEquals(1, mailbox.getMailsSentTo("admin@example.com").size());
        try (var connection = dataSource.getConnection(); var rows = connection.createStatement().executeQuery(
            "select email, email_normalized from app_users where id='" + ADMIN_ID + "'")) {
            assertTrue(rows.next());
            assertEquals("Admin.New@Example.com", rows.getString(1));
            assertEquals("admin.new@example.com", rows.getString(2));
        }
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
            () -> accounts.completeEmailChange(raw, "127.0.0.2", "email-change-test"));
    }

    private String tokenFrom(String message, String marker) {
        var markerIndex = message.indexOf(marker);
        assertTrue(markerIndex >= 0);
        return message.substring(markerIndex + marker.length()).lines()
            .findFirst().orElseThrow().strip();
    }

    public static class SmtpProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "glacier.smtp.enabled", "true",
                "glacier.smtp.sender-name", "Glacier Notes Test",
                "glacier.smtp.sender-address", "notes@example.com"
            );
        }
    }
}
