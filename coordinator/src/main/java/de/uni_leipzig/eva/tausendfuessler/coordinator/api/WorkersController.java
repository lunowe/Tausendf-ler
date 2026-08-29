package de.uni_leipzig.eva.tausendfuessler.coordinator.api;

import de.uni_leipzig.eva.tausendfuessler.coordinator.api.ApiDtos.WorkerInfo;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.WorkerRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api")
public class WorkersController {

    private final WorkerRegistry workers;

    public WorkersController(WorkerRegistry workers) {
        this.workers = workers;
    }

    /** {@code [{workerId, threads, inFlight, connectedAt}]} - currently connected workers, oldest connection first. */
    @GetMapping("/workers")
    public List<WorkerInfo> workers() {
        return workers.all().stream()
                .map(WorkerInfo::of)
                .sorted(Comparator.comparing(WorkerInfo::connectedAt).thenComparing(WorkerInfo::workerId))
                .toList();
    }
}
