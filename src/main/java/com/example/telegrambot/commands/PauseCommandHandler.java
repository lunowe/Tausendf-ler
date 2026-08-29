package com.example.telegrambot.commands;

import com.example.telegrambot.dto.JobDetail;
import com.example.telegrambot.dto.JobStatus;
import com.example.telegrambot.service.CoordinatorClient;
import com.example.telegrambot.service.MessageSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class PauseCommandHandler implements CommandHandler {

    private final CoordinatorClient coordinatorClient;

    public PauseCommandHandler(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @Override
    public void handle(Update update, MessageSender sender) {
        String text = update.getMessage().getText();
        String[] parts = text.split(" ");

        if (parts.length < 2) {
            sender.sendReply(update, "❌ Verwendung: `/pause <Job-ID>`\n" +
                    "Beispiel: `/pause abc-123`");
            return;
        }

        String jobId = parts[1];

        try {
            // Prüfen, ob der Job überhaupt pausiert werden kann
            JobDetail detail = coordinatorClient.getJobDetail(jobId);

            if (detail.getStatus() == JobStatus.COMPLETED) {
                sender.sendReply(update, "⚠️ Auftrag `" + jobId + "` ist bereits abgeschlossen.");
                return;
            }
            if (detail.getStatus() == JobStatus.ABORTED) {
                sender.sendReply(update, "⚠️ Auftrag `" + jobId + "` wurde bereits abgebrochen.");
                return;
            }
            if (detail.getStatus() == JobStatus.PAUSED) {
                sender.sendReply(update, "⚠️ Auftrag `" + jobId + "` ist bereits pausiert.");
                return;
            }
            if (detail.getStatus() == JobStatus.PENDING) {
                sender.sendReply(update, "⚠️ Auftrag `" + jobId + "` wartet noch auf Start.");
                return;
            }

            coordinatorClient.pauseJob(jobId);
            sender.sendReply(update, "⏸️ Auftrag `" + jobId + "` wurde **pausiert**.");

        } catch (Exception e) {
            sender.sendReply(update, "❌ Fehler beim Pausieren: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
