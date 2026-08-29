package de.uni_leipzig.eva.tausendfuessler.bot.service;

import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobDetail;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobStatus;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live stream via polling: for every subscribed job, fetch pages newer than the last seen cursor
 * and forward them to the chat. When the job reaches a terminal state, send the final report and unsubscribe.
 */
@Service
public class ResultPoller {

    private static final Logger log = LoggerFactory.getLogger(ResultPoller.class);

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
            var results = coordinatorClient.getNewResults(jobId, sub.lastSeq);
            for (PageResult page : results) {
                messageSender.send(sub.chatId, formatPage(page));
                sub.lastSeq = Math.max(sub.lastSeq, page.getSeq());
            }

            JobDetail detail = coordinatorClient.getJobDetail(jobId);
            if (isTerminal(detail.getStatus())) {
                messageSender.send(sub.chatId, formatReport(detail));
                unsubscribe(jobId);
            }
        } catch (Exception e) {
            log.warn("polling job {} failed: {}", jobId, e.getMessage());
        }
    }

    private static boolean isTerminal(JobStatus status) {
        return status == JobStatus.COMPLETED || status == JobStatus.ABORTED || status == JobStatus.FAILED;
    }

    private String formatPage(PageResult page) {
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
