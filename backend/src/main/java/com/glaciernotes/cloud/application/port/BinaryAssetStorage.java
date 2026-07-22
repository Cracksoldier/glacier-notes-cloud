package com.glaciernotes.cloud.application.port;

import java.io.InputStream;
import java.nio.file.Path;

public interface BinaryAssetStorage {
    void store(String key, Path content, long contentLength, String contentType);
    StoredObject load(String key);
    void delete(String key);
    boolean healthy();
    String backend();

    record StoredObject(InputStream stream, long contentLength, String contentType) {}
}
