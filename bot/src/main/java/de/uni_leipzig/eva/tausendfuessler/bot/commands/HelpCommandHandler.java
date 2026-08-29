package de.uni_leipzig.eva.tausendfuessler.bot.commands;

import de.uni_leipzig.eva.tausendfuessler.bot.service.MessageSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class HelpCommandHandler implements CommandHandler {

    private static final String HELP = """
            🐛 Tausendfüßler – verteilter Webcrawler

            /crawl <URL> [Tiefe] [Filter ...]  – Crawl starten, Ergebnisse kommen live
            /list                              – deine Aufträge
            /status <Job-ID>                   – Details zu einem Auftrag
            /pause <Job-ID>                    – Auftrag pausieren
            /resume <Job-ID>                   – Auftrag fortsetzen
            /abort <Job-ID>                    – Auftrag abbrechen (Ergebnisse bleiben)
            /stats                             – Nutzungsstatistik
            /help                              – diese Hilfe

            Beispiel: /crawl https://example.com 2
            """;

    @Override
    public void handle(Update update, MessageSender sender) {
        sender.sendReply(update, HELP);
    }
}
