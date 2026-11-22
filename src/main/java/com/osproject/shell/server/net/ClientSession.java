package com.osproject.shell.server.net;

import com.osproject.shell.server.protocol.ErrorCode;
import com.osproject.shell.server.protocol.MalformedRequestException;
import com.osproject.shell.server.protocol.MessageCodec;
import com.osproject.shell.server.protocol.Request;
import com.osproject.shell.server.protocol.Response;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves one client connection.
 *
 * <p>The connection thread reads one request per line. Login and connection control are
 * handled in order on that thread; once authenticated, other requests run concurrently on a
 * per-session pool so a long {@code shell.exec} never blocks {@code metrics}. Responses are
 * written through a single synchronized writer and may arrive out of order.</p>
 */
public final class ClientSession implements Runnable {

    /** Maximum size of one request line. */
    static final int MAX_LINE_BYTES = 1024 * 1024;

    private static final Logger LOG = Logger.getLogger(ClientSession.class.getName());
    private static final int MAX_CONCURRENT_REQUESTS = 16;
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();

    private final Socket socket;
    private final MessageCodec codec;
    private final RequestDispatcher dispatcher;
    private final SessionState state;
    private final Consumer<ClientSession> onClose;
    private final ThreadPoolExecutor workers;
    private final AtomicBoolean closed = new AtomicBoolean();
    private Writer writer;

    /**
     * Creates a session for an accepted socket.
     *
     * @param socket     the connected socket
     * @param codec      message codec
     * @param dispatcher request dispatcher
     * @param state      the session state
     * @param onClose    callback run once when the session ends
     */
    public ClientSession(Socket socket, MessageCodec codec, RequestDispatcher dispatcher, SessionState state,
                         Consumer<ClientSession> onClose) {
        this.socket = socket;
        this.codec = codec;
        this.dispatcher = dispatcher;
        this.state = state;
        this.onClose = onClose;
        // Runs on the reading thread when all workers are busy, which applies backpressure.
        this.workers = new ThreadPoolExecutor(0, MAX_CONCURRENT_REQUESTS, 30, TimeUnit.SECONDS,
                new SynchronousQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "session-worker-" + WORKER_IDS.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Override
    public void run() {
        LOG.info(() -> "Connection opened from " + state.remote());
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            LineReader reader = new LineReader(socket.getInputStream(), MAX_LINE_BYTES);
            String line;
            while (!closed.get() && (line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    accept(line);
                }
            }
        } catch (LineReader.LineTooLongException e) {
            send(Response.failure(Response.UNKNOWN_ID, ErrorCode.BAD_REQUEST, e.getMessage()));
        } catch (IOException e) {
            if (!closed.get()) {
                LOG.log(Level.FINE, "Connection error with " + state.remote(), e);
            }
        } finally {
            close();
        }
    }

    private void accept(String line) {
        Request request;
        try {
            request = codec.decode(line);
        } catch (MalformedRequestException e) {
            send(Response.failure(e.requestId(), e.code(), e.getMessage()));
            return;
        }
        if (dispatcher.isSequential(state, request)) {
            respond(request);
            return;
        }
        try {
            workers.execute(() -> respond(request));
        } catch (RejectedExecutionException e) {
            // The session is closing.
        }
    }

    private void respond(Request request) {
        send(dispatcher.dispatch(state, request));
        if (state.closeRequested()) {
            close();
        }
    }

    private void send(Response response) {
        String json = codec.encode(response);
        synchronized (this) {
            if (writer == null || closed.get()) {
                return;
            }
            try {
                writer.write(json);
                writer.write('\n');
                writer.flush();
            } catch (IOException e) {
                LOG.log(Level.FINE, "Cannot write to " + state.remote(), e);
                close();
            }
        }
    }

    /**
     * Closes the connection and interrupts running requests (killing their commands).
     * Safe to call more than once and from any thread.
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        workers.shutdownNow();
        try {
            socket.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Error closing socket of " + state.remote(), e);
        }
        LOG.info(() -> "Connection closed: " + state.user().map(u -> u + "@").orElse("") + state.remote());
        onClose.accept(this);
    }
}
