package com.glaciernotes.cloud.application.auth;

import com.glaciernotes.cloud.application.lifecycle.MailMessages;
import com.glaciernotes.cloud.application.operations.AuditService;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.persistence.entity.InstanceSettingsEntity;
import com.glaciernotes.cloud.persistence.entity.MfaChallengeEntity;
import com.glaciernotes.cloud.persistence.entity.UserEntity;
import com.glaciernotes.cloud.persistence.entity.UserMfaTotpEntity;
import com.glaciernotes.cloud.persistence.repository.LoginRateLimiter;
import com.glaciernotes.cloud.persistence.repository.MfaRepository;
import com.glaciernotes.cloud.security.ClientKeyHasher;
import com.glaciernotes.cloud.security.MfaTokenService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class MfaChallengeService {
    /** Bounds how many parallel password steps one account can keep open. */
    private static final int MAX_OPEN_CHALLENGES = 3;

    private final EntityManager entityManager;
    private final MfaRepository mfaRepository;
    private final MfaTokenService mfaTokens;
    private final MfaCodeVerifier codeVerifier;
    private final LoginRateLimiter rateLimiter;
    private final ClientKeyHasher keyHasher;
    private final TimeProvider timeProvider;
    private final SessionIssuer sessionIssuer;
    private final AuditService audit;
    private final MfaMetrics metrics;
    private final Event<SecondFactorEvent> notifications;

    public MfaChallengeService(
        EntityManager entityManager,
        MfaRepository mfaRepository,
        MfaTokenService mfaTokens,
        MfaCodeVerifier codeVerifier,
        LoginRateLimiter rateLimiter,
        ClientKeyHasher keyHasher,
        TimeProvider timeProvider,
        SessionIssuer sessionIssuer,
        AuditService audit,
        MfaMetrics metrics,
        Event<SecondFactorEvent> notifications
    ) {
        this.entityManager = entityManager;
        this.mfaRepository = mfaRepository;
        this.mfaTokens = mfaTokens;
        this.codeVerifier = codeVerifier;
        this.rateLimiter = rateLimiter;
        this.keyHasher = keyHasher;
        this.timeProvider = timeProvider;
        this.sessionIssuer = sessionIssuer;
        this.audit = audit;
        this.metrics = metrics;
        this.notifications = notifications;
    }

    /**
     * Runs inside the password step's transaction. The plaintext token is returned once and is not
     * recoverable afterwards — only its keyed hash is stored.
     */
    LoginResult.SecondFactorRequired issue(
        UserEntity user,
        boolean rememberMe,
        Instant now,
        InstanceSettingsEntity settings,
        InetAddress clientAddress,
        String clientDescription
    ) {
        pruneOpenChallenges(user, now);
        var token = mfaTokens.newChallengeToken();
        var expiresAt = now.plusSeconds(settings.mfaChallengeLifetimeMinutes() * 60L);
        mfaRepository.createChallenge(
            user.id(), mfaTokens.hashChallengeToken(token), rememberMe, expiresAt,
            clientAddress, clientDescription
        );
        metrics.challengeIssued();
        return new LoginResult.SecondFactorRequired(
            token, expiresAt, settings.mfaChallengeAttemptLimit()
        );
    }

    @Transactional(dontRollbackOn = {MfaFailure.class, AuthenticationFailure.class})
    public LoginResult.SessionIssued complete(
        String challengeToken,
        String code,
        String clientAddress,
        String clientDescription,
        String correlationId
    ) {
        var now = timeProvider.now();
        var address = AuthenticationService.parseAddress(clientAddress);
        var description = AuthenticationService.normalizeClientDescription(clientDescription);
        var ipKey = keyHasher.hash("login-ip:" + clientAddress);
        var settings = entityManager.find(InstanceSettingsEntity.class, (short) 1);
        rateLimiter.assertSecondFactorAllowed(ipKey, now);

        var challenge = mfaRepository.findChallenge(mfaTokens.hashChallengeToken(challengeToken))
            .filter(candidate -> candidate.usableAt(now))
            .orElseThrow(MfaFailure::challengeInvalid);

        // The lock is what makes concurrent completions of one challenge resolve to a single success.
        var user = entityManager.find(
            UserEntity.class, challenge.userId(), LockModeType.PESSIMISTIC_WRITE
        );
        if (user != null) {
            user.unlockIfTemporaryLockExpired(now);
        }
        var enrollment = mfaRepository.findEnrollment(challenge.userId()).orElse(null);
        if (!stillEligible(user, enrollment, challenge, now)) {
            challenge.consume(now);
            entityManager.flush();
            throw MfaFailure.challengeInvalid();
        }

        var acceptedFactor = codeVerifier.verify(enrollment, user.id(), code, now);
        if (acceptedFactor == null) {
            throw recordFailedAttempt(challenge, user, settings, ipKey, now, correlationId);
        }

        challenge.consume(now);
        rateLimiter.clearSecondFactor(ipKey);
        rateLimiter.clearIdentifier(keyHasher.hash("login-identifier:" + user.usernameNormalized()));
        rateLimiter.clearIdentifier(keyHasher.hash("login-identifier:" + user.emailNormalized()));
        metrics.challengeConsumed();
        metrics.verificationSucceeded(acceptedFactor);
        audit.record(
            "RECOVERY_CODE".equals(acceptedFactor)
                ? MfaAuditEvents.RECOVERY_CODE_USED
                : MfaAuditEvents.CHALLENGE_COMPLETED,
            user.id(), user.id(), "USER", user.id(), "SUCCESS", correlationId,
            Map.of("factor", acceptedFactor)
        );
        if ("RECOVERY_CODE".equals(acceptedFactor)) {
            notifications.fire(new SecondFactorEvent(
                user.id(), user.email(), MailMessages.RECOVERY_CODE_USED, now, description
            ));
        }
        return sessionIssuer.issue(
            user, challenge.rememberMe(), now, settings, address, description, true
        );
    }

    /**
     * Re-checked at the second step because the account may have been deactivated, locked, deleted,
     * or had its password changed since the password step. Every rejection is reported as an unknown
     * challenge, so that a caller holding only a password learns nothing about account state.
     */
    private boolean stillEligible(
        UserEntity user,
        UserMfaTotpEntity enrollment,
        MfaChallengeEntity challenge,
        Instant now
    ) {
        if (user == null || !"ACTIVE".equals(user.status())) {
            return false;
        }
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
            return false;
        }
        if (user.passwordChangedAt() == null
            || user.passwordChangedAt().isAfter(challenge.createdAt())) {
            return false;
        }
        return enrollment != null && enrollment.active();
    }

    private MfaFailure recordFailedAttempt(
        MfaChallengeEntity challenge,
        UserEntity user,
        InstanceSettingsEntity settings,
        String ipKey,
        Instant now,
        String correlationId
    ) {
        var attempts = challenge.recordFailure();
        long retryAfter = rateLimiter.recordSecondFactorFailure(ipKey, now, settings);
        user.registerFailedLogin(settings.loginLockThreshold(), now, settings.loginLockMinutes());
        if (user.lockedUntil() != null) {
            retryAfter = Math.max(retryAfter, AuthenticationService.secondsUntil(user.lockedUntil(), now));
        }
        metrics.verificationFailed();
        audit.record(
            MfaAuditEvents.CHALLENGE_FAILED, null, user.id(), "USER", user.id(), "FAILURE",
            correlationId, Map.of("attempts", Integer.toString(attempts))
        );
        if (attempts >= settings.mfaChallengeAttemptLimit()) {
            challenge.consume(now);
            metrics.challengeExhausted();
            entityManager.flush();
            return MfaFailure.attemptsExhausted(retryAfter);
        }
        entityManager.flush();
        return MfaFailure.invalidCode();
    }

    private void pruneOpenChallenges(UserEntity user, Instant now) {
        var open = entityManager.createQuery(
                "select c from MfaChallengeEntity c "
                    + "where c.userId = :userId and c.consumedAt is null and c.expiresAt > :now "
                    + "order by c.createdAt desc, c.id desc",
                MfaChallengeEntity.class
            )
            .setParameter("userId", user.id())
            .setParameter("now", now)
            .getResultList();
        for (int index = MAX_OPEN_CHALLENGES - 1; index < open.size(); index++) {
            entityManager.remove(open.get(index));
        }
    }
}
