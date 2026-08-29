package de.uni_leipzig.eva.tausendfuessler.bot.commands;

import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobInfo;
import de.uni_leipzig.eva.tausendfuessler.bot.service.CoordinatorClient;
import de.uni_leipzig.eva.tausendfuessler.bot.service.MessageSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

/** /list – alle Aufträge dieses Chats. */
@Component
public class ListCommandHandler implements CommandHandler {

    private final CoordinatorClient coordinatorClient;

    public ListCommandHandler(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @Override
    public void handle(Update update, MessageSender sender) {
        try {
            List<JobInfo> jobs = coordinatorClient.listJobs(update.getMessage().getChatId());
            if (jobs.isEmpty()) {
                sender.sendReply(update, "📋 Noch keine Aufträge. Starte einen mit /crawl <URL>.");
                return;
            }
            StringBuilder sb = new StringBuilder("📋 Deine Aufträge:\n\n");
            for (JobInfo job : jobs) {
                sb.append(StatusCommandHandler.formatStatus(job.getStatus()))
                  .append("  ").append(job.getJobId())
                  .append("  ").append(job.getUrl())
                  .append("  (").append(job.getPagesVisited()).append(" Seiten)\n");
            }
            sender.sendReply(update, sb.toString());
        } catch (Exception e) {
            sender.sendReply(update, "❌ Liste konnte nicht abgerufen werden: " + e.getMessage());
        }
    }
}
