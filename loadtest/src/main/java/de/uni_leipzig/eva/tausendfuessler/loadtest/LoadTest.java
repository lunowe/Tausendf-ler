package de.uni_leipzig.eva.tausendfuessler.loadtest;

import de.uni_leipzig.eva.tausendfuessler.loadtest.scenario.DedupScenario;
import de.uni_leipzig.eva.tausendfuessler.loadtest.scenario.ErrorRatioScenario;
import de.uni_leipzig.eva.tausendfuessler.loadtest.scenario.LiveLatencyScenario;
import de.uni_leipzig.eva.tausendfuessler.loadtest.scenario.StartupScenario;
import de.uni_leipzig.eva.tausendfuessler.loadtest.scenario.StatusLatencyScenario;
import de.uni_leipzig.eva.tausendfuessler.loadtest.scenario.ThroughputScenario;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Testclient from the Skizze ("Last-Simulation & Test der nicht-funktionalen Anforderungen"): runs the
 * measurement scenarios against a running coordinator and prints/writes the numbers.
 */
public final class LoadTest {

    static {
        // must run before the first logger is created (in-process workers log via logback)
        System.setProperty("logback.configurationFile", "logback-loadtest.xml");
    }

    private static final List<Scenario> SCENARIOS = List.of(
            new StartupScenario(),
            new StatusLatencyScenario(),
            new ErrorRatioScenario(),
            new ThroughputScenario(),
            new LiveLatencyScenario(),
            new DedupScenario());

    private LoadTest() {}

    public static void main(String[] args) throws IOException {
        Instant clientStart = Instant.now();
        Options options;
        List<Scenario> selected;
        try {
            options = Options.parse(args);
            selected = select(options.scenario());
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(Options.USAGE);
            System.exit(2);
            return;
        }

        CoordinatorApi api = new CoordinatorApi(options.coordinator());
        Scenario.Context context = new Scenario.Context(api, options, clientStart);
        System.out.println("Tausendfuessler Lasttest gegen " + api.baseUrl()
                + (options.runLabel().isBlank() ? "" : " (" + options.runLabel() + ")"));

        boolean startsWithStartup = selected.get(0) instanceof StartupScenario;
        if (!startsWithStartup && api.health().status() != 200) {
            System.err.println("Koordinator " + api.baseUrl() + " antwortet nicht auf /api/health - laeuft er?");
            System.exit(2);
            return;
        }

        List<ScenarioResult> results = new ArrayList<>();
        for (Scenario scenario : selected) {
            System.out.println();
            System.out.println("--- Szenario " + scenario.name() + " ---");
            ScenarioResult result;
            try {
                result = scenario.run(context);
            } catch (Exception e) {
                result = ScenarioResult.failed(scenario.name(), scenario.nfa(), "Abbruch: " + e);
            }
            result.print();
            results.add(result);
        }

        printSummary(results);
        if (options.report() != null) {
            ReportWriter.write(options.report(), options.runLabel(), api.baseUrl(), results);
            System.out.println("Report geschrieben: " + options.report().toAbsolutePath());
        }
        System.exit(results.stream().allMatch(ScenarioResult::passed) ? 0 : 1);
    }

    static List<Scenario> select(String name) {
        if ("all".equals(name)) {
            return SCENARIOS;
        }
        return SCENARIOS.stream().filter(s -> s.name().equals(name)).findFirst().map(List::of)
                .orElseThrow(() -> new IllegalArgumentException("unknown scenario '" + name + "'"));
    }

    private static void printSummary(List<ScenarioResult> results) {
        System.out.println();
        System.out.println("=== Zusammenfassung ===");
        System.out.printf("%-16s %-60s %s%n", "Szenario", "NFA", "Ergebnis");
        for (ScenarioResult result : results) {
            System.out.printf("%-16s %-60s %s%n", result.name(), result.nfa(), result.verdict());
        }
        System.out.println();
        System.out.println("Anderweitig nachgewiesen:");
        ReportWriter.EVIDENCE_ELSEWHERE.forEach(line -> System.out.println("  * " + line));
    }
}
