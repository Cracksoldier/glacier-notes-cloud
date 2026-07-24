package com.glaciernotes.cloud.application.transfer;

import com.glaciernotes.cloud.persistence.entity.TransferJobEntity;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class TransferWorker {
    private final TransferJobStore jobs;
    private final PortableTransferCodec codec;
    private final TransferInspector inspector;
    private final TransferApplyService applier;

    public TransferWorker(TransferJobStore jobs, PortableTransferCodec codec, TransferInspector inspector,
                          TransferApplyService applier) {
        this.jobs = jobs; this.codec = codec; this.inspector = inspector; this.applier = applier;
    }

    void recover(@Observes StartupEvent ignored) { jobs.recoverRunning(); }

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void process() {
        jobs.claimNext().ifPresent(this::execute);
    }

    private void execute(java.util.UUID id) {
        TransferJobEntity job = jobs.require(id); Path path = Path.of(job.temporaryPath());
        try {
            var cancellation = cancellation(id);
            if (job.kind().equals("EXPORT")) {
                codec.export(job.targetUserId(), job.scopeKind(), job.scopeEntityId(), path, cancellation);
                jobs.succeeded(id, Files.size(path));
            } else if (job.phase().equals("INSPECT")) {
                var result = inspector.inspect(path, job.targetUserId(), cancellation);
                if (result.errors().isEmpty()) jobs.inspected(id, result.inspection().counts(),
                    result.conflicts(), result.inspection().decodedImageBytes());
                else jobs.failed(id, result.errors());
            } else {
                var inspected = inspector.inspect(path, job.targetUserId(), cancellation);
                if (!inspected.errors().isEmpty()) throw new IllegalStateException(inspected.errors().getFirst());
                applier.apply(id, path, job.targetUserId(), job.importStrategy(),
                    inspected.inspection(), cancellation);
                jobs.completeImport(id, job.byteSize() == null ? 0 : job.byteSize(), "background-transfer");
            }
        } catch (Canceled canceled) {
            jobs.canceled(id);
        } catch (Exception failure) {
            String message = failure instanceof PortableTransferCodec.FormatException && failure.getMessage() != null
                ? failure.getMessage()
                : failure instanceof IOException ? "The portable file could not be processed."
                : failure.getMessage() == null ? "The transfer failed." : failure.getMessage();
            jobs.failed(id, List.of(message.substring(0, Math.min(512, message.length()))));
        }
    }

    private PortableTransferCodec.Cancellation cancellation(java.util.UUID id) {
        AtomicInteger calls = new AtomicInteger();
        return () -> { if (calls.incrementAndGet() % 100 == 1 && jobs.cancelRequested(id)) throw new Canceled(); };
    }

    public void cleanup() { jobs.expire(); }
    private static final class Canceled extends RuntimeException {}
}
