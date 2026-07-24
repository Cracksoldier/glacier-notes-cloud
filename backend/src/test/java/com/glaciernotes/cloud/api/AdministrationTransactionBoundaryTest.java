package com.glaciernotes.cloud.api;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdministrationTransactionBoundaryTest {
    @Test
    void mutationsThatEmitAuditEventsDeclareOneTransactionBoundary() throws Exception {
        assertTransactional("updateInstanceLogo", InputStream.class);
        assertTransactional("deleteInstanceLogo");
        assertTransactional("testSmtp");
        assertTransactional("createBackup");
    }

    private void assertTransactional(String method, Class<?>... parameterTypes) throws Exception {
        assertNotNull(
            AdministrationResource.class.getMethod(method, parameterTypes)
                .getAnnotation(Transactional.class),
            method + " must share its mutation and audit transaction"
        );
    }
}
