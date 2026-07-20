package com.glaciernotes.cloud.api;

public final class CorrelationId {
    public static final String HEADER = "X-Correlation-ID";
    public static final String PROPERTY = CorrelationId.class.getName();

    private CorrelationId() {
    }
}

