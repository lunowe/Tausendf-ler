package de.uni_leipzig.eva.tausendfuessler.coordinator.api;

import de.uni_leipzig.eva.tausendfuessler.coordinator.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /** {@code {totalJobs, activeJobs, totalPagesCrawled, topDomains{domain: count}}} */
    @GetMapping("/stats")
    public StatsService.Stats stats() {
        return statsService.stats();
    }
}
