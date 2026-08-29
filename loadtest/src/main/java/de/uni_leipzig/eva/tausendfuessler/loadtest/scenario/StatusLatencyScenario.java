package de.uni_leipzig.eva.tausendfuessler.loadtest.scenario;

import de.uni_leipzig.eva.tausendfuessler.loadtest.CoordinatorApi;
import de.uni_leipzig.eva.tausendfuessler.loadtest.InProcessWorkers;
import de.uni_leipzig.eva.tausendfuessler.loadtest.LatencyStats;
import de.uni_leipzig.eva.tausendfuessler.loadtest.Scenario;
import de.uni_leipzig.eva.tausendfuessler.loadtest.ScenarioResult;
import de.uni_leipzig.eva.tausendfuessler.loadtest.SyntheticSite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 20 running jobs on a slow site, then 30 s of GET /api/jobs/{id} at ~10 req/s round-robin. One in-process worker
 * keeps the coordinator busy with results while the latencies are measured.
 */
public final class StatusLatencyScenario implements Scenario {

    static final long LIMIT_MS = 200;
    private static final int JOBS = 20;
    private static final long DURATION_MS = 30_000;
    private static final long INTERVAL_MS = 100;
    private static final SyntheticSite.Config SITE = new SyntheticSite.Config(300, 5, 3, 300, 1);

    @Override
    public String name() {
        return "status-latency";
    }

    @Override
    public String nfa() {
        return "Statusabfragen < 0,2 s bei < 20 gleichzeitigen Auftraegen";
    }

    @Override
    public ScenarioResult run(Context context) throws Exception {
        CoordinatorApi api = context.api();
        List<String> jobIds = new ArrayList<>();
        try (SyntheticSite site = new SyntheticSite(SITE);
             InProcessWorkers ignored = InProcessWorkers.start(context.options(), "lt-status", 1, 4)) {
            for (int i = 0; i < JOBS; i++) {
                jobIds.add(api.createJobOrThrow(site.startUrl(), 2));
            }
            // one uncounted request per job: the very first detail query on a freshly started coordinator takes
            // ~0.5 s (Hibernate query plan, JIT) and would otherwise be the only outlier in the measurement
            jobIds.forEach(api::job);
            System.out.printf("  %d Jobs angelegt, messe %d s lang Statusabfragen ...%n", JOBS, DURATION_MS / 1000);

            List<Long> latencies = new ArrayList<>();
            int notOk = 0;
            long start = System.nanoTime();
            long nextAt = start;
            for (int i = 0; (System.nanoTime() - start) / 1_000_000 < DURATION_MS; i++) {
                CoordinatorApi.Response response = api.job(jobIds.get(i % JOBS));
                latencies.add(response.latencyMs());
                if (response.status() != 200) {
                    notOk++;
                }
                nextAt += INTERVAL_MS * 1_000_000;
                long waitMs = (nextAt - System.nanoTime()) / 1_000_000;
                if (waitMs > 0) {
                    CoordinatorApi.sleep(waitMs);
                }
            }

            LatencyStats stats = LatencyStats.of(latencies);
            long exceeded = LatencyStats.countAbove(latencies, LIMIT_MS);
            Map<String, String> numbers = new LinkedHashMap<>();
            numbers.put("Gleichzeitige Auftraege", String.valueOf(JOBS));
            numbers.put("Statusabfragen", String.valueOf(stats.count()));
            numbers.put("p50", stats.p50() + " ms");
            numbers.put("p95", stats.p95() + " ms");
            numbers.put("max", stats.max() + " ms");
            numbers.put("Ueberschreitungen (> " + LIMIT_MS + " ms)", String.valueOf(exceeded));
            numbers.put("Antworten != 200", String.valueOf(notOk));
            return new ScenarioResult(name(), nfa(), exceeded == 0 && notOk == 0, numbers, List.of(
                    "Site: " + SITE.pages() + " Seiten, " + SITE.delayMs() + " ms Verzoegerung, maxDepth 2; "
                            + "waehrend der Messung crawlt ein In-Prozess-Worker mit 4 Threads. Vor der Messung wurde jeder "
                            + "Job einmal ungezaehlt abgefragt (Aufwaermen des frisch gestarteten Koordinators)."));
        } finally {
            jobIds.forEach(api::abort);
        }
    }
}
