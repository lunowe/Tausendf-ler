package com.example.telegrambot.commands;

import com.example.telegrambot.dto.JobDetail;
import com.example.telegrambot.dto.JobStatus;
import com.example.telegrambot.service.CoordinatorClient;
import com.example.telegrambot.service.MessageSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class StatusCommandHandler {

    private final CoordinatorClient coordinatorClient;

    public StatusCommandHandler(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    public void handle(Update update, MessageSender sender) {
        String text = update.getMessage().getText();
        String[] parts = text.split(" ");

        if (parts.length < 2) {
            sender.sendReply(update, "❌ Verwendung: `/status <Job-ID>`\n" +
                    "Beispiel: `/status abc-123`");
            return;
        }

        String jobId = parts[1];

        try {
            JobDetail detail = coordinatorClient.getJobDetail(jobId);

            StringBuilder response = new StringBuilder();
            response.append("📊 **Status für Auftrag `").append(detail.getJobId()).append("`**\n\n");
            response.append("🔗 URL: ").append(detail.getUrl()).append("\n");
            response.append("📏 Maximale Tiefe: ").append(detail.getMaxDepth()).append("\n");
            response.append("📌 Status: ").append(formatStatus(detail.getStatus())).append("\n");
            response.append("📄 Besuchte Seiten: ").append(detail.getPagesVisited()).append("\n");
            response.append("🔗 Extrahierte Links: ").append(detail.getLinksFound()).append("\n");
            response.append("❌ Fehler: ").append(detail.getErrors()).append("\n");

            if (detail.getStartedAt() != null) {
                response.append("▶️ Gestartet: ").append(formatTimestamp(detail.getStartedAt())).append("\n");
            }
            if (detail.getFinishedAt() != null) {
                response.append("⏹ Beendet: ").append(formatTimestamp(detail.getFinishedAt())).append("\n");
            }

            sender.sendReply(update, response.toString());

        } catch (Exception e) {
            sender.sendReply(update, "❌ Fehler beim Abrufen des Status: " + e.getMessage());
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