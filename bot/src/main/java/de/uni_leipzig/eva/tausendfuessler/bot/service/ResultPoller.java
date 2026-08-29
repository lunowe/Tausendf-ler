package de.uni_leipzig.eva.tausendfuessler.bot.service;

import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobDetail;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobStatus;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live stream via polling: for every subscribed job, fetch pages newer than the last seen cursor
 * and forward them to the chat. When the job reaches a terminal state, send the final report and unsubscribe.
 */
@Service
public class ResultPoller {

    private static final Logger log = LoggerFactory.getLogger(ResultPoller.class);
    /** Page size of GET /api/jobs/{id}/results (JobService.RESULT_PAGE_SIZE). */
    static final int RESULT_PAGE_SIZE = 50;
    private static final int MESSAGE_MAX_LENGTH = 4000;

    private final CoordinatorClient coordinatorClient;
    private final MessageSender messageSender;

    /** jobId -> subscription (a chat may follow several jobs at once). */
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public ResultPoller(CoordinatorClient coordinatorClient, MessageSender messageSender) {
        this.coordinatorClient = coordinatorClient;
        this.messageSender = messageSender;
    }

    public void subscribe(long chatId, String jobId) {
        subscriptions.put(jobId, new Subscription(chatId, 0L));
        log.info("chat {} subscribed to job {}", chatId, jobId);
    }

    public void unsubscribe(String jobId) {
        if (subscriptions.remove(jobId) != null) {
            log.info("job {} unsubscribed", jobId);
        }
    }

    @Scheduled(fixedDelayString = "${bot.poll-interval-ms:1000}")
    public void pollForNewResults() {
        subscriptions.forEach(this::pollJob);
    }

    private void pollJob(String jobId, Subscription sub) {
        try {
            // status first: every page persisted before this status was set is then guaranteed to be drained below
            JobDetail detail = coordinatorClient.getJobDetail(jobId);

            List<PageResult> batch;
            do {
                batch = coordinatorClient.getNewResults(jobId, sub.lastSeq);
                if (!batch.isEmpty()) {
                    for (PageResult page : batch) {
                        sub.lastSeq = Math.max(sub.lastSeq, page.getSeq());
                    }
                    PageResult last = batch.get(batch.size() - 1);
                    log.info("job {} seq {}..{} delivered, last crawled {} ms ago", jobId,
                            batch.get(0).getSeq(), last.getSeq(), millisSince(last.getCrawledAt()));
                    for (String text : batchMessages(batch)) {
                        messageSender.send(sub.chatId, text);
                    }
                }
            } while (batch.size() >= RESULT_PAGE_SIZE); // the coordinator caps one call at RESULT_PAGE_SIZE rows

            if (isTerminal(detail.getStatus())) {
                messageSender.send(sub.chatId, formatReport(detail));
                unsubscribe(jobId);
            }
        } catch (Exception e) {
            log.warn("polling job {} failed: {}", jobId, e.getMessage());
        }
    }

    /** Several pages per Telegram message (Telegram rate-limits ~1 msg/s per chat), split at the length limit. */
    static List<String> batchMessages(List<PageResult> pages) {
        List<String> messages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (PageResult page : pages) {
            String text = formatPage(page);
            if (current.length() > 0 && current.length() + 2 + text.length() > MESSAGE_MAX_LENGTH) {
                messages.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(text);
        }
        if (current.length() > 0) {
            messages.add(current.toString());
        }
        return messages;
    }

    /** Live-delivery latency (NFA: < 2 s); -1 if the timestamp is missing or unparseable. */
    private static long millisSince(String isoInstant) {
        try {
            return isoInstant == null ? -1 : System.currentTimeMillis() - Instant.parse(isoInstant).toEpochMilli();
        } catch (DateTimeParseException e) {
            return -1;
        }
    }

    private static boolean isTerminal(JobStatus status) {
        return status == JobStatus.COMPLETED || status == JobStatus.ABORTED || status == JobStatus.FAILED;
    }

    private static String formatPage(PageResult page) {
        StringBuilder sb = new StringBuilder("📄 ").append(page.getUrl()).append('\n');
        if (page.getTitle() != null && !page.getTitle().isBlank()) {
            sb.append("📌 ").append(page.getTitle()).append('\n');
        }
        if (page.getTextSnippet() != null && !page.getTextSnippet().isBlank()) {
            sb.append("📝 ").append(page.getTextSnippet()).append('\n');
        }
        return sb.append("📏 Tiefe ").append(page.getDepth()).toString();
    }

    private String formatReport(JobDetail d) {
        StringBuilder sb = new StringBuilder();
        sb.append(d.getStatus() == JobStatus.COMPLETED ? "✅ Crawl abgeschlossen" : "🚫 Crawl beendet (" + d.getStatus() + ")")
          .append("\n\n")
          .append("🆔 ").append(d.getJobId()).append('\n')
          .append("🔗 ").append(d.getUrl()).append('\n')
          .append("📄 Besuchte Seiten: ").append(d.getPagesVisited()).append('\n')
          .append("🔗 Extrahierte Links: ").append(d.getLinksFound()).append('\n')
          .append("❌ Fehler: ").append(d.getErrors()).append('\n');
        if (d.getStartedAt() != null && d.getFinishedAt() != null) {
            long seconds = Duration.between(d.getStartedAt(), d.getFinishedAt()).getSeconds();
            sb.append("⏳ Dauer: ").append(seconds).append(" s");
        }
        return sb.toString();
    }

    private static final class Subscription {
        final long chatId;
        volatile long lastSeq;

        Subscription(long chatId, long lastSeq) {
            this.chatId = chatId;
            this.lastSeq = lastSeq;
        }
    }
}
