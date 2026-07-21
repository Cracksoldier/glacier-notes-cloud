package com.glaciernotes.cloud.application.lifecycle;

import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LifecycleEmailService {
    private static final Logger LOG = Logger.getLogger(LifecycleEmailService.class);
    private final Mailer mailer;
    private final GlacierConfiguration configuration;

    public LifecycleEmailService(Mailer mailer, GlacierConfiguration configuration) {
        this.mailer = mailer;
        this.configuration = configuration;
    }

    public boolean configured() {
        return configuration.smtp().enabled() && configuration.smtp().senderAddress().isPresent();
    }

    public boolean sendInvitation(String recipient, String activationUrl) {
        return send(recipient, "Your Glacier Notes invitation", """
            You have been invited to Glacier Notes.

            Open this link to create your account:
            %s

            If you did not expect this invitation, you can ignore this message.
            """.formatted(activationUrl));
    }

    public boolean sendPasswordReset(String recipient, String resetUrl) {
        return send(recipient, "Reset your Glacier Notes password", """
            A password reset was requested for your Glacier Notes account.

            Open this link to choose a new password:
            %s

            If you did not request this reset, you can ignore this message.
            """.formatted(resetUrl));
    }

    private boolean send(String recipient, String subject, String body) {
        if (!configured()) return false;
        try {
            var address = configuration.smtp().senderAddress().orElseThrow();
            var name = configuration.smtp().senderName().orElse("Glacier Notes");
            mailer.send(Mail.withText(recipient, subject, body).setFrom(name + " <" + address + ">"));
            return true;
        } catch (RuntimeException failure) {
            LOG.warnf("Outbound lifecycle email failed category=%s", failure.getClass().getSimpleName());
            return false;
        }
    }
}
