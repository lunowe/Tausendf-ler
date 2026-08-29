package de.uni_leipzig.eva.tausendfuessler.coordinator.socket;

import com.fasterxml.jackson.databind.JsonNode;
import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import de.uni_leipzig.eva.tausendfuessler.common.protocol.ProtocolJson;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobRuntime;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobRuntimeRegistry;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobStatus;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.WorkerRegistry;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.JobEntity;
import de.uni_leipzig.eva.tausendfuessler.coordinator.service.JobService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WorkerSocketServerTest {

    @Autowired WorkerSocketServer server;
    @Autowired JobService jobService;
    @Autowired JobRuntimeRegistry runtimes;
    @Autowired WorkerRegistry workers;
    @Autowired TestRestTemplate rest;

    /** Minimal fake worker over a raw socket. */
    private final class FakeWorker implements AutoCloseable {
        final Socket socket;
        final BufferedReader in;
        final BufferedWriter out;

        FakeWorker() throws IOException {
            socket = new Socket("localhost", server.getPort());
            socket.setSoTimeout(5000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void send(Message m) throws IOException {
            out.write(ProtocolJson.encode(m));
            out.write('\n');
            out.flush();
        }

        Message receive() throws IOException {
            String line = in.readLine();
            assertThat(line).as("connection closed").isNotNull();
            return ProtocolJson.decode(line);
        }

        /** Polls REQUEST_WORK until a package of {@code jobId} arrives (other test classes may leave jobs behind). */
        Message.WorkPackage requestWorkFor(String workerId, String jobId) {
            return await().atMost(Duration.ofSeconds(5)).until(() -> {
                send(new Message.RequestWork(workerId, 8));
                Message reply = receive();
                return reply instanceof Message.WorkPackage p && p.jobId().equals(jobId) ? p : null;
            }, p -> p != null);
        }

        void pageResult(String workerId, String jobId, String url, int depth, List<String> links) throws IOException {
            send(new Message.PageResult(workerId, jobId, url, depth, 200, "Title of " + url,
                    "Snippet text for " + url, links, null, System.currentTimeMillis()));
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @Test
    void serverIsListeningOnEphemeralPort() {
        assertThat(server.isRunning()).isTrue();
        assertThat(server.getPort()).isPositive();
    }

    @Test
    void fullCrawlRoundTripOverSocketAndRest() throws Exception {
        try (FakeWorker w = new FakeWorker()) {
            w.send(new Message.Register("w-roundtrip", 8, null));
            assertThat(w.receive()).isEqualTo(new Message.Registered("w-roundtrip"));

            JobEntity job = jobService.createJob("https://roundtrip.example/", 1, List.of(), 42L);
            String jobId = job.getId();

            Message.WorkPackage pkg = w.requestWorkFor("w-roundtrip", jobId);
            assertThat(pkg.depth()).isZero();
            assertThat(pkg.urls()).containsExactly("https://roundtrip.example");

            w.pageResult("w-roundtrip", jobId, pkg.urls().get(0), 0,
                    List.of("https://roundtrip.example/a", "https://roundtrip.example/b", "https://roundtrip.example/a#dup"));

            Message.WorkPackage next = w.requestWorkFor("w-roundtrip", jobId);
            assertThat(next.depth()).isEqualTo(1);
            assertThat(next.urls()).containsExactlyInAnyOrder("https://roundtrip.example/a", "https://roundtrip.example/b");

            for (String url : next.urls()) {
                w.pageResult("w-roundtrip", jobId, url, 1, List.of("https://roundtrip.example/too-deep"));
            }

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                ResponseEntity<JsonNode> detail = rest.getForEntity("/api/jobs/" + jobId, JsonNode.class);
                assertThat(detail.getStatusCode().value()).isEqualTo(200);
                assertThat(detail.getBody().get("status").asText()).isEqualTo("COMPLETED");
            });

            ResponseEntity<JsonNode> detail = rest.getForEntity("/api/jobs/" + jobId, JsonNode.class);
            assertThat(detail.getBody().get("pagesVisited").asLong()).isEqualTo(3);
            assertThat(detail.getBody().get("linksFound").asLong()).isEqualTo(5);
            assertThat(detail.getBody().get("errors").asLong()).isZero();
            assertThat(detail.getBody().get("finishedAt").asText()).isNotBlank();
            assertThat(runtimes.get(jobId)).isEmpty();

            ResponseEntity<JsonNode> results = rest.getForEntity("/api/jobs/" + jobId + "/results?afterSeq=0", JsonNode.class);
            assertThat(results.getBody()).hasSize(3);
            assertThat(results.getBody().findValues("seq")).extracting(JsonNode::asLong).containsExactly(1L, 2L, 3L);
            assertThat(results.getBody().get(0).get("url").asText()).isEqualTo("https://roundtrip.example");
            assertThat(results.getBody().get(0).get("title").asText()).startsWith("Title of");
            assertThat(results.getBody().get(0).get("crawledAt").asText()).contains("T");

            ResponseEntity<JsonNode> tail = rest.getForEntity("/api/jobs/" + jobId + "/results?afterSeq=2", JsonNode.class);
            assertThat(tail.getBody()).hasSize(1);
            assertThat(tail.getBody().get(0).get("seq").asLong()).isEqualTo(3);

            ResponseEntity<JsonNode> search = rest.getForEntity("/api/search?q=roundtrip.example/a&limit=5", JsonNode.class);
            assertThat(search.getBody()).hasSize(1);
            assertThat(search.getBody().get(0).get("jobId").asText()).isEqualTo(jobId);

            ResponseEntity<JsonNode> stats = rest.getForEntity("/api/stats", JsonNode.class);
            assertThat(stats.getBody().get("totalJobs").asLong()).isGreaterThanOrEqualTo(1);
            assertThat(stats.getBody().get("topDomains").get("roundtrip.example").asLong()).isEqualTo(3);
        }
    }

    @Test
    void crashedWorkerHasItsInFlightUrlsRequeued() throws Exception {
        JobEntity job = jobService.createJob("https://crash.example/", 0, List.of(), 7L);
        JobRuntime runtime = runtimes.get(job.getId()).orElseThrow();

        FakeWorker dying = new FakeWorker();
        dying.send(new Message.Register("w-dying", 2, null));
        dying.receive();
        dying.requestWorkFor("w-dying", job.getId());
        assertThat(runtime.inFlightCount()).isEqualTo(1);

        dying.close(); // simulate crash while the URL is in flight

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(workers.get("w-dying")).isEmpty();
            assertThat(runtime.inFlightCount()).isZero();
        });

        try (FakeWorker fresh = new FakeWorker()) {
            fresh.send(new Message.Register("w-fresh", 2, null));
            fresh.receive();
            Message.WorkPackage again = fresh.requestWorkFor("w-fresh", job.getId());
            assertThat(again.urls()).containsExactly("https://crash.example");
            fresh.pageResult("w-fresh", job.getId(), "https://crash.example", 0, List.of());
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(jobService.getDetail(job.getId()).getStatus()).isEqualTo(JobStatus.COMPLETED));
        }
    }

    @Test
    void failingResultYieldsErrorButKeepsConnectionAndRequeuesNothingElse() throws Exception {
        JobEntity job = jobService.createJob("https://toolong.example/", 1, List.of(), 8L);
        try (FakeWorker w = new FakeWorker()) {
            w.send(new Message.Register("w-toolong", 2, null));
            w.receive();
            Message.WorkPackage pkg = w.requestWorkFor("w-toolong", job.getId());

            // a URL that does not fit pages.url (varchar 2048) -> DB rejects the row, but the connection must survive
            String tooLong = "https://toolong.example/" + "x".repeat(3000);
            w.send(new Message.PageResult("w-toolong", job.getId(), tooLong, 0, 200, "t", "s", List.of(), null,
                    System.currentTimeMillis()));
            assertThat(w.receive()).isInstanceOf(Message.Error.class);

            w.pageResult("w-toolong", job.getId(), pkg.urls().get(0), 0, List.of("https://toolong.example/" + "y".repeat(3000)));
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(jobService.getDetail(job.getId()).getStatus()).isEqualTo(JobStatus.COMPLETED));
            assertThat(workers.get("w-toolong")).isPresent();
        }
    }

    @Test
    void malformedLineYieldsErrorButKeepsConnection() throws Exception {
        try (FakeWorker w = new FakeWorker()) {
            w.send(new Message.RequestWork("nobody", 1)); // before REGISTER
            assertThat(w.receive()).isInstanceOf(Message.Error.class);

            w.out.write("this is not json\n");
            w.out.flush();
            assertThat(w.receive()).isInstanceOf(Message.Error.class);

            w.send(new Message.Register("w-late", 1, null));
            assertThat(w.receive()).isEqualTo(new Message.Registered("w-late"));
        }
    }

    /** Own Spring context with a configured worker token; the outer tests run without one. */
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "tausendfuessler.worker-token=" + WithWorkerToken.TOKEN)
    @ActiveProfiles("test")
    class WithWorkerToken {

        static final String TOKEN = "test-worker-token";

        @Autowired WorkerSocketServer tokenServer;
        @Autowired WorkerRegistry tokenWorkers;

        private Socket connect() throws IOException {
            Socket socket = new Socket("localhost", tokenServer.getPort());
            socket.setSoTimeout(5000);
            return socket;
        }

        private Message exchange(Socket socket, Message request) throws IOException {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            out.write(ProtocolJson.encode(request));
            out.write('\n');
            out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            return ProtocolJson.decode(in.readLine());
        }

        @Test
        void wrongOrMissingTokenIsRejectedAndSocketClosed() throws Exception {
            try (Socket socket = connect()) {
                assertThat(exchange(socket, new Message.Register("w-wrong", 2, "nope")))
                        .isEqualTo(new Message.Error("unauthorized"));
                assertThat(socket.getInputStream().read()).as("coordinator closes the socket").isEqualTo(-1);
            }
            try (Socket socket = connect()) {
                assertThat(exchange(socket, new Message.Register("w-none", 2, null)))
                        .isEqualTo(new Message.Error("unauthorized"));
                assertThat(socket.getInputStream().read()).isEqualTo(-1);
            }
            assertThat(tokenWorkers.get("w-wrong")).isEmpty();
            assertThat(tokenWorkers.get("w-none")).isEmpty();
        }

        @Test
        void correctTokenRegisters() throws Exception {
            try (Socket socket = connect()) {
                assertThat(exchange(socket, new Message.Register("w-token", 2, TOKEN)))
                        .isEqualTo(new Message.Registered("w-token"));
                assertThat(tokenWorkers.get("w-token")).isPresent();
            }
        }
    }
}
