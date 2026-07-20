package com.glaciernotes.cloud.application.port;

import com.glaciernotes.cloud.domain.OwnerId;

import java.io.InputStream;
import java.util.UUID;

/** Replaceable binary-storage boundary; filesystem, PostgreSQL, and S3 adapters arrive in M7. */
public interface BinaryAssetStorage {
    void store(OwnerId ownerId, UUID assetId, InputStream content, long contentLength);

    InputStream load(OwnerId ownerId, UUID assetId);

    void delete(OwnerId ownerId, UUID assetId);
}
