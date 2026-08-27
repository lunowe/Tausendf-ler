package com.example.telegrambot.commands;

import com.example.telegrambot.dto.JobDetail;
import com.example.telegrambot.dto.JobStatus;
import com.example.telegrambot.service.CoordinatorClient;
import com.example.telegrambot.service.MessageSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class AbortCommandHandler implements CommandHandler {

    private final CoordinatorClient coordinatorClient;

    public AbortCommandHandler(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @Override
    public void handle(Update update, MessageSender sender) {
        String text = update.getMessage().getText();
        String[] parts = text.split(" ");

        if (parts.length < 2) {
            sender.sendReply(update, "❌ Verwendung: `/abort <Job-ID>`\n" +
                    "Beispiel: `/abort abc-123`");
            return;
        }

        String jobId = parts[1];

        try {
            // Prüfen, ob der Job überhaupt abgebrochen werden kann
            JobDetail detail = coordinatorClient.getJobDetail(jobId);

            if (detail.getStatus() == JobStatus.COMPLETED) {
                sender.sendReply(update, "⚠️ Auftrag `" + jobId + "` ist bereits abgeschlossen.");
                return;
            }
            if (detail.getStatus() == JobStatus.ABORTED) {
                sender.sendReply(update, "⚠️ Auftrag `" + jobId + "` wurde bereits abgebrochen.");
                return;
            }

            coordinatorClient.abortJob(jobId);
            sender.sendReply(update, "🚫 Auftrag `" + jobId + "` wurde **abgebrochen**.\n" +
                    "Die bereits gesammelten Ergebnisse bleiben erhalten.");

        } catch (Exception e) {
            sender.sendReply(update, "❌ Fehler beim Abbrechen: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
