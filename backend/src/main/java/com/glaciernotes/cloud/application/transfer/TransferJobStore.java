package com.glaciernotes.cloud.application.transfer;

import com.glaciernotes.cloud.application.storage.ExternalStorageOperations;
import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.persistence.entity.AuditEventEntity;
import com.glaciernotes.cloud.persistence.entity.TransferJobEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TransferJobStore {
    private final EntityManager em;
    private final TimeProvider clock;
    private final IdGenerator ids;
    private final ExternalStorageOperations operations;

    public TransferJobStore(EntityManager em, TimeProvider clock, IdGenerator ids,
                            ExternalStorageOperations operations) {
        this.em = em; this.clock = clock; this.ids = ids; this.operations = operations;
    }

    @Transactional public void persist(TransferJobEntity job) { em.persist(job); }

    @Transactional
    public Optional<UUID> claimNext() {
        @SuppressWarnings("unchecked") List<UUID> ids = em.createNativeQuery("""
            select id from transfer_jobs where state = 'QUEUED' and cancel_requested = false
            order by created_at, id for update skip locked limit 1
            """).getResultList();
        if (ids.isEmpty()) return Optional.empty();
        TransferJobEntity job = em.find(TransferJobEntity.class, ids.getFirst(), LockModeType.PESSIMISTIC_WRITE);
        job.running(clock.now());
        return Optional.of(job.id());
    }

    @Transactional
    public TransferJobEntity require(UUID id) {
        TransferJobEntity job = em.find(TransferJobEntity.class, id);
        if (job == null) throw new jakarta.ws.rs.NotFoundException();
        return job;
    }

    @Transactional
    public TransferJobEntity requireForUser(UUID id, UUID actor, String kind, boolean admin) {
        TransferJobEntity job = require(id);
        boolean allowed = admin ? job.blindAdmin() && job.requestedBy().equals(actor)
            : !job.blindAdmin() && job.requestedBy().equals(actor) && job.targetUserId().equals(actor);
        if (!allowed || !job.kind().equals(kind)) throw new jakarta.ws.rs.NotFoundException();
        return job;
    }

    @Transactional
    public void inspected(UUID id, Map<String, Long> counts, boolean conflicts, long quota) {
        require(id).inspected(counts, conflicts, quota);
    }

    @Transactional public void succeeded(UUID id, long size) { require(id).succeeded(size, clock.now()); }
    @Transactional public void succeeded(UUID id, long size, Map<String, Long> counts) { require(id).succeeded(size, counts, clock.now()); }
    @Transactional
    public void completeImport(UUID id, long size, String correlationId) {
        TransferJobEntity job = require(id);
        job.succeeded(size, clock.now());
        enqueueTemporaryFile(job);
        if (job.blindAdmin()) auditAdminImport(job, correlationId);
    }
    @Transactional public void failed(UUID id, List<String> errors) {
        TransferJobEntity job = require(id);
        job.failed(errors, clock.now());
        enqueueTemporaryFile(job);
    }
    @Transactional public void canceled(UUID id) {
        TransferJobEntity job = require(id);
        job.canceled(clock.now());
        enqueueTemporaryFile(job);
    }
    @Transactional
    public boolean cancelRequested(UUID id) {
        return (Boolean) em.createNativeQuery("select cancel_requested from transfer_jobs where id=:id")
            .setParameter("id", id).getSingleResult();
    }

    @Transactional
    public void queueApply(UUID id, UUID actor, boolean admin, String strategy) {
        TransferJobEntity job = requireForUser(id, actor, "IMPORT", admin);
        if (!job.state().equals("READY")) throw new jakarta.ws.rs.ClientErrorException(409);
        if (!List.of("PRESERVE", "ADD_AS_COPIES", "REPLACE_BY_ID").contains(strategy))
            throw new jakarta.ws.rs.BadRequestException();
        if (Boolean.TRUE.equals(job.hasConflicts()) && strategy.equals("PRESERVE"))
            throw new jakarta.ws.rs.ClientErrorException(409);
        job.apply(strategy);
    }

    @Transactional
    public TransferJobEntity requestCancel(UUID id, UUID actor, String kind, boolean admin) {
        TransferJobEntity job = requireForUser(id, actor, kind, admin);
        job.requestCancel(clock.now());
        if (job.state().equals("CANCELED")) enqueueTemporaryFile(job);
        return job;
    }

    @Transactional
    public void recoverRunning() {
        em.createQuery("select j from TransferJobEntity j where j.state = 'RUNNING'", TransferJobEntity.class)
            .getResultList().forEach(TransferJobEntity::requeue);
    }

    @Transactional
    public List<TransferJobEntity> expire() {
        List<TransferJobEntity> jobs = em.createQuery("select j from TransferJobEntity j where j.expiresAt <= :now " +
                "and j.state not in ('EXPIRED', 'RUNNING')", TransferJobEntity.class)
            .setParameter("now", clock.now()).getResultList();
        jobs.forEach(job -> {
            job.expired(clock.now());
            enqueueTemporaryFile(job);
        });
        return jobs;
    }

    private void auditAdminImport(TransferJobEntity job, String correlationId) {
        em.persist(AuditEventEntity.administrative(ids.nextId(), "ADMIN_IMPORT_APPLIED",
            job.requestedBy(), job.targetUserId(), "USER", job.targetUserId(), clock.now(),
            correlationId == null ? "background-transfer" : correlationId,
            Map.of("jobId", job.id().toString())));
    }

    private void enqueueTemporaryFile(TransferJobEntity job) {
        if (job.temporaryPath() != null) {
            operations.enqueueTransferFileDelete(new OwnerId(job.targetUserId()),
                job.id(), job.temporaryPath());
        }
    }
}
