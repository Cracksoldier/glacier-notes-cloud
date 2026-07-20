package com.glaciernotes.cloud.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "labels")
public class LabelEntity extends OwnedMutableEntity {
    @Column(nullable = false)
    private String name;
    @Column(name = "name_normalized", nullable = false)
    private String nameNormalized;

    protected LabelEntity() {
    }
}

