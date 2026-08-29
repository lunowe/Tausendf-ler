package de.uni_leipzig.eva.tausendfuessler.worker;

import com.sun.net.httpserver.HttpServer;
import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import de.uni_leipzig.eva.tausendfuessler.common.protocol.ProtocolJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerClientTest {

    private HttpServer httpServer;
    private String pageUrl;
    private WorkerClient client;
    private Thread clientThread;

    @BeforeEach
    void startHttpServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.createContext("/page", exchange -> {
            var body = """
                    <html><head><title>Local test page</title></head><body>
                    hello <a href="/one">one</a> <a href="/two">two</a>
                    </body></html>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        httpServer.start();
        pageUrl = "http://localhost:" + httpServer.getAddress().getPort() + "/page";
    }

    @AfterEach
    void stopClientAndServer() throws InterruptedException {
        if (client != null) {
            client.close();
        }
        if (clientThread != null) {
            clientThread.join(2_000);
        }
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    @Timeout(10)
    void registersRequestsWorkAndReturnsCrawledPage() throws Exception {
        try (var server = new ServerSocket(0)) {
            var coordinator = connectWorker(server);
            try (coordinator) {
                assertRegistration(coordinator);
                coordinator.send(new Message.Registered("worker-test"));
                assertThat(coordinator.readUntil(Message.RequestWork.class, Duration.ofSeconds(2))).isNotNull();

                coordinator.send(new Message.WorkPackage("job-1", 0, List.of(pageUrl), List.of()));

                var result = coordinator.readUntil(Message.PageResult.class, Duration.ofSeconds(5));
                assertThat(result).isNotNull();
                assertThat(result.workerId()).isEqualTo("worker-test");
                assertThat(result.jobId()).isEqualTo("job-1");
                assertThat(result.httpStatus()).isEqualTo(200);
                assertThat(result.title()).isEqualTo("Local test page");
                assertThat(result.textSnippet()).contains("hello");
                assertThat(result.links()).containsExactlyInAnyOrder(
                        "http://localhost:" + httpServer.getAddress().getPort() + "/one",
                        "http://localhost:" + httpServer.getAddress().getPort() + "/two");
                assertThat(result.error()).isNull();
            }
        }
    }

    @Test
    @Timeout(10)
    void pausedWorkWaitsForResumeBeforeFetching() throws Exception {
        try (var server = new ServerSocket(0)) {
            var coordinator = connectWorker(server);
            try (coordinator) {
                assertRegistration(coordinator);
                coordinator.send(new Message.Registered("worker-test"));
                assertThat(coordinator.readUntil(Message.RequestWork.class, Duration.ofSeconds(2))).isNotNull();

                coordinator.send(new Message.JobSignal("job-paused", Message.Signal.PAUSE));
                coordinator.send(new Message.WorkPackage("job-paused", 1, List.of(pageUrl), List.of()));

                assertThat(coordinator.readUntil(Message.PageResult.class, Duration.ofSeconds(1))).isNull();
                coordinator.send(new Message.JobSignal("job-paused", Message.Signal.RESUME));

                var result = coordinator.readUntil(Message.PageResult.class, Duration.ofSeconds(5));
                assertThat(result).isNotNull();
                assertThat(result.jobId()).isEqualTo("job-paused");
                assertThat(result.httpStatus()).isEqualTo(200);
            }
        }
    }

    @Test
    @Timeout(10)
    void abortedWorkIsDroppedAndCapacityBecomesAvailableAgain() throws Exception {
        try (var server = new ServerSocket(0)) {
            var coordinator = connectWorker(server);
            try (coordinator) {
                assertRegistration(coordinator);
                coordinator.send(new Message.Registered("worker-test"));
                assertThat(coordinator.readUntil(Message.RequestWork.class, Duration.ofSeconds(2))).isNotNull();

                coordinator.send(new Message.JobSignal("job-aborted", Message.Signal.ABORT));
                coordinator.send(new Message.WorkPackage("job-aborted", 0, List.of(pageUrl), List.of()));

                boolean requestedAgain = false;
                boolean pageResultReceived = false;
                long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                while (System.nanoTime() < deadline) {
                    var message = coordinator.read(Duration.ofMillis(250));
                    if (message instanceof Message.RequestWork request && request.capacity() == 2) {
                        requestedAgain = true;
                    }
                    if (message instanceof Message.PageResult) {
                        pageResultReceived = true;
                    }
                }

                assertThat(pageResultReceived).isFalse();
                assertThat(requestedAgain).isTrue();
            }
        }
    }

    private FakeCoordinator connectWorker(ServerSocket server) throws IOException {
        client = new WorkerClient("localhost", server.getLocalPort(), "worker-test", 2);
        clientThread = new Thread(client::run, "worker-client-test");
        clientThread.setDaemon(true);
        clientThread.start();
        return new FakeCoordinator(server.accept());
    }

    private static void assertRegistration(FakeCoordinator coordinator) throws IOException {
        var register = coordinator.readUntil(Message.Register.class, Duration.ofSeconds(2));
        assertThat(register).isNotNull();
        assertThat(register.workerId()).isEqualTo("worker-test");
        assertThat(register.threads()).isEqualTo(2);
    }

    private static final class FakeCoordinator implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader input;
        private final BufferedWriter output;

        private FakeCoordinator(Socket socket) throws IOException {
            this.socket = socket;
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        private void send(Message message) throws IOException {
            output.write(ProtocolJson.encode(message));
            output.write('\n');
            output.flush();
        }

        private Message read(Duration timeout) throws IOException {
            socket.setSoTimeout((int) Math.max(1, timeout.toMillis()));
            try {
                var line = input.readLine();
                return line == null ? null : ProtocolJson.decode(line);
            } catch (SocketTimeoutException e) {
                return null;
            }
        }

        private <T extends Message> T readUntil(Class<T> type, Duration timeout) throws IOException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                long remaining = deadline - System.nanoTime();
                var message = read(Duration.ofNanos(remaining));
                if (type.isInstance(message)) {
                    return type.cast(message);
                }
                if (message == null) {
                    return null;
                }
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
