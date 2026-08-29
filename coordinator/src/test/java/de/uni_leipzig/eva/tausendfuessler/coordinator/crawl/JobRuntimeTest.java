package de.uni_leipzig.eva.tausendfuessler.coordinator.crawl;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class JobRuntimeTest {

    private static final String START = "https://example.org/";

    @Test
    void startUrlIsHandedOutFirstAtDepthZero() {
        JobRuntime job = new JobRuntime("j1", START, 2, List.of());

        Message.WorkPackage pkg = job.takeWork(10, "w1");

        assertThat(pkg).isNotNull();
        assertThat(pkg.depth()).isZero();
        assertThat(pkg.urls()).containsExactly("https://example.org");
        assertThat(job.takeWork(10, "w1")).isNull();
        assertThat(job.isFinished()).isFalse();
    }

    @Test
    void dedupUnderConcurrentOffersHandsOutEachUrlExactlyOnce() throws Exception {
        JobRuntime job = new JobRuntime("j1", START, 5, List.of());
        job.takeWork(1, "w0");
        job.complete(START);

        List<String> urls = IntStream.range(0, 1000).mapToObj(i -> "https://example.org/page/" + i).toList();
        List<String> variants = new ArrayList<>();
        for (String u : urls) {
            variants.add(u);
            variants.add(u + "/");           // trailing slash
            variants.add(u + "#section");    // fragment
            variants.add(u.replace("example.org", "EXAMPLE.org"));
        }

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                go.await();
                job.offerLinks(0, variants);
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        Set<String> handedOut = ConcurrentHashMap.newKeySet();
        Message.WorkPackage pkg;
        int total = 0;
        while ((pkg = job.takeWork(37, "w1")) != null) {
            for (String u : pkg.urls()) {
                assertThat(handedOut.add(u)).as("url handed out twice: " + u).isTrue();
            }
            total += pkg.urls().size();
        }
        assertThat(total).isEqualTo(1000);
    }

    @Test
    void depthLimitStopsLinksBeyondMaxDepth() {
        JobRuntime job = new JobRuntime("j1", START, 1, List.of());

        assertThat(job.offerLinks(0, List.of("https://example.org/a"))).isEqualTo(1);
        assertThat(job.offerLinks(1, List.of("https://example.org/b"))).isZero();
        assertThat(job.frontierSize()).isEqualTo(2); // start + a
    }

    @Test
    void filterKeepsOnlyUrlsContainingAnyFilterString() {
        JobRuntime job = new JobRuntime("j1", START, 3, List.of("/docs", "/blog"));

        int added = job.offerLinks(0, List.of(
                "https://example.org/docs/intro",
                "https://example.org/blog/post",
                "https://example.org/shop/item"));

        assertThat(added).isEqualTo(2);
        job.takeWork(1, "w1"); // start url
        Message.WorkPackage pkg = job.takeWork(10, "w1");
        assertThat(pkg.depth()).isEqualTo(1);
        assertThat(pkg.urls()).containsExactlyInAnyOrder("https://example.org/docs/intro", "https://example.org/blog/post");
        assertThat(pkg.filters()).containsExactly("/docs", "/blog");
    }

    @Test
    void lowestDepthIsDrainedFirst() {
        JobRuntime job = new JobRuntime("j1", START, 3, List.of());
        job.offerLinks(0, List.of("https://example.org/d1"));
        job.offerLinks(1, List.of("https://example.org/d2"));

        assertThat(job.takeWork(10, "w1").depth()).isZero();
        assertThat(job.takeWork(10, "w1").depth()).isEqualTo(1);
        assertThat(job.takeWork(10, "w1").depth()).isEqualTo(2);
    }

    @Test
    void requeueAfterCrashReturnsInFlightUrlsOfThatWorkerOnly() {
        JobRuntime job = new JobRuntime("j1", START, 3, List.of());
        job.offerLinks(0, List.of("https://example.org/a", "https://example.org/b", "https://example.org/c"));
        job.takeWork(1, "w1");                        // start url -> w1
        job.complete(START);
        Message.WorkPackage w1 = job.takeWork(2, "w1"); // a, b -> w1
        Message.WorkPackage w2 = job.takeWork(2, "w2"); // c -> w2
        assertThat(job.inFlightCount()).isEqualTo(3);

        assertThat(job.requeue("w1")).isEqualTo(2);

        assertThat(job.inFlightCount()).isEqualTo(1);
        Message.WorkPackage again = job.takeWork(10, "w3");
        assertThat(again.depth()).isEqualTo(1);
        assertThat(again.urls()).containsExactlyInAnyOrderElementsOf(w1.urls());
        assertThat(again.urls()).doesNotContainAnyElementsOf(w2.urls());
    }

    @Test
    void finishedOnlyWhenFrontierAndInFlightAreEmpty() {
        JobRuntime job = new JobRuntime("j1", START, 1, List.of());
        assertThat(job.isFinished()).isFalse();

        job.takeWork(1, "w1");
        assertThat(job.isFinished()).isFalse();

        job.offerLinks(0, List.of("https://example.org/a"));
        job.complete("https://example.org/");
        assertThat(job.isFinished()).isFalse();

        job.takeWork(1, "w1");
        job.complete("https://example.org/a#frag");
        assertThat(job.isFinished()).isTrue();
    }
}
