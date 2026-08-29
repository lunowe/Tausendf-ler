package de.uni_leipzig.eva.tausendfuessler.bot.commands;

import de.uni_leipzig.eva.tausendfuessler.bot.dto.Stats;
import de.uni_leipzig.eva.tausendfuessler.bot.service.CoordinatorClient;
import de.uni_leipzig.eva.tausendfuessler.bot.service.MessageSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

/** /stats – systemweite Nutzungsstatistik. */
@Component
public class StatsCommandHandler implements CommandHandler {

    private final CoordinatorClient coordinatorClient;

    public StatsCommandHandler(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @Override
    public void handle(Update update, MessageSender sender) {
        try {
            Stats s = coordinatorClient.getStats();
            StringBuilder sb = new StringBuilder("📊 Statistik\n\n")
                    .append("Aufträge gesamt: ").append(s.getTotalJobs()).append('\n')
                    .append("Aktive Aufträge: ").append(s.getActiveJobs()).append('\n')
                    .append("Seiten gesamt: ").append(s.getTotalPagesCrawled()).append('\n');
            if (s.getTopDomains() != null && !s.getTopDomains().isEmpty()) {
                sb.append("\nMeist gecrawlte Domains:\n");
                s.getTopDomains().forEach((domain, count) ->
                        sb.append("  ").append(domain).append(": ").append(count).append('\n'));
            }
            sender.sendReply(update, sb.toString());
        } catch (Exception e) {
            sender.sendReply(update, "❌ Statistik konnte nicht abgerufen werden: " + e.getMessage());
        }
    }
}
