package de.uni_leipzig.eva.krakensuche.worker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerApplicationTest {

    @Test
    void parsesUrlArg() {
        var args = new String[]{"--url", "https://example.com", "--threads", "4"};
        var map = WorkerApplication.parseArgs(args);
        assertThat(map.get("--url")).isEqualTo("https://example.com");
        assertThat(map.get("--threads")).isEqualTo("4");
    }

    @Test
    void parsesUrlWithoutThreads() {
        var args = new String[]{"--url", "https://example.com"};
        var map = WorkerApplication.parseArgs(args);
        assertThat(map.get("--url")).isEqualTo("https://example.com");
        assertThat(map).hasSize(1);
    }

    @Test
    void parsesEmptyArgs() {
        var map = WorkerApplication.parseArgs(new String[]{});
        assertThat(map).isEmpty();
    }

    @Test
    void handlesFlagWithoutValue() {
        var args = new String[]{"--help"};
        var map = WorkerApplication.parseArgs(args);
        assertThat(map.get("--help")).isEqualTo("");
    }
}
