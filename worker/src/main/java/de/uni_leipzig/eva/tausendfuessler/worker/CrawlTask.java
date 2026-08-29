package de.uni_leipzig.eva.tausendfuessler.worker;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import de.uni_leipzig.eva.tausendfuessler.worker.crawler.CrawlFailure;
import de.uni_leipzig.eva.tausendfuessler.worker.crawler.CrawlOutcome;
import de.uni_leipzig.eva.tausendfuessler.worker.crawler.CrawlSuccess;
import de.uni_leipzig.eva.tausendfuessler.worker.pool.CrawlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Applies PAUSE/RESUME/ABORT immediately before handing a URL to the existing crawl core. */
final class CrawlTask {

    private static final Logger log = LoggerFactory.getLogger(CrawlTask.class);
    private static final long PAUSE_POLL_MS = 200;

    private final String jobId;
    private final String url;
    private final CrawlExecutor executor;
    private final Map<String, Message.Signal> jobControl;
    private final BooleanSupplier sessionActive;

    CrawlTask(String jobId, String url, CrawlExecutor executor,
              Map<String, Message.Signal> jobControl, BooleanSupplier sessionActive) {
        this.jobId = jobId;
        this.url = url;
        this.executor = executor;
        this.jobControl = jobControl;
        this.sessionActive = sessionActive;
    }

    CompletableFuture<CrawlOutcome> submit() {
        return executor.submit(url, this::awaitPermission).thenApply(outcome -> {
            int status = switch (outcome) {
                case CrawlSuccess success -> success.httpStatus();
                case CrawlFailure ignored -> 0;
            };
            log.info("crawled url={} status={} thread={}", url, status, Thread.currentThread().threadId());
            return outcome;
        });
    }

    private void awaitPermission() {
        while (sessionActive.getAsBoolean()) {
            var signal = jobControl.get(jobId);
            if (signal == Message.Signal.ABORT) {
                throw new CancellationException("job aborted");
            }
            if (signal != Message.Signal.PAUSE) {
                return;
            }
            try {
                Thread.sleep(PAUSE_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("crawl task interrupted");
            }
        }
        throw new CancellationException("connection lost");
    }
}
