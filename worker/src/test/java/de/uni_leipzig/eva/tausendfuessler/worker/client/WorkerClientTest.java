package de.uni_leipzig.eva.tausendfuessler.worker.client;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import de.uni_leipzig.eva.tausendfuessler.common.protocol.ProtocolJson;
import de.uni_leipzig.eva.tausendfuessler.worker.crawler.HtmlExtractor;
import de.uni_leipzig.eva.tausendfuessler.worker.crawler.PageFetcher;
import de.uni_leipzig.eva.tausendfuessler.worker.pool.CrawlExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives a {@link WorkerClient} against a fake coordinator on a local {@link ServerSocket}. */
class WorkerClientTest {

    private final PageFetcher fetcher = new PageFetcher() {
        @Override
        public FetchResult fetch(String url) {
            return new FetchResult(200, "<html><head><title>T</title></head><body>hi <a href='http://x.test/a'>a</a></body></html>");
        }
    };
    private final CrawlExecutor executor = new CrawlExecutor(2, fetcher, new HtmlExtractor());
    private WorkerClient client;
    private Thread clientThread;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.stop();
        }
        if (clientThread != null) {
            clientThread.interrupt();
        }
        executor.shutdown();
    }

    @Test
    @Timeout(15)
    void registersRequestsWorkAndReportsResults() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            startClient(server.getLocalPort());
            try (Socket socket = server.accept()) {
                var conn = new Conn(socket);

                var register = (Message.Register) conn.read();
                assertThat(register.workerId()).isEqualTo("w1");
                assertThat(register.threads()).isEqualTo(2);
                conn.write(new Message.Registered("w1"));

                var request = (Message.RequestWork) conn.read();
                assertThat(request.capacity()).isEqualTo(2);
                conn.write(new Message.WorkPackage("job1", 0, List.of("http://a.test", "http://b.test"), List.of()));

                var r1 = (Message.PageResult) conn.read();
                var r2 = (Message.PageResult) conn.read();
                assertThat(List.of(r1.url(), r2.url())).containsExactlyInAnyOrder("http://a.test", "http://b.test");
                assertThat(r1.jobId()).isEqualTo("job1");
                assertThat(r1.error()).isNull();
                assertThat(r1.title()).isEqualTo("T");
                assertThat(r1.links()).containsExactly("http://x.test/a");

                // package done -> worker asks again; NO_WORK -> asks again after retryAfterMs
                assertThat(conn.read()).isInstanceOf(Message.RequestWork.class);
                conn.write(new Message.NoWork(50));
                assertThat(conn.read()).isInstanceOf(Message.RequestWork.class);
            }
        }
    }

    @Test
    @Timeout(15)
    void abortedJobResultsAreDropped() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            startClient(server.getLocalPort());
            try (Socket socket = server.accept()) {
                var conn = new Conn(socket);
                conn.read();
                conn.write(new Message.Registered("w1"));
                conn.read();
                conn.write(new Message.JobSignal("dead", Message.Signal.ABORT));
                conn.write(new Message.WorkPackage("dead", 1, List.of("http://a.test"), List.of()));

                // no PAGE_RESULT for the aborted job, only the next REQUEST_WORK
                assertThat(conn.read()).isInstanceOf(Message.RequestWork.class);
            }
        }
    }

    @Test
    @Timeout(20)
    void reconnectsAfterConnectionLoss() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            startClient(server.getLocalPort());
            try (Socket first = server.accept()) {
                assertThat(new Conn(first).read()).isInstanceOf(Message.Register.class);
            }
            try (Socket second = server.accept()) {
                assertThat(new Conn(second).read()).isInstanceOf(Message.Register.class);
            }
        }
    }

    private void startClient(int port) {
        client = new WorkerClient("localhost", port, "w1", 2, executor);
        clientThread = new Thread(client::run, "worker-client");
        clientThread.setDaemon(true);
        clientThread.start();
    }

    private static final class Conn {
        private final BufferedReader in;
        private final BufferedWriter out;

        Conn(Socket socket) throws IOException {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        Message read() throws IOException {
            String line = in.readLine();
            assertThat(line).as("connection closed by worker").isNotNull();
            return ProtocolJson.decode(line);
        }

        void write(Message message) throws IOException {
            out.write(ProtocolJson.encode(message));
            out.write('\n');
            out.flush();
        }
    }
}
