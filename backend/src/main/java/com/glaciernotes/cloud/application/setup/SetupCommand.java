package com.glaciernotes.cloud.application.setup;

public record SetupCommand(
    String username,
    String usernameNormalized,
    String email,
    String emailNormalized,
    String displayName,
    String language,
    char[] password
) {
}
