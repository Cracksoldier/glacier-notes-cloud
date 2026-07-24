package com.glaciernotes.cloud.application.image;

import com.glaciernotes.cloud.application.port.BinaryAssetStorage;
import com.glaciernotes.cloud.application.storage.ExternalStorageOperations;
import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.SystemContentScope;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.generated.model.ImageMetadata;
import com.glaciernotes.cloud.generated.model.StorageUsage;
import com.glaciernotes.cloud.infrastructure.ImageBinaryStorage.ImageStorageException;
import com.glaciernotes.cloud.persistence.entity.ImageAssetEntity;
import com.glaciernotes.cloud.persistence.entity.InstanceStateEntity;
import com.glaciernotes.cloud.persistence.repository.ImageAssetRepository;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@ApplicationScoped
public class ImageService {
    private final ImageAssetRepository repository;
    private final ImageProcessor processor;
    private final BinaryAssetStorage storage;
    private final IdGenerator ids;
    private final TimeProvider clock;
    private final EntityManager entityManager;
    private final ExternalStorageOperations operations;
    private final ImageWriteTransactions writes;

    public ImageService(ImageAssetRepository repository, ImageProcessor processor,
                        BinaryAssetStorage storage, IdGenerator ids, TimeProvider clock,
                        EntityManager entityManager, ExternalStorageOperations operations,
                        ImageWriteTransactions writes) {
        this.repository = repository; this.processor = processor; this.storage = storage;
        this.ids = ids; this.clock = clock; this.entityManager = entityManager;
        this.operations = operations; this.writes = writes;
    }

    @Transactional
    public void validateBackend(@Observes StartupEvent ignored) {
        InstanceStateEntity state = entityManager.find(InstanceStateEntity.class, (short) 1, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        String selected = state.imageStorageBackend();
        long assets = ((Number) entityManager.createNativeQuery("select count(*) from image_assets").getSingleResult()).longValue()
            + operations.pendingBinaryOperations();
        if (selected != null && !selected.equals(storage.backend()) && assets > 0) {
            throw new IllegalStateException("Image backend is immutable after the first upload; migrate assets before changing GLACIER_IMAGE_BACKEND");
        }
        if (!storage.backend().equals(selected)) state.selectImageStorageBackend(storage.backend(), clock.now());
    }

    public ImageMetadata upload(OwnerId owner, Path upload, String originalName) {
        var settings = repository.settings();
        try (var processed = processor.process(upload, settings.maximumImageBytes())) {
            UUID id = ids.nextId();
            String prefix = "assets/" + id + "/" + UUID.randomUUID();
            String key = prefix + "-image";
            String thumbnailKey = prefix + "-thumbnail";
            UUID reservationId = ids.nextId();
            long reservedBytes = processed.byteSize() + processed.thumbnailByteSize();
            boolean reserved = false;
            try {
                writes.prepareUpload(owner, reservationId, storage.backend(), key, thumbnailKey,
                    processed.sourceMimeType(), reservedBytes);
                reserved = true;
                storage.store(key, processed.main(), processed.byteSize(), processed.mimeType());
                storage.store(thumbnailKey, processed.thumbnail(), processed.thumbnailByteSize(), processed.mimeType());
                var asset = new ImageAssetEntity(owner.value(), id, processed.mimeType(), cleanName(originalName),
                    processed.byteSize(), processed.width(), processed.height(), processed.hash(), storage.backend(), key,
                    processed.mimeType(), processed.thumbnailByteSize(), processed.thumbnailWidth(), processed.thumbnailHeight(),
                    thumbnailKey, clock.now());
                writes.finalizeUpload(owner, reservationId, asset);
                return metadata(asset);
            } catch (RuntimeException failure) {
                if (reserved) operations.expedite(reservationId);
                if (failure instanceof ImageFailure imageFailure) throw imageFailure;
                throw ImageFailure.unavailable();
            }
        }
    }

    public ImageMetadata metadata(OwnerId owner, UUID id) { return metadata(require(owner, id, false)); }
    public Download download(OwnerId owner, UUID id, boolean thumbnail) {
        ImageAssetEntity asset = require(owner, id, false);
        try {
            var object = storage.load(thumbnail ? asset.thumbnailStorageKey() : asset.storageKey());
            return new Download(object, thumbnail ? asset.thumbnailMimeType() : asset.mimeType(), asset.contentHash());
        } catch (ImageStorageException failure) { throw ImageFailure.unavailable(); }
    }

    @Transactional
    public void delete(OwnerId owner, UUID id) {
        ImageAssetEntity asset = require(owner, id, true);
        if (repository.referenced(owner, id)) throw ImageFailure.referenced();
        operations.enqueueBinaryDelete(owner, asset.storageBackend(),
            asset.storageKey(), asset.thumbnailStorageKey());
        repository.remove(owner, asset);
    }

    public StorageUsage usage(OwnerId owner) {
        var settings = repository.settings(); long used = repository.usedBytes(owner); long quota = settings.perUserStorageQuotaBytes();
        return new StorageUsage().usedBytes(used).quotaBytes(quota).remainingBytes(Math.max(0, quota - used)).imageCount(repository.count(owner));
    }

    @Scheduled(every = "1h", delayed = "1m")
    @Transactional
    void collectOrphans() {
        Number locked = (Number) entityManager.createNativeQuery("select pg_try_advisory_xact_lock(7197007)::int").getSingleResult();
        if (locked.intValue() == 0) return;
        int grace = repository.settings().imageOrphanGraceHours();
        for (ImageAssetEntity asset : repository.garbage(SystemContentScope.BACKGROUND_MAINTENANCE,
                clock.now().minus(grace, ChronoUnit.HOURS))) {
            OwnerId owner = new OwnerId(asset.key().ownerId());
            operations.enqueueBinaryDelete(owner, asset.storageBackend(),
                asset.storageKey(), asset.thumbnailStorageKey());
            repository.remove(owner, asset);
        }
    }

    private ImageAssetEntity require(OwnerId owner, UUID id, boolean lock) { return repository.find(owner, id, lock).orElseThrow(ImageFailure::notFound); }
    private ImageMetadata metadata(ImageAssetEntity value) {
        return new ImageMetadata().id(value.id()).mimeType(ImageMetadata.MimeTypeEnum.fromValue(value.mimeType())).byteSize(value.byteSize())
            .width(value.width()).height(value.height()).thumbnailMimeType(ImageMetadata.ThumbnailMimeTypeEnum.fromValue(value.thumbnailMimeType()))
            .thumbnailByteSize(value.thumbnailByteSize()).thumbnailWidth(value.thumbnailWidth())
            .thumbnailHeight(value.thumbnailHeight()).contentUrl("/api/v1/images/" + value.id())
            .thumbnailUrl("/api/v1/images/" + value.id() + "/thumbnail")
            .createdAt(OffsetDateTime.ofInstant(value.createdAt(), ZoneOffset.UTC));
    }
    private String cleanName(String value) {
        if (value == null) return null;
        String clean = value.replaceAll("[\\r\\n]", "");
        return clean.substring(0, Math.min(512, clean.length()));
    }
    public record Download(BinaryAssetStorage.StoredObject object, String mimeType, String etag) {}
}
