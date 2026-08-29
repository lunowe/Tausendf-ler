package com.example.telegrambot.commands;

import com.example.telegrambot.config.CommandRegistry;
import com.example.telegrambot.service.MessageSender;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class HelpCommandHandler implements CommandHandler {

    private final CommandRegistry commandRegistry;

    public HelpCommandHandler(@Lazy CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }

    @Override
    public void handle(Update update, MessageSender sender) {
        //  alle Befehle aus der Registry holen
        StringBuilder help = new StringBuilder("Verfügbare Befehle:\n\n");
        for (String cmd : commandRegistry.getAllCommands()) {
            help.append(cmd).append("\n");
        }
        help.append("\nBeispiel: /crawl https://bioshi24.be/de/products/bio-buchweizengrutze-ungerostet-5-kg-horeca-45391 2");

        sender.sendReply(update, help.toString());
    }
}
