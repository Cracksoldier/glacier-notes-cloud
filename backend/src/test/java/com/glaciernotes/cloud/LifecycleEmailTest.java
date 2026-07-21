package com.glaciernotes.cloud;

import com.glaciernotes.cloud.application.lifecycle.LifecycleService;
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

    @Inject LifecycleService lifecycle;
    @Inject MockMailbox mailbox;
    @Inject DataSource dataSource;

    @BeforeEach
    void prepare() throws SQLException {
        reset();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 insert into app_users(id, username, username_normalized, email, email_normalized, role, status)
                 values (?, 'admin', 'admin', 'admin@example.com', 'admin@example.com', 'ADMIN', 'ACTIVE')
                 """)) {
            statement.setObject(1, ADMIN_ID);
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
        assertTrue(invitationMail.getText().contains("/accept-invitation?token="));

        lifecycle.requestPasswordReset("admin@example.com", "127.0.0.1", "smtp-test");

        var resetMail = mailbox.getMailsSentTo("admin@example.com").getFirst();
        assertTrue(resetMail.getText().contains("/reset-password?token="));
        assertEquals(2, mailbox.getTotalMessagesSent());
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
