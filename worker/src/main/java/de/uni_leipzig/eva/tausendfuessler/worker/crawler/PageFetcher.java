package de.uni_leipzig.eva.tausendfuessler.worker.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class PageFetcher {

    private static final Logger log = LoggerFactory.getLogger(PageFetcher.class);
    private static final String USER_AGENT = "Tausendfuessler/0.1";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client;

    public PageFetcher() {
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build();
    }

    public FetchResult fetch(String url) {
        log.debug("fetched url={} thread={}", url, Thread.currentThread().threadId());
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new FetchResult(response.statusCode(), response.body(), response.uri().toString());
        } catch (Exception e) {
            throw new CrawlException("Failed to fetch " + url + ": " + e.getMessage(), e);
        }
    }

    /** {@code finalUrl} is the URL after redirects; relative links must be resolved against it. */
    public record FetchResult(int httpStatus, String body, String finalUrl) {
        public FetchResult(int httpStatus, String body) {
            this(httpStatus, body, null);
        }
    }
}
