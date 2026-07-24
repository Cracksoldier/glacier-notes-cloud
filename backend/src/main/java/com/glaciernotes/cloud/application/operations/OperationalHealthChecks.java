package com.glaciernotes.cloud.application.operations;

import com.glaciernotes.cloud.application.port.BinaryAssetStorage;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;

import java.sql.Connection;

public final class OperationalHealthChecks {
    private OperationalHealthChecks() {}

    @ApplicationScoped
    @Liveness
    public static class ProcessHealth implements HealthCheck {
        @Override public HealthCheckResponse call() {
            return HealthCheckResponse.up("glacier-notes-process");
        }
    }

    @ApplicationScoped
    @Readiness
    public static class DatabaseHealth implements HealthCheck {
        private final AgroalDataSource dataSource;
        public DatabaseHealth(AgroalDataSource dataSource) { this.dataSource = dataSource; }
        @Override public HealthCheckResponse call() {
            try (Connection connection = dataSource.getConnection()) {
                return connection.isValid(2) ? HealthCheckResponse.up("postgresql")
                    : HealthCheckResponse.down("postgresql");
            } catch (Exception failure) { return HealthCheckResponse.down("postgresql"); }
        }
    }

    @ApplicationScoped
    @Readiness
    public static class ImageStorageHealth implements HealthCheck {
        private final BinaryAssetStorage storage;
        public ImageStorageHealth(BinaryAssetStorage storage) { this.storage = storage; }
        @Override public HealthCheckResponse call() {
            return storage.healthy() ? HealthCheckResponse.up("image-storage")
                : HealthCheckResponse.down("image-storage");
        }
    }

    @ApplicationScoped
    @Readiness
    public static class JobSubsystemHealth implements HealthCheck {
        private final JobLeaseRepository jobs;
        public JobSubsystemHealth(JobLeaseRepository jobs) { this.jobs = jobs; }
        @Override public HealthCheckResponse call() {
            return jobs.healthy() ? HealthCheckResponse.up("scheduled-jobs")
                : HealthCheckResponse.down("scheduled-jobs");
        }
    }
}
