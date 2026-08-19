package com.example.telegrambot;

import com.example.telegrambot.commands.CommandHandler;
import com.example.telegrambot.commands.CrawlCommandHandler;
import com.example.telegrambot.commands.StatusCommandHandler;
import com.example.telegrambot.config.CommandRegistry;
import com.example.telegrambot.service.MessageSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class TausflerBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.token}")
    private String token;

    @Value("${telegram.bot.username}")
    private String username;

    private final CommandRegistry commandRegistry;
    private final CrawlCommandHandler crawlHandler;
    private final StatusCommandHandler statusHandler;
    private final MessageSender messageSender;


    public TausflerBot(CommandRegistry commandRegistry, MessageSender messageSender) {
        this.commandRegistry = commandRegistry;
        this.messageSender = messageSender;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        // 1. Prüfen: Ist es eine Text-Nachricht?
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        // 2. Befehl extrahieren (alles vor dem ersten Leerzeichen)
        String text = update.getMessage().getText();
        String command = text.split(" ")[0];

        // 3. Passenden Handler aus der Registry holen
        CommandHandler handler = commandRegistry.getHandler(command);

        // 4. Wenn gefunden -> an Handler weiterleiten
        if (handler != null) {
            handler.handle(update, messageSender);
        } else {
            // 5. Sonst: Fehlermeldung über MessageSender
            messageSender.sendReply(update, "Unbekannter Befehl. Nutze /help für Hilfe.");
        }
    }
}
