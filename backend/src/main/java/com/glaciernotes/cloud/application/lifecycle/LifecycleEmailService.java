package com.glaciernotes.cloud.application.lifecycle;

import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.generated.model.SmtpStatus;
import com.glaciernotes.cloud.persistence.entity.InstanceSettingsEntity;
import com.glaciernotes.cloud.persistence.entity.UserSettingsEntity;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class LifecycleEmailService {
    private static final Logger LOG = Logger.getLogger(LifecycleEmailService.class);
    private final Mailer mailer;
    private final GlacierConfiguration configuration;
    private final EntityManager entityManager;
    private final TimeProvider time;

    public LifecycleEmailService(Mailer mailer, GlacierConfiguration configuration,
                                 EntityManager entityManager, TimeProvider time) {
        this.mailer = mailer;
        this.configuration = configuration;
        this.entityManager = entityManager;
        this.time = time;
    }

    public boolean configured() {
        return configuration.smtp().enabled() && configuration.smtp().senderAddress().isPresent();
    }

    /** The invitee has no account row yet, so only the instance default is available. */
    public boolean sendInvitation(String recipient, String activationUrl) {
        return send(recipient, MailMessages.INVITATION, defaultLanguage(), activationUrl);
    }

    public boolean sendPasswordReset(UUID userId, String recipient, String resetUrl) {
        return send(recipient, MailMessages.PASSWORD_RESET, languageOf(userId), resetUrl);
    }

    public boolean sendEmailChangeVerification(UUID userId, String recipient, String verificationUrl) {
        return send(recipient, MailMessages.EMAIL_CHANGE_VERIFICATION, languageOf(userId), verificationUrl);
    }

    public boolean sendEmailChangedNotice(UUID userId, String recipient) {
        return send(recipient, MailMessages.EMAIL_CHANGED_NOTICE, languageOf(userId));
    }

    public boolean sendNotification(UUID userId, String recipient, MailMessages message, Object... arguments) {
        return send(recipient, message, languageOf(userId), arguments);
    }

    /** The recipient's own preference, or the instance default when the account has no settings row. */
    public String languageOf(UUID userId) {
        var settings = userId == null ? null : entityManager.find(UserSettingsEntity.class, userId);
        return settings == null || settings.language() == null ? defaultLanguage() : settings.language();
    }

    /** Addressed to whoever operates the instance, so it stays in the project language. */
    @Transactional
    public boolean sendTest(String recipient) {
        return deliver(recipient, "Glacier Notes SMTP test", """
            This message confirms that Glacier Notes can deliver email using the configured SMTP service.
            No action is required.
            """);
    }

    @Transactional
    public SmtpStatus status() {
        var settings = entityManager.find(InstanceSettingsEntity.class, (short) 1);
        String name = settings.smtpSenderName() == null
            ? configuration.smtp().senderName().orElse("Glacier Notes") : settings.smtpSenderName();
        String address = settings.smtpSenderAddress() == null
            ? configuration.smtp().senderAddress().orElse("noreply@localhost") : settings.smtpSenderAddress();
        Object[] row = (Object[]) entityManager.createNativeQuery("""
            select last_successful_at, last_failure_category from smtp_delivery_status where singleton_key=1
            """).getSingleResult();
        boolean configured = configured();
        String state = !configured ? "NOT_CONFIGURED" : row[1] != null ? "FAILED"
            : row[0] != null ? "SUCCEEDED" : "READY";
        var model = new SmtpStatus().configured(configured).senderName(name).senderAddress(address)
            .state(SmtpStatus.StateEnum.fromValue(state));
        if (row[0] != null) {
            model.lastSuccessfulAt(((java.time.OffsetDateTime) row[0]).withOffsetSameInstant(ZoneOffset.UTC));
        }
        if (row[1] != null) {
            model.lastFailureCategory(SmtpStatus.LastFailureCategoryEnum.fromValue(row[1].toString()));
        }
        return model;
    }

    private String defaultLanguage() {
        var settings = entityManager.find(InstanceSettingsEntity.class, (short) 1);
        return settings == null || settings.defaultLanguage() == null ? "en" : settings.defaultLanguage();
    }

    private boolean send(String recipient, MailMessages message, String language, Object... arguments) {
        return deliver(recipient, message.subject(language), message.body(language, arguments));
    }

    private boolean deliver(String recipient, String subject, String body) {
        if (!configured()) return false;
        try {
            var settings = entityManager.find(InstanceSettingsEntity.class, (short) 1);
            var address = settings.smtpSenderAddress() == null
                ? configuration.smtp().senderAddress().orElseThrow() : settings.smtpSenderAddress();
            var name = settings.smtpSenderName() == null
                ? configuration.smtp().senderName().orElse("Glacier Notes") : settings.smtpSenderName();
            mailer.send(Mail.withText(recipient, subject, body).setFrom(name + " <" + address + ">"));
            entityManager.createNativeQuery("""
                update smtp_delivery_status set last_successful_at=:now,last_failure_at=null,
                  last_failure_category=null,updated_at=:now where singleton_key=1
                """).setParameter("now", time.now()).executeUpdate();
            return true;
        } catch (RuntimeException failure) {
            String category = category(failure);
            entityManager.createNativeQuery("""
                update smtp_delivery_status set last_failure_at=:now,last_failure_category=:category,
                  updated_at=:now where singleton_key=1
                """).setParameter("now", time.now()).setParameter("category", category).executeUpdate();
            LOG.warnf("Outbound lifecycle email failed category=%s", failure.getClass().getSimpleName());
            return false;
        }
    }

    private String category(RuntimeException failure) {
        String name = failure.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (name.contains("auth")) return "AUTHENTICATION";
        if (name.contains("ssl") || name.contains("tls")) return "TLS";
        if (name.contains("connect") || name.contains("timeout")) return "CONNECTION";
        if (name.contains("mail")) return "DELIVERY";
        return "UNKNOWN";
    }
}
