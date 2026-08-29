package de.uni_leipzig.eva.tausendfuessler.worker.net;

import de.uni_leipzig.eva.tausendfuessler.common.protocol.Message;
import de.uni_leipzig.eva.tausendfuessler.common.protocol.ProtocolJson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** A UTF-8, line-delimited protocol connection to the coordinator. */
public final class CoordinatorConnection implements AutoCloseable {

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    public CoordinatorConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public synchronized void send(Message message) throws IOException {
        writer.write(ProtocolJson.encode(message));
        writer.write('\n');
        writer.flush();
    }

    /** Reads and dispatches messages on the calling thread until EOF or an I/O failure. */
    public void readLoop(Consumer<Message> handler) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            handler.accept(ProtocolJson.decode(line));
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
