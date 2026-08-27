package com.example.telegrambot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CrawlResponse {
    @JsonProperty("jobId")
    private String jobId;

    @JsonProperty("status")
    private JobStatus status;

    @JsonProperty("message")
    private String message;

    public CrawlResponse() {}

    public CrawlResponse(String jobId, JobStatus status, String message) {
        this.jobId = jobId;
        this.status = status;
        this.message = message;
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
