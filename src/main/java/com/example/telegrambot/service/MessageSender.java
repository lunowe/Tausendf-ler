package com.example.telegrambot.service;

import com.example.telegrambot.TausflerBot;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Service
public class MessageSender {

    private final TausflerBot bot;

    public MessageSender(@Lazy TausflerBot bot){
        this.bot = bot;
    }


    public void sendReply(Update update, String text) {
        Long chatId = update.getMessage().getChatId();
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
