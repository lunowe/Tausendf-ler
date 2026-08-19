package com.example.telegrambot.service;

import com.example.telegrambot.dto.CrawlRequest;
import com.example.telegrambot.dto.CrawlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    public JobDetail getJobDetail(String jobId) {
        String url = baseUrl + "/api/jobs/" + jobId;
        ResponseEntity<JobDetail> response = restTemplate.getForEntity(url, JobDetail.class);
        return response.getBody();
    }
}
