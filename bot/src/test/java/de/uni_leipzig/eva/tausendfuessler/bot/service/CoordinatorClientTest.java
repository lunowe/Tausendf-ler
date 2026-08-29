package de.uni_leipzig.eva.tausendfuessler.bot.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

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
}
