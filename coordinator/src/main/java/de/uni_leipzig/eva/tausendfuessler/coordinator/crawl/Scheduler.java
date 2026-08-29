package de.uni_leipzig.eva.tausendfuessler.coordinator.crawl;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Answers REQUEST_WORK. Workers pull whenever they have free slots, so the worker with the least open work
 * naturally asks most often (Least-Work-First). Jobs are served round-robin so that many parallel jobs all progress.
 */
@Component
public class Scheduler {

    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);
    static final long RETRY_AFTER_MS = 500;

    private final JobRuntimeRegistry jobs;
    private final WorkerRegistry workers;
    private final AtomicInteger roundRobin = new AtomicInteger();

    public Scheduler(JobRuntimeRegistry jobs, WorkerRegistry workers) {
        this.jobs = jobs;
        this.workers = workers;
    }

    /** @return a {@link Message.WorkPackage} or {@link Message.NoWork} */
    public Message assign(Message.RequestWork request) {
        WorkerSession session = workers.get(request.workerId()).orElse(null);
        int packageSize = request.capacity();
        if (session != null) {
            packageSize = Math.min(packageSize, 2 * Math.max(1, session.threads()));
        }
        if (packageSize <= 0) {
            return new Message.NoWork(RETRY_AFTER_MS);
        }

        List<JobRuntime> running = new ArrayList<>();
        for (JobRuntime runtime : jobs.all()) {
            if (runtime.status() == JobStatus.RUNNING) {
                running.add(runtime);
            }
        }
        if (running.isEmpty()) {
            return new Message.NoWork(RETRY_AFTER_MS);
        }

        int start = Math.floorMod(roundRobin.getAndIncrement(), running.size());
        for (int i = 0; i < running.size(); i++) {
            JobRuntime runtime = running.get((start + i) % running.size());
            Message.WorkPackage pkg = runtime.takeWork(packageSize, request.workerId());
            if (pkg != null) {
                if (session != null) {
                    session.addInFlight(pkg.urls().size());
                }
                log.info("worker {} <- {} urls of job {} depth {} | in-flight per worker: {}",
                        request.workerId(), pkg.urls().size(), pkg.jobId(), pkg.depth(), workers.loadSnapshot());
                return pkg;
            }
        }
        return new Message.NoWork(RETRY_AFTER_MS);
    }
}
