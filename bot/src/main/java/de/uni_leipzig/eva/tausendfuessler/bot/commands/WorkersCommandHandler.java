package de.uni_leipzig.eva.tausendfuessler.bot.commands;

import de.uni_leipzig.eva.tausendfuessler.bot.dto.WorkerInfo;
import de.uni_leipzig.eva.tausendfuessler.bot.service.CoordinatorClient;
import de.uni_leipzig.eva.tausendfuessler.bot.service.MessageSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** /workers – alle aktuell verbundenen Worker. */
@Component
public class WorkersCommandHandler implements CommandHandler {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final CoordinatorClient coordinatorClient;

    public WorkersCommandHandler(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @Override
    public void handle(Update update, MessageSender sender) {
        try {
            sender.sendReply(update, format(coordinatorClient.listWorkers()));
        } catch (Exception e) {
            sender.sendReply(update, "❌ Worker konnten nicht abgerufen werden: " + e.getMessage());
        }
    }

    static String format(List<WorkerInfo> workers) {
        if (workers.isEmpty()) {
            return "Keine Worker verbunden.";
        }
        StringBuilder sb = new StringBuilder("🖥️ ").append(workers.size()).append(" Worker online\n\n");
        for (WorkerInfo w : workers) {
            sb.append(w.workerId())
              .append(" · ").append(w.threads()).append(" Threads")
              .append(" · ").append(w.inFlight()).append(" in Arbeit")
              .append(" · seit ").append(w.connectedAt() == null ? "?" : TIME.format(w.connectedAt()))
              .append('\n');
        }
        return sb.toString();
    }
}
