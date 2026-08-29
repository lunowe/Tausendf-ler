package com.example.telegrambot.commands;

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

        sender.sendReply(update, "📊 Statistiken kommen noch...");
    }
}
