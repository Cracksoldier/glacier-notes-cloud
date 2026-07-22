package com.glaciernotes.cloud.application.transfer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TransferModels {
    private TransferModels() {}

    public record ExportCommand(String scope, UUID resourceId) {}
    public record ApplyCommand(String strategy) {}
    public record Counts(long notebooks, long notes, long labels, long images, long checklistItems) {}
    public record JobView(UUID id, String kind, String state, String phase, Counts counts,
                          Boolean hasConflicts, Long quotaImpactBytes, List<String> errors,
                          String downloadUrl, OffsetDateTime createdAt,
                          OffsetDateTime completedAt, OffsetDateTime expiresAt) {}
}
