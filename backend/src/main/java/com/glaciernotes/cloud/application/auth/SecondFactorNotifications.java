package com.glaciernotes.cloud.application.auth;

import com.glaciernotes.cloud.application.lifecycle.LifecycleEmailService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class SecondFactorNotifications {
    private static final Logger LOG = Logger.getLogger(SecondFactorNotifications.class);

    private final LifecycleEmailService email;

    public SecondFactorNotifications(LifecycleEmailService email) {
        this.email = email;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void onSecondFactorEvent(
        @Observes(during = TransactionPhase.AFTER_SUCCESS) SecondFactorEvent event
    ) {
        try {
            var language = email.languageOf(event.userId());
            email.sendNotification(
                event.userId(),
                event.recipient(),
                event.message(),
                DateTimeFormatter.ISO_INSTANT.format(event.occurredAt()),
                device(event.clientDescription(), language)
            );
        } catch (RuntimeException failure) {
            // The category alone: a notification body names the account and must not reach the log.
            LOG.warnf("Second-factor notification failed category=%s", failure.getClass().getSimpleName());
        }
    }

    private String device(String clientDescription, String language) {
        if (clientDescription != null && !clientDescription.isBlank()) {
            return clientDescription;
        }
        return "de".equals(language) ? "unbekannt" : "unknown";
    }
}
