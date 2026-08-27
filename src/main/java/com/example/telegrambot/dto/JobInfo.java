package com.example.telegrambot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class JobInfo {
    @JsonProperty("jobId")
    private String jobId;

    @JsonProperty("url")
    private String url;

    @JsonProperty("status")
    private JobStatus status;

    @JsonProperty("pagesVisited")
    private int pagesVisited;

    @JsonProperty("createdAt")
    private Instant createdAt;

    public JobInfo() {}

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public int getPagesVisited() { return pagesVisited; }
    public void setPagesVisited(int pagesVisited) { this.pagesVisited = pagesVisited; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
