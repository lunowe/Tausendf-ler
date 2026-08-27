package com.example.telegrambot.commands;

import com.example.telegrambot.service.CoordinatorClient;
import com.example.telegrambot.service.MessageSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class ListCommandHandler implements CommandHandler {

    private final CoordinatorClient coordinatorClient;

    public ListCommandHandler(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @Override
    public void handle(Update update, MessageSender sender) {
        // TODO: CoordinatorClient.listJobs() implementieren
        sender.sendReply(update, "📋 Liste aller Jobs kommt noch...");
    }
}
