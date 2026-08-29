package de.uni_leipzig.eva.tausendfuessler.coordinator.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Own Spring context with a configured key; all other integration tests run with authentication disabled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "tausendfuessler.api-key=" + ApiKeyFilterTest.KEY)
@ActiveProfiles("test")
class ApiKeyFilterTest {

    static final String KEY = "test-api-key";

    @Autowired TestRestTemplate rest;

    private ResponseEntity<JsonNode> get(String path, String key) {
        HttpHeaders headers = new HttpHeaders();
        if (key != null) {
            headers.set(ApiKeyFilter.HEADER, key);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    @Test
    void missingKeyIs401() {
        ResponseEntity<JsonNode> response = get("/api/stats", null);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/json");
        assertThat(response.getBody().get("error").asText()).isEqualTo("unauthorized");
    }

    @Test
    void wrongKeyIs401() {
        assertThat(get("/api/stats", "nope").getStatusCode().value()).isEqualTo(401);
        assertThat(get("/api/jobs?owner=1", KEY + "x").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void correctKeyPasses() {
        assertThat(get("/api/stats", KEY).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/workers", KEY).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void healthIsAlwaysOpen() {
        ResponseEntity<JsonNode> response = get("/api/health", null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().get("status").asText()).isEqualTo("UP");
    }

    @Test
    void corsPreflightPassesWithoutKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:3000");
        headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, ApiKeyFilter.HEADER);
        ResponseEntity<Void> response = rest.exchange("/api/stats", HttpMethod.OPTIONS, new HttpEntity<>(headers), Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:3000");
        assertThat(response.getHeaders().getAccessControlAllowHeaders()).contains(ApiKeyFilter.HEADER);
    }
}
