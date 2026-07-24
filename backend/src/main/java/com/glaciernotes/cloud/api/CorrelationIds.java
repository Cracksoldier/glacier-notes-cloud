package com.glaciernotes.cloud.api;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIds {
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private CorrelationIds() {
    }

    public static String resolve(String incoming) {
        return incoming != null && VALID.matcher(incoming).matches()
            ? incoming
            : UUID.randomUUID().toString();
    }
}
