package de.uni_leipzig.eva.krakensuche.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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

    @Test
    @Timeout(10)
    void missingUrlPrintsUsageToStdErrAndExitsWithCodeOne() throws Exception {
        var javaBin = System.getProperty("java.home") + "/bin/java";
        var classPath = System.getProperty("java.class.path");
        var pb = new ProcessBuilder(javaBin, "-cp", classPath,
                "de.uni_leipzig.eva.krakensuche.worker.WorkerApplication");
        var process = pb.start();
        var err = new String(process.getErrorStream().readAllBytes());
        var exitCode = process.waitFor();
        assertThat(exitCode).isEqualTo(1);
        assertThat(err).contains("Usage:");
    }

    @Test
    @Timeout(10)
    void crawlFailurePrintsErrorToStdErrAndExitsWithCodeOne() throws Exception {
        var javaBin = System.getProperty("java.home") + "/bin/java";
        var classPath = System.getProperty("java.class.path");
        var pb = new ProcessBuilder(javaBin, "-cp", classPath,
                "de.uni_leipzig.eva.krakensuche.worker.WorkerApplication",
                "--url", "http://localhost:1/nonexistent", "--threads", "1");
        var process = pb.start();
        var err = new String(process.getErrorStream().readAllBytes());
        var exitCode = process.waitFor();
        assertThat(exitCode).isEqualTo(1);
        assertThat(err).contains("Error:");
    }
}
