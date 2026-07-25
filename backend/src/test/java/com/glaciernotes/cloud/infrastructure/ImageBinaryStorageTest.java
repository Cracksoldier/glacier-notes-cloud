package com.glaciernotes.cloud.infrastructure;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression tests for the path-traversal guard in {@code ImageBinaryStorage#safePath}: a storage
 * key must never resolve outside the configured filesystem root. {@code load}/{@code delete} wrap
 * the guard's {@link IllegalArgumentException} in {@link ImageBinaryStorage.ImageStorageException},
 * so this asserts against the wrapper and its cause rather than the raw guard exception.
 */
@QuarkusTest
class ImageBinaryStorageTest {
    @Inject
    ImageBinaryStorage storage;

    @ParameterizedTest
    @ValueSource(strings = {
        "../../../etc/passwd",
        "/etc/passwd",
        "images/../../../etc/passwd",
        "..",
        "../",
    })
    void rejectsStorageKeysThatEscapeTheFilesystemRoot(String maliciousKey) {
        assertUnsafeKeyRejected(assertThrows(
            ImageBinaryStorage.ImageStorageException.class, () -> storage.load(maliciousKey)));
        assertUnsafeKeyRejected(assertThrows(
            ImageBinaryStorage.ImageStorageException.class, () -> storage.delete(maliciousKey)));
    }

    private void assertUnsafeKeyRejected(ImageBinaryStorage.ImageStorageException thrown) {
        var cause = assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
        assertEquals("Unsafe image storage key", cause.getMessage());
    }
}
