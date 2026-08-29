package de.uni_leipzig.eva.tausendfuessler.loadtest;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Command-line options. {@code apiKey} / {@code workerToken} are {@code null} when the coordinator runs without
 * authentication.
 */
public record Options(String coordinator, String workerHost, int workerPort, String scenario,
                      Path report, String runLabel, int seconds, int pages, String apiKey, String workerToken) {

    public static final String USAGE = """
            Usage: java -jar loadtest.jar [--coordinator http://localhost:8080] [--worker-host localhost] [--worker-port 9090]
                   [--scenario all|startup|status-latency|error-ratio|throughput|live-latency|dedup]
                   [--report docs/NFA-Report.md] [--run-label "..."] [--seconds 60] [--pages 2000]
                   [--api-key <API_KEY>] [--worker-token <WORKER_TOKEN>]   (Standard: Umgebungsvariablen API_KEY, WORKER_TOKEN)""";

    public static Options parse(String[] args) {
        return parse(args, System.getenv());
    }

    static Options parse(String[] args, Map<String, String> env) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument " + args[i]);
            }
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                map.put(args[i], args[++i]);
            } else {
                map.put(args[i], "");
            }
        }
        String report = map.get("--report");
        return new Options(
                map.getOrDefault("--coordinator", "http://localhost:8080"),
                map.getOrDefault("--worker-host", "localhost"),
                positive(map.getOrDefault("--worker-port", "9090"), "--worker-port"),
                map.getOrDefault("--scenario", "all"),
                report == null || report.isBlank() ? null : Path.of(report),
                map.getOrDefault("--run-label", ""),
                positive(map.getOrDefault("--seconds", "60"), "--seconds"),
                positive(map.getOrDefault("--pages", "2000"), "--pages"),
                secret(map.get("--api-key"), env.get("API_KEY")),
                secret(map.get("--worker-token"), env.get("WORKER_TOKEN")));
    }

    /** Argument wins over environment; blank means "none". */
    private static String secret(String argument, String environment) {
        String value = argument != null && !argument.isBlank() ? argument : environment;
        return value == null || value.isBlank() ? null : value;
    }

    private static int positive(String value, String option) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " must be a positive integer, got '" + value + "'");
        }
    }
}
