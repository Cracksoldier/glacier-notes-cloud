package com.glaciernotes.cloud;

import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

import java.util.Map;

@QuarkusTest
@TestProfile(PostgresqlImageResourceTest.Profile.class)
public class PostgresqlImageResourceTest extends ImageResourceTest {
    public static class Profile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() {
            return Map.of("glacier.images.backend", "POSTGRESQL");
        }
    }
}
