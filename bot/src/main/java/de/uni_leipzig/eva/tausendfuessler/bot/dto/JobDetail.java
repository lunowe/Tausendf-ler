package de.uni_leipzig.eva.tausendfuessler.bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class JobDetail {
    @JsonProperty("jobId")
    private String jobId;

    @JsonProperty("url")
    private String url;

    @JsonProperty("maxDepth")
    private int maxDepth;

    @JsonProperty("status")
    private JobStatus status;

    @JsonProperty("pagesVisited")
    private int pagesVisited;

    @JsonProperty("linksFound")
    private int linksFound;

    @JsonProperty("errors")
    private int errors;

    @JsonProperty("startedAt")
    private Instant startedAt;

    @JsonProperty("finishedAt")
    private Instant finishedAt;

    public JobDetail() {}

    // Getter
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public int getPagesVisited() { return pagesVisited; }
    public void setPagesVisited(int pagesVisited) { this.pagesVisited = pagesVisited; }

    public int getLinksFound() { return linksFound; }
    public void setLinksFound(int linksFound) { this.linksFound = linksFound; }

    public int getErrors() { return errors; }
    public void setErrors(int errors) { this.errors = errors; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
