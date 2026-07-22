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

    public UUID id() { return key.id(); }
    public UUID notebookId() { return notebookId; }
    public String type() { return type; }
    public String title() { return title; }
    public String content() { return content; }
    public boolean pinned() { return pinned; }
    public boolean archived() { return archived; }
    public String color() { return color; }
    public Instant deletedAt() { return deletedAt; }

    public void replace(String title, String content, boolean pinned, boolean archived,
                        String color, Instant now) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.archived = archived;
        this.color = color;
        this.updatedAt = now;
    }

    public void move(UUID notebookId, Instant now) {
        this.notebookId = notebookId;
        this.updatedAt = now;
    }

    public void convert(String type, String content, Instant now) {
        this.type = type;
        this.content = content;
        this.updatedAt = now;
    }

    public void restoreEditable(String type, String title, String content, boolean pinned,
                                boolean archived, String color, Instant now) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.archived = archived;
        this.color = color;
        this.updatedAt = now;
    }

    public void trash(Instant now) { deletedAt = now; updatedAt = now; }
    public void restore(Instant now) { deletedAt = null; updatedAt = now; }
}
