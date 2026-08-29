package de.uni_leipzig.eva.krakensuche.worker.pool;

import de.uni_leipzig.eva.krakensuche.worker.crawler.CrawlOutcome;
import de.uni_leipzig.eva.krakensuche.worker.crawler.CrawlSuccess;
import de.uni_leipzig.eva.krakensuche.worker.crawler.CrawlFailure;
import de.uni_leipzig.eva.krakensuche.worker.crawler.CrawlException;
import de.uni_leipzig.eva.krakensuche.worker.crawler.HtmlExtractor;
import de.uni_leipzig.eva.krakensuche.worker.crawler.PageFetcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlExecutorTest {

    private final PageFetcher fetcher = new PageFetcher() {
        @Override
        public FetchResult fetch(String url) {
            if ("http://error.test".equals(url)) {
                throw new CrawlException("Connection refused", new RuntimeException());
            }
            return switch (url) {
                case "http://success.test" -> new FetchResult(200, """
                        <html><head><title>Test</title></head>
                        <body><p>hello</p><a href="https://example.com">link</a></body></html>
                        """);
                case "http://notfound.test" -> new FetchResult(404, "");
                default -> throw new CrawlException("Unknown URL", new RuntimeException());
            };
        }
    };

    private final HtmlExtractor extractor = new HtmlExtractor();

    @Test
    void returnsSuccessForValidUrl() {
        var executor = new CrawlExecutor(2, fetcher, extractor);
        try {
            var outcome = executor.submit("http://success.test").join();
            assertThat(outcome).isInstanceOf(CrawlSuccess.class);
            var success = (CrawlSuccess) outcome;
            assertThat(success.url()).isEqualTo("http://success.test");
            assertThat(success.httpStatus()).isEqualTo(200);
            assertThat(success.title()).isEqualTo("Test");
            assertThat(success.outgoingLinks()).contains("https://example.com");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void returnsFailureForHttpError() {
        var executor = new CrawlExecutor(2, fetcher, extractor);
        try {
            var outcome = executor.submit("http://notfound.test").join();
            assertThat(outcome).isInstanceOf(CrawlFailure.class);
            assertThat(((CrawlFailure) outcome).error()).contains("404");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void returnsFailureForConnectionError() {
        var executor = new CrawlExecutor(2, fetcher, extractor);
        try {
            var outcome = executor.submit("http://error.test").join();
            assertThat(outcome).isInstanceOf(CrawlFailure.class);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void handlesConcurrentCrawls() {
        var executor = new CrawlExecutor(4, fetcher, extractor);
        try {
            var futures = List.of(
                    executor.submit("http://success.test"),
                    executor.submit("http://notfound.test"),
                    executor.submit("http://success.test"),
                    executor.submit("http://error.test")
            );
            var results = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .thenApply(v -> futures.stream().map(CompletableFuture::join).toList())
                    .join();

            assertThat(results).hasSize(4);
            assertThat(results).anyMatch(r -> r instanceof CrawlSuccess);
            assertThat(results).anyMatch(r -> r instanceof CrawlFailure);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void handlesHighVolumeConcurrentCrawls() {
        var executor = new CrawlExecutor(4, fetcher, extractor);
        try {
            var futures = IntStream.range(0, 100)
                    .mapToObj(i -> executor.submit("http://success.test"))
                    .toList();
            var results = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .thenApply(v -> futures.stream().map(CompletableFuture::join).toList())
                    .join();

            assertThat(results).hasSize(100);
            assertThat(results).allMatch(r -> r instanceof CrawlSuccess);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void shutsDownGracefully() {
        var executor = new CrawlExecutor(1, fetcher, extractor);
        executor.submit("http://success.test").join();
        executor.shutdown();
        assertThat(executor.executor.isShutdown()).isTrue();
    }
}
