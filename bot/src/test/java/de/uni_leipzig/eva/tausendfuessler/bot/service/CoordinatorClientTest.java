package de.uni_leipzig.eva.tausendfuessler.bot.service;

import de.uni_leipzig.eva.tausendfuessler.bot.dto.WorkerInfo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CoordinatorClientTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    private final CoordinatorClient client = new CoordinatorClient(restTemplate, "http://coordinator");

    @Test
    void errorFieldOfTheBodyBecomesTheExceptionMessage() {
        server.expect(requestTo("http://coordinator/api/jobs/x/pause"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Cannot go from PAUSED to PAUSED\"}"));

        assertThatThrownBy(() -> client.pauseJob("x"))
                .isInstanceOf(CoordinatorException.class)
                .hasMessage("Cannot go from PAUSED to PAUSED");
        server.verify();
    }

    @Test
    void fallsBackToTheStatusTextWithoutJsonBody() {
        server.expect(requestTo("http://coordinator/api/jobs/x"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("<html>nope</html>"));

        assertThatThrownBy(() -> client.getJobDetail("x"))
                .isInstanceOf(CoordinatorException.class)
                .satisfies(e -> assertThat(e.getMessage()).isEqualTo("Not Found"));
    }

    @Test
    void sendsTheApiKeyHeaderWhenConfigured() {
        RestTemplate withKey = new RestTemplateBuilder()
                .additionalInterceptors(new ApiKeyInterceptor("secret"))
                .build();
        MockRestServiceServer keyedServer = MockRestServiceServer.bindTo(withKey).build();
        CoordinatorClient keyedClient = new CoordinatorClient(withKey, "http://coordinator");

        keyedServer.expect(requestTo("http://coordinator/api/workers"))
                .andExpect(header("X-Api-Key", "secret"))
                .andRespond(withSuccess(
                        "[{\"workerId\":\"w1\",\"threads\":8,\"inFlight\":3,\"connectedAt\":\"2026-08-29T10:00:00Z\"}]",
                        MediaType.APPLICATION_JSON));

        List<WorkerInfo> workers = keyedClient.listWorkers();
        assertThat(workers).hasSize(1);
        assertThat(workers.get(0).workerId()).isEqualTo("w1");
        assertThat(workers.get(0).inFlight()).isEqualTo(3);
        keyedServer.verify();
    }

    @Test
    void sendsNoApiKeyHeaderWithoutConfiguration() {
        server.expect(requestTo("http://coordinator/api/workers"))
                .andExpect(headerDoesNotExist("X-Api-Key"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.listWorkers()).isEmpty();
        server.verify();
    }

    @Test
    void searchEncodesReservedCharactersInTheQuery() {
        server.expect(requestTo("http://coordinator/api/search?q=Tom%20%26%20Jerry%20C%2B%2B&limit=10"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.search("Tom & Jerry C++", 10)).isEmpty();
        server.verify();
    }
}
