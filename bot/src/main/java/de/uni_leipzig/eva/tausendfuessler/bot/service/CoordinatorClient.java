package de.uni_leipzig.eva.tausendfuessler.bot.service;

import de.uni_leipzig.eva.tausendfuessler.bot.dto.CrawlRequest;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.CrawlResponse;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobDetail;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobInfo;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.PageResult;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.Stats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * REST client for the coordinator. The endpoints used here define the coordinator's public API (see README).
 */
@Service
public class CoordinatorClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CoordinatorClient(RestTemplate restTemplate,
                             @Value("${coordinator.api.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public CrawlResponse startCrawl(CrawlRequest request) {
        return restTemplate.postForObject(baseUrl + "/api/jobs", request, CrawlResponse.class);
    }

    public JobDetail getJobDetail(String jobId) {
        return restTemplate.getForObject(baseUrl + "/api/jobs/" + jobId, JobDetail.class);
    }

    /** Pages with seq > afterSeq, ascending. */
    public List<PageResult> getNewResults(String jobId, long afterSeq) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/api/jobs/" + jobId + "/results")
                .queryParam("afterSeq", afterSeq)
                .build()
                .toUri();
        PageResult[] body = restTemplate.getForObject(uri, PageResult[].class);
        return body == null ? List.of() : Arrays.asList(body);
    }

    public void pauseJob(String jobId) {
        restTemplate.postForObject(baseUrl + "/api/jobs/" + jobId + "/pause", null, Void.class);
    }

    public void resumeJob(String jobId) {
        restTemplate.postForObject(baseUrl + "/api/jobs/" + jobId + "/resume", null, Void.class);
    }

    public void abortJob(String jobId) {
        restTemplate.postForObject(baseUrl + "/api/jobs/" + jobId + "/abort", null, Void.class);
    }

    /** Jobs of one Telegram chat. */
    public List<JobInfo> listJobs(long chatId) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/api/jobs")
                .queryParam("owner", chatId)
                .build()
                .toUri();
        JobInfo[] body = restTemplate.getForObject(uri, JobInfo[].class);
        return body == null ? List.of() : Arrays.asList(body);
    }

    public Stats getStats() {
        return restTemplate.getForObject(baseUrl + "/api/stats", Stats.class);
    }
}
