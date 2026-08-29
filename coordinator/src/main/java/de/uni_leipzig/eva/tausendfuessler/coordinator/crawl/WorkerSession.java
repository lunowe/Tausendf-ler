package de.uni_leipzig.eva.tausendfuessler.coordinator.crawl;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import de.uni_leipzig.eva.tausendfuessler.common.protocol.ProtocolJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/** One connected worker: its output stream (writes are serialized) and the number of URLs it still owes us. */
public final class WorkerSession {

    private static final Logger log = LoggerFactory.getLogger(WorkerSession.class);

    private final String workerId;
    private final int threads;
    private final Writer out;
    private final Instant connectedAt = Instant.now();
    private final AtomicInteger inFlight = new AtomicInteger();

    public WorkerSession(String workerId, int threads, Writer out) {
        this.workerId = workerId;
        this.threads = threads;
        this.out = out;
    }

    /** Writes one message line. Synchronized: the handler thread and broadcasting REST threads share the socket. */
    public synchronized void send(Message message) {
        try {
            out.write(ProtocolJson.encode(message));
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            log.warn("Cannot send {} to worker {}: {}", message.getClass().getSimpleName(), workerId, e.getMessage());
        }
    }

    public String workerId() { return workerId; }
    public int threads() { return threads; }
    public Instant connectedAt() { return connectedAt; }
    public int inFlight() { return inFlight.get(); }
    public void addInFlight(int n) { inFlight.addAndGet(n); }
    public void completedOne() { inFlight.updateAndGet(v -> Math.max(0, v - 1)); }
}
