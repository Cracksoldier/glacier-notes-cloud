package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "checklist_items")
public class ChecklistItemEntity extends OwnedMutableEntity {
    @Column(name = "note_id", nullable = false)
    private UUID noteId;
    @Column(nullable = false, columnDefinition = "text")
    private String text;
    private boolean checked;
    @Column(name = "sort_order")
    private int sortOrder;

    protected ChecklistItemEntity() {
    }

    public ChecklistItemEntity(UUID ownerId, UUID id, UUID noteId, String text,
                               boolean checked, int sortOrder, Instant now) {
        this.key = new OwnedEntityId(ownerId, id);
        this.noteId = noteId;
        this.text = text;
        this.checked = checked;
        this.sortOrder = sortOrder;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID id() { return key.id(); }
    public UUID noteId() { return noteId; }
    public String text() { return text; }
    public boolean checked() { return checked; }
    public int sortOrder() { return sortOrder; }

    public void update(String text, boolean checked, int sortOrder, Instant now) {
        this.text = text;
        this.checked = checked;
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }
}
