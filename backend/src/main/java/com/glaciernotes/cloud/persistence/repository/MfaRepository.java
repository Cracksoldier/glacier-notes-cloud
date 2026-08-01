package com.glaciernotes.cloud.persistence.repository;

import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.persistence.entity.MfaChallengeEntity;
import com.glaciernotes.cloud.persistence.entity.UserMfaRecoveryCodeEntity;
import com.glaciernotes.cloud.persistence.entity.UserMfaTotpEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class MfaRepository {
    private final EntityManager entityManager;
    private final TimeProvider timeProvider;
    private final IdGenerator idGenerator;

    public MfaRepository(EntityManager entityManager, TimeProvider timeProvider, IdGenerator idGenerator) {
        this.entityManager = entityManager;
        this.timeProvider = timeProvider;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public UserMfaTotpEntity saveEnrollment(UserMfaTotpEntity enrollment) {
        entityManager.persist(enrollment);
        return enrollment;
    }

    @Transactional
    public Optional<UserMfaTotpEntity> findEnrollment(UUID userId) {
        return Optional.ofNullable(entityManager.find(UserMfaTotpEntity.class, userId));
    }

    @Transactional
    public void deleteEnrollment(UUID userId) {
        entityManager.createQuery("delete from UserMfaRecoveryCodeEntity c where c.userId = :userId")
            .setParameter("userId", userId)
            .executeUpdate();
        entityManager.createQuery("delete from MfaChallengeEntity c where c.userId = :userId")
            .setParameter("userId", userId)
            .executeUpdate();
        entityManager.createQuery("delete from UserMfaTotpEntity t where t.userId = :userId")
            .setParameter("userId", userId)
            .executeUpdate();
    }

    @Transactional
    public void replaceRecoveryCodes(UUID userId, List<String> codeHashes) {
        entityManager.createQuery("delete from UserMfaRecoveryCodeEntity c where c.userId = :userId")
            .setParameter("userId", userId)
            .executeUpdate();
        var now = timeProvider.now();
        for (String codeHash : codeHashes) {
            entityManager.persist(
                new UserMfaRecoveryCodeEntity(idGenerator.nextId(), userId, codeHash, now)
            );
        }
    }

    @Transactional
    public long countUnusedRecoveryCodes(UUID userId) {
        return entityManager.createQuery(
                "select count(c) from UserMfaRecoveryCodeEntity c "
                    + "where c.userId = :userId and c.usedAt is null",
                Long.class
            )
            .setParameter("userId", userId)
            .getSingleResult();
    }

    /**
     * Consumes the code in a single indexed lookup, returning false when it is unknown or already
     * used. The caller must not distinguish those two outcomes.
     */
    @Transactional
    public boolean consumeRecoveryCode(UUID userId, String codeHash) {
        return entityManager.createQuery(
                "update UserMfaRecoveryCodeEntity c set c.usedAt = :now "
                    + "where c.userId = :userId and c.codeHash = :codeHash and c.usedAt is null"
            )
            .setParameter("now", timeProvider.now())
            .setParameter("userId", userId)
            .setParameter("codeHash", codeHash)
            .executeUpdate() == 1;
    }

    @Transactional
    public MfaChallengeEntity createChallenge(
        UUID userId,
        String tokenHash,
        boolean rememberMe,
        Instant expiresAt,
        InetAddress ipAddress,
        String clientDescription
    ) {
        var challenge = new MfaChallengeEntity(
            idGenerator.nextId(),
            userId,
            tokenHash,
            rememberMe,
            timeProvider.now(),
            expiresAt,
            ipAddress,
            clientDescription
        );
        entityManager.persist(challenge);
        return challenge;
    }

    @Transactional
    public Optional<MfaChallengeEntity> findChallenge(String tokenHash) {
        return entityManager.createQuery(
                "select c from MfaChallengeEntity c where c.tokenHash = :tokenHash",
                MfaChallengeEntity.class
            )
            .setParameter("tokenHash", tokenHash)
            .setMaxResults(1)
            .getResultStream()
            .findFirst();
    }

    /**
     * Enrollments left behind by a swap of the enrollment encryption secret, which are no longer
     * decryptable. A count rather than a list: the accounts are of no use to a log reader and would
     * put a roster of second-factor users somewhere it does not belong.
     */
    @Transactional
    public long countEnrollmentsUnderOtherKeys(String currentKeyId) {
        return entityManager.createQuery(
                "select count(t) from UserMfaTotpEntity t where t.keyId <> :keyId",
                Long.class
            )
            .setParameter("keyId", currentKeyId)
            .getSingleResult();
    }

    /**
     * Resolved for a whole page of users at once so that the administrative user list does not issue
     * one enrollment lookup per row.
     */
    @Transactional
    public Map<UUID, Instant> activeEnrollments(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery(
                "select t.userId, t.confirmedAt from UserMfaTotpEntity t "
                    + "where t.userId in :userIds and t.status = 'ACTIVE' and t.confirmedAt is not null",
                Object[].class
            )
            .setParameter("userIds", userIds)
            .getResultStream()
            .collect(Collectors.toUnmodifiableMap(row -> (UUID) row[0], row -> (Instant) row[1]));
    }
}
