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

    // NEU: Neue Ergebnisse seit Zeitstempel abrufen (für Live-Stream)
    public List<PageResult> getNewResults(String jobId, String sinceTimestamp) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/jobs/" + jobId + "/results")
                .queryParam("since", sinceTimestamp)
                .build()
                .toUri();

        ResponseEntity<PageResult[]> response = restTemplate.getForEntity(uri, PageResult[].class);
        return Arrays.asList(response.getBody());
    }
}
