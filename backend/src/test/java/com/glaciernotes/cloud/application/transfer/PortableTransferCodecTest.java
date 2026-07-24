package com.glaciernotes.cloud.application.transfer;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glaciernotes.cloud.configuration.GlacierConfiguration;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

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
                "mimeType":"image/gif","base64":"bm90IGJhc2U2NA=="}]
            }
            """);

        var result = codec.inspect(invalid, () -> {});
        String errors = String.join("; ", result.errors());
        assertTrue(errors.contains("format must be glacier-notes-export"), errors);
        assertTrue(errors.contains("schemaVersion must be 1"), errors);
        assertTrue(errors.contains("exportedAt"), errors);
        assertTrue(errors.contains("missing notebook"), errors);
        assertTrue(errors.contains("unsupported MIME type"), errors);
    }

    @Test
    void rejectsMalformedImageBase64WithAnActionableFormatError() throws Exception {
        Path invalid = temporaryDirectory.resolve("invalid-base64.glacier.json");
        Files.writeString(invalid, portableImage("not base64"));

        var failure = assertThrows(
            PortableTransferCodec.FormatException.class,
            () -> codec.inspect(invalid, () -> {})
        );

        assertTrue(failure.getMessage().contains("base64 is invalid"), failure.getMessage());
    }

    @Test
    void streamsImageBase64WithoutRelaxingTheGeneralStringLimit() throws Exception {
        byte[] image = new byte[96];
        String base64 = Base64.getEncoder().encodeToString(image);
        PortableTransferCodec strictCodec = new PortableTransferCodec(
            new ObjectMapper(), null, null, transferLimits(64, 128)
        );
        Path input = temporaryDirectory.resolve("streamed-base64.glacier.json");
        Files.writeString(input, portableImage(base64));

        var inspection = strictCodec.inspect(input, () -> {});

        assertTrue(inspection.errors().isEmpty(), () -> String.join("; ", inspection.errors()));
        assertEquals(image.length, inspection.decodedImageBytes());
    }

    private String portableImage(String base64) {
        return """
            {
              "format":"glacier-notes-export","schemaVersion":1,
              "exportedAt":"2026-01-01T00:00:00Z",
              "notebooks":[],"notes":[],"labels":[],
              "images":[{"id":"77777777-7777-4777-8777-777777777777",
                "mimeType":"image/png","base64":"%s"}]
            }
            """.formatted(base64);
    }

    private GlacierConfiguration.Transfer transferLimits(int maximumStringLength, long maximumImageBytes) {
        return (GlacierConfiguration.Transfer) Proxy.newProxyInstance(
            GlacierConfiguration.Transfer.class.getClassLoader(),
            new Class<?>[]{GlacierConfiguration.Transfer.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "maximumJsonDepth" -> 32;
                case "maximumStringLength" -> maximumStringLength;
                case "maximumImageBytes", "maximumDecodedImageBytes" -> maximumImageBytes;
                case "maximumNotebooks", "maximumNotes", "maximumLabels",
                     "maximumImages", "maximumChecklistItems" -> 100;
                case "retentionHours" -> 24;
                case "maximumUploadBytes" -> maximumImageBytes * 2;
                case "temporaryRoot" -> temporaryDirectory;
                case "toString" -> "test transfer limits";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    @Test
    void rejectsMissingPortableArraysAsAFormatError() throws Exception {
        Path invalid = temporaryDirectory.resolve("missing-arrays.glacier.json");
        Files.writeString(invalid, "{\"format\":\"glacier-notes-export\",\"schemaVersion\":1}");
        var failure = assertThrows(PortableTransferCodec.FormatException.class,
            () -> codec.inspect(invalid, () -> {}));
        assertTrue(failure.getMessage().contains("arrays are required"));
    }

    @Test
    void derivesTheEncodedImageLimitFromTheConfiguredDecodedLimit() {
        assertEquals(4, PortableTransferCodec.encodedBase64Limit(1));
        assertEquals(4, PortableTransferCodec.encodedBase64Limit(3));
        assertEquals(8, PortableTransferCodec.encodedBase64Limit(4));
        assertEquals(13_981_016, PortableTransferCodec.encodedBase64Limit(10 * 1024 * 1024));
        assertThrows(
            IllegalStateException.class,
            () -> PortableTransferCodec.encodedBase64Limit(Integer.MAX_VALUE)
        );
        assertThrows(
            IllegalStateException.class,
            () -> PortableTransferCodec.encodedBase64Limit(Long.MAX_VALUE)
        );
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
