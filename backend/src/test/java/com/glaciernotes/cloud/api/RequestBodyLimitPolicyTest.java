package com.glaciernotes.cloud.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestBodyLimitPolicyTest {
    @Test
    void addsMultipartOverheadToUploadLimits() {
        var policy = RequestBodyLimitPolicy.from(10, 2, 102, 20, 100);

        assertEquals(10, policy.defaultMaximumBodyBytes());
        assertEquals(22, policy.imageMaximumBodyBytes());
        assertEquals(102, policy.transferMaximumBodyBytes());
    }

    @Test
    void rejectsInvalidOrInconsistentLimits() {
        assertThrows(
            IllegalStateException.class,
            () -> RequestBodyLimitPolicy.from(10, 2, 101, 20, 100)
        );
        assertThrows(
            IllegalStateException.class,
            () -> RequestBodyLimitPolicy.from(0, 2, 102, 20, 100)
        );
        assertThrows(
            IllegalStateException.class,
            () -> RequestBodyLimitPolicy.from(10, Long.MAX_VALUE, Long.MAX_VALUE, 20, 100)
        );
    }
}
