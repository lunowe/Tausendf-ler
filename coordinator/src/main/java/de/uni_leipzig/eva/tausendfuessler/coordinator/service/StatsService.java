package de.uni_leipzig.eva.tausendfuessler.coordinator.service;

import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobStatus;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.JobRepository;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.PageRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    public record Stats(long totalJobs, long activeJobs, long totalPagesCrawled, Map<String, Long> topDomains) {}

    private static final int TOP_N = 5;

    private final JobRepository jobs;
    private final PageRepository pages;

    public StatsService(JobRepository jobs, PageRepository pages) {
        this.jobs = jobs;
        this.pages = pages;
    }

    public Stats stats() {
        long totalJobs = jobs.count();
        long activeJobs = jobs.countByStatusIn(List.of(JobStatus.RUNNING, JobStatus.PAUSED));
        long totalPages = pages.count();
        return new Stats(totalJobs, activeJobs, totalPages, topDomains(pages.findAllUrls()));
    }

    static Map<String, Long> topDomains(List<String> urls) {
        Map<String, Long> counts = new HashMap<>();
        for (String url : urls) {
            String host = hostOf(url);
            if (host != null) {
                counts.merge(host, 1L, Long::sum);
            }
        }
        Map<String, Long> top = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_N)
                .forEach(e -> top.put(e.getKey(), e.getValue()));
        return top;
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
