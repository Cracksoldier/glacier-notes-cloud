package com.glaciernotes.cloud.application.operations;

import com.glaciernotes.cloud.application.content.ContentService;
import com.glaciernotes.cloud.application.image.ImageService;
import com.glaciernotes.cloud.application.lifecycle.AccountDeletionService;
import com.glaciernotes.cloud.application.lifecycle.TrashRetentionService;
import com.glaciernotes.cloud.application.transfer.TransferWorker;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class CoordinatedJobs {
    private static final Logger LOG = Logger.getLogger(CoordinatedJobs.class);
    private static final Duration LEASE_DURATION = Duration.ofMinutes(30);
    private static final Duration RENEWAL_INTERVAL = Duration.ofMinutes(10);
    private final JobLeaseRepository leases;
    private final CleanupService cleanup;
    private final TrashRetentionService trash;
    private final ContentService content;
    private final ImageService images;
    private final AccountDeletionService deletion;
    private final TransferWorker transfers;
    private final ScheduledExecutorService renewals = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().daemon().name("glacier-job-lease-renewal").factory()
    );

    public CoordinatedJobs(JobLeaseRepository leases, CleanupService cleanup,
                           TrashRetentionService trash, ContentService content, ImageService images,
                           AccountDeletionService deletion, TransferWorker transfers) {
        this.leases = leases;
        this.cleanup = cleanup;
        this.trash = trash;
        this.content = content;
        this.images = images;
        this.deletion = deletion;
        this.transfers = transfers;
    }

    @Scheduled(every = "1h", delayed = "20s") void invitations() { run("invitation-cleanup", cleanup::expireInvitations); }
    @Scheduled(every = "1h", delayed = "30s") void resets() { run("reset-token-cleanup", cleanup::removePasswordResetTokens); }
    @Scheduled(every = "1h", delayed = "40s") void emails() { run("email-token-cleanup", cleanup::removeEmailChangeTokens); }
    @Scheduled(every = "1h", delayed = "50s") void sessions() { run("session-cleanup", cleanup::removeSessions); }
    @Scheduled(every = "1h", delayed = "1m") void trash() { run("trash-cleanup", trash::purgeExpiredTrash); }
    @Scheduled(every = "1h", delayed = "2m") void history() { run("history-cleanup", content::cleanNoteHistory); }
    @Scheduled(every = "1h", delayed = "3m") void tombstones() { run("tombstone-cleanup", cleanup::removeTombstones); }
    @Scheduled(every = "1h", delayed = "4m") void images() { run("orphan-image-cleanup", images::collectOrphans); }
    @Scheduled(every = "1h", delayed = "5m") void audit() { run("audit-cleanup", cleanup::removeAuditEvents); }
    @Scheduled(every = "1m", delayed = "30s") void deletion() { run("account-deletion", deletion::finalizeDueAccounts); }
    @Scheduled(every = "1h", delayed = "6m") void transfers() { run("transfer-cleanup", transfers::cleanup); }

    void run(String name, Runnable work) {
        UUID runId = leases.acquire(name, LEASE_DURATION);
        if (runId == null) return;
        Throwable failure = null;
        AtomicBoolean leaseLost = new AtomicBoolean();
        Thread owner = Thread.currentThread();
        ScheduledFuture<?> renewal = renewals.scheduleAtFixedRate(
            () -> renew(name, runId, owner, leaseLost),
            RENEWAL_INTERVAL.toSeconds(), RENEWAL_INTERVAL.toSeconds(), TimeUnit.SECONDS
        );
        MDC.put("correlationId", "job-" + runId);
        MDC.put("jobId", runId.toString());
        try {
            work.run();
            if (leaseLost.get()) throw new LeaseLostException();
        } catch (RuntimeException exception) {
            failure = exception;
            LOG.errorf(exception, "Scheduled job failed job=%s jobId=%s", name, runId);
        } finally {
            renewal.cancel(false);
            if (leaseLost.get()) Thread.interrupted();
            leases.finish(name, runId, failure);
            MDC.remove("jobId");
            MDC.remove("correlationId");
        }
    }

    private void renew(String name, UUID runId, Thread owner, AtomicBoolean leaseLost) {
        try {
            if (leases.renew(name, runId, LEASE_DURATION)) return;
        } catch (RuntimeException failure) {
            LOG.errorf(failure, "Scheduled job lease renewal failed job=%s jobId=%s", name, runId);
        }
        if (leaseLost.compareAndSet(false, true)) owner.interrupt();
    }

    @PreDestroy
    void stopRenewals() {
        renewals.shutdownNow();
    }

    private static final class LeaseLostException extends RuntimeException {
        private LeaseLostException() {
            super("The scheduled-job lease was lost.");
        }
    }
}
