package de.uni_leipzig.eva.tausendfuessler.bot.commands;

import de.uni_leipzig.eva.tausendfuessler.bot.dto.CrawlRequest;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.CrawlResponse;
import de.uni_leipzig.eva.tausendfuessler.bot.service.CoordinatorClient;
import de.uni_leipzig.eva.tausendfuessler.bot.service.MessageSender;
import de.uni_leipzig.eva.tausendfuessler.bot.service.ResultPoller;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /crawl &lt;URL&gt; [Tiefe] [Filter ...] */
@Component
public class CrawlCommandHandler implements CommandHandler {

    private static final String USAGE = "Verwendung: /crawl <URL> [Tiefe 1-10] [Filter1 Filter2 ...]\n"
            + "Beispiel: /crawl https://example.com 2 blog";
    private static final int MAX_DEPTH = 10;

    private final CoordinatorClient coordinatorClient;
    private final ResultPoller resultPoller;

    public CrawlCommandHandler(CoordinatorClient coordinatorClient, ResultPoller resultPoller) {
        this.coordinatorClient = coordinatorClient;
        this.resultPoller = resultPoller;
    }

    @Override
    public void handle(Update update, MessageSender sender) {
        String[] parts = update.getMessage().getText().trim().split("\\s+");
        if (parts.length < 2) {
            sender.sendReply(update, USAGE);
            return;
        }

        String url = parts[1];
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            sender.sendReply(update, "Ungültige URL – bitte mit http:// oder https:// beginnen.\n" + USAGE);
            return;
        }

        int depth = 1;
        List<String> filters = new ArrayList<>();
        int rest = 2;
        if (parts.length > 2 && parts[2].matches("\\d+")) {
            depth = Integer.parseInt(parts[2]);
            rest = 3;
        }
        filters.addAll(Arrays.asList(parts).subList(rest, parts.length));

        if (depth < 1 || depth > MAX_DEPTH) {
            sender.sendReply(update, "Tiefe muss zwischen 1 und " + MAX_DEPTH + " liegen.");
            return;
        }

        long chatId = update.getMessage().getChatId();
        try {
            CrawlResponse response = coordinatorClient.startCrawl(new CrawlRequest(url, depth, filters, chatId));
            resultPoller.subscribe(chatId, response.getJobId());
            sender.sendReply(update, "✅ Crawl gestartet\n"
                    + "🆔 " + response.getJobId() + "\n"
                    + "🔗 " + url + "\n"
                    + "📏 Tiefe " + depth
                    + (filters.isEmpty() ? "" : "\n🔎 Filter: " + String.join(", ", filters))
                    + "\n\n📡 Neue Seiten erscheinen hier live.");
        } catch (Exception e) {
            sender.sendReply(update, "❌ Crawl konnte nicht gestartet werden: " + e.getMessage());
        }
    }
}
