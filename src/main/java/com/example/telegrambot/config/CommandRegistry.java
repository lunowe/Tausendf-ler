package com.example.telegrambot.config;

import com.example.telegrambot.commands.CommandHandler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CommandRegistry {

    private final Map<String, CommandHandler> commandMap = new HashMap<>();
    private final List<CommandHandler> handlers;

    public CommandRegistry(List<CommandHandler> handlers) {
        this.handlers = handlers;
    }

    @PostConstruct
    private void init() {
        for (CommandHandler handler : handlers) {
            String className = handler.getClass().getSimpleName();
            // Konvention: "CrawlCommandHandler" → "/crawl"
            String command = className.replace("CommandHandler", "").toLowerCase();
            commandMap.put("/" + command, handler);
        }
        System.out.println(">>>>> Registrierte Befehle: " + commandMap.keySet() + " <<<<<");
    }

    public CommandHandler getHandler(String command) {
        return commandMap.get(command);
    }


    public Set<String> getAllCommands() {
        return commandMap.keySet();

    }
}
