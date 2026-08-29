package de.uni_leipzig.eva.tausendfuessler.loadtest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;

/**
 * Generated website on an ephemeral local port: pages {@code /p/0 .. /p/N-1}, each linking to a few
 * pseudo-random pages (deterministic per seed) plus the same "popular" pages {@code /p/0 .. /p/popular-1},
 * so the coordinator's dedup is exercised across workers. Every page has a distinct title, roughly 1 KB of
 * text and a marker word. An optional per-request delay simulates a slow server.
 */
public final class SyntheticSite implements AutoCloseable {

    public record Config(int pages, int linksPerPage, int popularPages, long delayMs, long seed) {
        public Config {
            if (pages < 1) {
                throw new IllegalArgumentException("pages must be >= 1");
            }
            if (linksPerPage < 0 || popularPages < 0 || delayMs < 0) {
                throw new IllegalArgumentException("linksPerPage, popularPages and delayMs must be >= 0");
            }
        }
    }

    private static final String PATH_PREFIX = "/p/";
    private static final int TEXT_LENGTH = 1_000;
    private static final String FILLER = "Der Tausendfuessler krabbelt gemuetlich durch das Netz und sammelt Seiten ein. ";

    private final Config config;
    private final HttpServer server;

    public SyntheticSite(Config config) throws IOException {
        this.config = config;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // one thread per in-flight request, otherwise the delay would serialize all fetches
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String startUrl() {
        return pageUrl(0);
    }

    public String pageUrl(int index) {
        return baseUrl() + PATH_PREFIX + index;
    }

    public Config config() {
        return config;
    }

    /** Pages that page {@code index} links to: the popular pages first, then the pseudo-random ones. */
    List<Integer> linksOf(int index) {
        LinkedHashSet<Integer> targets = new LinkedHashSet<>();
        int popular = Math.min(config.popularPages(), config.pages());
        for (int i = 0; i < popular; i++) {
            if (i != index) {
                targets.add(i);
            }
        }
        int wantedRandom = Math.min(config.linksPerPage(), config.pages() - 1);
        Random random = new Random(config.seed() * 31 + index);
        int added = 0;
        int attempts = 0;
        while (added < wantedRandom && attempts++ < wantedRandom * 20) {
            int target = random.nextInt(config.pages());
            if (target != index && targets.add(target)) {
                added++;
            }
        }
        return new ArrayList<>(targets);
    }

    String html(int index) {
        StringBuilder html = new StringBuilder()
                .append("<html><head><title>Seite ").append(index).append("</title></head><body>")
                .append("<h1>Seite ").append(index).append("</h1><p>").append(marker(index)).append(' ');
        while (html.length() < TEXT_LENGTH) {
            html.append(FILLER);
        }
        html.append("</p><ul>");
        for (int target : linksOf(index)) {
            html.append("<li><a href=\"").append(PATH_PREFIX).append(target).append("\">Seite ")
                    .append(target).append("</a></li>");
        }
        return html.append("</ul></body></html>").toString();
    }

    static String marker(int index) {
        return String.format("markerwort%05d", index);
    }

    private void handle(HttpExchange exchange) throws IOException {
        sleep(config.delayMs());
        int index = pageIndex(exchange.getRequestURI().getPath());
        String body = index < 0 ? "not found" : html(index);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(index < 0 ? 404 : 200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private int pageIndex(String path) {
        if (!path.startsWith(PATH_PREFIX)) {
            return -1;
        }
        try {
            int index = Integer.parseInt(path.substring(PATH_PREFIX.length()));
            return index >= 0 && index < config.pages() ? index : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
