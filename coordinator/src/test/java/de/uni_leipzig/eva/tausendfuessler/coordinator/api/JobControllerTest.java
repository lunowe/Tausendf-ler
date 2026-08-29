package de.uni_leipzig.eva.tausendfuessler.coordinator.api;

import com.fasterxml.jackson.databind.JsonNode;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobRuntimeRegistry;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JobControllerTest {

    @Autowired TestRestTemplate rest;
    @Autowired JobRuntimeRegistry runtimes;

    private String createJob(long owner) {
        ResponseEntity<JsonNode> created = rest.postForEntity("/api/jobs",
                Map.of("url", "https://api.example/start", "maxDepth", 2, "filters", List.of("example"), "owner", owner),
                JsonNode.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody().get("status").asText()).isEqualTo("RUNNING");
        assertThat(created.getBody().get("message").asText()).isNotBlank();
        return created.getBody().get("jobId").asText();
    }

    @Test
    void createListDetailAndLifecycle() {
        String jobId = createJob(1001L);
        assertThat(runtimes.get(jobId)).isPresent();

        ResponseEntity<JsonNode> list = rest.getForEntity("/api/jobs?owner=1001", JsonNode.class);
        assertThat(list.getStatusCode().value()).as(String.valueOf(list.getBody())).isEqualTo(200);
        assertThat(list.getBody().findValues("jobId")).extracting(JsonNode::asText).contains(jobId);
        assertThat(rest.getForEntity("/api/jobs?owner=999999", JsonNode.class).getBody()).isEmpty();

        JsonNode detail = rest.getForEntity("/api/jobs/" + jobId, JsonNode.class).getBody();
        assertThat(detail.get("url").asText()).isEqualTo("https://api.example/start");
        assertThat(detail.get("maxDepth").asInt()).isEqualTo(2);
        assertThat(detail.get("currentDepth").asInt()).isEqualTo(0);
        assertThat(detail.get("startedAt").asText()).contains("T");
        assertThat(detail.get("finishedAt").isNull()).isTrue();

        assertThat(rest.postForEntity("/api/jobs/" + jobId + "/pause", null, Void.class).getStatusCode().value()).isEqualTo(204);
        assertThat(runtimes.get(jobId).orElseThrow().status()).isEqualTo(JobStatus.PAUSED);
        assertThat(rest.postForEntity("/api/jobs/" + jobId + "/pause", null, JsonNode.class).getStatusCode().value()).isEqualTo(409);

        assertThat(rest.postForEntity("/api/jobs/" + jobId + "/resume", null, Void.class).getStatusCode().value()).isEqualTo(204);
        assertThat(runtimes.get(jobId).orElseThrow().status()).isEqualTo(JobStatus.RUNNING);
        assertThat(rest.postForEntity("/api/jobs/" + jobId + "/resume", null, JsonNode.class).getStatusCode().value()).isEqualTo(409);

        assertThat(rest.postForEntity("/api/jobs/" + jobId + "/abort", null, Void.class).getStatusCode().value()).isEqualTo(204);
        assertThat(runtimes.get(jobId)).isEmpty();
        assertThat(rest.getForEntity("/api/jobs/" + jobId, JsonNode.class).getBody().get("status").asText()).isEqualTo("ABORTED");

        ResponseEntity<JsonNode> conflict = rest.postForEntity("/api/jobs/" + jobId + "/abort", null, JsonNode.class);
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
        assertThat(conflict.getBody().get("error").asText()).isNotBlank();
    }

    @Test
    void unknownJobIs404() {
        assertThat(rest.getForEntity("/api/jobs/nope", JsonNode.class).getStatusCode().value()).isEqualTo(404);
        assertThat(rest.getForEntity("/api/jobs/nope/results", JsonNode.class).getStatusCode().value()).isEqualTo(404);
        ResponseEntity<JsonNode> pause = rest.postForEntity("/api/jobs/nope/pause", null, JsonNode.class);
        assertThat(pause.getStatusCode().value()).isEqualTo(404);
        assertThat(pause.getBody().get("error").asText()).contains("nope");
    }

    @Test
    void invalidInputIs400() {
        ResponseEntity<JsonNode> badUrl = rest.postForEntity("/api/jobs",
                Map.of("url", "not a url", "maxDepth", 1, "owner", 1L), JsonNode.class);
        assertThat(badUrl.getStatusCode().value()).isEqualTo(400);
        assertThat(badUrl.getBody().get("error").asText()).isNotBlank();

        ResponseEntity<JsonNode> missingOwner = rest.postForEntity("/api/jobs",
                Map.of("url", "https://api.example/", "maxDepth", 1), JsonNode.class);
        assertThat(missingOwner.getStatusCode().value()).isEqualTo(400);

        ResponseEntity<JsonNode> negativeDepth = rest.postForEntity("/api/jobs",
                Map.of("url", "https://api.example/", "maxDepth", -1, "owner", 1L), JsonNode.class);
        assertThat(negativeDepth.getStatusCode().value()).isEqualTo(400);

        assertThat(rest.getForEntity("/api/search?q=", JsonNode.class).getStatusCode().value()).isEqualTo(400);
    }
}
