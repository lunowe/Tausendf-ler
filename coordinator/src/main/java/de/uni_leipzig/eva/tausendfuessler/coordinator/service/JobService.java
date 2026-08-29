package de.uni_leipzig.eva.tausendfuessler.coordinator.service;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobRuntime;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobRuntimeRegistry;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobStatus;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.UrlNormalizer;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.WorkerRegistry;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.JobEntity;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.JobRepository;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.PageEntity;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.PageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Job lifecycle: create, pause, resume, abort, read. Keeps DB status and in-memory runtime in sync. */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    static final int RESULT_PAGE_SIZE = 50;

    private final JobRepository jobs;
    private final PageRepository pages;
    private final JobRuntimeRegistry runtimes;
    private final WorkerRegistry workers;

    public JobService(JobRepository jobs, PageRepository pages, JobRuntimeRegistry runtimes, WorkerRegistry workers) {
        this.jobs = jobs;
        this.pages = pages;
        this.runtimes = runtimes;
        this.workers = workers;
    }

    public JobEntity createJob(String url, int maxDepth, List<String> filters, long owner) {
        if (UrlNormalizer.normalize(url) == null) {
            throw new IllegalArgumentException("url must be an absolute http(s) URL");
        }
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be >= 0");
        }
        List<String> cleanFilters = (filters == null ? List.<String>of() : filters).stream()
                .filter(f -> f != null && !f.isBlank()).map(String::trim).toList();

        JobEntity job = new JobEntity(UUID.randomUUID().toString(), url.trim(), maxDepth, cleanFilters, owner, Instant.now());
        jobs.save(job); // PENDING

        JobRuntime runtime = new JobRuntime(job.getId(), job.getUrl(), maxDepth, cleanFilters);
        runtimes.register(runtime);

        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        jobs.save(job);
        log.info("job {} created by {}: {} (maxDepth={}, filters={})", job.getId(), owner, job.getUrl(), maxDepth, cleanFilters);
        return job;
    }

    public void pause(String jobId) {
        JobEntity job = require(jobId);
        transition(job, JobStatus.RUNNING, JobStatus.PAUSED);
        runtimes.get(jobId).ifPresent(r -> r.setStatus(JobStatus.PAUSED));
        workers.broadcast(new Message.JobSignal(jobId, Message.Signal.PAUSE));
    }

    public void resume(String jobId) {
        JobEntity job = require(jobId);
        transition(job, JobStatus.PAUSED, JobStatus.RUNNING);
        runtimes.get(jobId).ifPresent(r -> r.setStatus(JobStatus.RUNNING));
        workers.broadcast(new Message.JobSignal(jobId, Message.Signal.RESUME));
    }

    public void abort(String jobId) {
        JobEntity job = require(jobId);
        JobStatus current = job.getStatus();
        if (current != JobStatus.RUNNING && current != JobStatus.PAUSED) {
            throw new IllegalStateException("Cannot abort job in status " + current);
        }
        job.setStatus(JobStatus.ABORTED);
        job.setFinishedAt(Instant.now());
        jobs.save(job);
        runtimes.remove(jobId);
        workers.broadcast(new Message.JobSignal(jobId, Message.Signal.ABORT));
        log.info("job {} aborted", jobId);
    }

    public JobEntity getDetail(String jobId) {
        return require(jobId);
    }

    public List<JobEntity> list(long owner) {
        return jobs.findByOwnerOrderByCreatedAtDesc(owner);
    }

    /** Pages with {@code seq > afterSeq}, ascending, at most {@link #RESULT_PAGE_SIZE}. */
    public List<PageEntity> results(String jobId, long afterSeq) {
        require(jobId);
        return pages.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, afterSeq, PageRequest.of(0, RESULT_PAGE_SIZE));
    }

    private JobEntity require(String jobId) {
        return jobs.findById(jobId).orElseThrow(() -> new NoSuchElementException("Unknown job " + jobId));
    }

    private void transition(JobEntity job, JobStatus from, JobStatus to) {
        if (job.getStatus() != from) {
            throw new IllegalStateException("Cannot go from " + job.getStatus() + " to " + to);
        }
        job.setStatus(to);
        jobs.save(job);
        log.info("job {} {} -> {}", job.getId(), from, to);
    }
}
