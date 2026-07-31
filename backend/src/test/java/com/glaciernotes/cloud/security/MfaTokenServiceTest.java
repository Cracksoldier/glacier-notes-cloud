package com.glaciernotes.cloud.security;

import com.glaciernotes.cloud.configuration.SecretProvider;
import com.glaciernotes.cloud.configuration.StubConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MfaTokenServiceTest {
    private static final String SECRET = "an-enrollment-encryption-secret-of-sufficient-length";
    private static final String OTHER_SECRET = "a-rotated-enrollment-encryption-secret-of-length";

    @Test
    void challengeTokensCarryAtLeast256BitsOfEntropy() {
        var decoded = Base64.getUrlDecoder().decode(service(SECRET).newChallengeToken());

        assertEquals(32, decoded.length);
    }

    @Test
    void challengeTokensAreUnpredictable() {
        var service = service(SECRET);
        var tokens = new HashSet<String>();
        for (int index = 0; index < 200; index++) {
            tokens.add(service.newChallengeToken());
        }

        assertEquals(200, tokens.size());
    }

    @Test
    void hashingIsDeterministicAndDoesNotRevealTheToken() {
        var service = service(SECRET);
        var token = service.newChallengeToken();

        var hash = service.hashChallengeToken(token);

        assertEquals(hash, service.hashChallengeToken(token));
        assertEquals(64, hash.length());
        assertFalse(hash.contains(token));
    }

    @Test
    void challengeAndRecoveryHashesAreSeparatedByTheirDomainPrefix() {
        var service = service(SECRET);

        assertNotEquals(service.hashChallengeToken("ABCD-EFGH-JKMN"), service.hashRecoveryCode("ABCD-EFGH-JKMN"));
    }

    @Test
    void hashesAreBoundToTheEnrollmentSecret() {
        var token = "ABCD-EFGH-JKMN";

        assertNotEquals(
            service(SECRET).hashRecoveryCode(token),
            service(OTHER_SECRET).hashRecoveryCode(token)
        );
    }

    @Test
    void recoveryCodesAreDistinctAndUseAnUnambiguousAlphabet() {
        var codes = service(SECRET).newRecoveryCodes(10);

        assertEquals(10, codes.size());
        assertEquals(10, new HashSet<>(codes).size());
        for (String code : codes) {
            assertTrue(code.matches("[ABCDEFGHJKMNPQRSTVWXYZ23456789]{4}(-[ABCDEFGHJKMNPQRSTVWXYZ23456789]{4}){2}"), code);
        }
    }

    @Test
    void recoveryCodesAreNormalizedBeforeHashingSoTranscriptionVariantsMatch() {
        var service = service(SECRET);
        var expected = service.hashRecoveryCode("ABCD-EFGH-JKMN");

        assertEquals(expected, service.hashRecoveryCode("abcd-efgh-jkmn"));
        assertEquals(expected, service.hashRecoveryCode("ABCDEFGHJKMN"));
        assertEquals(expected, service.hashRecoveryCode("ABCD EFGH JKMN"));
    }

    @Test
    void comparisonRejectsMismatchesAndNulls() {
        var service = service(SECRET);

        assertTrue(service.matches("same", "same"));
        assertFalse(service.matches("same", "different"));
        assertFalse(service.matches(null, "same"));
    }

    private MfaTokenService service(String secret) {
        return new MfaTokenService(new SecretProvider(
            new StubConfiguration().mfa(true, Optional.empty(), Optional.of(secret))
        ));
    }
}
