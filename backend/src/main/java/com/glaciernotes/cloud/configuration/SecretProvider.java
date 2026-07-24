package com.glaciernotes.cloud.configuration;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@ApplicationScoped
public class SecretProvider {
    private final GlacierConfiguration configuration;

    public SecretProvider(GlacierConfiguration configuration) {
        this.configuration = configuration;
    }

    public Optional<String> bootstrapToken() {
        return resolve(
            configuration.bootstrap().tokenFile(),
            configuration.bootstrap().token(),
            "bootstrap token"
        );
    }

    public Optional<String> sessionSecret() {
        return resolve(
            configuration.security().sessionSecretFile(),
            configuration.security().sessionSecret(),
            "session secret"
        );
    }

    private Optional<String> resolve(
        Optional<Path> configuredFile,
        Optional<String> configuredValue,
        String description
    ) {
        if (configuredFile.isPresent()) {
            try {
                return nonBlank(stripTerminalNewline(Files.readString(configuredFile.orElseThrow())));
            } catch (IOException exception) {
                throw new IllegalStateException("Could not read configured " + description + " file", exception);
            }
        }
        return configuredValue.flatMap(this::nonBlank);
    }

    private Optional<String> nonBlank(String value) {
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private String stripTerminalNewline(String value) {
        return value.replaceFirst("[\\r\\n]+$", "");
    }
}
