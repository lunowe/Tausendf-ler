package com.example.telegrambot.commands;

import com.example.telegrambot.dto.JobDetail;
import com.example.telegrambot.dto.JobStatus;
import com.example.telegrambot.service.CoordinatorClient;
import com.example.telegrambot.service.MessageSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class ResumeCommandHandler implements CommandHandler {

    private final CoordinatorClient coordinatorClient;

    public ResumeCommandHandler(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @Override
    public void handle(Update update, MessageSender sender) {
        String text = update.getMessage().getText();
        String[] parts = text.split(" ");

        if (parts.length < 2) {
            sender.sendReply(update, "❌ Verwendung: `/resume <Job-ID>`\n" +
                    "Beispiel: `/resume abc-123`");
            return;
        }

        String jobId = parts[1];

        try {
            // Prüfen, ob der Job überhaupt fortgesetzt werden kann
            JobDetail detail = coordinatorClient.getJobDetail(jobId);

            if (detail.getStatus() == JobStatus.COMPLETED) {
                sender.sendReply(update, "⚠️ Auftrag `" + jobId + "` ist bereits abgeschlossen.");
                return;
            }
            if (detail.getStatus() == JobStatus.ABORTED) {
                sender.sendReply(update, "⚠️ Auftrag `" + jobId + "` wurde abgebrochen.");
                return;
            }
            if (detail.getStatus() != JobStatus.PAUSED) {
                sender.sendReply(update, "⚠️ Auftrag `" + jobId + "` ist nicht pausiert (Status: " + detail.getStatus() + ").");
                return;
            }

            coordinatorClient.resumeJob(jobId);
            sender.sendReply(update, "▶️ Auftrag `" + jobId + "` wurde **fortgesetzt**.");

        } catch (Exception e) {
            sender.sendReply(update, "❌ Fehler beim Fortsetzen: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
