package de.uni_leipzig.eva.tausendfuessler.coordinator.service;

import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.JobEntity;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.JobRepository;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.PageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Deletes jobs (and their pages) older than {@code tausendfuessler.result-retention-days} once at startup. */
@Component
public class RetentionCleanup {

    private static final Logger log = LoggerFactory.getLogger(RetentionCleanup.class);

    private final JobRepository jobs;
    private final PageRepository pages;
    private final int retentionDays;

    public RetentionCleanup(JobRepository jobs, PageRepository pages,
                            @Value("${tausendfuessler.result-retention-days:30}") int retentionDays) {
        this.jobs = jobs;
        this.pages = pages;
        this.retentionDays = retentionDays;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        List<JobEntity> old = jobs.findByCreatedAtBefore(cutoff);
        long deletedPages = 0;
        for (JobEntity job : old) {
            deletedPages += pages.deleteByJobId(job.getId());
        }
        long deletedJobs = jobs.deleteByCreatedAtBefore(cutoff);
        log.info("retention cleanup: deleted {} jobs and {} pages older than {} days", deletedJobs, deletedPages, retentionDays);
    }
}
