package com.example.telegrambot.service;

import com.example.telegrambot.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Service
public class CoordinatorClient {

    private final RestTemplate restTemplate;

    @Value("${coordinator.api.base-url:http://localhost:8080}")
    private String baseUrl;

    public CoordinatorClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // Auftrag starten
    public CrawlResponse startCrawl(CrawlRequest request) {
        String url = baseUrl + "/api/crawl";
        ResponseEntity<CrawlResponse> response = restTemplate.postForEntity(
            url, request, CrawlResponse.class
        );
        return response.getBody();
    }

    // Job-Details abrufen (für Status)
    public JobDetail getJobDetail(String jobId) {
        String url = baseUrl + "/api/jobs/" + jobId;
        ResponseEntity<JobDetail> response = restTemplate.getForEntity(url, JobDetail.class);
        return response.getBody();
    }

    // Neue Ergebnisse seit Zeitstempel abrufen (für Live-Stream)
    public List<PageResult> getNewResults(String jobId, String sinceTimestamp) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/jobs/" + jobId + "/results")
                .queryParam("since", sinceTimestamp)
                .build()
                .toUri();

        ResponseEntity<PageResult[]> response = restTemplate.getForEntity(uri, PageResult[].class);
        return Arrays.asList(response.getBody());
    }

    // Job pausieren
    public void pauseJob(String jobId) {
        String url = baseUrl + "/api/jobs/" + jobId + "/pause";
        restTemplate.postForEntity(url, null, Void.class);
    }

    // Job fortsetzen
    public void resumeJob(String jobId) {
        String url = baseUrl + "/api/jobs/" + jobId + "/resume";
        restTemplate.postForEntity(url, null, Void.class);
    }

    // Job abbrechen
    public void abortJob(String jobId) {
        String url = baseUrl + "/api/jobs/" + jobId + "/abort";
        restTemplate.postForEntity(url, null, Void.class);
    }

    // Alle Jobs auflisten
    public List<JobInfo> listJobs() {
        String url = baseUrl + "/api/jobs";
        ResponseEntity<JobInfo[]> response = restTemplate.getForEntity(url, JobInfo[].class);
        return Arrays.asList(response.getBody());
    }

    // Statistiken abrufen
    public Stats getStats() {
        String url = baseUrl + "/api/stats";
        ResponseEntity<Stats> response = restTemplate.getForEntity(url, Stats.class);
        return response.getBody();
    }
}
