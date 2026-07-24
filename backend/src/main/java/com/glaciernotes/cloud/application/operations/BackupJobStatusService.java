package com.glaciernotes.cloud.application.operations;

import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.persistence.entity.BackupJobEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class BackupJobStatusService {
    private final EntityManager entityManager;
    private final TimeProvider time;

    public BackupJobStatusService(EntityManager entityManager, TimeProvider time) {
        this.entityManager = entityManager;
        this.time = time;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void complete(UUID id, String output, long size, String checksum) {
        var entity = entityManager.find(BackupJobEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (entity == null) throw new IllegalStateException("The backup job no longer exists.");
        entity.succeeded(time.now(), output, size, checksum);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void fail(UUID id, String category) {
        var entity = entityManager.find(BackupJobEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (entity != null) {
            entity.failed(time.now(), category.substring(0, Math.min(64, category.length())));
        }
    }
}
