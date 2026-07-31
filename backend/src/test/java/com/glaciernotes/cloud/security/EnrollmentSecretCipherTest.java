package com.glaciernotes.cloud.security;

import com.glaciernotes.cloud.configuration.SecretProvider;
import com.glaciernotes.cloud.configuration.StubConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnrollmentSecretCipherTest {
    private static final String SECRET = "an-enrollment-encryption-secret-of-sufficient-length";
    private static final String OTHER_SECRET = "a-rotated-enrollment-encryption-secret-of-length";
    private static final byte[] PLAINTEXT = "12345678901234567890".getBytes(StandardCharsets.UTF_8);

    @Test
    void encryptedSecretsRoundTripExactly() {
        var cipher = cipher(SECRET);

        assertArrayEquals(PLAINTEXT, cipher.decrypt(cipher.encrypt(PLAINTEXT)));
    }

    @Test
    void everyRecordGetsItsOwnNonceAndDiffersOnTheWire() {
        var cipher = cipher(SECRET);

        var first = cipher.encrypt(PLAINTEXT);
        var second = cipher.encrypt(PLAINTEXT);

        assertFalse(Arrays.equals(first.nonce(), second.nonce()));
        assertFalse(Arrays.equals(first.ciphertext(), second.ciphertext()));
    }

    @Test
    void ciphertextCarriesTheKeyIdentifierThatProducedIt() {
        var cipher = cipher(SECRET);
        var rotated = cipher(OTHER_SECRET);

        assertEquals(cipher.keyId(), cipher.encrypt(PLAINTEXT).keyId());
        assertNotEquals(cipher.keyId(), rotated.keyId());
    }

    @Test
    void decryptionUnderTheWrongKeyFailsClosed() {
        var encrypted = cipher(SECRET).encrypt(PLAINTEXT);
        var rotated = cipher(OTHER_SECRET);

        assertThrows(IllegalStateException.class, () -> rotated.decrypt(encrypted));
    }

    @Test
    void tamperedCiphertextIsRejectedByTheAuthenticationTag() {
        var cipher = cipher(SECRET);
        var encrypted = cipher.encrypt(PLAINTEXT);
        encrypted.ciphertext()[0] ^= 0x01;

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(encrypted));
    }

    @Test
    void anAbsentSecretIsRefusedRatherThanSubstituted() {
        var provider = new SecretProvider(new StubConfiguration().mfa(true, Optional.empty(), Optional.empty()));

        assertThrows(IllegalStateException.class, () -> new EnrollmentSecretCipher(provider));
    }

    private EnrollmentSecretCipher cipher(String secret) {
        return new EnrollmentSecretCipher(new SecretProvider(
            new StubConfiguration().mfa(true, Optional.empty(), Optional.of(secret))
        ));
    }
}
