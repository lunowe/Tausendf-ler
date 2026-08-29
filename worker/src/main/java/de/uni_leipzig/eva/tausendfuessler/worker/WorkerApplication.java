package de.uni_leipzig.eva.tausendfuessler.worker;

import de.uni_leipzig.eva.tausendfuessler.worker.client.WorkerClient;
import de.uni_leipzig.eva.tausendfuessler.worker.crawler.CrawlSuccess;
import de.uni_leipzig.eva.tausendfuessler.worker.crawler.CrawlFailure;
import de.uni_leipzig.eva.tausendfuessler.worker.pool.CrawlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class WorkerApplication {

    private static final Logger log = LoggerFactory.getLogger(WorkerApplication.class);

    public static void main(String[] args) {
        var parsed = parseArgs(args);
        var coordinator = parsed.get("--coordinator");
        var url = parsed.get("--url");
        if (coordinator == null && url == null) {
            System.err.println("Usage: java -jar worker.jar --coordinator <host:port> [--threads <n>] [--id <workerId>]");
            System.err.println("       java -jar worker.jar --url <url> [--threads <n>]   (single crawl, no coordinator)");
            System.exit(1);
        }
        var threads = Integer.parseInt(parsed.getOrDefault("--threads", String.valueOf(Runtime.getRuntime().availableProcessors())));

        if (coordinator != null) {
            runAgainstCoordinator(coordinator, threads, parsed.getOrDefault("--id", "worker-" + UUID.randomUUID().toString().substring(0, 8)));
            return;
        }
        crawlOnce(url, threads);
    }

    /** Normal mode: connect to the coordinator and process work packages until killed. */
    private static void runAgainstCoordinator(String coordinator, int threads, String workerId) {
        var hostPort = coordinator.split(":");
        var host = hostPort[0];
        var port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 9090;

        var executor = new CrawlExecutor(threads);
        var client = new WorkerClient(host, port, workerId, threads, executor);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            client.stop();
            executor.shutdown();
        }));
        log.info("worker {} starting, coordinator={}:{} threads={}", workerId, host, port, threads);
        client.run();
    }

    /** Debug mode: crawl one URL and print the result. */
    private static void crawlOnce(String url, int threads) {
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
