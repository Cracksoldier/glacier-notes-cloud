package com.glaciernotes.cloud.security;

import com.glaciernotes.cloud.configuration.SecretProvider;
import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@ApplicationScoped
public class EnrollmentSecretCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String DERIVATION_INFO = "glacier-notes:mfa-enrollment-key:v1";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_ID_BYTES = 8;

    private final SecretKeySpec key;
    private final String keyId;

    public EnrollmentSecretCipher(SecretProvider secretProvider) {
        var material = deriveKey(EnrollmentSecret.resolve(secretProvider));
        key = new SecretKeySpec(material, "AES");
        keyId = fingerprint(material);
    }

    public String keyId() {
        return keyId;
    }

    public EncryptedSecret encrypt(byte[] plaintext) {
        var nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new EncryptedSecret(cipher.doFinal(plaintext), nonce, keyId);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AES-GCM is unavailable", exception);
        }
    }

    public byte[] decrypt(EncryptedSecret encrypted) {
        if (!keyId.equals(encrypted.keyId())) {
            throw new IllegalStateException("Enrollment was encrypted under a different key");
        }
        try {
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, encrypted.nonce()));
            return cipher.doFinal(encrypted.ciphertext());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Enrollment could not be decrypted", exception);
        }
    }

    private static byte[] deriveKey(byte[] secret) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(DERIVATION_INFO.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private static String fingerprint(byte[] material) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(material);
            var truncated = new byte[KEY_ID_BYTES];
            System.arraycopy(digest, 0, truncated, 0, KEY_ID_BYTES);
            return HexFormat.of().formatHex(truncated);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record EncryptedSecret(byte[] ciphertext, byte[] nonce, String keyId) {
    }
}
