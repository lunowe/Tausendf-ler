package com.example.telegrambot.commands;

import com.example.telegrambot.dto.CrawlRequest;
import com.example.telegrambot.dto.CrawlResponse;
import com.example.telegrambot.service.CoordinatorClient;
import com.example.telegrambot.service.MessageSender;
import com.example.telegrambot.service.ResultPoller;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.ArrayList;
import java.util.List;

@Component
public class CrawlCommandHandler implements CommandHandler {

    private final CoordinatorClient coordinatorClient;
    private final ResultPoller resultPoller;

    public CrawlCommandHandler(CoordinatorClient coordinatorClient, ResultPoller resultPoller) {
        this.coordinatorClient = coordinatorClient;
        this.resultPoller = resultPoller;
    }

    @Override
    public void handle(Update update, MessageSender sender) {
        String text = update.getMessage().getText();
        String[] parts = text.split(" ");

        if (parts.length < 2) {
            sender.sendReply(update, "Falsch, so hier bitte: `/crawl <URL> [Tiefe] [Filter1 Filter2 ...]`\n" +
                    "Beispiel: `/crawl https://example.com 2 product blog`");
            return;
        }

        String url = parts[1];
        int depth = 1;
        List<String> filters = new ArrayList<>();

        if (parts.length >= 3) {
            try {
                depth = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                filters.add(parts[2]);
            }
        }

        int startIdx = 3;
        if (depth > 1) {
            for (int i = 3; i < parts.length; i++) {
                filters.add(parts[i]);
            }
        } else if (parts.length >= 3) {
            for (int i = 2; i < parts.length; i++) {
                if (i == 2 && parts[2].equals(filters.get(0))) continue;
                filters.add(parts[i]);
            }
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            sender.sendReply(update, "Ungültige URL. Bitte mit `http://` oder `https://` beginnen.");
            return;
        }

        if (depth < 1 || depth > 10) {
            sender.sendReply(update, "Tiefe muss zwischen 1 und 10 liegen.");
            return;
        }

        try {
            CrawlRequest request = new CrawlRequest(url, depth, filters);
            CrawlResponse response = coordinatorClient.startCrawl(request);

            String jobId = response.getJobId();
            Long chatIdLong = Long.parseLong(update.getMessage().getChatId().toString());

            resultPoller.subscribe(chatIdLong, jobId);

            sender.sendReply(update, "✅ **Crawl-Auftrag gestartet!**\n" +
                    "🆔 Job-ID: `" + jobId + "`\n" +
                    "🔗 URL: " + url + "\n" +
                    "📏 Tiefe: " + depth + "\n\n" +
                    "📡 Live-Ergebnisse werden hier angezeigt...");

        } catch (Exception e) {
            sender.sendReply(update, "FEHLER beim Starten des Crawls: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
