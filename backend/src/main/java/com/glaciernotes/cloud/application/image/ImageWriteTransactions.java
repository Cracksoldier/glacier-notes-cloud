package com.glaciernotes.cloud.application.image;

import com.glaciernotes.cloud.application.storage.ExternalStorageOperations;
import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.persistence.entity.ImageAssetEntity;
import com.glaciernotes.cloud.persistence.repository.ImageAssetRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class ImageWriteTransactions {
    private final ImageAssetRepository repository;
    private final ExternalStorageOperations operations;

    public ImageWriteTransactions(ImageAssetRepository repository, ExternalStorageOperations operations) {
        this.repository = repository;
        this.operations = operations;
    }

    @Transactional
    public void prepareUpload(OwnerId owner, UUID reservationId, String backend,
                              String primaryKey, String secondaryKey, String sourceMimeType,
                              long reservedBytes) {
        repository.lockOwner(owner);
        var settings = repository.settings();
        if (!settings.allowedUploadTypes().contains(sourceMimeType)) {
            throw ImageFailure.invalid("IMAGE_TYPE_UNSUPPORTED",
                "This image type is disabled by the administrator.");
        }
        long projected = repository.usedBytes(owner) + operations.pendingBytes(owner) + reservedBytes;
        if (projected > settings.perUserStorageQuotaBytes()) throw ImageFailure.quota();
        operations.reserveBinary(owner, reservationId, null, backend,
            primaryKey, secondaryKey, reservedBytes);
    }

    @Transactional
    public void finalizeUpload(OwnerId owner, UUID reservationId, ImageAssetEntity asset) {
        repository.lockOwner(owner);
        long projected = repository.usedBytes(owner) + operations.pendingBytes(owner);
        if (projected > repository.settings().perUserStorageQuotaBytes()) throw ImageFailure.quota();
        repository.persist(owner, asset);
        repository.flush();
        operations.completeReservation(reservationId);
    }
}
