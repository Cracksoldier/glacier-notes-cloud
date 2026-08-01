package com.glaciernotes.cloud;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/** Shared so that every suite needing a mail sink runs against one Quarkus instance. */
public class SmtpTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "glacier.smtp.enabled", "true",
            "glacier.smtp.sender-name", "Glacier Notes Test",
            "glacier.smtp.sender-address", "notes@example.com"
        );
    }
}
