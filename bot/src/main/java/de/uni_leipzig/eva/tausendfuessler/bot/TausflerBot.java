package de.uni_leipzig.eva.tausendfuessler.bot;

import de.uni_leipzig.eva.tausendfuessler.bot.commands.CommandHandler;
import de.uni_leipzig.eva.tausendfuessler.bot.config.AllowedChats;
import de.uni_leipzig.eva.tausendfuessler.bot.config.CommandRegistry;
import de.uni_leipzig.eva.tausendfuessler.bot.service.MessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

/** Receives Telegram updates via long polling and dispatches "/command ..." messages to the matching handler. */
@Component
public class TausflerBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TausflerBot.class);

    private final String username;
    private final CommandRegistry commandRegistry;
    private final MessageSender messageSender;
    private final AllowedChats allowedChats;

    public TausflerBot(@Value("${telegram.bot.token}") String token,
                       @Value("${telegram.bot.username}") String username,
                       CommandRegistry commandRegistry,
                       MessageSender messageSender,
                       AllowedChats allowedChats) {
        super(token);
        this.username = username;
        this.commandRegistry = commandRegistry;
        this.messageSender = messageSender;
        this.allowedChats = allowedChats;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        long chatId = update.getMessage().getChatId();
        if (!allowedChats.allows(chatId)) {
            log.warn("rejected message from chat {} (not in TELEGRAM_ALLOWED_CHATS)", chatId);
            messageSender.sendReply(update, "⛔ Dieser Bot ist privat.");
            return;
        }
        String text = update.getMessage().getText().trim();
        String command = text.split("\\s+")[0];
        // "/crawl@tausfler_bot" in group chats -> "/crawl"
        int at = command.indexOf('@');
        if (at > 0) {
            command = command.substring(0, at);
        }

        CommandHandler handler = commandRegistry.getHandler(command.toLowerCase());
        if (handler == null) {
            messageSender.sendReply(update, "Unbekannter Befehl. Nutze /help für Hilfe.");
            return;
        }
        try {
            handler.handle(update, messageSender);
        } catch (Exception e) {
            log.error("handler for {} failed", command, e);
            messageSender.sendReply(update, "❌ Interner Fehler: " + e.getMessage());
        }
    }
}
