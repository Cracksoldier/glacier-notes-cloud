package com.glaciernotes.cloud.security;

import com.glaciernotes.cloud.configuration.SecretPolicy;
import com.glaciernotes.cloud.configuration.SecretProvider;

import java.nio.charset.StandardCharsets;

final class EnrollmentSecret {
    private EnrollmentSecret() {
    }

    static byte[] resolve(SecretProvider secretProvider) {
        return secretProvider.enrollmentSecret()
            .filter(SecretPolicy::valid)
            .orElseThrow(() -> new IllegalStateException(
                "A 32-512 character enrollment encryption secret without whitespace is required"
            ))
            .getBytes(StandardCharsets.UTF_8);
    }
}
