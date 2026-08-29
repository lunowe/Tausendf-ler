package de.uni_leipzig.eva.tausendfuessler.loadtest.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import de.uni_leipzig.eva.tausendfuessler.loadtest.CoordinatorApi;
import de.uni_leipzig.eva.tausendfuessler.loadtest.InProcessWorkers;
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
 * The same job (same site, same start URL, maxDepth 3) first with one in-process worker, then with two.
 * Throughput = pagesVisited / (finishedAt - startedAt) from the job detail.
 */
public final class ThroughputScenario implements Scenario {

    static final double MIN_GAIN_PERCENT = 60;
    private static final int THREADS_PER_WORKER = 4;
    private static final int MAX_DEPTH = 3;
    private static final int LINKS_PER_PAGE = 8;
    /**
     * Simulated network latency per fetch. Without it the local site answers in < 1 ms and the single per-job
     * result path in the coordinator (DB write per page) becomes the limit, so more workers cannot show anything.
     */
    private static final long FETCH_DELAY_MS = 100;
    private static final Duration JOB_TIMEOUT = Duration.ofMinutes(15);
    private static final Duration PROBE_WAIT = Duration.ofSeconds(3);

    private record Run(int workers, long pages, double seconds, double pagesPerSecond) {}

    @Override
    public String name() {
        return "throughput";
    }

    @Override
    public String nfa() {
        return ">= 60 % mehr Durchsatz mit 2 Workern als mit 1 Worker";
    }

    @Override
    public ScenarioResult run(Context context) throws Exception {
        SyntheticSite.Config config = new SyntheticSite.Config(context.options().pages(), LINKS_PER_PAGE, 3, FETCH_DELAY_MS, 3);
        try (SyntheticSite site = new SyntheticSite(config)) {
            String foreignWorker = detectForeignWorker(context.api(), site);
            if (foreignWorker != null) {
                return ScenarioResult.failed(name(), nfa(), foreignWorker);
            }

            Run one = measure(context, site, 1);
            Run two = measure(context, site, 2);
            double gain = one.pagesPerSecond() == 0 ? 0 : (two.pagesPerSecond() - one.pagesPerSecond()) / one.pagesPerSecond() * 100;

            Map<String, String> numbers = new LinkedHashMap<>();
            numbers.put("Site", config.pages() + " Seiten, maxDepth " + MAX_DEPTH + ", " + FETCH_DELAY_MS + " ms Verzoegerung je Abruf");
            numbers.put("1 Worker: Seiten / Dauer", one.pages() + " / " + String.format("%.1f s", one.seconds()));
            numbers.put("1 Worker: Durchsatz", String.format("%.1f Seiten/s", one.pagesPerSecond()));
            numbers.put("2 Worker: Seiten / Dauer", two.pages() + " / " + String.format("%.1f s", two.seconds()));
            numbers.put("2 Worker: Durchsatz", String.format("%.1f Seiten/s", two.pagesPerSecond()));
            numbers.put("Relative Steigerung", String.format("%.1f %%", gain));
            List<String> notes = new ArrayList<>();
            notes.add("Je Worker " + THREADS_PER_WORKER + " Crawl-Threads, alle Worker laufen im Testclient-Prozess; "
                    + "waehrend dieses Szenarios darf kein weiterer Worker mit dem Koordinator verbunden sein "
                    + "(wird vor der Messung per Probe-Job geprueft).");
            if (one.pages() != two.pages()) {
                notes.add("Achtung: unterschiedliche Seitenzahl in beiden Laeufen (" + one.pages() + " vs. " + two.pages() + ").");
            }
            return new ScenarioResult(name(), nfa(), gain >= MIN_GAIN_PERCENT, numbers, notes);
        }
    }

    private Run measure(Context context, SyntheticSite site, int workerCount) throws Exception {
        CoordinatorApi api = context.api();
        System.out.printf("  Lauf mit %d Worker(n) ...%n", workerCount);
        try (InProcessWorkers ignored = InProcessWorkers.start(context.options(), "lt-throughput" + workerCount, workerCount, THREADS_PER_WORKER)) {
            String jobId = api.createJobOrThrow(site.startUrl(), MAX_DEPTH);
            JsonNode detail = api.awaitJob(jobId, CoordinatorApi::isCompleted, JOB_TIMEOUT);
            long pages = detail.path("pagesVisited").asLong();
            Instant started = Instant.parse(detail.path("startedAt").asText());
            Instant finished = Instant.parse(detail.path("finishedAt").asText());
            double seconds = Duration.between(started, finished).toMillis() / 1000.0;
            Run run = new Run(workerCount, pages, seconds, seconds <= 0 ? 0 : pages / seconds);
            System.out.printf("  %d Worker: %d Seiten in %.1f s = %.1f Seiten/s%n", workerCount, pages, seconds, run.pagesPerSecond());
            return run;
        }
    }

    /**
     * A depth-0 job with no worker of ours running must stay untouched; if it gets crawled, someone else is
     * connected and the comparison would be meaningless.
     *
     * @return a message describing the problem, or {@code null} if the coordinator has no other workers
     */
    private static String detectForeignWorker(CoordinatorApi api, SyntheticSite site) {
        String probe = api.createJobOrThrow(site.pageUrl(site.config().pages() - 1), 0);
        CoordinatorApi.sleep(PROBE_WAIT.toMillis());
        JsonNode detail = api.job(probe).body();
        boolean crawled = detail != null && (CoordinatorApi.isCompleted(detail) || detail.path("pagesVisited").asLong() > 0);
        if (!crawled) {
            api.abort(probe);
            return null;
        }
        return "Ein fremder Worker ist mit dem Koordinator verbunden (Probe-Job " + probe
                + " wurde ohne eigene Worker gecrawlt). Bitte alle externen Worker beenden und das Szenario wiederholen.";
    }
}
