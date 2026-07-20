package com.glaciernotes.cloud.persistence.entity;

import com.glaciernotes.cloud.domain.OwnerId;
import com.glaciernotes.cloud.domain.note.Note;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notes")
public class NoteEntity extends OwnedMutableEntity {
    @Column(name = "notebook_id", nullable = false)
    private UUID notebookId;
    @Column(name = "note_type", nullable = false)
    private String type;
    @Column(nullable = false, columnDefinition = "text")
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    private boolean pinned;
    private boolean archived;
    private String color;
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected NoteEntity() {
    }

    public NoteEntity(Note note) {
        key = new OwnedEntityId(note.ownerId().value(), note.id());
        update(note);
        createdAt = note.createdAt();
        version = note.version();
    }

    public void update(Note note) {
        notebookId = note.notebookId();
        type = note.type();
        title = note.title();
        content = note.content();
        pinned = note.pinned();
        archived = note.archived();
        color = note.color();
        deletedAt = note.deletedAt();
        updatedAt = note.updatedAt();
    }

    public Note toDomain() {
        return new Note(
            new OwnerId(key.ownerId()), key.id(), notebookId, type, title, content,
            pinned, archived, color, deletedAt, createdAt, updatedAt, version
        );
    }
}
