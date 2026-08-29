package de.uni_leipzig.eva.tausendfuessler.worker.net;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinatorConnectionTest {

    @Test
    @Timeout(5)
    void exchangesOneMessageInEachDirection() throws Exception {
        try (var server = new ServerSocket(0);
             var clientSocket = new Socket("localhost", server.getLocalPort());
             var serverSocket = server.accept();
             var client = new CoordinatorConnection(clientSocket);
             var coordinator = new CoordinatorConnection(serverSocket)) {

            var received = new ArrayBlockingQueue<Message>(1);
            var reader = new Thread(() -> {
                try {
                    coordinator.readLoop(received::add);
                } catch (Exception ignored) {
                    // The test closes the connection after receiving one message.
                }
            });
            reader.setDaemon(true);
            reader.start();

            client.send(new Message.Register("worker-1", 3, null));
            assertThat(received.poll(1, TimeUnit.SECONDS))
                    .isEqualTo(new Message.Register("worker-1", 3, null));

            var reply = new ArrayBlockingQueue<Message>(1);
            var replyReader = new Thread(() -> {
                try {
                    client.readLoop(reply::add);
                } catch (Exception ignored) {
                    // The test closes the connection after receiving one message.
                }
            });
            replyReader.setDaemon(true);
            replyReader.start();

            coordinator.send(new Message.Registered("worker-1"));
            assertThat(reply.poll(1, TimeUnit.SECONDS))
                    .isEqualTo(new Message.Registered("worker-1"));
        }
    }
}
