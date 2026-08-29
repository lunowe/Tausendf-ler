package com.example.telegrambot.commands;

import com.example.telegrambot.service.MessageSender;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface CommandHandler {
    void handle(Update update, MessageSender sender);
}
