package de.uni_leipzig.eva.krakensuche.worker.pool;

import de.uni_leipzig.eva.krakensuche.worker.crawler.CrawlException;
import de.uni_leipzig.eva.krakensuche.worker.crawler.CrawlOutcome;
import de.uni_leipzig.eva.krakensuche.worker.crawler.CrawlSuccess;
import de.uni_leipzig.eva.krakensuche.worker.crawler.CrawlFailure;
import de.uni_leipzig.eva.krakensuche.worker.crawler.HtmlExtractor;
import de.uni_leipzig.eva.krakensuche.worker.crawler.PageFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CrawlExecutor {

    private static final Logger log = LoggerFactory.getLogger(CrawlExecutor.class);

    final ExecutorService executor;
    private final PageFetcher fetcher;
    private final HtmlExtractor extractor;

    public CrawlExecutor(int threadCount) {
        this.executor = Executors.newFixedThreadPool(threadCount);
        this.fetcher = new PageFetcher();
        this.extractor = new HtmlExtractor();
    }

    public CrawlExecutor(int threadCount, PageFetcher fetcher, HtmlExtractor extractor) {
        this.executor = Executors.newFixedThreadPool(threadCount);
        this.fetcher = fetcher;
        this.extractor = extractor;
    }

    public CompletableFuture<CrawlOutcome> submit(String url) {
        return CompletableFuture.supplyAsync(() -> crawl(url), executor);
    }

    public void shutdown() {
        executor.shutdown();
    }

    private CrawlOutcome crawl(String url) {
        try {
            var fetchResult = fetcher.fetch(url);
            if (fetchResult.httpStatus() < 200 || fetchResult.httpStatus() >= 400) {
                return new CrawlFailure(url, "HTTP " + fetchResult.httpStatus());
            }
            var extracted = extractor.extract(url, fetchResult.body());
            return new CrawlSuccess(
                    url,
                    fetchResult.httpStatus(),
                    extracted.title(),
                    extracted.plainText(),
                    extracted.outgoingLinks()
            );
        } catch (CrawlException e) {
            log.warn("crawl failed url={} error={}", url, e.getMessage());
            return new CrawlFailure(url, e.getMessage());
        } catch (Exception e) {
            log.error("unexpected error crawling url={}", url, e);
            return new CrawlFailure(url, "Unexpected error: " + e.getMessage());
        }
    }
}
