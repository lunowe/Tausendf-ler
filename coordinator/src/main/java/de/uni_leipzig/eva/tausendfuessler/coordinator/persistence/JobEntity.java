package de.uni_leipzig.eva.tausendfuessler.coordinator.persistence;

import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "jobs")
public class JobEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 2048)
    private String url;

    private int maxDepth;

    /** Comma-joined filter strings; empty = no filter. */
    @Column(length = 2048)
    private String filters;

    /** Telegram chat id. */
    private long owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status;

    private long pagesVisited;
    private long linksFound;
    private long errors;
    /** Highest depth for which a page result has arrived; see JobRuntime#currentDepth. */
    private int currentDepth;

    @Column(nullable = false)
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;

    protected JobEntity() {}

    public JobEntity(String id, String url, int maxDepth, List<String> filters, long owner, Instant createdAt) {
        this.id = id;
        this.url = url;
        this.maxDepth = maxDepth;
        this.filters = String.join(",", filters);
        this.owner = owner;
        this.status = JobStatus.PENDING;
        this.createdAt = createdAt;
    }

    public List<String> filterList() {
        if (filters == null || filters.isBlank()) {
            return List.of();
        }
        return Arrays.stream(filters.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public String getId() { return id; }
    public String getUrl() { return url; }
    public int getMaxDepth() { return maxDepth; }
    public String getFilters() { return filters; }
    public long getOwner() { return owner; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public long getPagesVisited() { return pagesVisited; }
    public void setPagesVisited(long pagesVisited) { this.pagesVisited = pagesVisited; }
    public long getLinksFound() { return linksFound; }
    public void setLinksFound(long linksFound) { this.linksFound = linksFound; }
    public long getErrors() { return errors; }
    public void setErrors(long errors) { this.errors = errors; }
    public int getCurrentDepth() { return currentDepth; }
    public void setCurrentDepth(int currentDepth) { this.currentDepth = currentDepth; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
