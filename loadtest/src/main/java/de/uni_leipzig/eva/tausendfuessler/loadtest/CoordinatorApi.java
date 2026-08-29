package de.uni_leipzig.eva.tausendfuessler.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/** Thin client for the coordinator REST API (PROTOCOL.md section 1). Records status and latency of every call. */
public final class CoordinatorApi {

    /** Telegram chat id used as owner of all load-test jobs. */
    public static final long OWNER = 4711;

    /** {@code status == 0} means the request never got an HTTP answer (connection refused, timeout, ...). */
    public record Response(int status, JsonNode body, long latencyMs, String error) {
        public boolean is2xx() {
            return status >= 200 && status < 300;
        }
    }

    public record Call(String method, String path, int status, long latencyMs) {}

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

    public CoordinatorApi(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Response health() {
        return get("/api/health");
    }

    public Response stats() {
        return get("/api/stats");
    }

    public Response createJob(String url, int maxDepth) {
        return postJson("/api/jobs", Map.of("url", url, "maxDepth", maxDepth, "filters", List.of(), "owner", OWNER));
    }

    public Response list(long owner) {
        return get("/api/jobs?owner=" + owner);
    }

    public Response job(String jobId) {
        return get("/api/jobs/" + jobId);
    }

    public Response results(String jobId, long afterSeq) {
        return get("/api/jobs/" + jobId + "/results?afterSeq=" + afterSeq);
    }

    public Response pause(String jobId) {
        return post("/api/jobs/" + jobId + "/pause", HttpRequest.BodyPublishers.noBody(), null);
    }

    public Response resume(String jobId) {
        return post("/api/jobs/" + jobId + "/resume", HttpRequest.BodyPublishers.noBody(), null);
    }

    public Response abort(String jobId) {
        return post("/api/jobs/" + jobId + "/abort", HttpRequest.BodyPublishers.noBody(), null);
    }

    public Response get(String path) {
        return send("GET", path, HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
    }

    public Response postJson(String path, Object body) {
        try {
            return postRaw(path, json.writeValueAsString(body));
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot serialize body", e);
        }
    }

    /** Posts the string as-is (also deliberately broken JSON). */
    public Response postRaw(String path, String body) {
        return post(path, HttpRequest.BodyPublishers.ofString(body), "application/json");
    }

    private Response post(String path, HttpRequest.BodyPublisher body, String contentType) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).POST(body);
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        return send("POST", path, request);
    }

    private Response send(String method, String path, HttpRequest.Builder request) {
        long start = System.nanoTime();
        int status;
        JsonNode body = null;
        String error = null;
        try {
            HttpResponse<String> response = http.send(request.timeout(REQUEST_TIMEOUT).build(),
                    HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();
            body = parse(response.body());
        } catch (IOException e) {
            status = 0;
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            status = 0;
            error = "interrupted";
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        calls.add(new Call(method, path, status, latencyMs));
        return new Response(status, body, latencyMs, error);
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return json.missingNode();
        }
        try {
            return json.readTree(body);
        } catch (IOException e) {
            return json.missingNode();
        }
    }

    /** Snapshot of all calls made so far. */
    public List<Call> calls() {
        synchronized (calls) {
            return new ArrayList<>(calls);
        }
    }

    // ---- convenience on top of the raw calls ----

    public String createJobOrThrow(String url, int maxDepth) {
        Response created = createJob(url, maxDepth);
        if (created.status() != 201 || created.body() == null || !created.body().hasNonNull("jobId")) {
            throw new IllegalStateException("job creation failed: HTTP " + created.status() + " " + created.error()
                    + " " + created.body());
        }
        return created.body().get("jobId").asText();
    }

    public JsonNode awaitJob(String jobId, Predicate<JsonNode> condition, Duration timeout) throws TimeoutException {
        long deadline = System.nanoTime() + timeout.toNanos();
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            Response response = job(jobId);
            if (response.status() == 200) {
                last = response.body();
                if (condition.test(last)) {
                    return last;
                }
            }
            sleep(200);
        }
        throw new TimeoutException("timeout after " + timeout.toSeconds() + " s waiting for job " + jobId
                + ", last state: " + last);
    }

    public static boolean isCompleted(JsonNode detail) {
        return "COMPLETED".equals(detail.path("status").asText());
    }

    public static boolean isFinal(JsonNode detail) {
        String status = detail.path("status").asText();
        return "COMPLETED".equals(status) || "ABORTED".equals(status) || "FAILED".equals(status);
    }

    /** Pages through {@code /results} like the bot does and returns everything. */
    public List<JsonNode> allResults(String jobId) {
        List<JsonNode> all = new ArrayList<>();
        long afterSeq = 0;
        while (true) {
            Response page = results(jobId, afterSeq);
            if (page.status() != 200) {
                throw new IllegalStateException("results of " + jobId + " failed: HTTP " + page.status() + " " + page.error());
            }
            if (page.body().isEmpty()) {
                return all;
            }
            page.body().forEach(all::add);
            afterSeq = page.body().get(page.body().size() - 1).get("seq").asLong();
        }
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
