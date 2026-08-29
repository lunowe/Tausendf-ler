package de.uni_leipzig.eva.tausendfuessler.coordinator.crawl;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Active (RUNNING/PAUSED) jobs by id. */
@Component
public class JobRuntimeRegistry {

    private final ConcurrentMap<String, JobRuntime> jobs = new ConcurrentHashMap<>();

    public void register(JobRuntime runtime) {
        jobs.put(runtime.jobId(), runtime);
    }

    public Optional<JobRuntime> get(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public JobRuntime remove(String jobId) {
        return jobs.remove(jobId);
    }

    public Collection<JobRuntime> all() {
        return jobs.values();
    }

    public int size() {
        return jobs.size();
    }
}
