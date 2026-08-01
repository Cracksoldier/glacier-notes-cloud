package com.glaciernotes.cloud.application.auth;

import java.util.UUID;

/**
 * The acting actor's re-authentication material. Exists so the administrative operations can carry
 * it from the HTTP adapter down to the gate without every signature along the way growing five
 * parameters.
 */
public record StepUpCredentials(
    UUID userId,
    UUID sessionId,
    char[] currentPassword,
    String code,
    String clientAddress
) {
}
