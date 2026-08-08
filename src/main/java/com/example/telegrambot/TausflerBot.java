package com.example.telegrambot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
public class TausflerBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.token}")
    private String token;

    @Value("${telegram.bot.username}")
    private String username;

    public TausflerBot() {
        System.out.println(">>>>> TAUSFLERBOT-KONSTRUKTOR AUFGERUFEN <<<<<");
    }

    @Override
    public String getBotToken() { return token; }

    @Override
    public String getBotUsername() { return username; }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            if (text.startsWith("/crawl")) {
                String[] parts = text.split(" ");
                if (parts.length >= 3) {
                    String url = parts[1];
                    int depth = Integer.parseInt(parts[2]);
                    sendReply(chatId, "Starte Crawl auf " + url + " mit Tiefe " + depth);
                } else {
                    sendReply(chatId, "Benutzung: /crawl <url> <tiefe>");
                }
            } else {
                sendReply(chatId, "Unbekannter Befehl. Versuch /crawl <url> <tiefe>");
            }
        }
    }

    private void sendReply(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
