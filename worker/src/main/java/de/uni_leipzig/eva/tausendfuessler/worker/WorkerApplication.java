package de.uni_leipzig.eva.tausendfuessler.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class WorkerApplication {

    private static final Logger log = LoggerFactory.getLogger(WorkerApplication.class);
    static final String USAGE = "Usage: java -jar worker.jar --coordinator <host:port> [--threads <n>] [--id <name>]";

    public static void main(String[] args) {
        var parsed = parseArgs(args);
        var coordinator = parsed.get("--coordinator");
        if (coordinator == null || coordinator.isBlank()) {
            System.err.println(USAGE);
            System.exit(1);
        }
        CoordinatorAddress address;
        int threads;
        try {
            address = parseCoordinator(coordinator);
            threads = Integer.parseInt(parsed.getOrDefault(
                    "--threads", String.valueOf(Runtime.getRuntime().availableProcessors())));
            if (threads < 1) {
                throw new IllegalArgumentException("threads must be positive");
            }
        } catch (IllegalArgumentException e) {
            System.err.println(USAGE);
            System.exit(1);
            return;
        }

        var workerId = parsed.getOrDefault("--id", defaultWorkerId());
        var client = new WorkerClient(address.host(), address.port(), workerId, threads);
        Runtime.getRuntime().addShutdownHook(new Thread(client::close, "worker-shutdown"));
        log.info("worker {} starting, coordinator={}:{} threads={}", workerId, address.host(), address.port(), threads);
        client.run();
    }

    static CoordinatorAddress parseCoordinator(String value) {
        int separator = value.lastIndexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("coordinator must be host:port");
        }
        return new CoordinatorAddress(value.substring(0, separator), Integer.parseInt(value.substring(separator + 1)));
    }

    static String defaultWorkerId() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "worker";
        }
        return hostname + "-" + UUID.randomUUID().toString().substring(0, 8);
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

    record CoordinatorAddress(String host, int port) {}
}
