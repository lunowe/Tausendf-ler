package de.uni_leipzig.eva.krakensuche.worker;

import de.uni_leipzig.eva.krakensuche.worker.crawler.CrawlSuccess;
import de.uni_leipzig.eva.krakensuche.worker.crawler.CrawlFailure;
import de.uni_leipzig.eva.krakensuche.worker.pool.CrawlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public final class WorkerApplication {

    private static final Logger log = LoggerFactory.getLogger(WorkerApplication.class);

    public static void main(String[] args) {
        var parsed = parseArgs(args);
        var url = parsed.get("--url");
        if (url == null) {
            System.err.println("Usage: java -jar krakensuche-worker.jar --url <url> [--threads <n>]");
            System.exit(1);
        }
        var threads = Integer.parseInt(parsed.getOrDefault("--threads", String.valueOf(Runtime.getRuntime().availableProcessors())));

        var executor = new CrawlExecutor(threads);
        try {
            log.info("crawling url={} threads={}", url, threads);
            var outcome = executor.submit(url).join();

            switch (outcome) {
                case CrawlSuccess s -> {
                    System.out.println("URL: " + s.url());
                    System.out.println("Status: " + s.httpStatus());
                    System.out.println("Title: " + (s.title() != null ? s.title() : "(none)"));
                    System.out.println("Text length: " + s.plainText().length());
                    System.out.println("Outgoing links: " + s.outgoingLinks().size());
                    s.outgoingLinks().forEach(link -> System.out.println("  -> " + link));
                }
                case CrawlFailure f -> {
                    System.err.println("URL: " + f.url());
                    System.err.println("Error: " + f.error());
                    System.exit(1);
                }
            }
        } finally {
            executor.shutdown();
        }
    }

    static Map<String, String> parseArgs(String[] args) {
        var map = new LinkedHashMap<String, String>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                var key = args[i];
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    map.put(key, args[++i]);
                } else {
                    map.put(key, "");
                }
            }
        }
        return map;
    }
}
