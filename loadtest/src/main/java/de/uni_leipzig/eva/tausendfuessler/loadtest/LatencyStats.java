package de.uni_leipzig.eva.tausendfuessler.loadtest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Percentiles over a list of millisecond latencies (nearest-rank method). */
public record LatencyStats(int count, long p50, long p95, long max, double mean) {

    public static LatencyStats of(List<Long> latenciesMs) {
        if (latenciesMs.isEmpty()) {
            return new LatencyStats(0, 0, 0, 0, 0);
        }
        List<Long> sorted = new ArrayList<>(latenciesMs);
        Collections.sort(sorted);
        double sum = 0;
        for (long value : sorted) {
            sum += value;
        }
        return new LatencyStats(sorted.size(), percentile(sorted, 50), percentile(sorted, 95),
                sorted.get(sorted.size() - 1), sum / sorted.size());
    }

    public static long countAbove(List<Long> latenciesMs, long thresholdMs) {
        return latenciesMs.stream().filter(value -> value > thresholdMs).count();
    }

    private static long percentile(List<Long> sorted, int percent) {
        int rank = (int) Math.ceil(percent / 100.0 * sorted.size());
        return sorted.get(Math.max(0, rank - 1));
    }
}
