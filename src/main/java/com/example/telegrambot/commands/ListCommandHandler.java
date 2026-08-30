package com.example.telegrambot.commands;


import com.example.telegrambot.service.CoordinatorClient;
import com.example.telegrambot.service.MessageSender;
import com.example.telegrambot.dto.JobInfo;
import com.example.telegrambot.dto.JobStatus;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class ListCommandHandler implements CommandHandler {

    private final CoordinatorClient coordinatorClient;

    public ListCommandHandler(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

   @Override
public void handle(Update update, MessageSender sender) {
    try {
        List<JobInfo> jobs = coordinatorClient.listJobs();
        if (jobs == null || jobs.isEmpty()) {
            sender.sendReply(update, "📭 Keine Aufträge vorhanden.");
            return;
        }

        StringBuilder sb = new StringBuilder("📋 **Alle Aufträge**\n\n");
        for (JobInfo job : jobs) {
            sb.append("🆔 `").append(job.getJobId()).append("`\n");
            sb.append("🔗 ").append(job.getUrl()).append("\n");
            sb.append("📌 Status: ").append(formatStatus(job.getStatus())).append("\n");
            sb.append("📄 Seiten: ").append(job.getPagesVisited()).append("\n");
            sb.append("⏱ Erstellt: ").append(formatTimestamp(job.getCreatedAt())).append("\n\n");
        }
        sender.sendReply(update, sb.toString());

    } catch (Exception e) {
        sender.sendReply(update, "❌ Fehler beim Abrufen der Liste: " + e.getMessage());
        e.printStackTrace();
    }
}



    private String formatStatus(JobStatus status) {
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

    private String formatTimestamp(Instant instant) {
        if (instant == null) return "Unbekannt";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }


}
