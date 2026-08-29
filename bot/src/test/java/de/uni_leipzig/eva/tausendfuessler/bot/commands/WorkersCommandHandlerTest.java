package de.uni_leipzig.eva.tausendfuessler.bot.commands;

import de.uni_leipzig.eva.tausendfuessler.bot.dto.WorkerInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkersCommandHandlerTest {

    @Test
    void emptyListSaysSo() {
        assertThat(WorkersCommandHandler.format(List.of())).isEqualTo("Keine Worker verbunden.");
    }

    @Test
    void oneLinePerWorker() {
        String text = WorkersCommandHandler.format(List.of(
                new WorkerInfo("laptop-a", 8, 3, Instant.parse("2026-08-29T10:00:00Z")),
                new WorkerInfo("laptop-b", 4, 0, Instant.parse("2026-08-29T10:05:00Z"))));

        assertThat(text).startsWith("🖥️ 2 Worker online\n\n");
        assertThat(text).contains("laptop-a · 8 Threads · 3 in Arbeit · seit ");
        assertThat(text).contains("laptop-b · 4 Threads · 0 in Arbeit · seit ");
        assertThat(text).matches("(?s).*seit \\d{2}:\\d{2}:\\d{2}\n.*");
    }
}
