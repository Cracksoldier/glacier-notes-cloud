package com.glaciernotes.cloud.application.transfer;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PortableTransferCodecTest {
    @Inject PortableTransferCodec codec;
    @TempDir Path temporaryDirectory;

    @Test
    void inspectsEveryDesktopSchemaV1ScopeWithDeterministicCounts() throws Exception {
        assertCounts("full.glacier.json", 2, 2, 2, 1, 1);
        assertCounts("notebook.glacier.json", 1, 1, 1, 0, 1);
        assertCounts("note.glacier.json", 1, 1, 1, 1, 0);
    }

    @Test
    void reportsActionableSchemaRelationshipAndImageErrorsWithoutApplyingData() throws Exception {
        Path invalid = temporaryDirectory.resolve("invalid.glacier.json");
        Files.writeString(invalid, """
            {
              "format":"another-format","schemaVersion":2,"exportedAt":"not-a-date",
              "notebooks":[],
              "notes":[{"id":"33333333-3333-4333-8333-333333333333",
                "notebookId":"11111111-1111-4111-8111-111111111111","type":"text",
                "title":"Broken","content":"glacier-img://77777777-7777-4777-8777-777777777777",
                "imageIds":[],"pinned":false,"archived":false,"labels":[],
                "createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}],
              "labels":[],
              "images":[{"id":"77777777-7777-4777-8777-777777777777",
                "mimeType":"image/gif","base64":"not base64"}]
            }
            """);

        var result = codec.inspect(invalid, () -> {});
        String errors = String.join("; ", result.errors());
        assertTrue(errors.contains("format must be glacier-notes-export"), errors);
        assertTrue(errors.contains("schemaVersion must be 1"), errors);
        assertTrue(errors.contains("exportedAt"), errors);
        assertTrue(errors.contains("missing notebook"), errors);
        assertTrue(errors.contains("unsupported MIME type"), errors);
        assertTrue(errors.contains("invalid or oversized base64"), errors);
    }

    @Test
    void rejectsMissingPortableArraysAsAFormatError() throws Exception {
        Path invalid = temporaryDirectory.resolve("missing-arrays.glacier.json");
        Files.writeString(invalid, "{\"format\":\"glacier-notes-export\",\"schemaVersion\":1}");
        var failure = assertThrows(PortableTransferCodec.FormatException.class,
            () -> codec.inspect(invalid, () -> {}));
        assertTrue(failure.getMessage().contains("arrays are required"));
    }

    private void assertCounts(String name, long notebooks, long notes, long labels,
                              long images, long checklist) throws Exception {
        var result = codec.inspect(Path.of("..", "compatibility-fixtures", "desktop-schema-v1", name), () -> {});
        assertTrue(result.errors().isEmpty(), () -> String.join("; ", result.errors()));
        assertEquals(notebooks, result.counts().get("notebooks"));
        assertEquals(notes, result.counts().get("notes"));
        assertEquals(labels, result.counts().get("labels"));
        assertEquals(images, result.counts().get("images"));
        assertEquals(checklist, result.counts().get("checklistItems"));
    }
}
