package de.uni_leipzig.eva.tausendfuessler.bot.commands;

import de.uni_leipzig.eva.tausendfuessler.bot.dto.SearchHit;
import de.uni_leipzig.eva.tausendfuessler.bot.service.CoordinatorClient;
import de.uni_leipzig.eva.tausendfuessler.bot.service.MessageSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

/** /search &lt;Text&gt; – Volltextsuche über alle gecrawlten Seiten. */
@Component
public class SearchCommandHandler implements CommandHandler {

    private static final int LIMIT = 10;

    private final CoordinatorClient coordinatorClient;

    public SearchCommandHandler(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @Override
    public void handle(Update update, MessageSender sender) {
        String[] parts = update.getMessage().getText().trim().split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            sender.sendReply(update, "Verwendung: /search <Text>");
            return;
        }
        String query = parts[1].trim();
        try {
            List<SearchHit> hits = coordinatorClient.search(query, LIMIT);
            if (hits.isEmpty()) {
                sender.sendReply(update, "Keine Treffer.");
                return;
            }
            StringBuilder sb = new StringBuilder("🔍 Treffer für \"").append(query).append("\":\n\n");
            for (SearchHit hit : hits) {
                sb.append("🔗 ").append(hit.getUrl());
                if (hit.getTitle() != null && !hit.getTitle().isBlank()) {
                    sb.append(" – ").append(hit.getTitle());
                }
                sb.append('\n');
            }
            sender.sendReply(update, sb.toString());
        } catch (Exception e) {
            sender.sendReply(update, "❌ Suche fehlgeschlagen: " + e.getMessage());
        }
    }
}
