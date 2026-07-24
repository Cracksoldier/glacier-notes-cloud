package com.glaciernotes.cloud.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretPolicyTest {
    @Test
    void acceptsOnlyBoundedSecretsWithoutWhitespace() {
        assertTrue(SecretPolicy.valid("a".repeat(32)));
        assertTrue(SecretPolicy.valid("a".repeat(512)));
        assertFalse(SecretPolicy.valid(null));
        assertFalse(SecretPolicy.valid("a".repeat(31)));
        assertFalse(SecretPolicy.valid("a".repeat(513)));
        assertFalse(SecretPolicy.valid("a".repeat(31) + " "));
        assertFalse(SecretPolicy.valid("a".repeat(31) + "\n"));
    }
}
