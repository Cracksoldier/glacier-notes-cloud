package com.glaciernotes.cloud.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretProviderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void mountedFileTakesPrecedenceAndTerminalNewlinesAreRemoved() throws IOException {
        var tokenFile = temporaryDirectory.resolve("bootstrap-token");
        Files.writeString(tokenFile, "file-bootstrap-secret-value\n");

        var provider = new SecretProvider(new StubConfiguration().bootstrapToken(
            Optional.of(tokenFile),
            Optional.of("environment-bootstrap-secret-value")
        ));

        assertEquals("file-bootstrap-secret-value", provider.bootstrapToken().orElseThrow());
    }

    @Test
    void configuredValueIsUsedWhenNoFileIsConfigured() {
        var provider = new SecretProvider(new StubConfiguration().bootstrapToken(
            Optional.empty(),
            Optional.of("environment-bootstrap-secret-value")
        ));

        assertEquals("environment-bootstrap-secret-value", provider.bootstrapToken().orElseThrow());
    }

    @Test
    void whitespaceOnlyValuesAreTreatedAsAbsent() {
        var provider = new SecretProvider(new StubConfiguration().bootstrapToken(
            Optional.empty(),
            Optional.of(" \t ")
        ));

        assertTrue(provider.bootstrapToken().isEmpty());
    }

    @Test
    void enrollmentSecretIsResolvedFromItsOwnFileIndependentlyOfTheSessionSecret() throws IOException {
        var secretFile = temporaryDirectory.resolve("mfa-encryption-secret");
        Files.writeString(secretFile, "file-enrollment-encryption-secret-value\n");

        var provider = new SecretProvider(new StubConfiguration()
            .sessionSecret(Optional.empty(), Optional.of("a-completely-different-session-secret"))
            .mfa(true, Optional.of(secretFile), Optional.empty()));

        assertEquals("file-enrollment-encryption-secret-value", provider.enrollmentSecret().orElseThrow());
    }
}
