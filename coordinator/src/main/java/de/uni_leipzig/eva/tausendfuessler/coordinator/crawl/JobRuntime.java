package de.uni_leipzig.eva.tausendfuessler.coordinator.crawl;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory state of one RUNNING/PAUSED job: frontier (per-depth queues), visited set (dedup), in-flight map.
 * <p>
 * Thread safety: the collections are concurrent; the compound operations that move URLs between frontier and
 * in-flight (and the finish check that looks at both) are {@code synchronized} on this instance so that a job
 * can never be reported finished while a URL is between the two structures.
 */
public final class JobRuntime {

    /** A URL that has been handed to a worker and not yet answered. */
    public record Assignment(String workerId, int depth) {}

    private final String jobId;
    private final int maxDepth;
    private final List<String> filters;
    private final AtomicReference<JobStatus> status = new AtomicReference<>(JobStatus.RUNNING);

    /** depth -> queue of normalized URLs; drained lowest depth first. */
    private final ConcurrentSkipListMap<Integer, ConcurrentLinkedQueue<String>> frontier = new ConcurrentSkipListMap<>();
    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, Assignment> inFlight = new ConcurrentHashMap<>();

    private final AtomicLong nextSeq = new AtomicLong();
    private final AtomicLong pagesVisited = new AtomicLong();
    private final AtomicLong linksFound = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    public JobRuntime(String jobId, String startUrl, int maxDepth, List<String> filters) {
        this.jobId = jobId;
        this.maxDepth = maxDepth;
        this.filters = List.copyOf(filters == null ? List.of() : filters);
        String start = UrlNormalizer.normalize(startUrl);
        if (start == null) {
            throw new IllegalArgumentException("Invalid start URL: " + startUrl);
        }
        // the start URL bypasses the filters, it is always crawled
        visited.add(start);
        queueFor(0).add(start);
    }

    /**
     * Adds links found on a page of depth {@code fromDepth} to the frontier for {@code fromDepth + 1},
     * applying the depth limit, the filters and the dedup set.
     *
     * @return number of URLs actually queued
     */
    public int offerLinks(int fromDepth, Collection<String> links) {
        int depth = fromDepth + 1;
        if (depth > maxDepth || links == null) {
            return 0;
        }
        int added = 0;
        for (String link : links) {
            String url = UrlNormalizer.normalize(link);
            if (url == null || !matchesFilter(url)) {
                continue;
            }
            if (visited.add(url)) { // atomic "first one wins"
                queueFor(depth).add(url);
                added++;
            }
        }
        return added;
    }

    /**
     * Moves up to {@code maxUrls} URLs of the lowest non-empty depth from the frontier to in-flight.
     *
     * @return the package or {@code null} if the frontier is empty
     */
    public synchronized Message.WorkPackage takeWork(int maxUrls, String workerId) {
        for (Map.Entry<Integer, ConcurrentLinkedQueue<String>> entry : frontier.entrySet()) {
            int depth = entry.getKey();
            ConcurrentLinkedQueue<String> queue = entry.getValue();
            List<String> urls = new ArrayList<>();
            String url;
            while (urls.size() < maxUrls && (url = queue.poll()) != null) {
                urls.add(url);
                inFlight.put(url, new Assignment(workerId, depth));
            }
            if (!urls.isEmpty()) {
                return new Message.WorkPackage(jobId, depth, urls, filters);
            }
        }
        return null;
    }

    /** Marks an in-flight URL as answered. */
    public synchronized void complete(String url) {
        String normalized = UrlNormalizer.normalize(url);
        inFlight.remove(normalized == null ? url : normalized);
    }

    /** Crash recovery: puts every URL assigned to {@code workerId} back into the frontier. */
    public synchronized int requeue(String workerId) {
        int count = 0;
        for (Map.Entry<String, Assignment> entry : inFlight.entrySet()) {
            if (entry.getValue().workerId().equals(workerId)) {
                queueFor(entry.getValue().depth()).add(entry.getKey());
                inFlight.remove(entry.getKey());
                count++;
            }
        }
        return count;
    }

    public synchronized boolean isFinished() {
        return inFlight.isEmpty() && frontier.values().stream().allMatch(ConcurrentLinkedQueue::isEmpty);
    }

    private ConcurrentLinkedQueue<String> queueFor(int depth) {
        return frontier.computeIfAbsent(depth, d -> new ConcurrentLinkedQueue<>());
    }

    private boolean matchesFilter(String url) {
        if (filters.isEmpty()) {
            return true;
        }
        for (String filter : filters) {
            if (url.contains(filter)) {
                return true;
            }
        }
        return false;
    }

    // ---- counters / accessors ----

    public String jobId() { return jobId; }
    public int maxDepth() { return maxDepth; }
    public List<String> filters() { return filters; }
    public JobStatus status() { return status.get(); }
    public void setStatus(JobStatus newStatus) { status.set(newStatus); }

    public long nextSeq() { return nextSeq.incrementAndGet(); }
    public long pagesVisited() { return pagesVisited.get(); }
    public long linksFound() { return linksFound.get(); }
    public long errors() { return errors.get(); }
    public void incrementPagesVisited() { pagesVisited.incrementAndGet(); }
    public void addLinksFound(int n) { linksFound.addAndGet(n); }
    public void incrementErrors() { errors.incrementAndGet(); }

    public int inFlightCount() { return inFlight.size(); }
    public int frontierSize() { return frontier.values().stream().mapToInt(ConcurrentLinkedQueue::size).sum(); }
}
