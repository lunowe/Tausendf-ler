package de.uni_leipzig.eva.tausendfuessler.coordinator.api;

import de.uni_leipzig.eva.tausendfuessler.coordinator.api.ApiDtos.CreateJobRequest;
import de.uni_leipzig.eva.tausendfuessler.coordinator.api.ApiDtos.JobCreated;
import de.uni_leipzig.eva.tausendfuessler.coordinator.api.ApiDtos.JobDetail;
import de.uni_leipzig.eva.tausendfuessler.coordinator.api.ApiDtos.JobSummary;
import de.uni_leipzig.eva.tausendfuessler.coordinator.api.ApiDtos.PageResult;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.JobEntity;
import de.uni_leipzig.eva.tausendfuessler.coordinator.service.JobService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobCreated create(@RequestBody CreateJobRequest request) {
        if (request == null || request.url() == null || request.url().isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        if (request.owner() == null) {
            throw new IllegalArgumentException("owner is required");
        }
        int maxDepth = request.maxDepth() == null ? 0 : request.maxDepth();
        JobEntity job = jobService.createJob(request.url(), maxDepth, request.filters(), request.owner());
        return new JobCreated(job.getId(), job.getStatus(), "Crawl gestartet");
    }

    @GetMapping
    public List<JobSummary> list(@RequestParam("owner") long owner) {
        return jobService.list(owner).stream().map(JobSummary::of).toList();
    }

    @GetMapping("/{id}")
    public JobDetail detail(@PathVariable("id") String id) {
        return JobDetail.of(jobService.getDetail(id));
    }

    @GetMapping("/{id}/results")
    public List<PageResult> results(@PathVariable("id") String id, @RequestParam(name = "afterSeq", defaultValue = "0") long afterSeq) {
        return jobService.results(id, afterSeq).stream().map(PageResult::of).toList();
    }

    @PostMapping("/{id}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pause(@PathVariable("id") String id) {
        jobService.pause(id);
    }

    @PostMapping("/{id}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resume(@PathVariable("id") String id) {
        jobService.resume(id);
    }

    @PostMapping("/{id}/abort")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abort(@PathVariable("id") String id) {
        jobService.abort(id);
    }
}
