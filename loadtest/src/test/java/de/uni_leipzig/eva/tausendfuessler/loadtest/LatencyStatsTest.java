package de.uni_leipzig.eva.tausendfuessler.loadtest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LatencyStatsTest {

    @Test
    void computesPercentilesWithNearestRank() {
        List<Long> values = new ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            values.add(i);
        }
        values.add(0, 100L); // order must not matter
        values.remove(values.size() - 1);

        LatencyStats stats = LatencyStats.of(values);
        assertThat(stats.count()).isEqualTo(100);
        assertThat(stats.p50()).isEqualTo(50);
        assertThat(stats.p95()).isEqualTo(95);
        assertThat(stats.max()).isEqualTo(100);
        assertThat(stats.mean()).isEqualTo(50.5);
    }

    @Test
    void smallSamplesAndEmptyInput() {
        assertThat(LatencyStats.of(List.of())).isEqualTo(new LatencyStats(0, 0, 0, 0, 0));
        assertThat(LatencyStats.of(List.of(7L))).isEqualTo(new LatencyStats(1, 7, 7, 7, 7));
        LatencyStats three = LatencyStats.of(List.of(30L, 10L, 20L));
        assertThat(three.p50()).isEqualTo(20);
        assertThat(three.p95()).isEqualTo(30);
    }

    @Test
    void countsValuesStrictlyAboveThreshold() {
        assertThat(LatencyStats.countAbove(List.of(100L, 200L, 201L, 999L), 200)).isEqualTo(2);
        assertThat(LatencyStats.countAbove(List.of(), 200)).isZero();
    }
}
