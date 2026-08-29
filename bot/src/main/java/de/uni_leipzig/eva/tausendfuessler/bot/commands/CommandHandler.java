package de.uni_leipzig.eva.tausendfuessler.bot.commands;

import de.uni_leipzig.eva.tausendfuessler.bot.service.MessageSender;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface CommandHandler {
    void handle(Update update, MessageSender sender);
}
