package de.uni_leipzig.eva.tausendfuessler.worker;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import de.uni_leipzig.eva.tausendfuessler.worker.crawler.CrawlFailure;
import de.uni_leipzig.eva.tausendfuessler.worker.crawler.CrawlOutcome;
import de.uni_leipzig.eva.tausendfuessler.worker.crawler.CrawlSuccess;
import de.uni_leipzig.eva.tausendfuessler.worker.net.CoordinatorConnection;
import de.uni_leipzig.eva.tausendfuessler.worker.pool.CrawlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Long-running TCP worker client described by PROTOCOL.md section 2. */
public final class WorkerClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WorkerClient.class);
    private static final int SNIPPET_LENGTH = 300;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final long POLL_INTERVAL_MS = 200;
    private static final long MIN_BACKOFF_MS = 1_000;
    private static final long MAX_BACKOFF_MS = 10_000;
    /** Safety net: ask again if the coordinator has not answered a REQUEST_WORK for this long. */
    private static final long REQUEST_TIMEOUT_MS = 2_000;

    private final String host;
    private final int port;
    private final String workerId;
    private final int threads;
    /** Shared secret for REGISTER ({@code WORKER_TOKEN}); {@code null} if the coordinator runs without one. */
    private final String token;
    private final CrawlExecutor executor;
    private final ConcurrentHashMap<String, Message.Signal> jobControl = new ConcurrentHashMap<>();
    private final Object wakeup = new Object();

    private volatile boolean running = true;
    private volatile boolean unauthorized;
    private volatile long lastNoWorkUntil;
    private volatile Session currentSession;

    public WorkerClient(String host, int port, String workerId, int threads) {
        this(host, port, workerId, threads, null);
    }

    public WorkerClient(String host, int port, String workerId, int threads, String token) {
        this(host, port, workerId, threads, token, new CrawlExecutor(threads));
    }

    WorkerClient(String host, int port, String workerId, int threads, String token, CrawlExecutor executor) {
        this.host = host;
        this.port = port;
        this.workerId = workerId;
        this.threads = threads;
        this.token = token;
        this.executor = executor;
    }

    /** {@code true} once the coordinator rejected our token; {@link #run()} then returns instead of reconnecting. */
    public boolean unauthorized() {
        return unauthorized;
    }

    /**
     * Blocks until {@link #close()} is called and reconnects after connection failures - except when the
     * coordinator answered REGISTER with {@code ERROR unauthorized}: a wrong token will not fix itself by retrying.
     */
    public void run() {
        long backoff = MIN_BACKOFF_MS;
        while (running) {
            Session session = null;
            try {
                session = connect();
                backoff = MIN_BACKOFF_MS;
                runSession(session);
            } catch (IOException | RuntimeException e) {
                if (running) {
                    log.warn("connection to {}:{} lost: {}", host, port, e.getMessage());
                }
            } finally {
                if (session != null) {
                    session.disconnect();
                }
            }

            if (running) {
                log.info("reconnecting in {} ms", backoff);
                await(backoff);
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            }
        }
    }

    private Session connect() throws IOException {
        var socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            var session = new Session(new CoordinatorConnection(socket));
            currentSession = session;
            lastNoWorkUntil = 0;
            session.connection.send(new Message.Register(workerId, threads, token));
            var reader = new Thread(() -> readMessages(session), "coordinator-reader");
            reader.setDaemon(true);
            reader.start();
            log.info("connected to coordinator {}:{} as {}", host, port, workerId);
            return session;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private void runSession(Session session) {
        while (running && session.active.get()) {
            long now = System.currentTimeMillis();
            boolean requestOpen = session.requestSentAt != 0 && now - session.requestSentAt < REQUEST_TIMEOUT_MS;
            if (session.registered && now >= lastNoWorkUntil && !requestOpen) {
                int freeSlots = threads - session.inFlight.get();
                if (freeSlots > 0) {
                    // one open request at a time, otherwise every completion would fetch another whole package
                    session.requestSentAt = now;
                    send(session, new Message.RequestWork(workerId, freeSlots));
                }
            }
            await(POLL_INTERVAL_MS);
        }
    }

    private void readMessages(Session session) {
        try {
            session.connection.readLoop(message -> handle(session, message));
        } catch (IOException | RuntimeException e) {
            if (running && session.active.get()) {
                log.warn("coordinator reader stopped: {}", e.getMessage());
            }
        } finally {
            session.disconnect();
            signalWakeup();
        }
    }

    private void handle(Session session, Message message) {
        switch (message) {
            case Message.Registered registered -> {
                session.registered = true;
                log.info("registered as {}", registered.workerId());
                signalWakeup();
            }
            case Message.WorkPackage workPackage -> {
                session.requestSentAt = 0;
                submit(session, workPackage);
            }
            case Message.NoWork noWork -> {
                session.requestSentAt = 0;
                lastNoWorkUntil = System.currentTimeMillis() + noWork.retryAfterMs();
            }
            case Message.JobSignal signal -> updateJobControl(signal);
            case Message.Error error -> {
                if (!session.registered && "unauthorized".equals(error.message())) {
                    log.error("coordinator {}:{} rejected worker {}: wrong or missing token (--token / WORKER_TOKEN) - giving up",
                            host, port, workerId);
                    unauthorized = true;
                    close();
                } else {
                    log.warn("coordinator reported error: {}", error.message());
                }
            }
            default -> log.warn("unexpected message from coordinator: {}", message);
        }
    }

    private void submit(Session session, Message.WorkPackage workPackage) {
        for (String url : workPackage.urls()) {
            session.inFlight.incrementAndGet();
            new CrawlTask(workPackage.jobId(), url, executor, jobControl, session.active::get)
                    .submit()
                    .whenComplete((outcome, failure) -> complete(session, workPackage, url, outcome, failure));
        }
    }

    private void complete(Session session, Message.WorkPackage workPackage, String url,
                          CrawlOutcome outcome, Throwable failure) {
        try {
            if (failure == null && session.active.get()) {
                send(session, toPageResult(workPackage, outcome));
            }
        } finally {
            session.inFlight.updateAndGet(value -> Math.max(0, value - 1));
            signalWakeup();
        }
    }

    private void updateJobControl(Message.JobSignal signal) {
        switch (signal.signal()) {
            case ABORT -> jobControl.put(signal.jobId(), Message.Signal.ABORT);
            case PAUSE -> jobControl.compute(signal.jobId(),
                    (jobId, current) -> current == Message.Signal.ABORT ? current : Message.Signal.PAUSE);
            case RESUME -> jobControl.computeIfPresent(signal.jobId(),
                    (jobId, current) -> current == Message.Signal.PAUSE ? null : current);
        }
        signalWakeup();
    }

    private Message.PageResult toPageResult(Message.WorkPackage workPackage, CrawlOutcome outcome) {
        long crawledAt = System.currentTimeMillis();
        return switch (outcome) {
            case CrawlSuccess success -> new Message.PageResult(
                    workerId, workPackage.jobId(), success.url(), workPackage.depth(), success.httpStatus(),
                    success.title(), snippet(success.plainText()), success.outgoingLinks(), null, crawledAt);
            case CrawlFailure failure -> new Message.PageResult(
                    workerId, workPackage.jobId(), failure.url(), workPackage.depth(), 0,
                    null, null, List.of(), failure.error(), crawledAt);
        };
    }

    private void send(Session session, Message message) {
        if (!session.active.get()) {
            return;
        }
        try {
            session.connection.send(message);
        } catch (IOException e) {
            session.disconnect();
            signalWakeup();
            log.warn("send to coordinator failed: {}", e.getMessage());
        }
    }

    private static String snippet(String text) {
        return text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH);
    }

    private void await(long millis) {
        synchronized (wakeup) {
            try {
                wakeup.wait(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void signalWakeup() {
        synchronized (wakeup) {
            wakeup.notifyAll();
        }
    }

    @Override
    public void close() {
        running = false;
        var session = currentSession;
        if (session != null) {
            session.disconnect();
        }
        executor.shutdown();
        signalWakeup();
    }

    private final class Session {
        private final CoordinatorConnection connection;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicInteger inFlight = new AtomicInteger();
        private volatile boolean registered;
        /** Epoch millis of the unanswered REQUEST_WORK, 0 if none is open. */
        private volatile long requestSentAt;

        private Session(CoordinatorConnection connection) {
            this.connection = connection;
        }

        private void disconnect() {
            if (active.compareAndSet(true, false)) {
                inFlight.set(0);
                if (currentSession == this) {
                    currentSession = null;
                }
                try {
                    connection.close();
                } catch (IOException ignored) {
                    // The connection is already unusable.
                }
            }
        }
    }
}
