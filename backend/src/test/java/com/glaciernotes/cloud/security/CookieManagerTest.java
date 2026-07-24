package com.glaciernotes.cloud.security;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class CookieManagerTest {
    @Inject
    EntityManager entityManager;

    @Inject
    SessionTokenService tokens;

    @Test
    @Transactional
    void malformedPublicBaseUrlProducesAnExplicitConfigurationFailure() throws Exception {
        entityManager.createNativeQuery("update instance_settings set public_base_url = null")
            .executeUpdate();
        CookieManager cookies = new CookieManager(entityManager, "http://[invalid", tokens);
        var method = CookieManager.class.getDeclaredMethod("secureCookies");
        method.setAccessible(true);
        InvocationTargetException failure = assertThrows(
            InvocationTargetException.class,
            () -> method.invoke(cookies)
        );
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }
}
