package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "image_assets")
public class ImageAssetEntity extends OwnedMutableEntity {
    @Column(name = "mime_type")
    private String mimeType;
    @Column(name = "original_file_name")
    private String originalFileName;
    @Column(name = "byte_size")
    private long byteSize;
    private int width;
    private int height;
    @Column(name = "content_hash")
    private String contentHash;
    @Column(name = "storage_backend")
    private String storageBackend;
    @Column(name = "storage_key")
    private String storageKey;

    protected ImageAssetEntity() {
    }
}

