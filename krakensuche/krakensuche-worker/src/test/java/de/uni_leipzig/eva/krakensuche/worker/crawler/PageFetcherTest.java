package de.uni_leipzig.eva.krakensuche.worker.crawler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageFetcherTest {

    private final PageFetcher fetcher = new PageFetcher();
    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/simple", exchange -> {
            var body = "<html><body>OK</body></html>".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/notfound", exchange -> {
            exchange.sendResponseHeaders(404, -1);
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://localhost:" + port + "/simple");
            exchange.sendResponseHeaders(301, -1);
        });
        server.createContext("/error500", exchange -> {
            exchange.sendResponseHeaders(500, -1);
        });
        server.setExecutor(null);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void fetchesSuccessfulPage() {
        var result = fetcher.fetch("http://localhost:" + port + "/simple");
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.body()).contains("OK");
    }

    @Test
    void returns404Status() {
        var result = fetcher.fetch("http://localhost:" + port + "/notfound");
        assertThat(result.httpStatus()).isEqualTo(404);
    }

    @Test
    void followsRedirect() {
        var result = fetcher.fetch("http://localhost:" + port + "/redirect");
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.body()).contains("OK");
    }

    @Test
    void returns500Status() {
        var result = fetcher.fetch("http://localhost:" + port + "/error500");
        assertThat(result.httpStatus()).isEqualTo(500);
    }

    @Test
    void throwsOnUnreachableHost() {
        assertThatThrownBy(() -> fetcher.fetch("http://localhost:1/nonexistent"))
                .isInstanceOf(CrawlException.class);
    }

    @Test
    void throwsOnInvalidUrl() {
        assertThatThrownBy(() -> fetcher.fetch("not a valid url"))
                .isInstanceOf(CrawlException.class);
    }
}
