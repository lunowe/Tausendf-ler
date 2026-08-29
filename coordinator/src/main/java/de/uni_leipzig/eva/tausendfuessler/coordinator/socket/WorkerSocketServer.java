package de.uni_leipzig.eva.tausendfuessler.coordinator.socket;

import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobRuntimeRegistry;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.Scheduler;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.WorkerRegistry;
import de.uni_leipzig.eva.tausendfuessler.coordinator.service.ResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Raw TCP server for workers. One accept thread, one {@link WorkerConnectionHandler} thread per connection
 * (cached thread pool). Started as a Spring lifecycle bean so it listens before the application reports ready.
 */
@Component
public class WorkerSocketServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WorkerSocketServer.class);

    private final int configuredPort;
    private final WorkerToken workerToken;
    private final WorkerRegistry workers;
    private final JobRuntimeRegistry jobs;
    private final Scheduler scheduler;
    private final ResultService resultService;

    private final Set<Socket> openConnections = ConcurrentHashMap.newKeySet();
    private volatile ServerSocket serverSocket;
    private volatile ExecutorService handlerPool;
    private volatile Thread acceptThread;
    private volatile boolean running;

    public WorkerSocketServer(@Value("${tausendfuessler.worker-port:9090}") int configuredPort,
                              @Value("${tausendfuessler.worker-token:}") String workerToken,
                              WorkerRegistry workers, JobRuntimeRegistry jobs,
                              Scheduler scheduler, ResultService resultService) {
        this.configuredPort = configuredPort;
        this.workerToken = new WorkerToken(workerToken);
        this.workers = workers;
        this.jobs = jobs;
        this.scheduler = scheduler;
        this.resultService = resultService;
    }

    @Override
    public void start() {
        try {
            serverSocket = new ServerSocket(configuredPort);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open worker socket on port " + configuredPort, e);
        }
        handlerPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "worker-handler");
            t.setDaemon(true);
            return t;
        });
        running = true;
        acceptThread = new Thread(this::acceptLoop, "worker-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        log.info("worker socket listening on port {}", getPort());
        if (!workerToken.enabled()) {
            log.warn("WORKER_TOKEN is not set - every worker may register");
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                openConnections.add(socket);
                log.info("worker connection from {}", socket.getRemoteSocketAddress());
                handlerPool.execute(new WorkerConnectionHandler(socket, workerToken, workers, jobs, scheduler,
                        resultService, () -> openConnections.remove(socket)));
            } catch (IOException e) {
                if (running) {
                    log.warn("accept failed: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        closeQuietly(serverSocket);
        for (Socket socket : openConnections) {
            closeQuietly(socket);
        }
        if (handlerPool != null) {
            handlerPool.shutdownNow();
        }
        log.info("worker socket closed");
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException ignored) {
            // shutting down anyway
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Actual port (relevant when configured with 0 = ephemeral, e.g. in tests). */
    public int getPort() {
        ServerSocket s = serverSocket;
        return s == null ? -1 : s.getLocalPort();
    }
}
