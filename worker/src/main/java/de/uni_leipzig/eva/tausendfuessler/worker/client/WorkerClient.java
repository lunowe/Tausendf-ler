package de.uni_leipzig.eva.tausendfuessler.worker.client;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import de.uni_leipzig.eva.tausendfuessler.common.protocol.ProtocolJson;
import de.uni_leipzig.eva.tausendfuessler.worker.crawler.CrawlFailure;
import de.uni_leipzig.eva.tausendfuessler.worker.crawler.CrawlOutcome;
import de.uni_leipzig.eva.tausendfuessler.worker.crawler.CrawlSuccess;
import de.uni_leipzig.eva.tausendfuessler.worker.pool.CrawlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP client side of the worker (see PROTOCOL.md, section 2).
 * <p>
 * One persistent connection to the coordinator, line-delimited JSON. The calling thread runs the
 * read loop; crawls run in the {@link CrawlExecutor}; results are written back through a single
 * synchronized writer. On connection loss the client reconnects with backoff (1 s … 10 s).
 */
public final class WorkerClient {

    private static final Logger log = LoggerFactory.getLogger(WorkerClient.class);
    private static final int SNIPPET_LENGTH = 300;
    private static final long MIN_BACKOFF_MS = 1_000;
    private static final long MAX_BACKOFF_MS = 10_000;

    private final String host;
    private final int port;
    private final String workerId;
    private final int threads;
    private final CrawlExecutor executor;

    /** Aborted jobs: results for them are not reported anymore. */
    private final Set<String> abortedJobs = ConcurrentHashMap.newKeySet();
    /** URLs handed to the pool that have not been answered with a PAGE_RESULT yet. */
    private final AtomicInteger inFlight = new AtomicInteger();
    private final ScheduledExecutorService retryTimer = Executors.newSingleThreadScheduledExecutor();

    private volatile boolean running = true;
    private volatile BufferedWriter writer;

    public WorkerClient(String host, int port, String workerId, int threads, CrawlExecutor executor) {
        this.host = host;
        this.port = port;
        this.workerId = workerId;
        this.threads = threads;
        this.executor = executor;
    }

    /** Blocks until {@link #stop()} is called; reconnects on every connection loss. */
    public void run() {
        long backoff = MIN_BACKOFF_MS;
        while (running) {
            try (Socket socket = new Socket(host, port)) {
                log.info("connected to coordinator {}:{} as {}", host, port, workerId);
                backoff = MIN_BACKOFF_MS;
                session(socket);
            } catch (IOException e) {
                log.warn("connection to {}:{} lost: {}", host, port, e.getMessage());
            }
            writer = null;
            if (!running) {
                break;
            }
            log.info("reconnecting in {} ms", backoff);
            sleep(backoff);
            backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
        }
    }

    public void stop() {
        running = false;
        retryTimer.shutdownNow();
    }

    private void session(Socket socket) throws IOException {
        var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        inFlight.set(0);

        send(new Message.Register(workerId, threads));
        String line;
        while (running && (line = in.readLine()) != null) {
            try {
                handle(ProtocolJson.decode(line));
            } catch (IllegalArgumentException e) {
                log.warn("ignoring malformed line: {}", e.getMessage());
            }
        }
    }

    private void handle(Message message) {
        switch (message) {
            case Message.Registered r -> {
                log.info("registered as {}", r.workerId());
                requestWork();
            }
            case Message.WorkPackage p -> onWorkPackage(p);
            case Message.NoWork n -> retryTimer.schedule(this::requestWork, n.retryAfterMs(), TimeUnit.MILLISECONDS);
            case Message.JobSignal s -> onSignal(s);
            case Message.Error e -> log.warn("coordinator reported error: {}", e.message());
            default -> log.warn("unexpected message from coordinator: {}", message);
        }
    }

    private void onSignal(Message.JobSignal signal) {
        log.info("job {} signal {}", signal.jobId(), signal.signal());
        // PAUSE/RESUME: running fetches finish and are reported; the coordinator simply stops
        // handing out packages for a paused job, so nothing else to do here.
        if (signal.signal() == Message.Signal.ABORT) {
            abortedJobs.add(signal.jobId());
        }
    }

    private void onWorkPackage(Message.WorkPackage pkg) {
        log.info("work package job={} depth={} urls={}", pkg.jobId(), pkg.depth(), pkg.urls().size());
        for (String url : pkg.urls()) {
            inFlight.incrementAndGet();
            executor.submit(url).thenAccept(outcome -> onOutcome(pkg, outcome));
        }
    }

    private void onOutcome(Message.WorkPackage pkg, CrawlOutcome outcome) {
        if (!abortedJobs.contains(pkg.jobId())) {
            send(toPageResult(pkg, outcome));
        }
        if (inFlight.decrementAndGet() == 0) {
            requestWork();
        }
    }

    private void requestWork() {
        int capacity = threads - inFlight.get();
        if (capacity > 0) {
            send(new Message.RequestWork(workerId, capacity));
        }
    }

    private Message.PageResult toPageResult(Message.WorkPackage pkg, CrawlOutcome outcome) {
        long now = System.currentTimeMillis();
        return switch (outcome) {
            case CrawlSuccess s -> new Message.PageResult(workerId, pkg.jobId(), s.url(), pkg.depth(),
                    s.httpStatus(), s.title(), snippet(s.plainText()), s.outgoingLinks(), null, now);
            case CrawlFailure f -> new Message.PageResult(workerId, pkg.jobId(), f.url(), pkg.depth(),
                    0, null, null, List.of(), f.error(), now);
        };
    }

    private static String snippet(String text) {
        return text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH);
    }

    private void send(Message message) {
        BufferedWriter out = writer;
        if (out == null) {
            log.debug("not connected, dropping {}", message);
            return;
        }
        synchronized (this) {
            try {
                out.write(ProtocolJson.encode(message));
                out.write('\n');
                out.flush();
            } catch (IOException e) {
                log.warn("send failed: {}", e.getMessage());
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
