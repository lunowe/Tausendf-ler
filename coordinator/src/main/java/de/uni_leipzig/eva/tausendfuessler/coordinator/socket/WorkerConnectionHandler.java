package de.uni_leipzig.eva.tausendfuessler.coordinator.socket;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import de.uni_leipzig.eva.tausendfuessler.common.protocol.ProtocolJson;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobRuntime;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.JobRuntimeRegistry;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.Scheduler;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.WorkerRegistry;
import de.uni_leipzig.eva.tausendfuessler.coordinator.crawl.WorkerSession;
import de.uni_leipzig.eva.tausendfuessler.coordinator.service.ResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Serves one worker connection: line loop, REGISTER handshake, REQUEST_WORK / PAGE_RESULT dispatch.
 * On EOF or socket error the worker's in-flight URLs are put back into the frontier of every job.
 */
final class WorkerConnectionHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(WorkerConnectionHandler.class);

    private final Socket socket;
    private final WorkerRegistry workers;
    private final JobRuntimeRegistry jobs;
    private final Scheduler scheduler;
    private final ResultService resultService;
    private final Runnable onClose;

    WorkerConnectionHandler(Socket socket, WorkerRegistry workers, JobRuntimeRegistry jobs,
                            Scheduler scheduler, ResultService resultService, Runnable onClose) {
        this.socket = socket;
        this.workers = workers;
        this.jobs = jobs;
        this.scheduler = scheduler;
        this.resultService = resultService;
        this.onClose = onClose;
    }

    @Override
    public void run() {
        WorkerSession session = null;
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Message message;
                try {
                    message = ProtocolJson.decode(line);
                } catch (IllegalArgumentException e) {
                    log.warn("malformed line from {}: {}", socket.getRemoteSocketAddress(), line);
                    writeLine(out, new Message.Error("malformed message"));
                    continue;
                }

                if (session == null) {
                    if (message instanceof Message.Register register) {
                        session = new WorkerSession(register.workerId(), register.threads(), out);
                        workers.register(session);
                        session.send(new Message.Registered(register.workerId()));
                        log.info("worker {} registered with {} threads ({} workers online)",
                                register.workerId(), register.threads(), workers.size());
                    } else {
                        writeLine(out, new Message.Error("first message must be REGISTER"));
                    }
                    continue;
                }

                switch (message) {
                    case Message.RequestWork request -> session.send(scheduler.assign(request));
                    case Message.PageResult result -> {
                        log.info("PAGE_RESULT from {} for job {} ({}) handled on thread {}",
                                result.workerId(), result.jobId(), result.url(), Thread.currentThread().threadId());
                        resultService.handle(result);
                    }
                    case Message.Register ignored -> session.send(new Message.Error("already registered"));
                    case Message.Error error -> log.warn("worker {} reports error: {}", session.workerId(), error.message());
                    default -> session.send(new Message.Error("unexpected message type"));
                }
            }
            log.info("worker {} disconnected (EOF)", session == null ? socket.getRemoteSocketAddress() : session.workerId());
        } catch (IOException e) {
            log.warn("connection to worker {} lost: {}",
                    session == null ? socket.getRemoteSocketAddress() : session.workerId(), e.getMessage());
        } finally {
            if (session != null) {
                recover(session.workerId());
            }
            onClose.run();
        }
    }

    /** Crash recovery: forget the worker and give its open URLs back to the frontiers. */
    private void recover(String workerId) {
        workers.remove(workerId);
        int requeued = 0;
        for (JobRuntime runtime : jobs.all()) {
            requeued += runtime.requeue(workerId);
        }
        log.warn("worker {} removed from registry, {} in-flight URL(s) requeued", workerId, requeued);
    }

    private static void writeLine(BufferedWriter out, Message message) throws IOException {
        out.write(ProtocolJson.encode(message));
        out.write('\n');
        out.flush();
    }
}
