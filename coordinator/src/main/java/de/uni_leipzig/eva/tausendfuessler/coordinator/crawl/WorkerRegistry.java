package de.uni_leipzig.eva.tausendfuessler.coordinator.crawl;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/** All currently connected workers. */
@Component
public class WorkerRegistry {

    private final ConcurrentMap<String, WorkerSession> sessions = new ConcurrentHashMap<>();

    public void register(WorkerSession session) {
        sessions.put(session.workerId(), session);
    }

    public Optional<WorkerSession> get(String workerId) {
        return Optional.ofNullable(sessions.get(workerId));
    }

    public WorkerSession remove(String workerId) {
        return sessions.remove(workerId);
    }

    public Collection<WorkerSession> all() {
        return sessions.values();
    }

    public int size() {
        return sessions.size();
    }

    /** Worker with the fewest open URLs (Least-Work-First). */
    public Optional<WorkerSession> leastLoaded() {
        return sessions.values().stream().min(Comparator.comparingInt(WorkerSession::inFlight));
    }

    /** "w1=3, w2=0" - logged on every assignment so the load distribution is visible. */
    public String loadSnapshot() {
        return sessions.values().stream()
                .collect(Collectors.toMap(WorkerSession::workerId, WorkerSession::inFlight, (a, b) -> a, TreeMap::new))
                .entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    public void broadcast(Message.JobSignal signal) {
        for (WorkerSession session : sessions.values()) {
            session.send(signal);
        }
    }
}
