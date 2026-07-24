package com.glaciernotes.cloud.application.storage;

import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

@ApplicationScoped
public class ExternalStorageOperations {
    private static final Duration RESERVATION_LEASE = Duration.ofHours(1);
    private static final Duration WORKER_LEASE = Duration.ofMinutes(2);

    private final EntityManager entityManager;
    private final IdGenerator ids;
    private final TimeProvider clock;

    public ExternalStorageOperations(EntityManager entityManager, IdGenerator ids, TimeProvider clock) {
        this.entityManager = entityManager;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional(REQUIRES_NEW)
    public void reserveBinary(OwnerId owner, UUID operationId, UUID transferJobId, String backend,
                              String primaryKey, String secondaryKey, long reservedBytes) {
        Instant now = clock.now();
        entityManager.createNativeQuery("""
            insert into external_storage_operations(
              id,operation_kind,state,owner_id,transfer_job_id,storage_backend,
              primary_location,secondary_location,reserved_bytes,attempt_count,
              available_at,lease_until,created_at,updated_at)
            values (:id,'ROLLBACK_BINARY_CREATE','PENDING',:owner,:job,:backend,
              :primary,:secondary,:reserved,0,:now,:lease,:now,:now)
            on conflict(id) do nothing
            """)
            .setParameter("id", operationId)
            .setParameter("owner", owner.value())
            .setParameter("job", transferJobId)
            .setParameter("backend", backend)
            .setParameter("primary", primaryKey)
            .setParameter("secondary", secondaryKey)
            .setParameter("reserved", reservedBytes)
            .setParameter("now", now)
            .setParameter("lease", now.plus(RESERVATION_LEASE))
            .executeUpdate();
    }

    @Transactional(REQUIRES_NEW)
    public void expedite(UUID operationId) {
        Instant now = clock.now();
        entityManager.createNativeQuery("""
            update external_storage_operations
            set available_at=:now,lease_until=null,updated_at=:now
            where id=:id and state='PENDING'
            """).setParameter("now", now).setParameter("id", operationId).executeUpdate();
    }

    public void completeReservation(UUID operationId) {
        entityManager.createNativeQuery("""
            delete from external_storage_operations
            where id=:id and operation_kind='ROLLBACK_BINARY_CREATE'
            """).setParameter("id", operationId).executeUpdate();
    }

    @Transactional
    public UUID enqueueBinaryDelete(OwnerId owner, String backend, String primaryKey, String secondaryKey) {
        return enqueue(owner, null, "DELETE_BINARY", backend, primaryKey, secondaryKey);
    }

    @Transactional
    public UUID enqueueTransferFileDelete(OwnerId owner, UUID transferJobId, String path) {
        return enqueue(owner, transferJobId, "DELETE_TRANSFER_FILE", null, path, null);
    }

    private UUID enqueue(OwnerId owner, UUID transferJobId, String kind, String backend,
                         String primary, String secondary) {
        UUID id = ids.nextId();
        Instant now = clock.now();
        entityManager.createNativeQuery("""
            insert into external_storage_operations(
              id,operation_kind,state,owner_id,transfer_job_id,storage_backend,
              primary_location,secondary_location,reserved_bytes,attempt_count,
              available_at,created_at,updated_at)
            values (:id,:kind,'PENDING',:owner,:job,:backend,:primary,:secondary,0,0,:now,:now,:now)
            """)
            .setParameter("id", id)
            .setParameter("kind", kind)
            .setParameter("owner", owner.value())
            .setParameter("job", transferJobId)
            .setParameter("backend", backend)
            .setParameter("primary", primary)
            .setParameter("secondary", secondary)
            .setParameter("now", now)
            .executeUpdate();
        return id;
    }

    public long pendingBytes(OwnerId owner) {
        return ((Number) entityManager.createNativeQuery("""
            select coalesce(sum(reserved_bytes),0) from external_storage_operations
            where owner_id=:owner and state='PENDING' and operation_kind='ROLLBACK_BINARY_CREATE'
            """).setParameter("owner", owner.value()).getSingleResult()).longValue();
    }

    public long pendingBinaryOperations() {
        return ((Number) entityManager.createNativeQuery("""
            select count(*) from external_storage_operations
            where operation_kind in ('ROLLBACK_BINARY_CREATE','DELETE_BINARY')
            """).getSingleResult()).longValue();
    }

    @Transactional(REQUIRES_NEW)
    public Optional<Operation> claimNext() {
        Instant now = clock.now();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
            select o.id,o.operation_kind,o.owner_id,o.transfer_job_id,o.storage_backend,
                   o.primary_location,o.secondary_location,o.attempt_count
            from external_storage_operations o
            where o.state='PENDING' and o.available_at<=:now
              and (o.lease_until is null or o.lease_until<=:now)
              and not (
                o.operation_kind='ROLLBACK_BINARY_CREATE' and o.transfer_job_id is not null
                and exists (
                  select 1 from transfer_jobs j
                  where j.id=o.transfer_job_id and j.state in ('QUEUED','RUNNING')
                )
              )
            order by o.available_at,o.created_at,o.id
            for update skip locked limit 1
            """).setParameter("now", now).getResultList();
        if (rows.isEmpty()) return Optional.empty();
        Object[] row = rows.getFirst();
        UUID id = (UUID) row[0];
        entityManager.createNativeQuery("""
            update external_storage_operations set lease_until=:lease,updated_at=:now where id=:id
            """).setParameter("lease", now.plus(WORKER_LEASE))
            .setParameter("now", now).setParameter("id", id).executeUpdate();
        return Optional.of(new Operation(id, (String) row[1], (UUID) row[2], (UUID) row[3],
            (String) row[4], (String) row[5], (String) row[6], ((Number) row[7]).intValue()));
    }

    @Transactional(REQUIRES_NEW)
    public void completed(UUID id) {
        entityManager.createNativeQuery("delete from external_storage_operations where id=:id")
            .setParameter("id", id).executeUpdate();
    }

    @Transactional(REQUIRES_NEW)
    public void retry(UUID id, int previousAttempts, String error) {
        int attempts = previousAttempts + 1;
        long delaySeconds = Math.min(3600, 1L << Math.min(attempts, 11));
        Instant now = clock.now();
        entityManager.createNativeQuery("""
            update external_storage_operations
            set attempt_count=:attempts,available_at=:available,lease_until=null,
                last_error=:error,updated_at=:now
            where id=:id and state='PENDING'
            """).setParameter("attempts", attempts)
            .setParameter("available", now.plusSeconds(delaySeconds))
            .setParameter("error", safeError(error))
            .setParameter("now", now).setParameter("id", id).executeUpdate();
    }

    @Transactional(REQUIRES_NEW)
    public void failed(UUID id, String error) {
        Instant now = clock.now();
        entityManager.createNativeQuery("""
            update external_storage_operations
            set state='FAILED',lease_until=null,last_error=:error,updated_at=:now
            where id=:id
            """).setParameter("error", safeError(error))
            .setParameter("now", now).setParameter("id", id).executeUpdate();
    }

    private String safeError(String value) {
        String message = value == null || value.isBlank() ? "External storage operation failed." : value;
        return message.substring(0, Math.min(512, message.length()));
    }

    public record Operation(UUID id, String kind, UUID ownerId, UUID transferJobId,
                            String backend, String primary, String secondary, int attempts) {}
}
