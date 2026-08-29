package de.uni_leipzig.eva.tausendfuessler.coordinator.api;

import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobStatus;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.JobEntity;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.PageEntity;

import java.time.Instant;
import java.util.List;

/** REST request/response records. Field names are the contract with the bot DTOs (PROTOCOL.md section 1). */
public final class ApiDtos {

    private ApiDtos() {}

    public record CreateJobRequest(String url, Integer maxDepth, List<String> filters, Long owner) {}

    public record JobCreated(String jobId, JobStatus status, String message) {}

    public record JobSummary(String jobId, String url, JobStatus status, long pagesVisited, Instant createdAt) {
        static JobSummary of(JobEntity j) {
            return new JobSummary(j.getId(), j.getUrl(), j.getStatus(), j.getPagesVisited(), j.getCreatedAt());
        }
    }

    public record JobDetail(String jobId, String url, int maxDepth, int currentDepth, JobStatus status,
                            long pagesVisited, long linksFound, long errors,
                            Instant startedAt, Instant finishedAt, Instant createdAt) {
        static JobDetail of(JobEntity j) {
            return new JobDetail(j.getId(), j.getUrl(), j.getMaxDepth(), j.getCurrentDepth(), j.getStatus(),
                    j.getPagesVisited(), j.getLinksFound(), j.getErrors(),
                    j.getStartedAt(), j.getFinishedAt(), j.getCreatedAt());
        }
    }

    public record PageResult(long seq, String url, String title, String textSnippet, int depth, Instant crawledAt) {
        static PageResult of(PageEntity p) {
            return new PageResult(p.getSeq(), p.getUrl(), p.getTitle(), p.getTextSnippet(), p.getDepth(), p.getCrawledAt());
        }
    }

    public record SearchHit(String url, String title, String textSnippet, String jobId) {
        static SearchHit of(PageEntity p) {
            return new SearchHit(p.getUrl(), p.getTitle(), p.getTextSnippet(), p.getJobId());
        }
    }

    public record ErrorBody(String error) {}
}
