package com.glaciernotes.cloud.application.auth;

import com.glaciernotes.cloud.application.port.PasswordVerifier;
import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.persistence.entity.AuditEventEntity;
import com.glaciernotes.cloud.persistence.entity.InstanceSettingsEntity;
import com.glaciernotes.cloud.persistence.entity.SessionEntity;
import com.glaciernotes.cloud.persistence.entity.UserEntity;
import com.glaciernotes.cloud.persistence.repository.LoginRateLimiter;
import com.glaciernotes.cloud.security.ClientKeyHasher;
import com.glaciernotes.cloud.security.SessionTokenService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.net.InetAddress;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

@ApplicationScoped
public class AuthenticationService {
    private final EntityManager entityManager;
    private final PasswordVerifier passwordVerifier;
    private final SessionTokenService tokenService;
    private final ClientKeyHasher keyHasher;
    private final LoginRateLimiter rateLimiter;
    private final TimeProvider timeProvider;
    private final IdGenerator idGenerator;
    private final String dummyPasswordHash;

    public AuthenticationService(
        EntityManager entityManager,
        PasswordVerifier passwordVerifier,
        SessionTokenService tokenService,
        ClientKeyHasher keyHasher,
        LoginRateLimiter rateLimiter,
        TimeProvider timeProvider,
        IdGenerator idGenerator
    ) {
        this.entityManager = entityManager;
        this.passwordVerifier = passwordVerifier;
        this.tokenService = tokenService;
        this.keyHasher = keyHasher;
        this.rateLimiter = rateLimiter;
        this.timeProvider = timeProvider;
        this.idGenerator = idGenerator;
        this.dummyPasswordHash = passwordVerifier.hash("not-a-real-glacier-user-password".toCharArray());
    }

    @Transactional(dontRollbackOn = AuthenticationFailure.class)
    public LoginResult login(
        String identifierInput,
        char[] password,
        boolean rememberMe,
        String clientAddress,
        String clientDescription,
        String correlationId
    ) {
        try {
            var now = timeProvider.now();
            clientDescription = normalizeClientDescription(clientDescription);
            var identifier = normalizeIdentifier(identifierInput);
            var identifierKey = keyHasher.hash("login-identifier:" + identifier);
            var ipKey = keyHasher.hash("login-ip:" + clientAddress);
            var settings = entityManager.find(InstanceSettingsEntity.class, (short) 1);
            rateLimiter.assertAllowed(identifierKey, ipKey, now);

            var users = entityManager.createQuery(
                    "select u from UserEntity u "
                        + "where u.usernameNormalized = :identifier or u.emailNormalized = :identifier",
                    UserEntity.class
                )
                .setParameter("identifier", identifier)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
                .getResultList();
            var user = users.isEmpty() ? null : users.getFirst();
            if (user != null) {
                user.unlockIfTemporaryLockExpired(now);
            }

            var encodedHash = user == null || user.passwordHash() == null
                ? dummyPasswordHash
                : user.passwordHash();
            var passwordMatches = passwordVerifier.matches(password, encodedHash);

            if (!passwordMatches || user == null || !"ACTIVE".equals(user.status())) {
                long retryAfter = rateLimiter.recordFailures(identifierKey, ipKey, now, settings);
                if (user != null && "ACTIVE".equals(user.status())) {
                    user.registerFailedLogin(
                        settings.loginLockThreshold(), now, settings.loginLockMinutes()
                    );
                    if (user.lockedUntil() != null) {
                        retryAfter = Math.max(retryAfter, secondsUntil(user.lockedUntil(), now));
                    }
                }
                entityManager.persist(AuditEventEntity.authenticationFailed(
                    idGenerator.nextId(), user == null ? null : user.id(), now,
                    parseAddress(clientAddress), clientDescription, correlationId
                ));
                entityManager.flush();
                if (retryAfter > 0) {
                    throw AuthenticationFailure.rateLimited(retryAfter);
                }
                throw AuthenticationFailure.invalidCredentials();
            }

            user.recordSuccessfulLogin(now);
            rateLimiter.clearIdentifier(identifierKey);
            var token = tokenService.newToken();
            var durationMinutes = settings.sessionDurationMinutes(rememberMe);
            var session = new SessionEntity(
                idGenerator.nextId(), user, tokenService.hashToken(token), rememberMe, now,
                now.plusSeconds(durationMinutes * 60L), parseAddress(clientAddress), clientDescription
            );
            entityManager.persist(session);
            entityManager.flush();
            return new LoginResult(token, view(session), durationMinutes * 60L);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private String normalizeIdentifier(String value) {
        var normalized = Normalizer.normalize(value == null ? "" : value.strip(), Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 320) {
            return "invalid-identifier";
        }
        return normalized;
    }

    private String normalizeClientDescription(String value) {
        if (value == null) {
            return null;
        }
        var normalized = value.replaceAll("[\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").strip();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.substring(0, Math.min(normalized.length(), 512));
    }

    private InetAddress parseAddress(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long secondsUntil(Instant blockedUntil, Instant now) {
        return Math.max(1, Duration.between(now, blockedUntil).toSeconds());
    }

    private SessionView view(SessionEntity session) {
        var user = session.user();
        return new SessionView(
            session.id(), user.id(), user.username(), user.email(), user.displayName(), user.role(),
            session.rememberMe(), session.createdAt(), session.lastSeenAt(), session.expiresAt(),
            session.clientDescription()
        );
    }

    public record LoginResult(String token, SessionView session, long cookieMaxAgeSeconds) {
    }
}
