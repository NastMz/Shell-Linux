package com.osproject.shell.server.net;

import com.osproject.shell.server.protocol.MessageCodec;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TCP server that accepts client connections and serves each one on its own thread.
 */
public final class ShellServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ShellServer.class.getName());

    private final ServerSocket serverSocket;
    private final RequestDispatcher dispatcher;
    private final ServerServices services;
    private final MessageCodec codec = new MessageCodec();
    private final Set<ClientSession> sessions = ConcurrentHashMap.newKeySet();
    private volatile boolean running = true;
    private Thread acceptThread;

    /**
     * Binds the server socket.
     *
     * @param bindAddress address to listen on
     * @param port        TCP port; 0 picks a free port
     * @param services    the request handlers' services
     * @throws IOException when the port cannot be bound
     */
    public ShellServer(InetAddress bindAddress, int port, ServerServices services) throws IOException {
        this.services = services;
        this.dispatcher = new RequestDispatcher(services);
        this.serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(bindAddress, port));
    }

    /**
     * Returns the port the server listens on.
     *
     * @return the local port
     */
    public int port() {
        return serverSocket.getLocalPort();
    }

    /**
     * Starts accepting connections on a background thread.
     */
    public synchronized void start() {
        if (acceptThread != null) {
            throw new IllegalStateException("Server already started");
        }
        acceptThread = new Thread(this::acceptLoop, "shell-server-accept");
        acceptThread.start();
        LOG.info(() -> "Listening on " + serverSocket.getInetAddress().getHostAddress() + ":" + port());
    }

    private void acceptLoop() {
        while (running) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (running) {
                    LOG.log(Level.SEVERE, "Accept failed", e);
                    continue;
                }
                return;
            }
            String remote = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
            ClientSession session = new ClientSession(socket, codec, dispatcher,
                    new SessionState(remote, services.homeDirectory()), sessions::remove);
            sessions.add(session);
            Thread thread = new Thread(session, "client-" + remote);
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * Stops accepting connections and closes every open session.
     */
    @Override
    public void close() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Error closing server socket", e);
        }
        sessions.forEach(ClientSession::close);
        Thread thread = acceptThread;
        if (thread != null) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
