package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.transfer.TransferModels.JobView;
import com.glaciernotes.cloud.generated.model.TransferCounts;
import com.glaciernotes.cloud.generated.model.TransferJob;

final class TransferJobMapper {
    private TransferJobMapper() {
    }

    static TransferJob toModel(JobView view) {
        TransferCounts counts = view.counts() == null ? null : new TransferCounts(
            view.counts().notebooks(), view.counts().notes(), view.counts().labels(),
            view.counts().images(), view.counts().checklistItems()
        );
        return new TransferJob(
            view.id(), TransferJob.KindEnum.fromValue(view.kind()),
            TransferJob.StateEnum.fromValue(view.state()), view.createdAt(), view.expiresAt()
        )
            .phase(view.phase() == null ? null : TransferJob.PhaseEnum.fromValue(view.phase()))
            .counts(counts)
            .hasConflicts(view.hasConflicts())
            .quotaImpactBytes(view.quotaImpactBytes())
            .errors(view.errors())
            .downloadUrl(view.downloadUrl())
            .completedAt(view.completedAt());
    }
}
