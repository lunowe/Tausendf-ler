package de.uni_leipzig.eva.tausendfuessler.coordinator.crawl;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerTest {

    private JobRuntimeRegistry jobs;
    private WorkerRegistry workers;
    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        jobs = new JobRuntimeRegistry();
        workers = new WorkerRegistry();
        scheduler = new Scheduler(jobs, workers);
    }

    @Test
    void noWorkWhenNoRunningJob() {
        assertThat(scheduler.assign(new Message.RequestWork("w1", 4))).isInstanceOf(Message.NoWork.class);

        JobRuntime paused = new JobRuntime("p", "https://a.example/", 1, List.of());
        paused.setStatus(JobStatus.PAUSED);
        jobs.register(paused);
        assertThat(scheduler.assign(new Message.RequestWork("w1", 4))).isInstanceOf(Message.NoWork.class);
    }

    @Test
    void roundRobinOverJobsAndPackageSizeLimitedToTwiceThreads() {
        JobRuntime a = new JobRuntime("a", "https://a.example/", 1, List.of());
        JobRuntime b = new JobRuntime("b", "https://b.example/", 1, List.of());
        jobs.register(a);
        jobs.register(b);
        a.offerLinks(0, List.of("https://a.example/1", "https://a.example/2", "https://a.example/3", "https://a.example/4", "https://a.example/5"));
        WorkerSession w1 = new WorkerSession("w1", 2, new StringWriter());
        workers.register(w1);

        Message.WorkPackage first = (Message.WorkPackage) scheduler.assign(new Message.RequestWork("w1", 100));
        Message.WorkPackage second = (Message.WorkPackage) scheduler.assign(new Message.RequestWork("w1", 100));

        assertThat(Set.of(first.jobId(), second.jobId())).containsExactlyInAnyOrder("a", "b");
        assertThat(first.urls()).hasSizeLessThanOrEqualTo(4);
        assertThat(w1.inFlight()).isEqualTo(first.urls().size() + second.urls().size());

        // drain everything: every url must come out exactly once
        Set<String> seen = new HashSet<>(first.urls());
        seen.addAll(second.urls());
        Message m;
        while ((m = scheduler.assign(new Message.RequestWork("w1", 100))) instanceof Message.WorkPackage pkg) {
            for (String u : pkg.urls()) {
                assertThat(seen.add(u)).isTrue();
            }
        }
        assertThat(seen).hasSize(7);
    }

    @Test
    void registryTracksLeastLoadedWorkerAndBroadcasts() {
        StringWriter out1 = new StringWriter();
        StringWriter out2 = new StringWriter();
        WorkerSession w1 = new WorkerSession("w1", 4, out1);
        WorkerSession w2 = new WorkerSession("w2", 4, out2);
        workers.register(w1);
        workers.register(w2);
        w1.addInFlight(3);

        assertThat(workers.leastLoaded()).contains(w2);
        assertThat(workers.loadSnapshot()).isEqualTo("w1=3, w2=0");

        workers.broadcast(new Message.JobSignal("j", Message.Signal.PAUSE));
        assertThat(out1.toString()).contains("\"type\":\"JOB_SIGNAL\"").contains("PAUSE");
        assertThat(out2.toString()).contains("JOB_SIGNAL");

        workers.remove("w1");
        assertThat(workers.size()).isEqualTo(1);
    }
}
