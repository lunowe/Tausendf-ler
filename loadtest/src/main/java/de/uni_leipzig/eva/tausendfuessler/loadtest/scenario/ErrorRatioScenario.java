package de.uni_leipzig.eva.tausendfuessler.loadtest.scenario;

import de.uni_leipzig.eva.tausendfuessler.loadtest.CoordinatorApi;
import de.uni_leipzig.eva.tausendfuessler.loadtest.InProcessWorkers;
import de.uni_leipzig.eva.tausendfuessler.loadtest.Scenario;
import de.uni_leipzig.eva.tausendfuessler.loadtest.ScenarioResult;
import de.uni_leipzig.eva.tausendfuessler.loadtest.SyntheticSite;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Sends a mix of valid and deliberately invalid requests for {@code --seconds}. 2xx/4xx answers are correct
 * behaviour, 5xx answers or missing answers (connection errors, timeouts) count as internal errors.
 */
public final class ErrorRatioScenario implements Scenario {

    static final double MAX_INTERNAL_RATIO = 0.001;
    private static final long INTERVAL_MS = 50;
    private static final SyntheticSite.Config SITE = new SyntheticSite.Config(50, 5, 3, 300, 2);

    /** One request type in the mix and the HTTP status the API contract prescribes for it. */
    private record Probe(String label, int expectedStatus, Supplier<CoordinatorApi.Response> request) {}

    @Override
    public String name() {
        return "error-ratio";
    }

    @Override
    public String nfa() {
        return "> 99,9 % der Statusanfragen ohne internen Fehler";
    }

    @Override
    public ScenarioResult run(Context context) throws Exception {
        CoordinatorApi api = context.api();
        int seconds = context.options().seconds();
        String runningJobForCleanup = null;
        try (SyntheticSite site = new SyntheticSite(SITE)) {
            String completedJob = completedJob(context, site); // first, while our worker is briefly connected
            String runningJob = api.createJobOrThrow(site.startUrl(), 2); // stays RUNNING: no worker crawls it
            runningJobForCleanup = runningJob;

            List<Probe> probes = List.of(
                    new Probe("GET job (gueltig)", 200, () -> api.job(runningJob)),
                    new Probe("GET jobs?owner", 200, () -> api.list(CoordinatorApi.OWNER)),
                    new Probe("GET stats", 200, api::stats),
                    new Probe("GET results (gueltig)", 200, () -> api.results(runningJob, 0)),
                    new Probe("GET health", 200, api::health),
                    new Probe("GET job (unbekannte ID)", 404, () -> api.job("does-not-exist")),
                    new Probe("POST jobs (kaputtes JSON)", 400, () -> api.postRaw("/api/jobs", "{\"url\": ")),
                    new Probe("POST jobs (leere URL)", 400, () -> api.postJson("/api/jobs", Map.of("url", "", "owner", 1))),
                    new Probe("POST pause (beendeter Job)", 409, () -> api.pause(completedJob)),
                    new Probe("GET jobs ohne owner", 400, () -> api.get("/api/jobs")),
                    new Probe("GET results (afterSeq=abc)", 400,
                            () -> api.get("/api/jobs/" + runningJob + "/results?afterSeq=abc")));

            System.out.printf("  sende %d s lang gemischte Anfragen (~%d/s) ...%n", seconds, 1000 / INTERVAL_MS);
            long total = 0;
            long ok2xx = 0;
            long client4xx = 0;
            long internal = 0;
            long unexpected = 0;
            long start = System.nanoTime();
            long nextAt = start;
            for (int i = 0; (System.nanoTime() - start) / 1_000_000 < seconds * 1000L; i++) {
                Probe probe = probes.get(i % probes.size());
                CoordinatorApi.Response response = probe.request().get();
                total++;
                int status = response.status();
                if (status == 0 || status >= 500) {
                    internal++;
                    System.out.printf("  interner Fehler bei %s: HTTP %d %s%n", probe.label(), status,
                            response.error() == null ? "" : response.error());
                } else if (status >= 400) {
                    client4xx++;
                } else {
                    ok2xx++;
                }
                if (status != probe.expectedStatus() && status != 0 && status < 500) {
                    unexpected++;
                }
                nextAt += INTERVAL_MS * 1_000_000;
                long waitMs = (nextAt - System.nanoTime()) / 1_000_000;
                if (waitMs > 0) {
                    CoordinatorApi.sleep(waitMs);
                }
            }

            double ratio = total == 0 ? 1 : (double) internal / total;
            Map<String, String> numbers = new LinkedHashMap<>();
            numbers.put("Dauer", seconds + " s");
            numbers.put("Anfragen gesamt", String.valueOf(total));
            numbers.put("2xx (gueltig)", String.valueOf(ok2xx));
            numbers.put("4xx (Clientfehler, erwartet)", String.valueOf(client4xx));
            numbers.put("5xx / keine Antwort (intern)", String.valueOf(internal));
            numbers.put("Anteil interner Fehler", String.format("%.3f %%", ratio * 100));
            numbers.put("Anteil ohne internen Fehler", String.format("%.3f %%", (1 - ratio) * 100));
            numbers.put("Antworten mit anderem Status als erwartet", String.valueOf(unexpected));
            return new ScenarioResult(name(), nfa(), ratio < MAX_INTERNAL_RATIO, numbers, List.of(
                    "Mix aus " + probes.size() + " Anfragetypen im Round-Robin: gueltige Status-, Listen-, Ergebnis- und "
                            + "Stats-Abfragen sowie ungueltige (unbekannte ID -> 404, kaputter Body -> 400, "
                            + "Pause auf beendetem Job -> 409)."));
        } finally {
            if (runningJobForCleanup != null) {
                api.abort(runningJobForCleanup);
            }
        }
    }

    /** A job that really finished (depth 0, one page) so that pause on it must answer 409. */
    private static String completedJob(Context context, SyntheticSite site) throws Exception {
        CoordinatorApi api = context.api();
        try (InProcessWorkers ignored = InProcessWorkers.start(context.options().workerHost(),
                context.options().workerPort(), "lt-errors", 1, 2)) {
            String jobId = api.createJobOrThrow(site.startUrl(), 0);
            api.awaitJob(jobId, CoordinatorApi::isCompleted, Duration.ofSeconds(60));
            return jobId;
        }
    }
}
