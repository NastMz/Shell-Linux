package com.osproject.shell.server.process;

import com.osproject.shell.server.protocol.RequestException;

/**
 * Sends signals to processes.
 */
@FunctionalInterface
public interface ProcessController {

    /** Signals accepted by {@code process.kill}. */
    enum Signal {
        /** Polite termination request. */
        TERM,
        /** Forced termination. */
        KILL
    }

    /**
     * Sends a signal to a process.
     *
     * @param pid    target process id
     * @param signal signal to send
     * @throws RequestException {@code BAD_REQUEST} for protected PIDs, {@code NOT_FOUND},
     *                          {@code PERMISSION_DENIED} or {@code INTERNAL}
     */
    void kill(long pid, Signal signal);
}
