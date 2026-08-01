package com.glaciernotes.cloud.application.auth;

import com.glaciernotes.cloud.application.lifecycle.LifecycleFailure;
import com.glaciernotes.cloud.application.lifecycle.PasswordManager;
import com.glaciernotes.cloud.application.operations.AuditService;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.persistence.entity.InstanceSettingsEntity;
import com.glaciernotes.cloud.persistence.entity.SessionEntity;
import com.glaciernotes.cloud.persistence.entity.UserEntity;
import com.glaciernotes.cloud.persistence.repository.EndpointRateLimiter;
import com.glaciernotes.cloud.persistence.repository.MfaRepository;
import com.glaciernotes.cloud.security.ClientKeyHasher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * The gate every operation that could hand an attacker the account passes through. An actor without
 * an active second factor sees exactly the password check that was there before, which is what lets
 * the enforcement ship without changing behavior for anyone who has not enrolled.
 */
@ApplicationScoped
public class StepUpService {
    private static final String USER_SCOPE = "STEP_UP_USER";
    private static final String IP_SCOPE = "STEP_UP_IP";
    private static final int MAX_FAILURES = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final EntityManager entityManager;
    private final MfaRepository mfaRepository;
    private final MfaCodeVerifier codeVerifier;
    private final PasswordManager passwords;
    private final EndpointRateLimiter rateLimiter;
    private final ClientKeyHasher keyHasher;
    private final TimeProvider timeProvider;
    private final AuditService audit;

    public StepUpService(
        EntityManager entityManager,
        MfaRepository mfaRepository,
        MfaCodeVerifier codeVerifier,
        PasswordManager passwords,
        EndpointRateLimiter rateLimiter,
        ClientKeyHasher keyHasher,
        TimeProvider timeProvider,
        AuditService audit
    ) {
        this.entityManager = entityManager;
        this.mfaRepository = mfaRepository;
        this.codeVerifier = codeVerifier;
        this.passwords = passwords;
        this.rateLimiter = rateLimiter;
        this.keyHasher = keyHasher;
        this.timeProvider = timeProvider;
        this.audit = audit;
    }

    /**
     * Consumes the password either way. Runs in the caller's transaction so that a rejection and its
     * audit row commit together; the caller must therefore not roll back on these failures.
     */
    @Transactional(
        value = Transactional.TxType.MANDATORY,
        dontRollbackOn = {LifecycleFailure.class, MfaFailure.class}
    )
    public void require(
        UUID userId,
        UUID sessionId,
        char[] currentPassword,
        String code,
        String clientAddress,
        String correlationId
    ) {
        var keys = scopedKeys(userId, clientAddress);
        var now = timeProvider.now();
        rateLimiter.assertAllowed(now, keys);

        var user = authenticate(userId, currentPassword, keys, correlationId);
        var enrollment = mfaRepository.findEnrollment(userId).orElse(null);
        if (enrollment == null || !enrollment.active()) {
            return;
        }

        var session = sessionId == null ? null : entityManager.find(SessionEntity.class, sessionId);
        if (graceOpen(session, now)) {
            return;
        }
        if (code == null || code.isBlank()) {
            throw MfaFailure.stepUpRequired();
        }

        var acceptedFactor = codeVerifier.verify(enrollment, userId, code, now);
        if (acceptedFactor == null) {
            audit.record(
                MfaAuditEvents.STEP_UP_FAILED, userId, userId, "USER", userId, "FAILURE",
                correlationId, Map.of()
            );
            recordFailure(keys);
            throw MfaFailure.invalidCode();
        }
        if (session != null) {
            session.recordStepUp(now);
        }
        audit.record(
            MfaAuditEvents.STEP_UP_SUCCEEDED, userId, userId, "USER", userId, "SUCCESS",
            correlationId, Map.of("factor", acceptedFactor)
        );
    }

    /**
     * The administrative operations had no password field before this milestone, so an outdated
     * client is told which one it is missing rather than being rejected as wrong.
     */
    @Transactional(
        value = Transactional.TxType.MANDATORY,
        dontRollbackOn = {LifecycleFailure.class, MfaFailure.class}
    )
    public void requireAdministrative(StepUpCredentials credentials, String correlationId) {
        var password = credentials.currentPassword();
        if (password == null || password.length == 0) {
            throw MfaFailure.stepUpPasswordRequired();
        }
        require(credentials.userId(), credentials.sessionId(), password, credentials.code(),
            credentials.clientAddress(), correlationId);
    }

    private UserEntity authenticate(
        UUID userId,
        char[] currentPassword,
        EndpointRateLimiter.ScopedKey[] keys,
        String correlationId
    ) {
        try {
            var user = entityManager.find(UserEntity.class, userId);
            if (user == null || currentPassword == null
                || !passwords.matchesCurrent(user, currentPassword)) {
                audit.record(
                    MfaAuditEvents.REAUTHENTICATION_FAILED, userId, userId, "USER", userId,
                    "FAILURE", correlationId, Map.of()
                );
                recordFailure(keys);
                throw LifecycleFailure.invalidCredentials();
            }
            return user;
        } finally {
            if (currentPassword != null) {
                Arrays.fill(currentPassword, '\0');
            }
        }
    }

    /**
     * A window of zero disables the grace period entirely, and a session that never proved
     * possession has no timestamp to measure from.
     */
    private boolean graceOpen(SessionEntity session, Instant now) {
        if (session == null || session.secondFactorVerifiedAt() == null) {
            return false;
        }
        var settings = entityManager.find(InstanceSettingsEntity.class, (short) 1);
        var graceMinutes = settings.mfaStepUpGraceMinutes();
        if (graceMinutes <= 0) {
            return false;
        }
        return session.secondFactorVerifiedAt().plusSeconds(graceMinutes * 60L).isAfter(now);
    }

    private EndpointRateLimiter.ScopedKey[] scopedKeys(UUID userId, String clientAddress) {
        return new EndpointRateLimiter.ScopedKey[] {
            new EndpointRateLimiter.ScopedKey(USER_SCOPE, keyHasher.hash("step-up-user:" + userId)),
            new EndpointRateLimiter.ScopedKey(IP_SCOPE, keyHasher.hash("step-up-ip:" + clientAddress))
        };
    }

    /** Both scopes are always counted, so one of them hitting its ceiling cannot shield the other. */
    private void recordFailure(EndpointRateLimiter.ScopedKey[] keys) {
        LifecycleFailure limited = null;
        for (var key : keys) {
            try {
                rateLimiter.record(key.scope(), key.keyHash(), MAX_FAILURES, WINDOW);
            } catch (LifecycleFailure failure) {
                limited = failure;
            }
        }
        if (limited != null) {
            throw limited;
        }
    }
}
