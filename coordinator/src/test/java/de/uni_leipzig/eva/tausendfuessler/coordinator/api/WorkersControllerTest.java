package de.uni_leipzig.eva.tausendfuessler.coordinator.api;

import com.fasterxml.jackson.databind.JsonNode;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.WorkerRegistry;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.WorkerSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.StringWriter;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WorkersControllerTest {

    @Autowired TestRestTemplate rest;
    @Autowired WorkerRegistry workers;

    /** Constructed in this order, so {@code first} has the older {@code connectedAt} (ties fall back to workerId). */
    private final WorkerSession first = new WorkerSession("wc-first", 4, new StringWriter());
    private final WorkerSession second = new WorkerSession("wc-second", 8, new StringWriter());

    @AfterEach
    void unregister() {
        workers.remove(first);
        workers.remove(second);
    }

    @Test
    void listsConnectedWorkersOldestFirst() {
        workers.register(second); // registration order must not matter, connectedAt does
        workers.register(first);
        second.addInFlight(3);

        ResponseEntity<JsonNode> response = rest.getForEntity("/api/workers", JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        JsonNode body = response.getBody();
        // other test classes may have live workers in the registry; only look at ours
        JsonNode a = find(body, "wc-first");
        JsonNode b = find(body, "wc-second");
        assertThat(a.get("threads").asInt()).isEqualTo(4);
        assertThat(a.get("inFlight").asInt()).isZero();
        assertThat(b.get("threads").asInt()).isEqualTo(8);
        assertThat(b.get("inFlight").asInt()).isEqualTo(3);
        assertThat(Instant.parse(a.get("connectedAt").asText())).isEqualTo(first.connectedAt());

        assertThat(first.connectedAt()).isBeforeOrEqualTo(second.connectedAt());
        assertThat(indexOf(body, "wc-first")).isLessThan(indexOf(body, "wc-second"));
    }

    private static JsonNode find(JsonNode list, String workerId) {
        return list.get(indexOf(list, workerId));
    }

    private static int indexOf(JsonNode list, String workerId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).get("workerId").asText().equals(workerId)) {
                return i;
            }
        }
        throw new AssertionError(workerId + " not in " + list);
    }
}
