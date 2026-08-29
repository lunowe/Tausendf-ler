package de.uni_leipzig.eva.tausendfuessler.coordinator.api;

import de.uni_leipzig.eva.tausendfuessler.coordinator.api.ApiDtos.SearchHit;
import de.uni_leipzig.eva.tausendfuessler.coordinator.persistence.SearchRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SearchController {

    private static final int MAX_LIMIT = 100;

    private final SearchRepository searchRepository;

    public SearchController(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    @GetMapping("/search")
    public List<SearchHit> search(@RequestParam("q") String q, @RequestParam(name = "limit", defaultValue = "10") int limit) {
        if (q.isBlank()) {
            throw new IllegalArgumentException("q must not be blank");
        }
        int effectiveLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return searchRepository.search(q.trim(), effectiveLimit).stream().map(SearchHit::of).toList();
    }
}
