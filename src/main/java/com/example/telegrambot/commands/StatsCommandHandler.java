package com.example.telegrambot.commands;

import com.example.telegrambot.dto.Stats;
import com.example.telegrambot.service.CoordinatorClient;
import com.example.telegrambot.service.MessageSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class StatsCommandHandler implements CommandHandler {

    private final CoordinatorClient coordinatorClient;

    public StatsCommandHandler(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @Override
public void handle(Update update, MessageSender sender) {
    try {
        Stats stats = coordinatorClient.getStats(); // Methode existiert in CoordinatorClient
        if (stats == null) {
            sender.sendReply(update, "❌ Keine Statistiken verfügbar.");
            return;
        }

        StringBuilder sb = new StringBuilder("📊 **System-Statistiken**\n\n");
        sb.append("📌 Aufträge insgesamt: ").append(stats.getTotalJobs()).append("\n");
        sb.append("📄 Gecrawlte Seiten: ").append(stats.getTotalPagesCrawled()).append("\n");
        sb.append("⚡ Aktive Aufträge: ").append(stats.getActiveJobs()).append("\n");

        if (stats.getTopDomains() != null && !stats.getTopDomains().isEmpty()) {
            sb.append("\n🏆 **Top-Domains:**\n");
            stats.getTopDomains().entrySet().stream()
                .limit(5)
                .forEach(e -> sb.append("  • ").append(e.getKey()).append(": ").append(e.getValue()).append("\n"));
        }

        sender.sendReply(update, sb.toString());

    } catch (Exception e) {
        sender.sendReply(update, "❌ Fehler beim Abrufen der Statistiken: " + e.getMessage());
        e.printStackTrace();
    }
}
}
