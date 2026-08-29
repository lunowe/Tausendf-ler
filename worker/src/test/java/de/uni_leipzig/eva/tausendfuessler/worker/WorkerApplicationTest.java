package de.uni_leipzig.eva.tausendfuessler.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerApplicationTest {

    @Test
    void parsesCoordinatorThreadsAndId() {
        var args = new String[]{"--coordinator", "localhost:9090", "--threads", "4", "--id", "w1"};
        var map = WorkerApplication.parseArgs(args);
        assertThat(map.get("--coordinator")).isEqualTo("localhost:9090");
        assertThat(map.get("--threads")).isEqualTo("4");
        assertThat(map.get("--id")).isEqualTo("w1");
    }

    @Test
    void parsesCoordinatorAddress() {
        var address = WorkerApplication.parseCoordinator("crawl-host:1234");
        assertThat(address.host()).isEqualTo("crawl-host");
        assertThat(address.port()).isEqualTo(1234);
    }

    @Test
    void tokenArgumentWinsOverEnvironmentAndBlankMeansNone() {
        assertThat(WorkerApplication.token("arg", "env")).isEqualTo("arg");
        assertThat(WorkerApplication.token(null, "env")).isEqualTo("env");
        assertThat(WorkerApplication.token("", "env")).isEqualTo("env");
        assertThat(WorkerApplication.token(null, "")).isNull();
        assertThat(WorkerApplication.token(null, null)).isNull();
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
    void missingCoordinatorPrintsUsageToStdErrAndExitsWithCodeOne() throws Exception {
        var javaBin = System.getProperty("java.home") + "/bin/java";
        var classPath = System.getProperty("java.class.path");
        var pb = new ProcessBuilder(javaBin, "-cp", classPath,
                "de.uni_leipzig.eva.tausendfuessler.worker.WorkerApplication");
        var process = pb.start();
        var err = new String(process.getErrorStream().readAllBytes());
        var exitCode = process.waitFor();
        assertThat(exitCode).isEqualTo(1);
        assertThat(err).contains(WorkerApplication.USAGE);
    }

    @Test
    @Timeout(10)
    void oldUrlModeIsRejected() throws Exception {
        var javaBin = System.getProperty("java.home") + "/bin/java";
        var classPath = System.getProperty("java.class.path");
        var pb = new ProcessBuilder(javaBin, "-cp", classPath,
                "de.uni_leipzig.eva.tausendfuessler.worker.WorkerApplication",
                "--url", "http://localhost:1/nonexistent", "--threads", "1");
        var process = pb.start();
        var err = new String(process.getErrorStream().readAllBytes());
        var exitCode = process.waitFor();
        assertThat(exitCode).isEqualTo(1);
        assertThat(err).contains(WorkerApplication.USAGE);
    }
}
