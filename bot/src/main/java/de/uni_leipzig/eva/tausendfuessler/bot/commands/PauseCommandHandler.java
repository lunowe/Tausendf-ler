package de.uni_leipzig.eva.tausendfuessler.bot.commands;

import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobDetail;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobStatus;
import de.uni_leipzig.eva.tausendfuessler.bot.service.CoordinatorClient;
import de.uni_leipzig.eva.tausendfuessler.bot.service.MessageSender;
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
        String[] parts = text.trim().split("\\s+");

        if (parts.length < 2) {
            sender.sendReply(update, "❌ Verwendung: /pause <Job-ID>\n" +
                    "Beispiel: /pause abc-123");
            return;
        }

        String jobId = parts[1];

        try {
            // Prüfen, ob der Job überhaupt pausiert werden kann
            JobDetail detail = coordinatorClient.getJobDetail(jobId);

            if (detail.getStatus() == JobStatus.FAILED) {
                sender.sendReply(update, "⚠️ Auftrag " + jobId + " ist fehlgeschlagen und kann nicht pausiert werden.");
                return;
            }
            if (detail.getStatus() == JobStatus.COMPLETED) {
                sender.sendReply(update, "⚠️ Auftrag " + jobId + " ist bereits abgeschlossen.");
                return;
            }
            if (detail.getStatus() == JobStatus.ABORTED) {
                sender.sendReply(update, "⚠️ Auftrag " + jobId + " wurde bereits abgebrochen.");
                return;
            }
            if (detail.getStatus() == JobStatus.PAUSED) {
                sender.sendReply(update, "⚠️ Auftrag " + jobId + " ist bereits pausiert.");
                return;
            }
            if (detail.getStatus() == JobStatus.PENDING) {
                sender.sendReply(update, "⚠️ Auftrag " + jobId + " wartet noch auf Start.");
                return;
            }

            coordinatorClient.pauseJob(jobId);
            sender.sendReply(update, "⏸️ Auftrag " + jobId + " wurde pausiert.");

        } catch (Exception e) {
            sender.sendReply(update, "❌ Fehler beim Pausieren: " + e.getMessage());
        }
    }
}
