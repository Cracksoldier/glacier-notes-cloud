package com.glaciernotes.cloud;

import com.glaciernotes.cloud.api.CorrelationId;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(RequestBodyLimitTest.Profile.class)
class RequestBodyLimitTest {
    @TestHTTPResource("/")
    URI baseUri;

    @Test
    void fixedLengthRequestsOverTheOrdinaryLimitUseProblemJson() throws Exception {
        var response = send(
            HttpRequest.BodyPublishers.ofString("x".repeat(1_025)),
            "fixed-limit-test"
        );

        assertProblem(response, "fixed-limit-test");
    }

    @Test
    void chunkedRequestsOverTheOrdinaryLimitUseProblemJson() throws Exception {
        byte[] body = "x".repeat(1_025).getBytes(StandardCharsets.UTF_8);
        var response = send(
            HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)),
            "chunked-limit-test"
        );

        assertProblem(response, "chunked-limit-test");
    }

    private HttpResponse<String> send(HttpRequest.BodyPublisher body, String correlationId)
        throws Exception {
        var request = HttpRequest.newBuilder(baseUri.resolve("/api/v1/auth/login"))
            .version(HttpClient.Version.HTTP_1_1)
            .header("Content-Type", "application/json")
            .header(CorrelationId.HEADER, correlationId)
            .POST(body)
            .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertProblem(HttpResponse<String> response, String correlationId) {
        assertEquals(413, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("")
            .startsWith("application/problem+json"));
        assertEquals(
            correlationId,
            response.headers().firstValue(CorrelationId.HEADER).orElseThrow()
        );
        assertTrue(response.body().contains("\"errorCode\":\"REQUEST_TOO_LARGE\""));
        assertTrue(response.body().contains("\"correlationId\":\"" + correlationId + "\""));
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("glacier.http.default-maximum-body-bytes", "1024");
        }
    }
}
