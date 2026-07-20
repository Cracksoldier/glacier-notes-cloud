package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

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
}
