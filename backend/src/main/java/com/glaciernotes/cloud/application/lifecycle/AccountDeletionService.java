package com.glaciernotes.cloud.application.lifecycle;

import com.glaciernotes.cloud.application.port.BinaryAssetStorage;
import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.generated.model.AdminDeletionRequest;
import com.glaciernotes.cloud.persistence.entity.AuditEventEntity;
import com.glaciernotes.cloud.persistence.entity.ImageAssetEntity;
import com.glaciernotes.cloud.persistence.entity.InstanceSettingsEntity;
import com.glaciernotes.cloud.persistence.entity.InstanceStateEntity;
import com.glaciernotes.cloud.persistence.entity.UserEntity;
import com.glaciernotes.cloud.persistence.repository.SessionRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AccountDeletionService {
    private final EntityManager entityManager;
    private final BinaryAssetStorage storage;
    private final SessionRepository sessions;
    private final TimeProvider time;
    private final IdGenerator ids;

    public AccountDeletionService(EntityManager entityManager, BinaryAssetStorage storage,
                                  SessionRepository sessions, TimeProvider time, IdGenerator ids) {
        this.entityManager = entityManager;
        this.storage = storage;
        this.sessions = sessions;
        this.time = time;
        this.ids = ids;
    }

    @Transactional
    public void scheduleSelf(UUID userId, String correlationId) {
        var settings = settings();
        if (!settings.selfDeletionEnabled()) {
            throw LifecycleFailure.invalidState("Self-service account deletion is disabled by the administrator.");
        }
        var user = require(userId, LockModeType.PESSIMISTIC_WRITE);
        assertMayDelete(user);
        schedule(user, userId, Duration.ZERO, "SELF_DELETION_REQUESTED", correlationId);
        finalizeAccount(userId);
    }

    @Transactional
    public UserEntity scheduleAdministrative(UUID userId, AdminDeletionRequest request, UUID actor,
                                             String correlationId) {
        var user = require(userId, LockModeType.PESSIMISTIC_WRITE);
        assertMayDelete(user);
        boolean immediate = request.getMode() == AdminDeletionRequest.ModeEnum.IMMEDIATE;
        if (immediate && !user.username().equals(request.getConfirmation())) {
            throw LifecycleFailure.invalidState("Immediate deletion requires the target username as confirmation.");
        }
        var retention = immediate ? Duration.ZERO : Duration.ofDays(settings().adminDeletionRetentionDays());
        schedule(user, actor, retention, immediate ? "ADMIN_DELETION_IMMEDIATE" : "ADMIN_DELETION_SCHEDULED",
            correlationId);
        if (immediate) finalizeAccount(userId);
        return user;
    }

    @Transactional
    public UserEntity restore(UUID userId, UUID actor, String correlationId) {
        var user = require(userId, LockModeType.PESSIMISTIC_WRITE);
        if (!"PENDING_DELETION".equals(user.status()) || user.deletionDueAt() == null
            || !user.deletionDueAt().isAfter(time.now())) {
            throw LifecycleFailure.invalidState("Only an account awaiting retained deletion can be restored.");
        }
        user.restoreDeletion(time.now());
        audit("ADMIN_DELETION_RESTORED", actor, userId, correlationId, Map.of());
        return user;
    }

    @Scheduled(every = "1m", delayed = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    void finalizeDueAccounts() {
        @SuppressWarnings("unchecked")
        List<UUID> due = entityManager.createNativeQuery("""
            select id from app_users
            where status = 'PENDING_DELETION' and deletion_due_at <= :now
            order by deletion_due_at, id for update skip locked limit 20
            """).setParameter("now", time.now()).getResultList();
        due.forEach(this::finalizeAccount);
    }

    @Transactional
    public void finalizeAccount(UUID userId) {
        var user = require(userId, LockModeType.PESSIMISTIC_WRITE);
        if (!"PENDING_DELETION".equals(user.status()) || user.deletionDueAt() == null
            || user.deletionDueAt().isAfter(time.now())) return;

        var images = entityManager.createQuery(
                "select i from ImageAssetEntity i where i.key.ownerId = :owner", ImageAssetEntity.class)
            .setParameter("owner", userId).getResultList();
        images.forEach(image -> {
            storage.delete(image.storageKey());
            if (image.thumbnailStorageKey() != null) storage.delete(image.thumbnailStorageKey());
        });

        @SuppressWarnings("unchecked")
        List<String> paths = entityManager.createNativeQuery("""
            select temporary_path from transfer_jobs
            where (requested_by = :userId or target_user_id = :userId) and temporary_path is not null
            """).setParameter("userId", userId).getResultList();
        paths.forEach(this::deleteTemporaryFile);

        execute("delete from transfer_jobs where requested_by = :userId or target_user_id = :userId", userId);
        execute("delete from user_sessions where user_id = :userId", userId);
        execute("delete from security_tokens where user_id = :userId", userId);
        execute("delete from user_password_history where user_id = :userId", userId);
        execute("delete from user_settings where user_id = :userId", userId);
        execute("delete from note_image_references where owner_id = :userId", userId);
        execute("delete from note_version_image_references where owner_id = :userId", userId);
        execute("delete from notes where owner_id = :userId", userId);
        execute("delete from labels where owner_id = :userId", userId);
        execute("delete from notebooks where owner_id = :userId", userId);
        execute("delete from image_assets where owner_id = :userId", userId);
        user.anonymizeDeleted(time.now());
        audit("ACCOUNT_PERMANENTLY_DELETED", null, userId, "account-deletion", Map.of());
    }

    private void schedule(UserEntity user, UUID actor, Duration retention, String event, String correlationId) {
        if (!List.of("ACTIVE", "LOCKED", "DEACTIVATED").contains(user.status())) {
            throw LifecycleFailure.invalidState("The account cannot be scheduled for deletion in its current state.");
        }
        var now = time.now();
        user.scheduleDeletion(actor, now, now.plus(retention));
        sessions.revokeAll(user.id());
        entityManager.createQuery("update SecurityTokenEntity t set t.revokedAt = :now where t.userId = :userId "
                + "and t.consumedAt is null and t.revokedAt is null")
            .setParameter("now", now).setParameter("userId", user.id()).executeUpdate();
        entityManager.createNativeQuery("""
            update transfer_jobs set cancel_requested = true,
                state = case when state in ('QUEUED', 'READY') then 'CANCELED' else state end,
                completed_at = case when state in ('QUEUED', 'READY') then :now else completed_at end
            where (requested_by = :userId or target_user_id = :userId)
              and state not in ('SUCCEEDED', 'FAILED', 'CANCELED', 'EXPIRED')
            """).setParameter("now", now).setParameter("userId", user.id()).executeUpdate();
        audit(event, actor, user.id(), correlationId, Map.of("deletionDueAt", user.deletionDueAt().toString()));
    }

    private void assertMayDelete(UserEntity user) {
        if (!"ADMIN".equals(user.role()) || !"ACTIVE".equals(user.status())) return;
        entityManager.find(InstanceStateEntity.class, (short) 1, LockModeType.PESSIMISTIC_WRITE);
        long otherAdmins = entityManager.createQuery("select count(u) from UserEntity u where u.role = 'ADMIN' "
                + "and u.status = 'ACTIVE' and u.id <> :userId", Long.class)
            .setParameter("userId", user.id()).getSingleResult();
        if (otherAdmins == 0) throw LifecycleFailure.lastAdmin();
    }

    private UserEntity require(UUID id, LockModeType lock) {
        var user = entityManager.find(UserEntity.class, id, lock);
        if (user == null || "DELETED".equals(user.status())) throw LifecycleFailure.notFound();
        return user;
    }

    private InstanceSettingsEntity settings() {
        return entityManager.find(InstanceSettingsEntity.class, (short) 1);
    }

    private void execute(String sql, UUID userId) {
        entityManager.createNativeQuery(sql).setParameter("userId", userId).executeUpdate();
    }

    private void deleteTemporaryFile(String value) {
        try {
            Files.deleteIfExists(Path.of(value));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not delete account transfer data", failure);
        }
    }

    private void audit(String type, UUID actor, UUID target, String correlationId, Map<String, String> metadata) {
        entityManager.persist(AuditEventEntity.administrative(ids.nextId(), type, actor, target,
            "USER", target, time.now(), correlationId, metadata));
    }
}
