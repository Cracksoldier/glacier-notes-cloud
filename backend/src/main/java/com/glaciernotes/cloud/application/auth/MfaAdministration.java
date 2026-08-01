package com.glaciernotes.cloud.application.auth;

import com.glaciernotes.cloud.application.lifecycle.LifecycleFailure;
import com.glaciernotes.cloud.application.lifecycle.MailMessages;
import com.glaciernotes.cloud.application.operations.AuditService;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.persistence.entity.UserEntity;
import com.glaciernotes.cloud.persistence.repository.MfaRepository;
import com.glaciernotes.cloud.persistence.repository.SessionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;

import java.util.Map;

/**
 * The ordinary remedy for a user who lost their authenticator, supplementing the operator
 * break-glass path that needs shell access and the bootstrap token.
 *
 * <p>Deliberately not gated on the feature flag: turning the flag off must not trap an account that
 * already carries an enrollment.
 */
@ApplicationScoped
public class MfaAdministration {
    private final MfaRepository mfaRepository;
    private final SessionRepository sessions;
    private final StepUpService stepUp;
    private final Event<SecondFactorEvent> notifications;
    private final TimeProvider timeProvider;
    private final AuditService audit;
    private final MfaMetrics metrics;

    public MfaAdministration(
        MfaRepository mfaRepository,
        SessionRepository sessions,
        StepUpService stepUp,
        Event<SecondFactorEvent> notifications,
        TimeProvider timeProvider,
        AuditService audit,
        MfaMetrics metrics
    ) {
        this.mfaRepository = mfaRepository;
        this.sessions = sessions;
        this.stepUp = stepUp;
        this.notifications = notifications;
        this.timeProvider = timeProvider;
        this.audit = audit;
        this.metrics = metrics;
    }

    @Transactional(
        value = Transactional.TxType.MANDATORY,
        dontRollbackOn = {LifecycleFailure.class, MfaFailure.class}
    )
    public void clear(UserEntity user, StepUpCredentials admin, String correlationId) {
        // Ahead of the enrollment lookup, so a caller who cannot prove themselves learns nothing
        // about the target.
        try {
            stepUp.requireAdministrative(admin, correlationId);
        } catch (LifecycleFailure | MfaFailure rejection) {
            audit.record(MfaAuditEvents.ADMINISTRATIVE_CLEAR, admin.userId(), user.id(), "USER",
                user.id(), "DENIED", correlationId, Map.of());
            throw rejection;
        }

        var enrollment = mfaRepository.findEnrollment(user.id()).orElse(null);
        if (enrollment == null || !enrollment.active()) {
            throw MfaFailure.notEnrolled();
        }
        mfaRepository.deleteEnrollment(user.id());
        // Every session of the target predates the clear and may be the attacker's.
        sessions.clearStepUp(user.id());
        sessions.revokeAll(user.id());
        metrics.administrativeClear();
        audit.record(MfaAuditEvents.ADMINISTRATIVE_CLEAR, admin.userId(), user.id(), "USER",
            user.id(), "SUCCESS", correlationId, Map.of());
        notifications.fire(new SecondFactorEvent(user.id(), user.email(),
            MailMessages.SECOND_FACTOR_CLEARED_BY_ADMINISTRATOR, timeProvider.now(), null));
    }
}
