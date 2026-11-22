package com.osproject.shell.server.net;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable per-connection state shared by the request handlers of one client.
 */
public final class SessionState {

    private final String remote;
    private final AtomicReference<String> user = new AtomicReference<>();
    private final AtomicReference<Path> cwd;
    private final AtomicInteger failedAuthAttempts = new AtomicInteger();
    private volatile boolean closeRequested;

    /**
     * Creates the state of a new, unauthenticated connection.
     *
     * @param remote     printable remote address, for logs
     * @param initialCwd starting working directory for {@code shell.exec}
     */
    public SessionState(String remote, Path initialCwd) {
        this.remote = remote;
        this.cwd = new AtomicReference<>(initialCwd);
    }

    /**
     * Returns the remote address.
     *
     * @return the printable remote address
     */
    public String remote() {
        return remote;
    }

    /**
     * Returns the authenticated user.
     *
     * @return the user name, or empty before a successful {@code auth}
     */
    public Optional<String> user() {
        return Optional.ofNullable(user.get());
    }

    void authenticated(String username) {
        user.set(username);
        failedAuthAttempts.set(0);
    }

    int recordFailedAuth() {
        return failedAuthAttempts.incrementAndGet();
    }

    /**
     * Returns the current working directory of the session shell.
     *
     * @return the working directory
     */
    public Path cwd() {
        return cwd.get();
    }

    void cwd(Path directory) {
        cwd.set(directory);
    }

    /**
     * Returns whether the connection must be closed after the current response.
     *
     * @return {@code true} after {@code bye} or too many failed logins
     */
    public boolean closeRequested() {
        return closeRequested;
    }

    void requestClose() {
        closeRequested = true;
    }
}
