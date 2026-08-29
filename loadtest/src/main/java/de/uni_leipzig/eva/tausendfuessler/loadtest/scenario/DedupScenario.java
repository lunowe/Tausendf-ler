package de.uni_leipzig.eva.tausendfuessler.loadtest.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import de.uni_leipzig.eva.tausendfuessler.loadtest.CoordinatorApi;
import de.uni_leipzig.eva.tausendfuessler.loadtest.InProcessWorkers;
import de.uni_leipzig.eva.tausendfuessler.loadtest.Scenario;
import de.uni_leipzig.eva.tausendfuessler.loadtest.ScenarioResult;
import de.uni_leipzig.eva.tausendfuessler.loadtest.SyntheticSite;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Two workers crawl a site where every page links to the same 50 hub pages, so both workers report the same
 * URLs thousands of times. Afterwards every URL must appear exactly once in the results.
 */
public final class DedupScenario implements Scenario {

    private static final Duration JOB_TIMEOUT = Duration.ofMinutes(5);
    private static final SyntheticSite.Config SITE = new SyntheticSite.Config(500, 3, 50, 0, 5);

    @Override
    public String name() {
        return "dedup";
    }

    @Override
    public String nfa() {
        return "Atomare und thread-sichere URL-Deduplizierung";
    }

    @Override
    public ScenarioResult run(Context context) throws Exception {
        CoordinatorApi api = context.api();
        try (SyntheticSite site = new SyntheticSite(SITE);
             InProcessWorkers ignored = InProcessWorkers.start(context.options(), "lt-dedup", 2, 4)) {
            String jobId = api.createJobOrThrow(site.startUrl(), 3);
            System.out.println("  Job " + jobId + " laeuft mit 2 Workern ...");
            JsonNode detail = api.awaitJob(jobId, CoordinatorApi::isCompleted, JOB_TIMEOUT);

            List<JsonNode> results = api.allResults(jobId);
            Map<String, Integer> occurrences = new HashMap<>();
            for (JsonNode result : results) {
                occurrences.merge(result.path("url").asText(), 1, Integer::sum);
            }
            List<String> duplicates = new ArrayList<>();
            occurrences.forEach((url, count) -> {
                if (count > 1) {
                    duplicates.add(url + " x" + count);
                }
            });
            long pagesVisited = detail.path("pagesVisited").asLong();

            Map<String, String> numbers = new LinkedHashMap<>();
            numbers.put("Site", SITE.pages() + " Seiten, jede verlinkt dieselben " + SITE.popularPages() + " Hub-Seiten");
            numbers.put("Gemeldete Links (linksFound)", detail.path("linksFound").asText());
            numbers.put("pagesVisited laut Job", String.valueOf(pagesVisited));
            numbers.put("Ergebniszeilen in /results", String.valueOf(results.size()));
            numbers.put("Verschiedene URLs", String.valueOf(occurrences.size()));
            numbers.put("Mehrfach enthaltene URLs", String.valueOf(duplicates.size()));
            List<String> notes = new ArrayList<>();
            notes.add("2 In-Prozess-Worker mit je 4 Threads, maxDepth 3.");
            if (!duplicates.isEmpty()) {
                notes.add("Duplikate: " + String.join(", ", duplicates.subList(0, Math.min(10, duplicates.size()))));
            }
            boolean passed = duplicates.isEmpty() && results.size() == pagesVisited && pagesVisited > 0;
            return new ScenarioResult(name(), nfa(), passed, numbers, notes);
        }
    }
}
