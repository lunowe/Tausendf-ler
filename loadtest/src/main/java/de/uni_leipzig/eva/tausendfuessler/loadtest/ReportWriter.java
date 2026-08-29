package de.uni_leipzig.eva.tausendfuessler.loadtest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes the scenario results as Markdown (one section per scenario) plus the NFAs that are proven elsewhere. */
public final class ReportWriter {

    /** NFAs from the Skizze that are not measured by this client but by existing tests and log lines. */
    static final List<String> EVIDENCE_ELSEWHERE = List.of(
            "Worker-Threadpool liefert alle Ergebnisse unter Last: `worker/.../pool/CrawlExecutorTest.handlesHighVolumeConcurrentCrawls`"
                    + " (100 gleichzeitige URLs auf 4 Threads, exakt 100 CrawlOutcomes).",
            "Dynamische Mehrkernauslastung: Zeilen `crawled url=... thread=...` im Worker-Log (Crawl-Threads) und"
                    + " `... handled on thread ...` in `logs/coordinator.log` (Worker-Handler-Threads).");

    private ReportWriter() {}

    public static String markdown(String runLabel, String coordinatorUrl, List<ScenarioResult> results) {
        StringBuilder md = new StringBuilder("# NFA-Report Tausendfuessler\n\n");
        md.append("* Lauf: ").append(runLabel.isBlank() ? "(ohne Label)" : runLabel).append('\n');
        md.append("* Koordinator: ").append(coordinatorUrl).append('\n');
        md.append("* Erzeugt von `loadtest.jar`; Worker liefen als In-Prozess-Instanzen von `WorkerClient` im Testclient.\n\n");

        md.append("## Zusammenfassung\n\n| Szenario | NFA | Ergebnis |\n|---|---|---|\n");
        for (ScenarioResult result : results) {
            md.append("| ").append(result.name()).append(" | ").append(result.nfa()).append(" | ")
                    .append(result.verdict()).append(" |\n");
        }

        for (ScenarioResult result : results) {
            md.append("\n## ").append(result.name()).append("\n\n");
            md.append("NFA: ").append(result.nfa()).append("  \nErgebnis: **").append(result.verdict()).append("**\n\n");
            if (!result.numbers().isEmpty()) {
                md.append("| Kennzahl | Wert |\n|---|---|\n");
                result.numbers().forEach((key, value) -> md.append("| ").append(key).append(" | ").append(value).append(" |\n"));
                md.append('\n');
            }
            result.notes().forEach(note -> md.append("* ").append(note).append('\n'));
        }

        md.append("\n## Anderweitig nachgewiesene NFAs\n\n");
        EVIDENCE_ELSEWHERE.forEach(line -> md.append("* ").append(line).append('\n'));
        return md.toString();
    }

    public static void write(Path path, String runLabel, String coordinatorUrl, List<ScenarioResult> results) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, markdown(runLabel, coordinatorUrl, results));
    }
}
