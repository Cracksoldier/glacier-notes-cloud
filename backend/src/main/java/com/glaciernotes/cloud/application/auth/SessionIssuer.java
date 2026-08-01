package com.glaciernotes.cloud.application.auth;

import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.persistence.entity.InstanceSettingsEntity;
import com.glaciernotes.cloud.persistence.entity.SessionEntity;
import com.glaciernotes.cloud.persistence.entity.UserEntity;
import com.glaciernotes.cloud.security.SessionTokenService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.net.InetAddress;
import java.time.Instant;

/**
 * Extracted so that the password step and the second-factor step can both end in an identical
 * session without either service depending on the other.
 */
@ApplicationScoped
public class SessionIssuer {
    private final EntityManager entityManager;
    private final SessionTokenService tokenService;
    private final IdGenerator idGenerator;

    public SessionIssuer(
        EntityManager entityManager,
        SessionTokenService tokenService,
        IdGenerator idGenerator
    ) {
        this.entityManager = entityManager;
        this.tokenService = tokenService;
        this.idGenerator = idGenerator;
    }

    public LoginResult.SessionIssued issue(
        UserEntity user,
        boolean rememberMe,
        Instant now,
        InstanceSettingsEntity settings,
        InetAddress clientAddress,
        String clientDescription,
        boolean secondFactorVerified
    ) {
        user.recordSuccessfulLogin(now);
        var token = tokenService.newToken();
        var durationMinutes = settings.sessionDurationMinutes(rememberMe);
        var session = new SessionEntity(
            idGenerator.nextId(), user, tokenService.hashToken(token), rememberMe, now,
            now.plusSeconds(durationMinutes * 60L), clientAddress, clientDescription,
            secondFactorVerified
        );
        entityManager.persist(session);
        entityManager.flush();
        return new LoginResult.SessionIssued(token, view(session), durationMinutes * 60L);
    }

    public SessionView view(SessionEntity session) {
        var user = session.user();
        return new SessionView(
            session.id(), user.id(), user.username(), user.email(), user.displayName(), user.role(),
            session.rememberMe(), session.createdAt(), session.lastSeenAt(), session.expiresAt(),
            session.clientDescription()
        );
    }
}
