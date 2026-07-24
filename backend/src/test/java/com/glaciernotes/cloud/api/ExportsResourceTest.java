package com.glaciernotes.cloud.api;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExportsResourceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesDownloadMetadataBeforeReturningAnOwnedStream() throws Exception {
        Path export = temporaryDirectory.resolve("export.json");
        byte[] content = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(export, content);

        try (Response response = ExportsResource.downloadResponse(export, UUID.randomUUID());
             InputStream stream = (InputStream) response.getEntity()) {
            assertEquals(Integer.toString(content.length), response.getHeaderString("Content-Length"));
            assertArrayEquals(content, stream.readAllBytes());
        }
        assertThrows(
            NoSuchFileException.class,
            () -> ExportsResource.downloadResponse(temporaryDirectory.resolve("missing"), UUID.randomUUID())
        );
    }
}
