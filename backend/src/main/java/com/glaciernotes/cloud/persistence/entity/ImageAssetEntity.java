package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "image_assets")
public class ImageAssetEntity extends OwnedMutableEntity {
    @Column(name = "mime_type", nullable = false)
    private String mimeType;
    @Column(name = "original_file_name")
    private String originalFileName;
    @Column(name = "byte_size", nullable = false)
    private long byteSize;
    private int width;
    private int height;
    @Column(name = "content_hash", nullable = false)
    private String contentHash;
    @Column(name = "storage_backend", nullable = false)
    private String storageBackend;
    @Column(name = "storage_key", nullable = false)
    private String storageKey;
    @Column(name = "thumbnail_mime_type")
    private String thumbnailMimeType;
    @Column(name = "thumbnail_byte_size")
    private Long thumbnailByteSize;
    @Column(name = "thumbnail_width")
    private Integer thumbnailWidth;
    @Column(name = "thumbnail_height")
    private Integer thumbnailHeight;
    @Column(name = "thumbnail_storage_key")
    private String thumbnailStorageKey;
    @Column(name = "orphaned_at")
    private Instant orphanedAt;

    protected ImageAssetEntity() {}

    public ImageAssetEntity(UUID ownerId, UUID id, String mimeType, String originalFileName,
                            long byteSize, int width, int height, String contentHash,
                            String storageBackend, String storageKey, String thumbnailMimeType,
                            long thumbnailByteSize, int thumbnailWidth, int thumbnailHeight,
                            String thumbnailStorageKey, Instant now) {
        key = new OwnedEntityId(ownerId, id);
        this.mimeType = mimeType;
        this.originalFileName = originalFileName;
        this.byteSize = byteSize;
        this.width = width;
        this.height = height;
        this.contentHash = contentHash;
        this.storageBackend = storageBackend;
        this.storageKey = storageKey;
        this.thumbnailMimeType = thumbnailMimeType;
        this.thumbnailByteSize = thumbnailByteSize;
        this.thumbnailWidth = thumbnailWidth;
        this.thumbnailHeight = thumbnailHeight;
        this.thumbnailStorageKey = thumbnailStorageKey;
        orphanedAt = now;
        createdAt = now;
        updatedAt = now;
    }

    public UUID id() { return key.id(); }
    public String mimeType() { return mimeType; }
    public long byteSize() { return byteSize; }
    public int width() { return width; }
    public int height() { return height; }
    public String contentHash() { return contentHash; }
    public String storageBackend() { return storageBackend; }
    public String storageKey() { return storageKey; }
    public String thumbnailMimeType() { return thumbnailMimeType; }
    public long thumbnailByteSize() { return thumbnailByteSize == null ? 0 : thumbnailByteSize; }
    public int thumbnailWidth() { return thumbnailWidth == null ? width : thumbnailWidth; }
    public int thumbnailHeight() { return thumbnailHeight == null ? height : thumbnailHeight; }
    public String thumbnailStorageKey() { return thumbnailStorageKey; }
    public Instant orphanedAt() { return orphanedAt; }
    public long totalBytes() { return byteSize + thumbnailByteSize(); }

    public void referenced(Instant now) { orphanedAt = null; updatedAt = now; }
    public void orphaned(Instant now) { if (orphanedAt == null) orphanedAt = now; updatedAt = now; }
}
