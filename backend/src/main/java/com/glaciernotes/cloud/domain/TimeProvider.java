package com.glaciernotes.cloud.domain;

import java.time.Instant;

@FunctionalInterface
public interface TimeProvider {
    Instant now();
}

