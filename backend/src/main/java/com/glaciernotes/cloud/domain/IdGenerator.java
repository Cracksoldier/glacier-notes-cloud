package com.glaciernotes.cloud.domain;

import java.util.UUID;

@FunctionalInterface
public interface IdGenerator {
    UUID nextId();
}

