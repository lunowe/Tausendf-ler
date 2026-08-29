package de.uni_leipzig.eva.tausendfuessler.coordinator.service;

import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobStatus;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.JobEntity;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * The frontier lives only in memory. Jobs that were still active when the coordinator last stopped
 * cannot be continued, so they are marked FAILED at startup.
 */
@Component
public class StartupRecovery {

    private static final Logger log = LoggerFactory.getLogger(StartupRecovery.class);

    private final JobRepository jobs;

    public StartupRecovery(JobRepository jobs) {
        this.jobs = jobs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failOrphanedJobs() {
        List<JobEntity> orphans = jobs.findByStatusIn(List.of(JobStatus.PENDING, JobStatus.RUNNING, JobStatus.PAUSED));
        for (JobEntity job : orphans) {
            job.setStatus(JobStatus.FAILED);
            job.setFinishedAt(Instant.now());
        }
        jobs.saveAll(orphans);
        if (!orphans.isEmpty()) {
            log.warn("marked {} job(s) from a previous run as FAILED", orphans.size());
        }
    }
}
