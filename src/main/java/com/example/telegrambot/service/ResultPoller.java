package com.example.telegrambot.service;

import com.example.telegrambot.dto.JobDetail;
import com.example.telegrambot.dto.JobStatus;
import com.example.telegrambot.dto.PageResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ResultPoller {

    private final CoordinatorClient coordinatorClient;
    private final MessageSender messageSender;

    // Chat-ID → Job-ID
    private final Map<Long, String> activeSubscriptions = new ConcurrentHashMap<>();
    // Job-ID → letzter gesendeter Zeitstempel
    private final Map<String, String> lastResultTimestamps = new ConcurrentHashMap<>();

    public ResultPoller(CoordinatorClient coordinatorClient, MessageSender messageSender) {
        this.coordinatorClient = coordinatorClient;
        this.messageSender = messageSender;
    }

    public void subscribe(Long chatId, String jobId) {
        activeSubscriptions.put(chatId, jobId);
        lastResultTimestamps.put(jobId, Instant.now().toString());
        System.out.println("Chat " + chatId + " abonniert Job " + jobId);
    }

    public void unsubscribe(Long chatId) {
        String jobId = activeSubscriptions.remove(chatId);
        if (jobId != null) {
            lastResultTimestamps.remove(jobId);
            System.out.println("Chat " + chatId + " von Job " + jobId + " abgemeldet");
        }
    }

    public String getSubscribedJobId(Long chatId) {
        return activeSubscriptions.get(chatId);
    }

    public boolean isSubscribed(Long chatId) {
        return activeSubscriptions.containsKey(chatId);
    }

    // NEU: Alle 2 Sekunden
    @Scheduled(fixedDelay = 2000)
    public void pollForNewResults() {
        if (activeSubscriptions.isEmpty()) return;

        for (Map.Entry<Long, String> entry : activeSubscriptions.entrySet()) {
            Long chatId = entry.getKey();
            String jobId = entry.getValue();

            try {
                // 1. Job-Status prüfen
                JobDetail detail = coordinatorClient.getJobDetail(jobId);

                // 2. Wenn Job beendet → Report + Abmelden
                if (detail.getStatus() == JobStatus.COMPLETED ||
                    detail.getStatus() == JobStatus.ABORTED ||
                    detail.getStatus() == JobStatus.FAILED) {
                    sendFinalReport(chatId, detail);
                    unsubscribe(chatId);
                    continue;
                }

                // 3. Neue Ergebnisse holen
                String since = lastResultTimestamps.getOrDefault(jobId,
                    Instant.now().minus(1, ChronoUnit.MINUTES).toString());

                var results = coordinatorClient.getNewResults(jobId, since);

                // 4. Ergebnisse senden
                if (results != null && !results.isEmpty()) {
                    for (PageResult result : results) {
                        messageSender.sendReply(chatId, formatPageResult(result));
                    }
                    // 5. Zeitstempel auf die letzte neue Seite setzen
                    String last = results.get(results.size() - 1).getCrawledAt();
                    if (last != null) {
                        lastResultTimestamps.put(jobId, last);
                    }
                }

            } catch (Exception e) {
                System.err.println("Polling-Fehler für Job " + jobId + ": " + e.getMessage());
            }
        }
    }

    private String formatPageResult(PageResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("📄 **Neue Seite gefunden!**\n");
        sb.append("🔗 ").append(result.getUrl()).append("\n");
        if (result.getTitle() != null && !result.getTitle().isEmpty()) {
            sb.append("📌 ").append(result.getTitle()).append("\n");
        }
        if (result.getTextSnippet() != null && !result.getTextSnippet().isEmpty()) {
            sb.append("📝 ").append(result.getTextSnippet()).append("\n");
        }
        sb.append("📏 Tiefe: ").append(result.getDepth());
        return sb.toString();
    }

    private void sendFinalReport(Long chatId, JobDetail detail) {
        StringBuilder report = new StringBuilder();
        report.append("✅ **Crawl abgeschlossen!**\n\n");
        report.append("📋 **Report für Auftrag `").append(detail.getJobId()).append("`**\n");
        report.append("🔗 Start-URL: ").append(detail.getUrl()).append("\n");
        report.append("📏 Maximale Tiefe: ").append(detail.getMaxDepth()).append("\n");
        report.append("📄 Besuchte Seiten: ").append(detail.getPagesVisited()).append("\n");
        report.append("🔗 Extrahierte Links: ").append(detail.getLinksFound()).append("\n");
        report.append("❌ Fehler: ").append(detail.getErrors()).append("\n");

        if (detail.getStartedAt() != null && detail.getFinishedAt() != null) {
            long duration = java.time.Duration.between(detail.getStartedAt(), detail.getFinishedAt()).getSeconds();
            report.append("⏳ Dauer: ").append(duration).append(" Sekunden\n");
        }

        messageSender.sendReply(chatId, report.toString());
    }
}
