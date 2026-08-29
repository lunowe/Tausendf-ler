package de.uni_leipzig.eva.tausendfuessler.coordinator;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import de.uni_leipzig.eva.tausendfuessler.coordinator.socket.WorkerSocketServer;
import de.uni_leipzig.eva.tausendfuessler.worker.WorkerClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full stack: REST -> coordinator -> TCP -> two real {@link WorkerClient}s -> local HTTP site -> results in H2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** Site: / -> a,b,c ; a -> a1,a2,/ ; b -> b1 ; c = 404 ; a1,a2,b1 leaves. */
    private static final Map<String, String> SITE = Map.of(
            "/", page("Startseite", "Willkommen", "/a", "/b", "/c"),
            "/a", page("Bereich A", "Abschnitt A", "/a1", "/a2", "/"),
            "/b", page("Bereich B", "Abschnitt B", "/b1"),
            "/a1", page("Blatt A1", "Der Zebrafisch schwimmt im Aquarium", "/"),
            "/a2", page("Blatt A2", "Ein Igel im Garten"),
            "/b1", page("Blatt B1", "Die Giraffe frisst Blaetter"));
    /** Reachable OK pages within depth 2: / ; a,b ; a1,a2,b1 */
    private static final int OK_PAGES_DEPTH_2 = 6;

    @Autowired TestRestTemplate rest;
    @Autowired WorkerSocketServer socketServer;

    private HttpServer site;
    private String siteRoot;
    private final AtomicLong siteDelayMs = new AtomicLong();
    private final List<WorkerClient> workers = new ArrayList<>();
    private final List<Thread> workerThreads = new ArrayList<>();

    @BeforeAll
    void startSiteAndWorkers() throws IOException {
        site = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        site.setExecutor(Executors.newCachedThreadPool());
        site.createContext("/", exchange -> {
            sleep(siteDelayMs.get());
            String body = SITE.get(exchange.getRequestURI().getPath());
            int status = body == null ? 404 : 200;
            byte[] bytes = (body == null ? "not found" : body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        site.start();
        siteRoot = "http://127.0.0.1:" + site.getAddress().getPort() + "/";

        for (String id : List.of("e2e-w1", "e2e-w2")) {
            WorkerClient worker = new WorkerClient("127.0.0.1", socketServer.getPort(), id, 2);
            Thread thread = new Thread(worker::run, id);
            thread.setDaemon(true);
            thread.start();
            workers.add(worker);
            workerThreads.add(thread);
        }
    }

    @AfterAll
    void stopSiteAndWorkers() throws InterruptedException {
        workers.forEach(WorkerClient::close);
        for (Thread t : workerThreads) {
            t.join(5_000);
        }
        site.stop(0);
    }

    @BeforeEach
    void noDelay() {
        siteDelayMs.set(0);
    }

    @Test
    void crawlsSiteWithTwoWorkersAndExposesResults() {
        String jobId = createJob(2);
        JsonNode detail = awaitJob(jobId, d -> "COMPLETED".equals(d.get("status").asText()));

        assertThat(detail.get("pagesVisited").asLong()).isEqualTo(OK_PAGES_DEPTH_2);
        assertThat(detail.get("errors").asLong()).isEqualTo(1);
        assertThat(detail.get("currentDepth").asInt()).isEqualTo(2);

        List<JsonNode> results = allResults(jobId);
        assertThat(results).hasSize(OK_PAGES_DEPTH_2);
        Set<String> urls = new HashSet<>();
        long lastSeq = 0;
        for (JsonNode r : results) {
            assertThat(urls.add(r.get("url").asText())).as("duplicate url " + r.get("url")).isTrue();
            assertThat(r.get("seq").asLong()).isGreaterThan(lastSeq);
            lastSeq = r.get("seq").asLong();
        }
        assertThat(results).extracting(r -> r.get("title").asText())
                .containsExactlyInAnyOrder("Startseite", "Bereich A", "Bereich B", "Blatt A1", "Blatt A2", "Blatt B1");

        JsonNode list = rest.getForObject("/api/jobs?owner=42", JsonNode.class);
        assertThat(list.findValuesAsText("jobId")).contains(jobId);

        JsonNode hits = rest.getForObject("/api/search?q=Zebrafisch", JsonNode.class);
        assertThat(hits.findValuesAsText("url")).contains(siteRoot + "a1");

        JsonNode stats = rest.getForObject("/api/stats", JsonNode.class);
        assertThat(stats.get("totalPagesCrawled").asLong()).isGreaterThanOrEqualTo(OK_PAGES_DEPTH_2);
    }

    @Test
    void pauseStopsProgressAndResumeCompletes() {
        siteDelayMs.set(500); // keep the job alive long enough to pause it
        String jobId = createJob(3);
        assertThat(post("/api/jobs/" + jobId + "/pause").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        sleep(1_000); // let fetches that were already in flight finish and report
        JsonNode paused = detail(jobId);
        assertThat(paused.get("status").asText()).isEqualTo("PAUSED");
        long before = paused.get("pagesVisited").asLong();
        sleep(1_000);
        assertThat(detail(jobId).get("pagesVisited").asLong()).isEqualTo(before);

        assertThat(post("/api/jobs/" + jobId + "/resume").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        JsonNode done = awaitJob(jobId, d -> "COMPLETED".equals(d.get("status").asText()));
        assertThat(done.get("pagesVisited").asLong()).isEqualTo(OK_PAGES_DEPTH_2);
        assertThat(allResults(jobId)).hasSize(OK_PAGES_DEPTH_2);
    }

    @Test
    void abortKeepsResultsReadable() {
        siteDelayMs.set(300);
        String jobId = createJob(2);
        awaitJob(jobId, d -> d.get("pagesVisited").asLong() >= 1);
        assertThat(post("/api/jobs/" + jobId + "/abort").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode detail = detail(jobId);
        assertThat(detail.get("status").asText()).isEqualTo("ABORTED");
        List<JsonNode> results = allResults(jobId);
        assertThat(results).isNotEmpty().hasSize((int) detail.get("pagesVisited").asLong());
        assertThat(post("/api/jobs/" + jobId + "/resume").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- helpers ----

    private String createJob(int maxDepth) {
        ResponseEntity<JsonNode> created = rest.postForEntity("/api/jobs",
                Map.of("url", siteRoot, "maxDepth", maxDepth, "filters", List.of(), "owner", 42), JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody().get("jobId").asText();
    }

    private ResponseEntity<Void> post(String path) {
        return rest.postForEntity(path, null, Void.class);
    }

    private JsonNode detail(String jobId) {
        return rest.getForObject("/api/jobs/" + jobId, JsonNode.class);
    }

    private JsonNode awaitJob(String jobId, Predicate<JsonNode> condition) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        JsonNode last;
        do {
            last = detail(jobId);
            if (condition.test(last)) {
                return last;
            }
            sleep(100);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("timeout waiting for job " + jobId + ", last state: " + last);
    }

    private List<JsonNode> allResults(String jobId) {
        List<JsonNode> all = new ArrayList<>();
        long afterSeq = 0;
        while (true) {
            JsonNode page = rest.getForObject("/api/jobs/" + jobId + "/results?afterSeq=" + afterSeq, JsonNode.class);
            if (page.isEmpty()) {
                return all;
            }
            page.forEach(all::add);
            afterSeq = page.get(page.size() - 1).get("seq").asLong();
        }
    }

    private static String page(String title, String text, String... links) {
        StringBuilder html = new StringBuilder("<html><head><title>").append(title).append("</title></head><body><p>")
                .append(text).append("</p>");
        for (String link : links) {
            html.append("<a href=\"").append(link).append("\">").append(link).append("</a> ");
        }
        return html.append("</body></html>").toString();
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
