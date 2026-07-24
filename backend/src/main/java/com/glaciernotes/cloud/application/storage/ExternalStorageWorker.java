package com.glaciernotes.cloud.application.storage;

import com.glaciernotes.cloud.application.port.BinaryAssetStorage;
import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class ExternalStorageWorker {
    private static final Logger LOG = Logger.getLogger(ExternalStorageWorker.class);

    private final ExternalStorageOperations operations;
    private final BinaryAssetStorage binaryStorage;
    private final Path transferRoot;

    public ExternalStorageWorker(ExternalStorageOperations operations, BinaryAssetStorage binaryStorage,
                                 GlacierConfiguration configuration) {
        this.operations = operations;
        this.binaryStorage = binaryStorage;
        this.transferRoot = configuration.transfer().temporaryRoot().toAbsolutePath().normalize();
    }

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void reconcile() {
        operations.claimNext().ifPresent(this::execute);
    }

    void execute(ExternalStorageOperations.Operation operation) {
        try {
            switch (operation.kind()) {
                case "ROLLBACK_BINARY_CREATE", "DELETE_BINARY" -> deleteBinary(operation);
                case "DELETE_TRANSFER_FILE" -> deleteTransferFile(operation);
                default -> {
                    operations.failed(operation.id(), "Unsupported external storage operation.");
                    return;
                }
            }
            operations.completed(operation.id());
        } catch (UnsafeLocation failure) {
            LOG.errorf("Refusing unsafe external storage operation %s: %s",
                operation.id(), failure.getMessage());
            operations.failed(operation.id(), failure.getMessage());
        } catch (RuntimeException | IOException failure) {
            LOG.warnf("External storage operation %s will be retried: %s",
                operation.id(), failure.getMessage());
            operations.retry(operation.id(), operation.attempts(), failure.getMessage());
        }
    }

    private void deleteBinary(ExternalStorageOperations.Operation operation) {
        if (!binaryStorage.backend().equals(operation.backend())) {
            throw new UnsafeLocation("The configured image backend does not match the journaled backend.");
        }
        binaryStorage.delete(operation.primary());
        if (operation.secondary() != null) binaryStorage.delete(operation.secondary());
    }

    private void deleteTransferFile(ExternalStorageOperations.Operation operation) throws IOException {
        Path target = Path.of(operation.primary()).toAbsolutePath().normalize();
        if (!target.startsWith(transferRoot) || target.equals(transferRoot)) {
            throw new UnsafeLocation("The transfer path is outside the configured temporary root.");
        }
        Files.deleteIfExists(target);
    }

    private static final class UnsafeLocation extends RuntimeException {
        private UnsafeLocation(String message) {
            super(message);
        }
    }
}
