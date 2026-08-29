package de.uni_leipzig.eva.tausendfuessler.loadtest.scenario;

import de.uni_leipzig.eva.tausendfuessler.loadtest.CoordinatorApi;
import de.uni_leipzig.eva.tausendfuessler.loadtest.Scenario;
import de.uni_leipzig.eva.tausendfuessler.loadtest.ScenarioResult;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Polls /api/health until it answers 200 and reports the coordinator's own startup time
 * (JVM start until ApplicationReadyEvent, as returned in the health body) - independent of when this client started.
 */
public final class StartupScenario implements Scenario {

    static final long LIMIT_SECONDS = 15;
    private static final long POLL_MS = 200;
    private static final Duration GIVE_UP = Duration.ofSeconds(120);

    @Override
    public String name() {
        return "startup";
    }

    @Override
    public String nfa() {
        return "Koordinator in < 15 s startbereit";
    }

    @Override
    public ScenarioResult run(Context context) {
        Instant deadline = Instant.now().plus(GIVE_UP);
        int attempts = 0;
        int lastStatus = 0;
        while (Instant.now().isBefore(deadline)) {
            attempts++;
            CoordinatorApi.Response response = context.api().health();
            lastStatus = response.status();
            if (response.status() == 200) {
                if (!response.body().hasNonNull("startupSeconds")) {
                    return ScenarioResult.failed(name(), nfa(),
                            "/api/health liefert kein Feld startupSeconds - Koordinator-Version zu alt?");
                }
                double seconds = response.body().get("startupSeconds").asDouble();
                Map<String, String> numbers = new LinkedHashMap<>();
                numbers.put("Startzeit des Koordinators (JVM-Start bis ApplicationReady)", String.format("%.1f s", seconds));
                numbers.put("Health-Anfragen bis zur ersten Antwort", String.valueOf(attempts));
                numbers.put("Grenzwert", LIMIT_SECONDS + " s");
                return new ScenarioResult(name(), nfa(), seconds < LIMIT_SECONDS, numbers, List.of(
                        "Der Koordinator misst die Zeit selbst (JVM-Start bis ApplicationReadyEvent) und meldet sie "
                                + "in /api/health als startupSeconds; der Zeitpunkt des Client-Starts spielt keine Rolle. "
                                + "Zum Vergleich: logs/coordinator.log (\"Started CoordinatorApplication in ... seconds\")."));
            }
            CoordinatorApi.sleep(POLL_MS);
        }
        return ScenarioResult.failed(name(), nfa(), "Koordinator hat innerhalb von " + GIVE_UP.toSeconds()
                + " s nicht mit 200 geantwortet (letzter Status " + lastStatus + ")");
    }
}
