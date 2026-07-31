package com.glaciernotes.cloud.security;

import com.glaciernotes.cloud.domain.TimeProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpVerifierTest {
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.UTF_8);
    private static final int PERIOD = 30;

    /**
     * The HMAC-SHA1 vectors published in RFC 6238 appendix B.
     */
    @ParameterizedTest
    @CsvSource({
        "59,94287082",
        "1111111109,07081804",
        "1111111111,14050471",
        "1234567890,89005924",
        "2000000000,69279037",
        "20000000000,65353130"
    })
    void acceptsThePublishedRfc6238Vectors(long epochSecond, String code) {
        var verifier = verifierAt(epochSecond);

        var accepted = verifier.verify(RFC_SECRET, code, 8, PERIOD, null);

        assertTrue(accepted.isPresent());
        assertEquals(epochSecond / PERIOD, accepted.getAsLong());
    }

    @Test
    void acceptsTheNeighbouringStepsAndRejectsAnythingWider() {
        var now = 1111111109L;
        var currentStep = now / PERIOD;
        var verifier = verifierAt(now);

        for (long offset = -1; offset <= 1; offset++) {
            var code = verifier.generate(RFC_SECRET, currentStep + offset, 8);
            assertTrue(
                verifier.verify(RFC_SECRET, code, 8, PERIOD, null).isPresent(),
                "step offset " + offset + " should be tolerated"
            );
        }
        for (long offset : new long[]{-2, 2}) {
            var code = verifier.generate(RFC_SECRET, currentStep + offset, 8);
            assertTrue(
                verifier.verify(RFC_SECRET, code, 8, PERIOD, null).isEmpty(),
                "step offset " + offset + " should be rejected"
            );
        }
    }

    @Test
    void rejectsAnAlreadyAcceptedStepAndEverythingBeforeIt() {
        var now = 1111111109L;
        var currentStep = now / PERIOD;
        var verifier = verifierAt(now);
        var code = verifier.generate(RFC_SECRET, currentStep, 8);

        assertTrue(verifier.verify(RFC_SECRET, code, 8, PERIOD, null).isPresent());
        assertTrue(verifier.verify(RFC_SECRET, code, 8, PERIOD, currentStep).isEmpty());

        var previous = verifier.generate(RFC_SECRET, currentStep - 1, 8);
        assertTrue(verifier.verify(RFC_SECRET, previous, 8, PERIOD, currentStep - 1).isEmpty());
    }

    @Test
    void rejectsCodesOfTheWrongLength() {
        var verifier = verifierAt(59);

        assertFalse(verifier.verify(RFC_SECRET, "9428708", 8, PERIOD, null).isPresent());
        assertFalse(verifier.verify(RFC_SECRET, null, 8, PERIOD, null).isPresent());
    }

    @Test
    void generatesSixDigitCodesWhenSixDigitsAreConfigured() {
        var verifier = verifierAt(59);

        assertEquals(6, verifier.generate(RFC_SECRET, 1, 6).length());
    }

    private TotpVerifier verifierAt(long epochSecond) {
        TimeProvider time = () -> Instant.ofEpochSecond(epochSecond);
        return new TotpVerifier(time);
    }
}
