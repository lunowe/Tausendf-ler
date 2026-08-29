package de.uni_leipzig.eva.tausendfuessler.bot.service;

import de.uni_leipzig.eva.tausendfuessler.bot.TausflerBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/** Sends plain-text messages (no Markdown parsing, so URLs and titles can never break the message). */
@Service
public class MessageSender {

    private static final Logger log = LoggerFactory.getLogger(MessageSender.class);
    private static final int TELEGRAM_MAX_LENGTH = 4096;

    private final TausflerBot bot;

    public MessageSender(@Lazy TausflerBot bot) {
        this.bot = bot;
    }

    public void sendReply(Update update, String text) {
        send(update.getMessage().getChatId(), text);
    }

    public void send(long chatId, String text) {
        String body = text.length() > TELEGRAM_MAX_LENGTH ? text.substring(0, TELEGRAM_MAX_LENGTH - 1) + "…" : text;
        SendMessage message = new SendMessage(String.valueOf(chatId), body);
        message.setDisableWebPagePreview(true);
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.warn("sending to chat {} failed: {}", chatId, e.getMessage());
        }
    }
}
