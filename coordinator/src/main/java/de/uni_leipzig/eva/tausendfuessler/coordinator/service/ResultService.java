package de.uni_leipzig.eva.tausendfuessler.coordinator.service;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobRuntime;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobRuntimeRegistry;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobStatus;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.WorkerRegistry;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.WorkerSession;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.JobRepository;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.PageEntity;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.PageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Handles PAGE_RESULT messages. Called concurrently from several worker handler threads; the per-job part is
 * synchronized on the {@link JobRuntime} so that seq numbers, counters and the completion check are consistent.
 */
@Service
public class ResultService {

    private static final Logger log = LoggerFactory.getLogger(ResultService.class);

    private final JobRuntimeRegistry runtimes;
    private final WorkerRegistry workers;
    private final JobRepository jobs;
    private final PageRepository pages;

    public ResultService(JobRuntimeRegistry runtimes, WorkerRegistry workers, JobRepository jobs, PageRepository pages) {
        this.runtimes = runtimes;
        this.workers = workers;
        this.jobs = jobs;
        this.pages = pages;
    }

    public void handle(Message.PageResult result) {
        workers.get(result.workerId()).ifPresent(WorkerSession::completedOne);

        JobRuntime runtime = runtimes.get(result.jobId()).orElse(null);
        if (runtime == null) {
            log.debug("result for inactive job {} ignored ({})", result.jobId(), result.url());
            return;
        }

        synchronized (runtime) {
            if (runtime.status() == JobStatus.ABORTED) {
                return; // aborted while this result was waiting for the lock; the job row is already final
            }
            runtime.complete(result.url());
            if (result.error() == null) {
                long seq = runtime.nextSeq();
                Instant crawledAt = Instant.ofEpochMilli(result.crawledAtEpochMs());
                pages.save(new PageEntity(runtime.jobId(), seq, result.url(), result.depth(), result.httpStatus(),
                        result.title(), result.textSnippet(), crawledAt, null));
                runtime.incrementPagesVisited();
                List<String> links = result.links() == null ? List.of() : result.links();
                runtime.addLinksFound(links.size());
                int queued = runtime.offerLinks(result.depth(), links);
                log.debug("job {} seq {} {} -> {} links, {} new", runtime.jobId(), seq, result.url(), links.size(), queued);
            } else {
                runtime.incrementErrors();
                log.info("job {} error at {}: {}", runtime.jobId(), result.url(), result.error());
            }

            jobs.findById(runtime.jobId()).ifPresent(job -> {
                job.setPagesVisited(runtime.pagesVisited());
                job.setLinksFound(runtime.linksFound());
                job.setErrors(runtime.errors());
                if (runtime.isFinished()) {
                    job.setStatus(JobStatus.COMPLETED);
                    job.setFinishedAt(Instant.now());
                    runtimes.remove(runtime.jobId());
                    log.info("job {} completed: {} pages, {} links, {} errors",
                            job.getId(), job.getPagesVisited(), job.getLinksFound(), job.getErrors());
                }
                jobs.save(job);
            });
        }
    }
}
