package com.glaciernotes.cloud.application.content;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.glaciernotes.cloud.domain.IdGenerator;
import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.TimeProvider;
import com.glaciernotes.cloud.domain.note.Note;
import com.glaciernotes.cloud.domain.notebook.Notebook;
import com.glaciernotes.cloud.generated.model.ChecklistItemInput;
import com.glaciernotes.cloud.generated.model.ChecklistItemView;
import com.glaciernotes.cloud.generated.model.ContentColor;
import com.glaciernotes.cloud.generated.model.ContentNote;
import com.glaciernotes.cloud.generated.model.EmptyTrashResult;
import com.glaciernotes.cloud.generated.model.LabelCreate;
import com.glaciernotes.cloud.generated.model.LabelUpdate;
import com.glaciernotes.cloud.generated.model.LabelView;
import com.glaciernotes.cloud.generated.model.NoteConversion;
import com.glaciernotes.cloud.generated.model.NoteCreate;
import com.glaciernotes.cloud.generated.model.NoteMove;
import com.glaciernotes.cloud.generated.model.NotePage;
import com.glaciernotes.cloud.generated.model.NoteSummary;
import com.glaciernotes.cloud.generated.model.NoteType;
import com.glaciernotes.cloud.generated.model.NoteUpdate;
import com.glaciernotes.cloud.generated.model.NoteVersion;
import com.glaciernotes.cloud.generated.model.NoteVersionPage;
import com.glaciernotes.cloud.generated.model.NoteVersionReason;
import com.glaciernotes.cloud.generated.model.NoteVersionSummary;
import com.glaciernotes.cloud.generated.model.NotebookCreate;
import com.glaciernotes.cloud.generated.model.NotebookReorder;
import com.glaciernotes.cloud.generated.model.NotebookUpdate;
import com.glaciernotes.cloud.generated.model.NotebookView;
import com.glaciernotes.cloud.generated.model.PageMetadata;
import com.glaciernotes.cloud.persistence.entity.ChecklistItemEntity;
import com.glaciernotes.cloud.persistence.entity.LabelEntity;
import com.glaciernotes.cloud.persistence.entity.NoteEntity;
import com.glaciernotes.cloud.persistence.entity.NotebookEntity;
import com.glaciernotes.cloud.persistence.entity.TombstoneEntity;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository.CollectionState;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository.Cursor;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository.NoteQuery;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository.SearchCursor;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository.SearchHit;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository.TrashState;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository.VersionCursor;
import com.glaciernotes.cloud.persistence.repository.CoreContentRepository.VersionRow;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class ContentService {
    private static final Pattern CHECKBOX = Pattern.compile("^\\s*[-*+]\\s+\\[([ xX])]\\s*(.*)$");
    private static final Pattern IMAGE_REFERENCE = Pattern.compile("glacier-img://([0-9a-fA-F-]{36})");
    private static final int PREVIEW_LENGTH = 240;

    private final CoreContentRepository repository;
    private final IdGenerator ids;
    private final TimeProvider clock;
    private final ObjectMapper objectMapper;

    public ContentService(CoreContentRepository repository, IdGenerator ids, TimeProvider clock,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.ids = ids;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public List<NotebookView> listNotebooks(OwnerId ownerId) {
        return repository.notebooks(ownerId, false).stream()
            .map(notebook -> notebookView(ownerId, notebook)).toList();
    }

    public NotebookView getNotebook(OwnerId ownerId, UUID notebookId) {
        return notebookView(ownerId, requireNotebook(ownerId, notebookId, false));
    }

    public NotebookView getDefaultNotebook(OwnerId ownerId) {
        return notebookView(ownerId, repository.defaultNotebook(ownerId, false)
            .orElseThrow(ContentFailure::notFound));
    }

    @Transactional
    public NotebookView createNotebook(OwnerId ownerId, NotebookCreate request) {
        String name = cleanName("name", request.getName());
        Instant now = clock.now();
        int sortOrder = repository.notebooks(ownerId, true).stream()
            .mapToInt(NotebookEntity::sortOrder).max().orElse(-1) + 1;
        NotebookEntity entity = new NotebookEntity(new Notebook(
            ownerId, ids.nextId(), name, storedColor(request.getColor()), false, sortOrder,
            now, now, 0
        ));
        repository.persistNotebook(ownerId, entity);
        repository.flush(ownerId);
        return notebookView(ownerId, entity);
    }

    @Transactional
    public NotebookView updateNotebook(OwnerId ownerId, UUID notebookId, NotebookUpdate request) {
        NotebookEntity entity = requireNotebook(ownerId, notebookId, true);
        checkVersion(entity.version(), request.getVersion());
        String name = request.getName() == null ? entity.name() : cleanName("name", request.getName());
        String color = request.getColor() == null ? entity.color() : storedColor(request.getColor());
        entity.change(name, color, clock.now());
        repository.flush(ownerId);
        return notebookView(ownerId, entity);
    }

    @Transactional
    public List<NotebookView> reorderNotebooks(OwnerId ownerId, NotebookReorder request) {
        List<NotebookEntity> notebooks = repository.notebooks(ownerId, true);
        if (request.getNotebooks().size() != notebooks.size()) {
            throw ContentFailure.invalid("notebooks", "Must contain every notebook exactly once.");
        }
        Map<UUID, NotebookEntity> byId = new HashMap<>();
        notebooks.forEach(notebook -> byId.put(notebook.key().id(), notebook));
        Set<UUID> seen = new HashSet<>();
        Instant now = clock.now();
        for (int position = 0; position < request.getNotebooks().size(); position++) {
            var entry = request.getNotebooks().get(position);
            NotebookEntity notebook = byId.get(entry.getId());
            if (notebook == null || !seen.add(entry.getId())) {
                throw ContentFailure.invalid("notebooks", "Must contain every notebook exactly once.");
            }
            checkVersion(notebook.version(), entry.getVersion());
            notebook.reorder(position, now);
        }
        repository.flush(ownerId);
        return notebooks.stream().sorted((left, right) -> Integer.compare(left.sortOrder(), right.sortOrder()))
            .map(notebook -> notebookView(ownerId, notebook)).toList();
    }

    @Transactional
    public void deleteNotebook(OwnerId ownerId, UUID notebookId, String strategy, long version) {
        NotebookEntity notebook = requireNotebook(ownerId, notebookId, true);
        checkVersion(notebook.version(), version);
        if (notebook.defaultNotebook()) {
            throw ContentFailure.conflict("The default notebook cannot be deleted.");
        }
        boolean trash = switch (strategy == null ? "" : strategy.toUpperCase(Locale.ROOT)) {
            case "MOVE_TO_DEFAULT" -> false;
            case "TRASH_NOTES" -> true;
            default -> throw ContentFailure.invalid("strategy", "Must be MOVE_TO_DEFAULT or TRASH_NOTES.");
        };
        NotebookEntity defaultNotebook = repository.defaultNotebook(ownerId, true)
            .orElseThrow(ContentFailure::notFound);
        Instant now = clock.now();
        repository.moveNotebookNotes(ownerId, notebookId, defaultNotebook.key().id(), trash, now);
        tombstone(ownerId, "NOTEBOOK", notebookId, notebook.version(), now);
        repository.removeNotebook(ownerId, notebook);
        repository.flush(ownerId);
    }

    public List<LabelView> listLabels(OwnerId ownerId) {
        return repository.labels(ownerId, false).stream().map(this::labelView).toList();
    }

    @Transactional
    public LabelView createLabel(OwnerId ownerId, LabelCreate request) {
        String name = cleanName("name", request.getName());
        String normalized = normalize(name);
        if (repository.labelByNormalizedName(ownerId, normalized).isPresent()) {
            throw ContentFailure.conflict("A label with that name already exists.");
        }
        Instant now = clock.now();
        LabelEntity entity = new LabelEntity(ownerId.value(), ids.nextId(), name, normalized, now);
        repository.persistLabel(ownerId, entity);
        repository.flush(ownerId);
        return labelView(entity);
    }

    @Transactional
    public LabelView updateLabel(OwnerId ownerId, UUID labelId, LabelUpdate request) {
        LabelEntity label = requireLabel(ownerId, labelId, true);
        checkVersion(label.version(), request.getVersion());
        String name = cleanName("name", request.getName());
        String normalized = normalize(name);
        repository.labelByNormalizedName(ownerId, normalized)
            .filter(other -> !other.id().equals(labelId))
            .ifPresent(other -> { throw ContentFailure.conflict("A label with that name already exists."); });
        label.rename(name, normalized, clock.now());
        repository.flush(ownerId);
        return labelView(label);
    }

    @Transactional
    public void deleteLabel(OwnerId ownerId, UUID labelId, long version) {
        LabelEntity label = requireLabel(ownerId, labelId, true);
        checkVersion(label.version(), version);
        tombstone(ownerId, "LABEL", labelId, label.version(), clock.now());
        repository.removeLabel(ownerId, label);
        repository.flush(ownerId);
    }

    public ContentNote getNote(OwnerId ownerId, UUID noteId) {
        return contentNote(ownerId, requireNote(ownerId, noteId, false));
    }

    public NoteVersionPage listNoteVersions(OwnerId ownerId, UUID noteId, String cursor, int limit) {
        requireNote(ownerId, noteId, false);
        VersionCursor decoded = decodeVersionCursor(cursor);
        List<VersionRow> rows = repository.versions(ownerId, noteId, decoded, limit + 1);
        boolean hasNext = rows.size() > limit;
        if (hasNext) rows = new ArrayList<>(rows.subList(0, limit));
        List<NoteVersionSummary> items = rows.stream().map(row -> versionSummary(noteId, row)).toList();
        PageMetadata page = new PageMetadata().size(items.size()).hasNext(hasNext);
        if (hasNext) page.nextCursor(encodeVersionCursor(rows.getLast()));
        return new NoteVersionPage().items(items).page(page);
    }

    public NoteVersion getNoteVersion(OwnerId ownerId, UUID noteId, UUID versionId) {
        requireNote(ownerId, noteId, false);
        return versionModel(noteId, repository.version(ownerId, noteId, versionId)
            .orElseThrow(ContentFailure::notFound));
    }

    @Transactional
    public void snapshotNoteVersion(OwnerId ownerId, UUID noteId, long version) {
        NoteEntity note = requireNote(ownerId, noteId, true);
        checkNoteVersion(ownerId, note, version);
        snapshot(ownerId, note, "EDITOR_CLOSE", clock.now());
    }

    @Transactional
    public void recordConflictSnapshot(OwnerId ownerId, UUID noteId) {
        repository.note(ownerId, noteId, true)
            .ifPresent(note -> snapshot(ownerId, note, "CONFLICT", clock.now()));
    }

    @Transactional
    public ContentNote restoreNoteVersion(OwnerId ownerId, UUID noteId, UUID versionId, long version) {
        NoteEntity note = requireNote(ownerId, noteId, true);
        checkNoteVersion(ownerId, note, version);
        VersionRow row = repository.version(ownerId, noteId, versionId)
            .orElseThrow(ContentFailure::notFound);
        SnapshotPayload target = payload(row);
        Instant now = clock.now();
        snapshot(ownerId, note, "VERSION_RESTORE", now);
        List<UUID> images = requireImages(ownerId, target.imageIds(), target.content());
        Set<UUID> labels = new LinkedHashSet<>();
        for (UUID labelId : target.labelIds()) {
            if (repository.label(ownerId, labelId, false).isPresent()) labels.add(labelId);
        }
        List<ChecklistItemInput> checklist = target.checklistItems().stream()
            .map(item -> new ChecklistItemInput(item.text(), item.checked())).toList();
        note.restoreEditable(target.noteType(), target.title(), target.content(), target.pinned(),
            target.archived(), target.color(), now);
        replaceChecklist(ownerId, note, checklist, now);
        repository.replaceNoteLabels(ownerId, noteId, labels);
        repository.replaceNoteImages(ownerId, noteId, images, now);
        repository.flush(ownerId);
        return contentNote(ownerId, note);
    }

    @Transactional
    public void cleanNoteHistory() {
        if (!repository.acquireHistoryCleanupLock()) return;
        var settings = repository.settings();
        Instant now = clock.now();
        var expired = repository.expiredVersions(settings.noteVersionMaximumCount(),
            now.minus(settings.noteVersionRetentionDays(), ChronoUnit.DAYS), 500);
        for (var key : expired) {
            List<UUID> images = repository.versionImages(key.ownerId(), key.id());
            repository.deleteVersion(key.ownerId(), key.id());
            repository.markImagesOrphaned(key.ownerId(), images, now);
        }
    }

    public NotePage listNotes(OwnerId ownerId, UUID notebookId, UUID labelId, NoteType noteType,
                              Boolean pinned, String archive, String trash, String cursor, int limit) {
        if (notebookId != null) requireNotebook(ownerId, notebookId, false);
        if (labelId != null) requireLabel(ownerId, labelId, false);
        CollectionState archiveState = enumValue(CollectionState.class, archive, "archive");
        TrashState trashState = enumValue(TrashState.class, trash, "trash");
        Cursor decoded = decodeCursor(cursor);
        List<NoteEntity> entities = repository.notes(ownerId,
            new NoteQuery(notebookId, labelId,
                noteType == null ? null : noteType.toString().toLowerCase(Locale.ROOT), pinned,
                archiveState, trashState), decoded, limit + 1);
        boolean hasNext = entities.size() > limit;
        if (hasNext) entities = new ArrayList<>(entities.subList(0, limit));
        List<NoteSummary> items = entities.stream().map(note -> noteSummary(ownerId, note)).toList();
        PageMetadata page = new PageMetadata().size(items.size()).hasNext(hasNext);
        if (hasNext) page.nextCursor(encodeCursor(entities.get(entities.size() - 1)));
        return new NotePage().items(items).page(page);
    }

    public NotePage searchNotes(OwnerId ownerId, String query, UUID notebookId, UUID labelId,
                                NoteType noteType, Boolean pinned, String archive, String trash,
                                String cursor, int limit) {
        String normalized = query == null ? "" : query.strip();
        if (normalized.isEmpty()) {
            return new NotePage().items(List.of()).page(new PageMetadata().size(0).hasNext(false));
        }
        if (notebookId != null) requireNotebook(ownerId, notebookId, false);
        if (labelId != null) requireLabel(ownerId, labelId, false);
        CollectionState archiveState = enumValue(CollectionState.class,
            archive == null ? "ALL" : archive, "archive");
        TrashState trashState = enumValue(TrashState.class,
            trash == null ? "ACTIVE" : trash, "trash");
        if (trashState == TrashState.ALL) throw ContentFailure.invalid("trash", "Search supports ACTIVE or TRASHED trash scope.");
        String fingerprint = searchFingerprint(normalized, notebookId, labelId, noteType, pinned,
            archiveState, trashState);
        SearchCursor decoded = decodeSearchCursor(cursor, fingerprint);
        List<SearchHit> hits = repository.searchNotes(ownerId, normalized,
            new NoteQuery(notebookId, labelId,
                noteType == null ? null : noteType.toString().toLowerCase(Locale.ROOT), pinned,
                archiveState, trashState), decoded, limit + 1);
        boolean hasNext = hits.size() > limit;
        if (hasNext) hits = new ArrayList<>(hits.subList(0, limit));
        List<NoteSummary> items = hits.stream().map(hit -> noteSummary(ownerId, hit.note())).toList();
        PageMetadata page = new PageMetadata().size(items.size()).hasNext(hasNext);
        if (hasNext) page.nextCursor(encodeSearchCursor(hits.getLast(), fingerprint));
        return new NotePage().items(items).page(page);
    }

    @Transactional
    public ContentNote createNote(OwnerId ownerId, NoteCreate request) {
        UUID noteId = request.getId() == null ? ids.nextId() : request.getId();
        if (repository.note(ownerId, noteId, false).isPresent()
            || repository.hasTombstone(ownerId, "NOTE", noteId)) {
            throw ContentFailure.conflict("A note with that id already exists.");
        }
        UUID notebookId = request.getNotebookId() == null
            ? repository.defaultNotebook(ownerId, false).orElseThrow(ContentFailure::notFound).key().id()
            : requireNotebook(ownerId, request.getNotebookId(), false).key().id();
        List<ChecklistItemInput> items = safe(request.getChecklistItems());
        String content = request.getContent() == null ? "" : request.getContent();
        validateBody(request.getNoteType(), content, items);
        Set<UUID> labels = requireLabels(ownerId, safe(request.getLabelIds()));
        List<UUID> images = requireImages(ownerId, safe(request.getImageIds()), content);
        Instant now = clock.now();
        NoteEntity entity = new NoteEntity(new Note(
            ownerId, noteId, notebookId, storedType(request.getNoteType()),
            request.getTitle() == null ? "" : request.getTitle(), content,
            Boolean.TRUE.equals(request.getPinned()), Boolean.TRUE.equals(request.getArchived()),
            storedColor(request.getColor()), null, now, now, 0
        ));
        repository.persistNote(ownerId, entity);
        replaceChecklist(ownerId, entity, items, now);
        repository.replaceNoteLabels(ownerId, noteId, labels);
        repository.replaceNoteImages(ownerId, noteId, images, now);
        repository.flush(ownerId);
        return contentNote(ownerId, entity);
    }

    @Transactional
    public ContentNote updateNote(OwnerId ownerId, UUID noteId, NoteUpdate request) {
        NoteEntity entity = requireNote(ownerId, noteId, true);
        checkNoteVersion(ownerId, entity, request.getVersion());
        NoteType type = apiType(entity.type());
        validateBody(type, request.getContent(), request.getChecklistItems());
        Set<UUID> labels = requireLabels(ownerId, request.getLabelIds());
        List<UUID> images = requireImages(ownerId,
            request.getImageIds() == null ? repository.noteImageIds(ownerId, noteId) : request.getImageIds(),
            request.getContent());
        SnapshotPayload current = snapshotPayload(ownerId, entity);
        SnapshotPayload target = new SnapshotPayload(1, entity.type(), request.getTitle(), request.getContent(),
            checklistPayload(request.getChecklistItems()), request.getPinned(), request.getArchived(),
            storedColor(request.getColor()), sorted(labels), images);
        if (hash(current).equals(hash(target))) return contentNote(ownerId, entity);
        Instant now = clock.now();
        entity.replace(request.getTitle(), request.getContent(), request.getPinned(), request.getArchived(),
            storedColor(request.getColor()), now);
        replaceChecklist(ownerId, entity, request.getChecklistItems(), now);
        repository.replaceNoteLabels(ownerId, noteId, labels);
        repository.replaceNoteImages(ownerId, noteId, images, now);
        repository.flush(ownerId);
        maybePeriodicSnapshot(ownerId, entity, now);
        return contentNote(ownerId, entity);
    }

    @Transactional
    public ContentNote moveNote(OwnerId ownerId, UUID noteId, NoteMove request) {
        NoteEntity note = requireNote(ownerId, noteId, true);
        checkNoteVersion(ownerId, note, request.getVersion());
        requireNotebook(ownerId, request.getNotebookId(), false);
        note.move(request.getNotebookId(), clock.now());
        repository.flush(ownerId);
        return contentNote(ownerId, note);
    }

    @Transactional
    public ContentNote trashNote(OwnerId ownerId, UUID noteId, long version) {
        NoteEntity note = requireNote(ownerId, noteId, true);
        checkNoteVersion(ownerId, note, version);
        if (note.deletedAt() != null) throw ContentFailure.invalidState("The note is already in trash.");
        note.trash(clock.now());
        repository.flush(ownerId);
        return contentNote(ownerId, note);
    }

    @Transactional
    public ContentNote restoreNote(OwnerId ownerId, UUID noteId, long version) {
        NoteEntity note = requireNote(ownerId, noteId, true);
        checkNoteVersion(ownerId, note, version);
        if (note.deletedAt() == null) throw ContentFailure.invalidState("The note is not in trash.");
        Instant now = clock.now();
        snapshot(ownerId, note, "TRASH_RESTORE", now);
        note.restore(now);
        repository.flush(ownerId);
        return contentNote(ownerId, note);
    }

    @Transactional
    public void purgeNote(OwnerId ownerId, UUID noteId, long version) {
        NoteEntity note = requireNote(ownerId, noteId, true);
        checkNoteVersion(ownerId, note, version);
        if (note.deletedAt() == null) throw ContentFailure.invalidState("Only trashed notes can be purged.");
        purge(ownerId, note, clock.now());
        repository.flush(ownerId);
    }

    @Transactional
    public EmptyTrashResult emptyTrash(OwnerId ownerId) {
        List<NoteEntity> notes = repository.trashedNotes(ownerId, true);
        Instant now = clock.now();
        notes.forEach(note -> purge(ownerId, note, now));
        repository.flush(ownerId);
        return new EmptyTrashResult().purgedCount((long) notes.size());
    }

    @Transactional
    public ContentNote convertNote(OwnerId ownerId, UUID noteId, NoteConversion request) {
        NoteEntity note = requireNote(ownerId, noteId, true);
        checkNoteVersion(ownerId, note, request.getVersion());
        NoteType current = apiType(note.type());
        if (current == request.getTargetType()) {
            throw ContentFailure.invalidState("The note already has the requested type.");
        }
        Instant now = clock.now();
        if (request.getTargetType() == NoteType.CHECKLIST) {
            List<ChecklistItemInput> items = markdownItems(note.content());
            note.convert("checklist", "", now);
            replaceChecklist(ownerId, note, items, now);
        } else {
            List<ChecklistItemEntity> items = repository.checklistItems(ownerId, noteId, true);
            String markdown = items.stream()
                .map(item -> "- [" + (item.checked() ? "x" : " ") + "] " + item.text())
                .reduce((left, right) -> left + "\n" + right).orElse("");
            for (ChecklistItemEntity item : items) {
                tombstone(ownerId, "CHECKLIST_ITEM", item.id(), item.version(), now);
                repository.removeChecklistItem(ownerId, item);
            }
            note.convert("text", markdown, now);
        }
        repository.flush(ownerId);
        maybePeriodicSnapshot(ownerId, note, now);
        return contentNote(ownerId, note);
    }

    private void replaceChecklist(OwnerId ownerId, NoteEntity note, List<ChecklistItemInput> requested,
                                  Instant now) {
        List<ChecklistItemEntity> existing = repository.checklistItems(ownerId, note.id(), true);
        Map<UUID, ChecklistItemEntity> byId = new HashMap<>();
        existing.forEach(item -> byId.put(item.id(), item));
        Set<UUID> retained = new HashSet<>();
        for (int index = 0; index < requested.size(); index++) {
            ChecklistItemInput input = requested.get(index);
            UUID id = input.getId() == null ? ids.nextId() : input.getId();
            if (!retained.add(id)) {
                throw ContentFailure.invalid("checklistItems", "Checklist item ids must be unique.");
            }
            ChecklistItemEntity item = byId.get(id);
            if (item == null) {
                if (repository.checklistItem(ownerId, id).isPresent()
                    || repository.hasTombstone(ownerId, "CHECKLIST_ITEM", id)) {
                    throw ContentFailure.invalid("checklistItems", "A checklist item id belongs to another note.");
                }
                repository.persistChecklistItem(ownerId, new ChecklistItemEntity(
                    ownerId.value(), id, note.id(), input.getText(), input.getChecked(), index, now
                ));
            } else {
                item.update(input.getText(), input.getChecked(), index, now);
            }
        }
        for (ChecklistItemEntity item : existing) {
            if (!retained.contains(item.id())) {
                tombstone(ownerId, "CHECKLIST_ITEM", item.id(), item.version(), now);
                repository.removeChecklistItem(ownerId, item);
            }
        }
    }

    private void purge(OwnerId ownerId, NoteEntity note, Instant now) {
        List<UUID> imageCandidates = repository.allNoteImages(ownerId, note.id());
        for (ChecklistItemEntity item : repository.checklistItems(ownerId, note.id(), true)) {
            tombstone(ownerId, "CHECKLIST_ITEM", item.id(), item.version(), now);
        }
        repository.replaceNoteImages(ownerId, note.id(), List.of(), now);
        tombstone(ownerId, "NOTE", note.id(), note.version(), now);
        repository.removeNote(ownerId, note);
        repository.flush(ownerId);
        repository.markImagesOrphaned(ownerId.value(), imageCandidates, now);
    }

    private void tombstone(OwnerId ownerId, String type, UUID entityId, long version, Instant now) {
        repository.persistTombstone(ownerId, new TombstoneEntity(
            ids.nextId(), ownerId.value(), type, entityId, now, now.plus(30, ChronoUnit.DAYS), version
        ));
    }

    private NotebookEntity requireNotebook(OwnerId ownerId, UUID id, boolean lock) {
        return repository.notebook(ownerId, id, lock).orElseThrow(ContentFailure::notFound);
    }

    private NoteEntity requireNote(OwnerId ownerId, UUID id, boolean lock) {
        return repository.note(ownerId, id, lock).orElseThrow(ContentFailure::notFound);
    }

    private LabelEntity requireLabel(OwnerId ownerId, UUID id, boolean lock) {
        return repository.label(ownerId, id, lock).orElseThrow(ContentFailure::notFound);
    }

    private Set<UUID> requireLabels(OwnerId ownerId, List<UUID> ids) {
        Set<UUID> distinct = new LinkedHashSet<>(ids);
        if (distinct.size() != ids.size()) {
            throw ContentFailure.invalid("labelIds", "Label ids must be unique.");
        }
        distinct.forEach(id -> requireLabel(ownerId, id, false));
        return distinct;
    }

    private List<UUID> requireImages(OwnerId ownerId, List<UUID> explicit, String content) {
        LinkedHashSet<UUID> ordered = new LinkedHashSet<>(explicit);
        var matcher = IMAGE_REFERENCE.matcher(content == null ? "" : content);
        while (matcher.find()) {
            try { ordered.add(UUID.fromString(matcher.group(1))); }
            catch (IllegalArgumentException ignored) { /* malformed references remain plain text */ }
        }
        if (ordered.size() > 500) throw ContentFailure.invalid("imageIds", "A note may reference at most 500 images.");
        for (UUID imageId : ordered) {
            if (!repository.ownedImageExists(ownerId, imageId)) throw ContentFailure.invalid("imageIds", "An image does not exist or is not owned by this user.");
        }
        return List.copyOf(ordered);
    }

    private NotebookView notebookView(OwnerId ownerId, NotebookEntity entity) {
        return new NotebookView().id(entity.key().id()).name(entity.name()).color(apiColor(entity.color()))
            .defaultNotebook(entity.defaultNotebook()).sortOrder(entity.sortOrder())
            .noteCount(repository.notebookNoteCount(ownerId, entity.key().id()))
            .createdAt(offset(entity.createdAt())).updatedAt(offset(entity.updatedAt()))
            .version(entity.version());
    }

    private LabelView labelView(LabelEntity entity) {
        return new LabelView().id(entity.id()).name(entity.name()).createdAt(offset(entity.createdAt()))
            .updatedAt(offset(entity.updatedAt())).version(entity.version());
    }

    private ContentNote contentNote(OwnerId ownerId, NoteEntity entity) {
        List<ChecklistItemView> items = repository.checklistItems(ownerId, entity.id(), false).stream()
            .map(this::checklistView).toList();
        return new ContentNote().id(entity.id()).notebookId(entity.notebookId())
            .noteType(apiType(entity.type())).title(entity.title()).content(entity.content())
            .checklistItems(items).pinned(entity.pinned()).archived(entity.archived())
            .color(apiColor(entity.color())).labelIds(repository.noteLabelIds(ownerId, entity.id()))
            .imageIds(repository.noteImageIds(ownerId, entity.id()))
            .deletedAt(offset(entity.deletedAt())).createdAt(offset(entity.createdAt()))
            .updatedAt(offset(entity.updatedAt())).version(entity.version());
    }

    private NoteSummary noteSummary(OwnerId ownerId, NoteEntity entity) {
        List<ChecklistItemView> checklist = repository.checklistItems(ownerId, entity.id(), false).stream()
            .limit(5).map(this::checklistView).toList();
        return new NoteSummary().id(entity.id()).notebookId(entity.notebookId())
            .noteType(apiType(entity.type())).title(entity.title()).preview(preview(entity.content()))
            .checklistPreview(checklist).pinned(entity.pinned()).archived(entity.archived())
            .color(apiColor(entity.color())).labelIds(repository.noteLabelIds(ownerId, entity.id()))
            .imageIds(repository.noteImageIds(ownerId, entity.id()))
            .deletedAt(offset(entity.deletedAt())).createdAt(offset(entity.createdAt()))
            .updatedAt(offset(entity.updatedAt())).version(entity.version());
    }

    private ChecklistItemView checklistView(ChecklistItemEntity item) {
        return new ChecklistItemView().id(item.id()).text(item.text()).checked(item.checked())
            .sortOrder(item.sortOrder()).createdAt(offset(item.createdAt()))
            .updatedAt(offset(item.updatedAt())).version(item.version());
    }

    private static void validateBody(NoteType type, String content, List<ChecklistItemInput> items) {
        if (type == NoteType.TEXT && !items.isEmpty()) {
            throw ContentFailure.invalid("checklistItems", "Text notes cannot contain checklist items.");
        }
        if (type == NoteType.CHECKLIST && content != null && !content.isBlank()) {
            throw ContentFailure.invalid("content", "Checklist notes cannot contain text content.");
        }
    }

    private static List<ChecklistItemInput> markdownItems(String markdown) {
        List<ChecklistItemInput> result = new ArrayList<>();
        for (String line : markdown.split("\\R")) {
            if (line.isBlank()) continue;
            var matcher = CHECKBOX.matcher(line);
            if (matcher.matches()) {
                result.add(new ChecklistItemInput(matcher.group(2), !matcher.group(1).isBlank()));
            } else {
                result.add(new ChecklistItemInput(line.strip(), false));
            }
        }
        return result;
    }

    private static String preview(String value) {
        int count = value.codePointCount(0, value.length());
        if (count <= PREVIEW_LENGTH) return value;
        return value.substring(0, value.offsetByCodePoints(0, PREVIEW_LENGTH));
    }

    private static String cleanName(String field, String value) {
        String clean = value == null ? "" : value.strip();
        if (clean.isEmpty()) throw ContentFailure.invalid(field, "Must not be blank.");
        if (clean.length() > 255) throw ContentFailure.invalid(field, "Must contain at most 255 characters.");
        return clean;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private void maybePeriodicSnapshot(OwnerId ownerId, NoteEntity note, Instant now) {
        Instant last = repository.lastSnapshotAt(ownerId, note.id());
        Instant baseline = last == null ? note.createdAt() : last;
        if (!baseline.isAfter(now.minus(5, ChronoUnit.MINUTES))) {
            snapshot(ownerId, note, "PERIODIC", now);
        }
    }

    private boolean snapshot(OwnerId ownerId, NoteEntity note, String reason, Instant now) {
        SnapshotPayload payload = snapshotPayload(ownerId, note);
        String json = json(payload);
        String contentHash = digest(json);
        if (contentHash.equals(repository.latestSnapshotHash(ownerId, note.id()))) return false;
        repository.insertVersion(ownerId, ids.nextId(), note.id(), note.version(), reason, now,
            json, contentHash, payload.imageIds());
        repository.markSnapshot(ownerId, note.id(), now);
        return true;
    }

    private SnapshotPayload snapshotPayload(OwnerId ownerId, NoteEntity note) {
        return new SnapshotPayload(1, note.type(), note.title(), note.content(),
            repository.checklistItems(ownerId, note.id(), false).stream()
                .map(item -> new SnapshotChecklistItem(item.text(), item.checked())).toList(),
            note.pinned(), note.archived(), note.color(),
            sorted(repository.noteLabelIds(ownerId, note.id())),
            repository.noteImageIds(ownerId, note.id()));
    }

    private static List<SnapshotChecklistItem> checklistPayload(List<ChecklistItemInput> items) {
        return items.stream().map(item -> new SnapshotChecklistItem(item.getText(), item.getChecked())).toList();
    }

    private static List<UUID> sorted(Iterable<UUID> values) {
        List<UUID> result = new ArrayList<>();
        values.forEach(result::add);
        result.sort(UUID::compareTo);
        return result;
    }

    private String json(SnapshotPayload payload) {
        try { return objectMapper.writeValueAsString(payload); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not serialize note history", exception); }
    }

    private SnapshotPayload payload(VersionRow row) {
        try { return objectMapper.readValue(row.payload(), SnapshotPayload.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not read note history", exception); }
    }

    private static String hash(SnapshotPayload payload) {
        StringBuilder value = new StringBuilder().append(payload.schemaVersion()).append('|')
            .append(payload.noteType()).append('|').append(payload.title()).append('|')
            .append(payload.content()).append('|').append(payload.pinned()).append('|')
            .append(payload.archived()).append('|').append(payload.color()).append('|')
            .append(payload.labelIds()).append('|').append(payload.imageIds());
        payload.checklistItems().forEach(item -> value.append('|').append(item.checked()).append(':').append(item.text()));
        return digest(value.toString());
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private NoteVersionSummary versionSummary(UUID noteId, VersionRow row) {
        SnapshotPayload value = payload(row);
        return new NoteVersionSummary().id(row.id()).noteId(noteId).sourceVersion(row.sourceVersion())
            .reason(NoteVersionReason.fromValue(row.reason())).snapshotAt(offset(row.snapshotAt()))
            .noteType(apiType(value.noteType())).title(value.title());
    }

    private NoteVersion versionModel(UUID noteId, VersionRow row) {
        SnapshotPayload value = payload(row);
        return new NoteVersion().id(row.id()).noteId(noteId).sourceVersion(row.sourceVersion())
            .reason(NoteVersionReason.fromValue(row.reason())).snapshotAt(offset(row.snapshotAt()))
            .noteType(apiType(value.noteType())).title(value.title()).content(value.content())
            .checklistItems(value.checklistItems().stream()
                .map(item -> new ChecklistItemInput(item.text(), item.checked())).toList())
            .pinned(value.pinned()).archived(value.archived()).color(apiColor(value.color()))
            .labelIds(value.labelIds()).imageIds(value.imageIds());
    }

    private static String searchFingerprint(String query, UUID notebookId, UUID labelId,
                                            NoteType noteType, Boolean pinned,
                                            CollectionState archive, TrashState trash) {
        return digest(String.join("|", query, String.valueOf(notebookId), String.valueOf(labelId),
            String.valueOf(noteType), String.valueOf(pinned), archive.name(), trash.name()));
    }

    private static String encodeSearchCursor(SearchHit hit, String fingerprint) {
        String value = hit.rank() + "|" + hit.note().updatedAt() + "|" + hit.note().id() + "|" + fingerprint;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static SearchCursor decodeSearchCursor(String cursor, String fingerprint) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String[] pieces = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                .split("\\|", -1);
            if (pieces.length != 4 || !pieces[3].equals(fingerprint)) throw new IllegalArgumentException();
            return new SearchCursor(Float.parseFloat(pieces[0]), Instant.parse(pieces[1]), UUID.fromString(pieces[2]));
        } catch (RuntimeException exception) {
            throw ContentFailure.invalid("cursor", "The search cursor is invalid for these filters.");
        }
    }

    private static String encodeVersionCursor(VersionRow row) {
        String value = row.snapshotAt() + "|" + row.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static VersionCursor decodeVersionCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String[] pieces = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                .split("\\|", -1);
            if (pieces.length != 2) throw new IllegalArgumentException();
            return new VersionCursor(Instant.parse(pieces[0]), UUID.fromString(pieces[1]));
        } catch (RuntimeException exception) {
            throw ContentFailure.invalid("cursor", "The history cursor is invalid.");
        }
    }

    private static void checkNoteVersion(OwnerId ownerId, NoteEntity note, Long supplied) {
        if (supplied == null || note.version() != supplied) {
            throw ContentFailure.noteVersion(ownerId, note.id(), note.version(), note.updatedAt());
        }
    }

    private static void checkVersion(long current, Long supplied) {
        if (supplied == null || current != supplied) throw ContentFailure.version(current);
    }

    private static String storedColor(ContentColor color) {
        return color == null || color == ContentColor.DEFAULT ? null : color.toString();
    }

    private static ContentColor apiColor(String color) {
        return color == null ? ContentColor.DEFAULT : ContentColor.fromValue(color);
    }

    private static String storedType(NoteType type) {
        return type.toString().toLowerCase(Locale.ROOT);
    }

    private static NoteType apiType(String type) {
        return NoteType.fromValue(type.toUpperCase(Locale.ROOT));
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static String encodeCursor(NoteEntity note) {
        String value = (note.pinned() ? "1" : "0") + "|" + note.updatedAt() + "|" + note.id();
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] pieces = decoded.split("\\|", -1);
            if (pieces.length != 3 || !(pieces[0].equals("0") || pieces[0].equals("1"))) throw new Exception();
            return new Cursor(pieces[0].equals("1"), Instant.parse(pieces[1]), UUID.fromString(pieces[2]));
        } catch (Exception exception) {
            throw ContentFailure.invalid("cursor", "The pagination cursor is invalid.");
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value == null ? "ACTIVE" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ContentFailure.invalid(field, "Contains an unsupported filter value.");
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record SnapshotPayload(int schemaVersion, String noteType, String title, String content,
                                   List<SnapshotChecklistItem> checklistItems, boolean pinned,
                                   boolean archived, String color, List<UUID> labelIds,
                                   List<UUID> imageIds) {}
    private record SnapshotChecklistItem(String text, boolean checked) {}
}
