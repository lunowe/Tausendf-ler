package de.uni_leipzig.eva.tausendfuessler.loadtest;

import de.uni_leipzig.eva.tausendfuessler.worker.WorkerClient;

import java.util.ArrayList;
import java.util.List;

/** Real {@link WorkerClient}s in daemon threads of this JVM (same approach as the coordinator's EndToEndTest). */
public final class InProcessWorkers implements AutoCloseable {

    /** After closing, wait this long so the coordinator has dropped the sessions before the next measurement. */
    private static final long SESSION_DROP_WAIT_MS = 2_000;

    private final List<WorkerClient> clients = new ArrayList<>();
    private final List<Thread> threads = new ArrayList<>();

    public static InProcessWorkers start(String host, int port, String idPrefix, int count, int threadsPerWorker) {
        InProcessWorkers workers = new InProcessWorkers();
        for (int i = 1; i <= count; i++) {
            String id = idPrefix + "-w" + i;
            WorkerClient client = new WorkerClient(host, port, id, threadsPerWorker);
            Thread thread = new Thread(client::run, id);
            thread.setDaemon(true);
            thread.start();
            workers.clients.add(client);
            workers.threads.add(thread);
        }
        System.out.printf("  %d In-Prozess-Worker mit je %d Threads gestartet -> %s:%d%n", count, threadsPerWorker, host, port);
        CoordinatorApi.sleep(1_000); // let them register before the job is created
        return workers;
    }

    @Override
    public void close() {
        clients.forEach(WorkerClient::close);
        for (Thread thread : threads) {
            try {
                thread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        CoordinatorApi.sleep(SESSION_DROP_WAIT_MS);
    }
}
