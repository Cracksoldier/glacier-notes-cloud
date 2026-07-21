package com.glaciernotes.cloud.persistence.repository;

import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import com.glaciernotes.cloud.application.auth.SessionView;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.persistence.entity.SessionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SessionRepository {
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);
    private final EntityManager entityManager;
    private final TimeProvider timeProvider;

    public SessionRepository(EntityManager entityManager, TimeProvider timeProvider) {
        this.entityManager = entityManager;
        this.timeProvider = timeProvider;
    }

    @Transactional
    public Optional<SessionView> authenticate(String tokenHash) {
        var rows = entityManager.createQuery(
                "select s from SessionEntity s join fetch s.user where s.tokenHash = :tokenHash",
                SessionEntity.class
            )
            .setParameter("tokenHash", tokenHash)
            .setMaxResults(1)
            .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var session = rows.getFirst();
        var now = timeProvider.now();
        if (!session.activeAt(now) || !"ACTIVE".equals(session.user().status())) {
            return Optional.empty();
        }
        if (session.lastSeenAt().plus(TOUCH_INTERVAL).isBefore(now)) {
            session.touch(now);
        }
        return Optional.of(view(session));
    }

    @Transactional
    public List<SessionView> listActive(UUID userId) {
        var now = timeProvider.now();
        return entityManager.createQuery(
                "select s from SessionEntity s join fetch s.user "
                    + "where s.user.id = :userId and s.revokedAt is null and s.expiresAt > :now "
                    + "order by s.lastSeenAt desc, s.id",
                SessionEntity.class
            )
            .setParameter("userId", userId)
            .setParameter("now", now)
            .getResultStream()
            .map(this::view)
            .toList();
    }

    @Transactional
    public boolean revoke(UUID userId, UUID sessionId) {
        var session = entityManager.createQuery(
                "select s from SessionEntity s where s.id = :sessionId and s.user.id = :userId",
                SessionEntity.class
            )
            .setParameter("sessionId", sessionId)
            .setParameter("userId", userId)
            .getResultStream()
            .findFirst()
            .orElse(null);
        if (session == null) {
            return false;
        }
        session.revoke(timeProvider.now());
        return true;
    }

    @Transactional
    public void revokeOthers(UUID userId, UUID currentSessionId) {
        entityManager.createQuery(
                "update SessionEntity s set s.revokedAt = :now "
                    + "where s.user.id = :userId and s.id <> :currentId and s.revokedAt is null"
            )
            .setParameter("now", timeProvider.now())
            .setParameter("userId", userId)
            .setParameter("currentId", currentSessionId)
            .executeUpdate();
    }

    @Transactional
    public void revokeCurrent(UUID userId, UUID sessionId) {
        if (!revoke(userId, sessionId)) {
            throw AuthenticationFailure.sessionNotFound();
        }
    }

    private SessionView view(SessionEntity session) {
        var user = session.user();
        return new SessionView(
            session.id(), user.id(), user.username(), user.email(), user.displayName(), user.role(),
            session.rememberMe(), session.createdAt(), session.lastSeenAt(), session.expiresAt(),
            session.clientDescription()
        );
    }
}
