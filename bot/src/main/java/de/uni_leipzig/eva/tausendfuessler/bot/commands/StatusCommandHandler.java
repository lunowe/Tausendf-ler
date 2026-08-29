package de.uni_leipzig.eva.tausendfuessler.bot.commands;

import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobDetail;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobStatus;
import de.uni_leipzig.eva.tausendfuessler.bot.service.CoordinatorClient;
import de.uni_leipzig.eva.tausendfuessler.bot.service.MessageSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** /status &lt;Job-ID&gt; */
@Component
public class StatusCommandHandler implements CommandHandler {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final CoordinatorClient coordinatorClient;

    public StatusCommandHandler(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @Override
    public void handle(Update update, MessageSender sender) {
        String[] parts = update.getMessage().getText().trim().split("\\s+");
        if (parts.length < 2) {
            sender.sendReply(update, "Verwendung: /status <Job-ID>");
            return;
        }
        try {
            JobDetail d = coordinatorClient.getJobDetail(parts[1]);
            StringBuilder sb = new StringBuilder();
            sb.append("📊 Status für ").append(d.getJobId()).append("\n\n")
              .append("🔗 ").append(d.getUrl()).append('\n')
              .append("📏 Max. Tiefe: ").append(d.getMaxDepth()).append('\n')
              .append("📌 ").append(formatStatus(d.getStatus())).append('\n')
              .append("📄 Besuchte Seiten: ").append(d.getPagesVisited()).append('\n')
              .append("🔗 Extrahierte Links: ").append(d.getLinksFound()).append('\n')
              .append("❌ Fehler: ").append(d.getErrors()).append('\n');
            appendTime(sb, "▶️ Gestartet: ", d.getStartedAt());
            appendTime(sb, "⏹ Beendet: ", d.getFinishedAt());
            sender.sendReply(update, sb.toString());
        } catch (Exception e) {
            sender.sendReply(update, "❌ Status konnte nicht abgerufen werden: " + e.getMessage());
        }
    }

    private static void appendTime(StringBuilder sb, String label, Instant instant) {
        if (instant != null) {
            sb.append(label).append(TIME.format(instant)).append('\n');
        }
    }

    static String formatStatus(JobStatus status) {
        if (status == null) return "❓ Unbekannt";
        return switch (status) {
            case PENDING -> "⏳ Wartend";
            case RUNNING -> "🔄 Läuft";
            case PAUSED -> "⏸️ Pausiert";
            case COMPLETED -> "✅ Abgeschlossen";
            case ABORTED -> "🚫 Abgebrochen";
            case FAILED -> "❌ Fehlgeschlagen";
        };
    }
}
