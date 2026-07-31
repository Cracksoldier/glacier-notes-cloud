package com.glaciernotes.cloud.security;

import com.glaciernotes.cloud.domain.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.OptionalLong;

@ApplicationScoped
public class TotpVerifier {
    private static final int STEP_TOLERANCE = 1;
    private static final String ALGORITHM = "HmacSHA1";

    private final TimeProvider timeProvider;

    public TotpVerifier(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    /**
     * Returns the accepted step, or empty when no step in the tolerated window matches.
     */
    public OptionalLong verify(byte[] secret, String code, int digits, int periodSeconds, Long lastAcceptedStep) {
        if (code == null || code.length() != digits) {
            return OptionalLong.empty();
        }
        var currentStep = timeProvider.now().getEpochSecond() / periodSeconds;
        for (long step = currentStep - STEP_TOLERANCE; step <= currentStep + STEP_TOLERANCE; step++) {
            if (lastAcceptedStep != null && step <= lastAcceptedStep) {
                continue;
            }
            if (constantTimeEquals(generate(secret, step, digits), code)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    public String generate(byte[] secret, long step, int digits) {
        var digest = hmac(secret, ByteBuffer.allocate(Long.BYTES).putLong(step).array());
        int offset = digest[digest.length - 1] & 0x0f;
        int binary = ((digest[offset] & 0x7f) << 24)
            | ((digest[offset + 1] & 0xff) << 16)
            | ((digest[offset + 2] & 0xff) << 8)
            | (digest[offset + 3] & 0xff);
        int modulus = (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", binary % modulus);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private byte[] hmac(byte[] secret, byte[] message) {
        try {
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA1 is unavailable", exception);
        }
    }
}
