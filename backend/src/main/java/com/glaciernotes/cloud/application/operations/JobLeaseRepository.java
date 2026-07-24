package com.glaciernotes.cloud.application.operations;

import com.glaciernotes.cloud.domain.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.util.UUID;

@ApplicationScoped
public class JobLeaseRepository {
    private final EntityManager entityManager;
    private final TimeProvider time;
    private final String instanceId = UUID.randomUUID().toString();

    public JobLeaseRepository(EntityManager entityManager, TimeProvider time) {
        this.entityManager = entityManager;
        this.time = time;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public UUID acquire(String name, Duration lease) {
        UUID runId = UUID.randomUUID();
        var now = time.now();
        int acquired = entityManager.createNativeQuery("""
            insert into job_locks(job_name,locked_by,locked_until,updated_at,version,run_id)
            values (:name,:owner,:until,:now,0,:run)
            on conflict(job_name) do update set locked_by=excluded.locked_by,
              locked_until=excluded.locked_until,updated_at=excluded.updated_at,
              version=job_locks.version+1,run_id=excluded.run_id
            where job_locks.locked_until is null or job_locks.locked_until <= :now
            """).setParameter("name", name).setParameter("owner", instanceId)
            .setParameter("until", now.plus(lease)).setParameter("now", now)
            .setParameter("run", runId)
            .executeUpdate();
        if (acquired == 0) return null;
        entityManager.createNativeQuery("""
            insert into scheduled_job_runs(job_name,run_id,state,started_at,completed_at,error_category,updated_at)
            values (:name,:run,'RUNNING',:now,null,null,:now)
            on conflict(job_name) do update set run_id=:run,state='RUNNING',started_at=:now,
              completed_at=null,error_category=null,updated_at=:now
            """).setParameter("name", name).setParameter("run", runId)
            .setParameter("now", now).executeUpdate();
        return runId;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean renew(String name, UUID runId, Duration lease) {
        var now = time.now();
        int renewed = entityManager.createNativeQuery("""
            update job_locks set locked_until=:until,updated_at=:now,version=version+1
            where job_name=:name and locked_by=:owner and run_id=:run and locked_until > :now
            """).setParameter("until", now.plus(lease)).setParameter("now", now)
            .setParameter("name", name).setParameter("owner", instanceId)
            .setParameter("run", runId).executeUpdate();
        if (renewed != 1) return false;
        entityManager.createNativeQuery("""
            update scheduled_job_runs set updated_at=:now
            where job_name=:name and run_id=:run and state='RUNNING'
            """).setParameter("now", now).setParameter("name", name)
            .setParameter("run", runId).executeUpdate();
        return true;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void finish(String name, UUID runId, Throwable failure) {
        entityManager.createNativeQuery("""
            update scheduled_job_runs set state=:state,completed_at=:now,error_category=:error,
              updated_at=:now where job_name=:name and run_id=:run
            """).setParameter("state", failure == null ? "SUCCEEDED" : "FAILED")
            .setParameter("now", time.now())
            .setParameter("error", failure == null ? null : failure.getClass().getSimpleName())
            .setParameter("name", name).setParameter("run", runId).executeUpdate();
        entityManager.createNativeQuery("""
            update job_locks set locked_by=null,locked_until=null,run_id=null,
              updated_at=:now,version=version+1
            where job_name=:name and locked_by=:owner and run_id=:run
            """).setParameter("now", time.now()).setParameter("name", name)
            .setParameter("owner", instanceId).setParameter("run", runId).executeUpdate();
    }

    @Transactional
    public boolean healthy() {
        entityManager.createNativeQuery("select count(*) from scheduled_job_runs").getSingleResult();
        return true;
    }

    @Transactional
    public long recentFailureCount() {
        Number failures = (Number) entityManager.createNativeQuery("""
            select count(*) from scheduled_job_runs
            where state='FAILED' and completed_at > :cutoff
            """).setParameter("cutoff", time.now().minus(Duration.ofHours(24))).getSingleResult();
        return failures.longValue();
    }
}
