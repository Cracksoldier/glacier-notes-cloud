package com.glaciernotes.cloud.application.transfer;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.glaciernotes.cloud.application.port.BinaryAssetStorage;
import com.glaciernotes.cloud.configuration.GlacierConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@ApplicationScoped
public class PortableTransferCodec {
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
    private static final Pattern IMAGE_REFERENCE = Pattern.compile("glacier-img://([0-9a-fA-F-]{36})");
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final Set<String> COLORS = Set.of("red", "orange", "yellow", "green", "teal", "blue", "purple", "pink", "gray");
    private final ObjectMapper mapper;
    private final DataSource dataSource;
    private final BinaryAssetStorage storage;
    private final GlacierConfiguration.Transfer limits;

    @Inject
    public PortableTransferCodec(ObjectMapper source, DataSource dataSource,
                                 BinaryAssetStorage storage, GlacierConfiguration configuration) {
        this(source, dataSource, storage, configuration.transfer());
    }

    PortableTransferCodec(ObjectMapper source, DataSource dataSource,
                          BinaryAssetStorage storage, GlacierConfiguration.Transfer limits) {
        this.dataSource = dataSource; this.storage = storage; this.limits = limits;
        encodedBase64Limit(limits.maximumImageBytes());
        JsonFactory factory = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
            .maxNestingDepth(limits.maximumJsonDepth())
            .maxStringLength(limits.maximumStringLength())
            .build()).build();
        this.mapper = new ObjectMapper(factory);
    }

    public Inspection inspect(Path input, Cancellation cancellation) throws IOException {
        var errors = new ArrayList<String>();
        var notebooks = new HashSet<UUID>(); var notes = new HashSet<UUID>();
        var labels = new HashSet<UUID>(); var images = new HashSet<UUID>();
        var checklistItems = new HashSet<UUID>(); var references = new ArrayList<References>();
        long[] decoded = {0}; long[] notebookCount = {0}; long[] noteCount = {0};
        long[] labelCount = {0}; long[] imageCount = {0}; long[] checklistCount = {0};
        Header header = read(input, (field, value, decodedImage) -> {
            cancellation.check();
            switch (field) {
                case "notebooks" -> {
                    JsonNode node = value;
                    notebookCount[0]++;
                    if (notebookCount[0] > limits.maximumNotebooks()) {
                        error(errors, "notebooks exceed the import limit");
                        return;
                    }
                    UUID id = uuid(node, "id", "notebook", errors);
                    duplicate(notebooks, id, "notebook", errors);
                    text(node, "name", true, 255, "notebook", errors);
                    date(node, "createdAt", true, "notebook", errors); date(node, "updatedAt", true, "notebook", errors);
                    integer(node, "sortOrder", "notebook", errors); color(node, "notebook", errors);
                }
                case "notes" -> {
                    JsonNode node = value;
                    noteCount[0]++;
                    if (noteCount[0] > limits.maximumNotes()) {
                        error(errors, "notes exceed the import limit");
                        return;
                    }
                    UUID id = uuid(node, "id", "note", errors);
                    duplicate(notes, id, "note", errors);
                    UUID notebook = uuid(node, "notebookId", "note", errors);
                    String type = text(node, "type", true, 16, "note", errors);
                    if (type != null && !Set.of("text", "checklist").contains(type)) error(errors, "note: unsupported type");
                    text(node, "title", true, 10_000, "note", errors);
                    text(node, "content", true, limits.maximumStringLength(), "note", errors);
                    bool(node, "pinned", "note", errors); bool(node, "archived", "note", errors);
                    date(node, "createdAt", true, "note", errors); date(node, "updatedAt", true, "note", errors);
                    date(node, "deletedAt", false, "note", errors); color(node, "note", errors);
                    Set<UUID> noteLabels = uuidArray(node, "labels", 500, "note", errors);
                    Set<UUID> noteImages = uuidArray(node, "imageIds", 500, "note", errors);
                    String content = node.path("content").isTextual() ? node.path("content").textValue() : "";
                    var matcher = IMAGE_REFERENCE.matcher(content);
                    while (matcher.find()) try { noteImages.add(UUID.fromString(matcher.group(1))); }
                    catch (IllegalArgumentException ignored) { error(errors, "note: malformed image reference"); }
                    JsonNode checklist = node.get("checklist");
                    if ("checklist".equals(type)) {
                        if (checklist == null || !checklist.isArray()) error(errors, "note: checklist is required");
                        else if (checklist.size() > 10_000) error(errors, "note: too many checklist items");
                        else for (JsonNode item : checklist) {
                            checklistCount[0]++;
                            if (checklistCount[0] > limits.maximumChecklistItems()) {
                                error(errors, "checklist items exceed the import limit");
                                continue;
                            }
                            UUID itemId = uuid(item, "id", "checklist item", errors);
                            duplicate(checklistItems, itemId, "checklist item", errors);
                            text(item, "text", true, 10_000, "checklist item", errors);
                            bool(item, "checked", "checklist item", errors);
                            integer(item, "sortOrder", "checklist item", errors);
                        }
                    } else if (checklist != null && !checklist.isNull() && checklist.size() > 0) {
                        error(errors, "text note: checklist must be absent or empty");
                    }
                    references.add(new References(id, notebook, noteLabels, noteImages));
                }
                case "labels" -> {
                    JsonNode node = value;
                    labelCount[0]++;
                    if (labelCount[0] > limits.maximumLabels()) {
                        error(errors, "labels exceed the import limit");
                        return;
                    }
                    duplicate(labels, uuid(node, "id", "label", errors), "label", errors);
                    text(node, "name", true, 255, "label", errors);
                }
                case "images" -> {
                    JsonNode node = value;
                    imageCount[0]++;
                    if (imageCount[0] > limits.maximumImages()) {
                        error(errors, "images exceed the import limit");
                        return;
                    }
                    duplicate(images, uuid(node, "id", "image", errors), "image", errors);
                    String mime = text(node, "mimeType", true, 64, "image", errors);
                    if (mime != null && !IMAGE_TYPES.contains(mime)) error(errors, "image: unsupported MIME type");
                    text(node, "fileName", false, 512, "image", errors);
                    if (decodedImage == null || decodedImage.content() == null || decodedImage.bytes() == 0) {
                        error(errors, "image: base64 is required");
                    } else {
                        decoded[0] += decodedImage.bytes();
                    }
                }
                default -> { }
            }
        });
        if (!"glacier-notes-export".equals(header.format)) error(errors, "format must be glacier-notes-export");
        if (header.schemaVersion != 1) error(errors, "schemaVersion must be 1");
        if (header.exportedAt == null) error(errors, "exportedAt must be an ISO-8601 timestamp");
        requireLimit(notebookCount[0], limits.maximumNotebooks(), "notebooks", errors);
        requireLimit(noteCount[0], limits.maximumNotes(), "notes", errors);
        requireLimit(labelCount[0], limits.maximumLabels(), "labels", errors);
        requireLimit(imageCount[0], limits.maximumImages(), "images", errors);
        requireLimit(checklistCount[0], limits.maximumChecklistItems(), "checklist items", errors);
        if (decoded[0] > limits.maximumDecodedImageBytes()) error(errors, "decoded images exceed the import limit");
        for (References reference : references) {
            if (reference.notebook != null && !notebooks.contains(reference.notebook)) error(errors, "note references a missing notebook");
            if (!labels.containsAll(reference.labels)) error(errors, "note references a missing label");
            if (!images.containsAll(reference.images)) error(errors, "note references a missing image");
        }
        if (header.defaultNotebookId != null && !notebooks.contains(header.defaultNotebookId))
            error(errors, "defaultNotebookId references a missing notebook");
        if (header.scopeKind != null && !Set.of("all", "notebook", "note").contains(header.scopeKind))
            error(errors, "scope kind is invalid");
        if (header.scopeKind != null && header.scopeKind.equals("notebook") && !notebooks.contains(header.scopeId))
            error(errors, "scope references a missing notebook");
        if (header.scopeKind != null && header.scopeKind.equals("note") && !notes.contains(header.scopeId))
            error(errors, "scope references a missing note");
        return new Inspection(Map.of("notebooks", notebookCount[0], "notes", noteCount[0],
            "labels", labelCount[0], "images", imageCount[0], "checklistItems", checklistCount[0]),
            decoded[0], List.copyOf(errors), notebooks, notes, labels, images, checklistItems, header);
    }

    private Header read(Path input, FieldConsumer consumer) throws IOException {
        Header header = new Header(); Set<String> requiredArrays = new HashSet<>();
        try (JsonParser parser = mapper.getFactory().createParser(input.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw new FormatException("The import root must be an object.");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName(); parser.nextToken();
                switch (field) {
                    case "format" -> header.format = parser.getValueAsString();
                    case "schemaVersion" -> header.schemaVersion = parser.getValueAsInt(-1);
                    case "exportedAt" -> { String value = parser.getValueAsString(); try { header.exportedAt = Instant.parse(value); } catch (Exception ignored) {} }
                    case "defaultNotebookId" -> { if (parser.currentToken() != JsonToken.VALUE_NULL) header.defaultNotebookId = parseUuid(parser.getValueAsString()); }
                    case "scope" -> parseScope(mapper.readTree(parser), header);
                    case "notebooks", "notes", "labels", "images" -> {
                        requiredArrays.add(field);
                        if (parser.currentToken() != JsonToken.START_ARRAY) {
                            throw new FormatException(field + " must be an array.");
                        }
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            if ("images".equals(field)) {
                                DecodedImage image = readImage(parser);
                                try {
                                    consumer.accept(field, image.metadata(), image);
                                } finally {
                                    if (image.content() != null) {
                                        Files.deleteIfExists(image.content());
                                    }
                                }
                            } else {
                                consumer.accept(field, mapper.readTree(parser), null);
                            }
                        }
                    }
                    default -> parser.skipChildren();
                }
            }
        }
        if (requiredArrays.size() != 4) {
            throw new FormatException("notebooks, notes, labels, and images arrays are required.");
        }
        return header;
    }

    public void forEach(Path input, String field, Consumer<JsonNode> consumer) throws IOException {
        read(input, (name, value, image) -> { if (name.equals(field)) consumer.accept(value); });
    }

    public void forEachImage(Path input, Consumer<DecodedImage> consumer) throws IOException {
        read(input, (name, value, image) -> {
            if ("images".equals(name)) {
                consumer.accept(image);
            }
        });
    }

    private DecodedImage readImage(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new FormatException("image must be an object.");
        }
        ObjectNode metadata = mapper.createObjectNode();
        Path content = null;
        long bytes = -1;
        try {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    throw new FormatException("image fields are malformed.");
                }
                String field = parser.currentName();
                JsonToken token = parser.nextToken();
                if ("base64".equals(field)) {
                    if (content != null) {
                        throw new FormatException("image: base64 must occur exactly once.");
                    }
                    if (token != JsonToken.VALUE_STRING) {
                        throw new FormatException("image: base64 must be a string.");
                    }
                    content = Files.createTempFile("glacier-import-image-", ".bin");
                    try (var output = new LimitedOutputStream(
                        Files.newOutputStream(content), limits.maximumImageBytes()
                    )) {
                        parser.readBinaryValue(Base64Variants.MIME_NO_LINEFEEDS, output);
                        bytes = output.count();
                    } catch (IOException | IllegalArgumentException invalid) {
                        throw new FormatException(
                            "image: base64 is invalid or exceeds the image limit.", invalid
                        );
                    }
                } else if (Set.of("id", "mimeType", "fileName").contains(field)) {
                    metadata.set(field, mapper.readTree(parser));
                } else {
                    parser.skipChildren();
                }
            }
            return new DecodedImage(metadata, content, bytes);
        } catch (IOException | RuntimeException failure) {
            if (content != null) {
                try {
                    Files.deleteIfExists(content);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    private void parseScope(JsonNode value, Header header) {
        if (value != null && value.isObject()) {
            header.scopeKind = value.path("kind").isTextual() ? value.path("kind").textValue() : null;
            if ("notebook".equals(header.scopeKind)) header.scopeId = parseUuid(value.path("notebookId").asText(null));
            if ("note".equals(header.scopeKind)) header.scopeId = parseUuid(value.path("noteId").asText(null));
        }
    }

    public void export(UUID owner, String scope, UUID scopeId, Path output, Cancellation cancellation) throws IOException {
        try (Connection connection = dataSource.getConnection();
             JsonGenerator json = mapper.getFactory().createGenerator(Files.newOutputStream(output))) {
            connection.setReadOnly(true); connection.setAutoCommit(false);
            json.writeStartObject();
            json.writeStringField("format", "glacier-notes-export"); json.writeNumberField("schemaVersion", 1);
            json.writeStringField("exportedAt", Instant.now().toString());
            writeNotebooks(connection, json, owner, scope, scopeId, cancellation);
            writeNotes(connection, json, owner, scope, scopeId, cancellation);
            writeLabels(connection, json, owner, scope, scopeId, cancellation);
            writeImages(connection, json, owner, scope, scopeId, cancellation);
            json.writeObjectFieldStart("scope"); json.writeStringField("kind", scope.toLowerCase());
            if (scope.equals("NOTEBOOK")) json.writeStringField("notebookId", scopeId.toString());
            if (scope.equals("NOTE")) json.writeStringField("noteId", scopeId.toString());
            json.writeEndObject();
            if (scope.equals("ALL")) {
                try (PreparedStatement statement = connection.prepareStatement("select id from notebooks where owner_id=? and is_default=true")) {
                    statement.setObject(1, owner); try (ResultSet row = statement.executeQuery()) {
                        if (row.next()) json.writeStringField("defaultNotebookId", row.getObject(1).toString());
                    }
                }
            }
            json.writeObjectFieldStart("cloudMetadata"); json.writeStringField("source", "glacier-notes-cloud");
            json.writeStringField("apiVersion", "v1"); json.writeEndObject(); json.writeEndObject();
            connection.rollback();
        } catch (SQLException failure) { throw new IOException("Could not read export data", failure); }
    }

    private void writeNotebooks(Connection c, JsonGenerator json, UUID owner, String scope, UUID scopeId,
                                Cancellation cancel) throws SQLException, IOException {
        String sql = switch (scope) {
            case "ALL" -> "select id,name,color,created_at,updated_at,sort_order from notebooks where owner_id=? order by sort_order,id";
            case "NOTEBOOK" -> "select id,name,color,created_at,updated_at,sort_order from notebooks where owner_id=? and id=?";
            default -> "select b.id,b.name,b.color,b.created_at,b.updated_at,b.sort_order from notebooks b join notes n on n.owner_id=b.owner_id and n.notebook_id=b.id where b.owner_id=? and n.id=?";
        };
        json.writeArrayFieldStart("notebooks"); rows(c, sql, owner, scopeId, row -> {
            cancel.check(); json.writeStartObject(); string(json, "id", row, 1); string(json, "name", row, 2);
            color(json, row.getString(3)); instant(json, "createdAt", row, 4); instant(json, "updatedAt", row, 5);
            json.writeNumberField("sortOrder", row.getInt(6)); json.writeEndObject();
        }); json.writeEndArray();
    }

    private void writeNotes(Connection c, JsonGenerator json, UUID owner, String scope, UUID scopeId,
                            Cancellation cancel) throws SQLException, IOException {
        String where = scope.equals("ALL") ? "n.owner_id=?" : scope.equals("NOTEBOOK")
            ? "n.owner_id=? and n.notebook_id=?" : "n.owner_id=? and n.id=?";
        String sql = "select n.id,n.notebook_id,n.note_type,n.title,n.content,n.pinned,n.archived,n.color,n.deleted_at,n.created_at,n.updated_at from notes n where " + where + " order by n.created_at,n.id";
        json.writeArrayFieldStart("notes"); rows(c, sql, owner, scopeId, row -> {
            cancel.check(); UUID note = row.getObject(1, UUID.class); json.writeStartObject();
            string(json, "id", row, 1); string(json, "notebookId", row, 2); string(json, "type", row, 3);
            string(json, "title", row, 4); string(json, "content", row, 5);
            if ("checklist".equals(row.getString(3))) writeChecklist(c, json, owner, note);
            writeUuidArray(c, json, "imageIds", "select image_id from note_image_references where owner_id=? and note_id=? order by sort_order,image_id", owner, note);
            json.writeBooleanField("pinned", row.getBoolean(6)); json.writeBooleanField("archived", row.getBoolean(7));
            color(json, row.getString(8));
            writeUuidArray(c, json, "labels", "select label_id from note_labels where owner_id=? and note_id=? order by label_id", owner, note);
            instant(json, "deletedAt", row, 9); instant(json, "createdAt", row, 10); instant(json, "updatedAt", row, 11);
            json.writeEndObject();
        }); json.writeEndArray();
    }

    private void writeChecklist(Connection c, JsonGenerator json, UUID owner, UUID note) throws SQLException, IOException {
        json.writeArrayFieldStart("checklist"); rows(c, "select id,text,checked,sort_order from checklist_items where owner_id=? and note_id=? order by sort_order,id", owner, note, row -> {
            json.writeStartObject(); string(json, "id", row, 1); string(json, "text", row, 2);
            json.writeBooleanField("checked", row.getBoolean(3)); json.writeNumberField("sortOrder", row.getInt(4)); json.writeEndObject();
        }); json.writeEndArray();
    }

    private void writeLabels(Connection c, JsonGenerator json, UUID owner, String scope, UUID scopeId,
                             Cancellation cancel) throws SQLException, IOException {
        String sql = scope.equals("ALL") ? "select id,name from labels where owner_id=? order by name,id" :
            "select distinct l.id,l.name from labels l join note_labels nl on nl.owner_id=l.owner_id and nl.label_id=l.id join notes n on n.owner_id=nl.owner_id and n.id=nl.note_id where l.owner_id=? and " +
                (scope.equals("NOTEBOOK") ? "n.notebook_id=?" : "n.id=?") + " order by l.name,l.id";
        json.writeArrayFieldStart("labels"); rows(c, sql, owner, scopeId, row -> {
            cancel.check(); json.writeStartObject(); string(json, "id", row, 1); string(json, "name", row, 2); json.writeEndObject();
        }); json.writeEndArray();
    }

    private void writeImages(Connection c, JsonGenerator json, UUID owner, String scope, UUID scopeId,
                             Cancellation cancel) throws SQLException, IOException {
        String sql = scope.equals("ALL") ? "select id,mime_type,original_file_name,storage_key,byte_size from image_assets where owner_id=? order by created_at,id" :
            "select distinct i.id,i.mime_type,i.original_file_name,i.storage_key,i.byte_size from image_assets i join note_image_references r on r.owner_id=i.owner_id and r.image_id=i.id join notes n on n.owner_id=r.owner_id and n.id=r.note_id where i.owner_id=? and " +
                (scope.equals("NOTEBOOK") ? "n.notebook_id=?" : "n.id=?") + " order by i.id";
        json.writeArrayFieldStart("images"); rows(c, sql, owner, scopeId, row -> {
            cancel.check(); json.writeStartObject(); string(json, "id", row, 1); string(json, "mimeType", row, 2);
            if (row.getString(3) != null) json.writeStringField("fileName", row.getString(3));
            try (InputStream input = storage.load(row.getString(4)).stream()) {
                json.writeFieldName("base64"); json.writeBinary(Base64Variants.MIME_NO_LINEFEEDS, input, row.getInt(5));
            } json.writeEndObject();
        }); json.writeEndArray();
    }

    private void writeUuidArray(Connection c, JsonGenerator json, String name, String sql, UUID owner, UUID id) throws SQLException, IOException {
        json.writeArrayFieldStart(name); rows(c, sql, owner, id, row -> json.writeString(row.getObject(1).toString())); json.writeEndArray();
    }
    private void rows(Connection c, String sql, UUID owner, UUID id, RowConsumer consumer) throws SQLException, IOException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setFetchSize(100); statement.setObject(1, owner);
            if (sql.chars().filter(value -> value == '?').count() > 1) statement.setObject(2, id);
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) consumer.accept(rows); }
        }
    }
    private void string(JsonGenerator json, String name, ResultSet row, int column) throws SQLException, IOException {
        json.writeStringField(name, row.getString(column));
    }
    private void instant(JsonGenerator json, String name, ResultSet row, int column) throws SQLException, IOException {
        Object value = row.getObject(column); if (value != null) json.writeStringField(name, value.toString());
    }
    private void color(JsonGenerator json, String stored) throws IOException {
        if (stored != null && !stored.equalsIgnoreCase("DEFAULT")) json.writeStringField("color", stored.toLowerCase());
    }

    private UUID uuid(JsonNode node, String field, String object, List<String> errors) {
        String value = node.path(field).isTextual() ? node.path(field).textValue() : null; UUID id = parseUuid(value);
        if (id == null) error(errors, object + ": " + field + " must be a UUID"); return id;
    }
    private UUID parseUuid(String value) {
        if (value == null || !UUID_PATTERN.matcher(value).matches()) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException invalid) { return null; }
    }
    private String text(JsonNode node, String field, boolean required, int max, String object, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) { if (required) error(errors, object + ": " + field + " is required"); return null; }
        if (!value.isTextual()) { error(errors, object + ": " + field + " must be text"); return null; }
        String text = value.textValue(); if (required && field.equals("name") && text.isBlank()) error(errors, object + ": name must not be blank");
        if (text.length() > max) error(errors, object + ": " + field + " is too long"); return text;
    }
    private void bool(JsonNode node, String field, String object, List<String> errors) {
        if (!node.path(field).isBoolean()) error(errors, object + ": " + field + " must be boolean");
    }
    private void integer(JsonNode node, String field, String object, List<String> errors) {
        if (!node.path(field).isIntegralNumber()) error(errors, object + ": " + field + " must be an integer");
    }
    private void date(JsonNode node, String field, boolean required, String object, List<String> errors) {
        JsonNode value = node.get(field); if (value == null || value.isNull()) { if (required) error(errors, object + ": " + field + " is required"); return; }
        try { Instant.parse(value.textValue()); } catch (NullPointerException | DateTimeParseException invalid) { error(errors, object + ": " + field + " is invalid"); }
    }
    private void color(JsonNode node, String object, List<String> errors) {
        JsonNode value = node.get("color"); if (value == null || value.isNull()) return;
        if (!value.isTextual() || !COLORS.contains(value.textValue().toLowerCase())) error(errors, object + ": color is unsupported");
    }
    private Set<UUID> uuidArray(JsonNode node, String field, int max, String object, List<String> errors) {
        var result = new LinkedHashSet<UUID>(); JsonNode value = node.get(field);
        if (value == null || !value.isArray()) { error(errors, object + ": " + field + " must be an array"); return result; }
        if (value.size() > max) error(errors, object + ": too many " + field);
        for (JsonNode item : value) { UUID id = parseUuid(item.isTextual() ? item.textValue() : null); if (id == null) error(errors, object + ": invalid " + field + " UUID"); else if (!result.add(id)) error(errors, object + ": duplicate " + field + " UUID"); }
        return result;
    }
    private void duplicate(Set<UUID> values, UUID id, String type, List<String> errors) { if (id != null && !values.add(id)) error(errors, type + ": duplicate id"); }
    static int encodedBase64Limit(long maximumBytes) {
        if (maximumBytes < 0) {
            throw new IllegalStateException("The maximum image size must not be negative");
        }
        final long encoded;
        try {
            encoded = Math.multiplyExact(Math.addExact(maximumBytes, 2) / 3, 4);
        } catch (ArithmeticException overflow) {
            throw new IllegalStateException(
                "The maximum encoded image size exceeds parser capacity", overflow
            );
        }
        if (encoded > Integer.MAX_VALUE) {
            throw new IllegalStateException("The maximum encoded image size exceeds parser capacity");
        }
        return Math.toIntExact(encoded);
    }
    private void requireLimit(long value, long maximum, String name, List<String> errors) { if (value > maximum) error(errors, name + " exceed the import limit"); }
    private void error(List<String> errors, String value) { if (errors.size() < 100) errors.add(value); }

    public interface Cancellation { void check(); }
    public static final class FormatException extends IOException {
        public FormatException(String message) { super(message); }
        public FormatException(String message, Throwable cause) { super(message, cause); }
    }
    private interface FieldConsumer {
        void accept(String field, JsonNode node, DecodedImage image) throws IOException;
    }
    private interface RowConsumer { void accept(ResultSet row) throws SQLException, IOException; }
    private static final class LimitedOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final long maximum;
        private long count;

        private LimitedOutputStream(OutputStream delegate, long maximum) {
            this.delegate = delegate;
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            delegate.write(value);
            count++;
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            requireCapacity(length);
            delegate.write(values, offset, length);
            count += length;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private long count() {
            return count;
        }

        private void requireCapacity(int additional) throws IOException {
            if (additional < 0 || count > maximum - additional) {
                throw new IOException("Decoded image exceeds the configured limit.");
            }
        }
    }
    private record References(UUID note, UUID notebook, Set<UUID> labels, Set<UUID> images) {}
    public record DecodedImage(JsonNode metadata, Path content, long bytes) {}
    public static final class Header { String format; int schemaVersion = -1; Instant exportedAt; String scopeKind; UUID scopeId; UUID defaultNotebookId; }
    public record Inspection(Map<String, Long> counts, long decodedImageBytes, List<String> errors,
                             Set<UUID> notebookIds, Set<UUID> noteIds, Set<UUID> labelIds,
                             Set<UUID> imageIds, Set<UUID> checklistItemIds, Header header) {}
}
