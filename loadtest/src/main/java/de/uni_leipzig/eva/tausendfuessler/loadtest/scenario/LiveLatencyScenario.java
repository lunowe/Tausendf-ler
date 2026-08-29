package de.uni_leipzig.eva.tausendfuessler.loadtest.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import de.uni_leipzig.eva.tausendfuessler.loadtest.CoordinatorApi;
import de.uni_leipzig.eva.tausendfuessler.loadtest.InProcessWorkers;
import de.uni_leipzig.eva.tausendfuessler.loadtest.LatencyStats;
import de.uni_leipzig.eva.tausendfuessler.loadtest.Scenario;
import de.uni_leipzig.eva.tausendfuessler.loadtest.ScenarioResult;
import de.uni_leipzig.eva.tausendfuessler.loadtest.SyntheticSite;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Polls /results every 500 ms like the bot and measures, per page, the time between the worker's
 * {@code crawledAt} and the moment the page arrived at this client.
 */
public final class LiveLatencyScenario implements Scenario {

    static final long LIMIT_MS = 2_000;
    private static final long POLL_MS = 500;
    private static final Duration JOB_TIMEOUT = Duration.ofMinutes(5);
    private static final SyntheticSite.Config SITE = new SyntheticSite.Config(200, 5, 3, 100, 4);

    @Override
    public String name() {
        return "live-latency";
    }

    @Override
    public String nfa() {
        return "Live-Ergebnisse innerhalb von 2 s nach Seitenabruf";
    }

    @Override
    public ScenarioResult run(Context context) throws Exception {
        CoordinatorApi api = context.api();
        try (SyntheticSite site = new SyntheticSite(SITE);
             InProcessWorkers ignored = InProcessWorkers.start(context.options(), "lt-live", 2, 4)) {
            String jobId = api.createJobOrThrow(site.startUrl(), 3);
            System.out.println("  Job " + jobId + " laeuft, polle /results alle " + POLL_MS + " ms ...");

            List<Long> delays = new ArrayList<>();
            long afterSeq = 0;
            long deadline = System.nanoTime() + JOB_TIMEOUT.toNanos();
            boolean jobFinal = false;
            while (System.nanoTime() < deadline) {
                CoordinatorApi.Response page = api.results(jobId, afterSeq);
                Instant received = Instant.now();
                if (page.status() == 200 && !page.body().isEmpty()) {
                    for (JsonNode result : page.body()) {
                        Instant crawledAt = Instant.parse(result.path("crawledAt").asText());
                        delays.add(Duration.between(crawledAt, received).toMillis());
                        afterSeq = result.path("seq").asLong();
                    }
                    continue; // drain the backlog before sleeping again
                }
                if (jobFinal) {
                    break; // final status seen and the last poll returned nothing more
                }
                JsonNode detail = api.job(jobId).body();
                jobFinal = detail != null && CoordinatorApi.isFinal(detail);
                CoordinatorApi.sleep(POLL_MS);
            }

            JsonNode detail = api.job(jobId).body();
            LatencyStats stats = LatencyStats.of(delays);
            long exceeded = LatencyStats.countAbove(delays, LIMIT_MS);
            Map<String, String> numbers = new LinkedHashMap<>();
            numbers.put("Job-Status am Ende", detail == null ? "?" : detail.path("status").asText());
            numbers.put("Empfangene Seiten", String.valueOf(stats.count()));
            numbers.put("Verzoegerung p50", stats.p50() + " ms");
            numbers.put("Verzoegerung p95", stats.p95() + " ms");
            numbers.put("Verzoegerung max", stats.max() + " ms");
            numbers.put("Ueberschreitungen (> " + LIMIT_MS + " ms)", String.valueOf(exceeded));
            boolean passed = exceeded == 0 && stats.count() > 0 && detail != null && CoordinatorApi.isCompleted(detail);
            return new ScenarioResult(name(), nfa(), passed, numbers, List.of(
                    "Verzoegerung = Empfang beim Client - crawledAt des Workers; Worker laufen im selben Prozess, "
                            + "die Uhren sind also identisch. Poll-Intervall " + POLL_MS + " ms (Bot: 1 s). Site: "
                            + SITE.pages() + " Seiten, " + SITE.delayMs() + " ms Verzoegerung, 2 Worker."));
        }
    }
}
