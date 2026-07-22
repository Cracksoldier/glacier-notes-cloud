package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.application.auth.AuthenticationFailure;
import com.glaciernotes.cloud.application.auth.SessionView;
import com.glaciernotes.cloud.application.content.ContentService;
import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.generated.api.NotesApi;
import com.glaciernotes.cloud.generated.model.ContentNote;
import com.glaciernotes.cloud.generated.model.EmptyTrashResult;
import com.glaciernotes.cloud.generated.model.NoteConversion;
import com.glaciernotes.cloud.generated.model.NoteCreate;
import com.glaciernotes.cloud.generated.model.NoteMove;
import com.glaciernotes.cloud.generated.model.NotePage;
import com.glaciernotes.cloud.generated.model.NoteType;
import com.glaciernotes.cloud.generated.model.NoteUpdate;
import com.glaciernotes.cloud.generated.model.NoteVersion;
import com.glaciernotes.cloud.generated.model.NoteVersionPage;
import com.glaciernotes.cloud.generated.model.VersionRequest;
import com.glaciernotes.cloud.security.AuthenticationIdentity;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
@RolesAllowed({"USER", "ADMIN"})
public class NotesResource implements NotesApi {
    private final ContentService content;
    private final SecurityIdentity identity;

    public NotesResource(ContentService content, SecurityIdentity identity) {
        this.content = content;
        this.identity = identity;
    }

    @Override
    public ContentNote convertNote(UUID noteId, NoteConversion request) {
        return content.convertNote(owner(), noteId, request);
    }

    @Override
    public ContentNote createNote(NoteCreate request) {
        return content.createNote(owner(), request);
    }

    @Override
    public EmptyTrashResult emptyTrash() {
        return content.emptyTrash(owner());
    }

    @Override
    public ContentNote getNote(UUID noteId) {
        return content.getNote(owner(), noteId);
    }

    @Override
    public NotePage listNotes(UUID notebookId, UUID labelId, NoteType noteType, Boolean pinned,
                              String archive, String trash, String cursor, Integer limit) {
        return content.listNotes(owner(), notebookId, labelId, noteType, pinned, archive, trash,
            cursor, limit);
    }

    @Override
    public NotePage searchNotes(String query, UUID notebookId, UUID labelId, NoteType noteType,
                                Boolean pinned, String archive, String trash, String cursor,
                                Integer limit) {
        return content.searchNotes(owner(), query, notebookId, labelId, noteType, pinned,
            archive, trash, cursor, limit);
    }

    @Override
    public NoteVersionPage listNoteVersions(UUID noteId, String cursor, Integer limit) {
        return content.listNoteVersions(owner(), noteId, cursor, limit);
    }

    @Override
    public NoteVersion getNoteVersion(UUID noteId, UUID versionId) {
        return content.getNoteVersion(owner(), noteId, versionId);
    }

    @Override
    public void snapshotNoteVersion(UUID noteId, VersionRequest request) {
        content.snapshotNoteVersion(owner(), noteId, request.getVersion());
    }

    @Override
    public ContentNote restoreNoteVersion(UUID noteId, UUID versionId, VersionRequest request) {
        return content.restoreNoteVersion(owner(), noteId, versionId, request.getVersion());
    }

    @Override
    public ContentNote moveNote(UUID noteId, NoteMove request) {
        return content.moveNote(owner(), noteId, request);
    }

    @Override
    public void purgeNote(Long version, UUID noteId) {
        content.purgeNote(owner(), noteId, version);
    }

    @Override
    public ContentNote restoreNote(UUID noteId, VersionRequest request) {
        return content.restoreNote(owner(), noteId, request.getVersion());
    }

    @Override
    public ContentNote trashNote(UUID noteId, VersionRequest request) {
        return content.trashNote(owner(), noteId, request.getVersion());
    }

    @Override
    public ContentNote updateNote(UUID noteId, NoteUpdate request) {
        return content.updateNote(owner(), noteId, request);
    }

    private OwnerId owner() {
        SessionView session = identity.getAttribute(AuthenticationIdentity.SESSION);
        if (session == null) throw AuthenticationFailure.sessionNotFound();
        return new OwnerId(session.userId());
    }
}
