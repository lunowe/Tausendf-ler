package de.uni_leipzig.eva.tausendfuessler.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.CrawlRequest;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.CrawlResponse;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobDetail;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobInfo;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.PageResult;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.SearchHit;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.Stats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * REST client for the coordinator. The endpoints used here define the coordinator's public API (see PROTOCOL.md).
 * Error answers ({@code 4xx/5xx} with body {@code {error: "..."}}) become {@link CoordinatorException}s whose
 * message is suitable for the chat.
 */
@Service
public class CoordinatorClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CoordinatorClient(RestTemplate restTemplate,
                             @Value("${coordinator.api.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public CrawlResponse startCrawl(CrawlRequest request) {
        return call(() -> restTemplate.postForObject(baseUrl + "/api/jobs", request, CrawlResponse.class));
    }

    public JobDetail getJobDetail(String jobId) {
        return call(() -> restTemplate.getForObject(baseUrl + "/api/jobs/" + jobId, JobDetail.class));
    }

    /** Pages with seq > afterSeq, ascending. */
    public List<PageResult> getNewResults(String jobId, long afterSeq) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/api/jobs/" + jobId + "/results")
                .queryParam("afterSeq", afterSeq)
                .build()
                .toUri();
        PageResult[] body = call(() -> restTemplate.getForObject(uri, PageResult[].class));
        return body == null ? List.of() : Arrays.asList(body);
    }

    public void pauseJob(String jobId) {
        call(() -> restTemplate.postForObject(baseUrl + "/api/jobs/" + jobId + "/pause", null, Void.class));
    }

    public void resumeJob(String jobId) {
        call(() -> restTemplate.postForObject(baseUrl + "/api/jobs/" + jobId + "/resume", null, Void.class));
    }

    public void abortJob(String jobId) {
        call(() -> restTemplate.postForObject(baseUrl + "/api/jobs/" + jobId + "/abort", null, Void.class));
    }

    /** Jobs of one Telegram chat. */
    public List<JobInfo> listJobs(long chatId) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/api/jobs")
                .queryParam("owner", chatId)
                .build()
                .toUri();
        JobInfo[] body = call(() -> restTemplate.getForObject(uri, JobInfo[].class));
        return body == null ? List.of() : Arrays.asList(body);
    }

    public Stats getStats() {
        return call(() -> restTemplate.getForObject(baseUrl + "/api/stats", Stats.class));
    }

    /** Full-text search over all crawled pages, at most {@code limit} hits. */
    public List<SearchHit> search(String query, int limit) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/api/search")
                .queryParam("q", query)
                .queryParam("limit", limit)
                .build()
                .toUri();
        SearchHit[] body = call(() -> restTemplate.getForObject(uri, SearchHit[].class));
        return body == null ? List.of() : Arrays.asList(body);
    }

    /** Runs one request; an HTTP error status becomes a {@link CoordinatorException} carrying the body's error text. */
    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpStatusCodeException e) {
            throw new CoordinatorException(errorMessage(e));
        }
    }

    private String errorMessage(HttpStatusCodeException e) {
        try {
            JsonNode error = objectMapper.readTree(e.getResponseBodyAsString()).get("error");
            if (error != null && !error.asText().isBlank()) {
                return error.asText();
            }
        } catch (IOException | RuntimeException ignored) {
            // no JSON body: fall through to the status text
        }
        return e.getStatusText().isBlank() ? String.valueOf(e.getStatusCode().value()) : e.getStatusText();
    }
}
