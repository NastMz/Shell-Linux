package com.osproject.shell.server.shell;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Drains a stream completely while keeping at most {@code limit} bytes of it.
 *
 * <p>Draining past the limit keeps the child process from blocking on a full pipe.
 * The captured text can be read while draining is still in progress.</p>
 */
final class BoundedCapture implements Runnable {

    private final InputStream in;
    private final int limit;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean truncated;

    BoundedCapture(InputStream in, int limit) {
        this.in = in;
        this.limit = limit;
    }

    @Override
    public void run() {
        byte[] chunk = new byte[8192];
        try (in) {
            int read;
            while ((read = in.read(chunk)) != -1) {
                append(chunk, read);
            }
        } catch (IOException e) {
            // Stream closed because the process was killed; keep what was captured.
        }
    }

    private synchronized void append(byte[] chunk, int length) {
        int room = limit - buffer.size();
        if (length > room) {
            truncated = true;
        }
        if (room > 0) {
            buffer.write(chunk, 0, Math.min(room, length));
        }
    }

    synchronized String text() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    synchronized boolean truncated() {
        return truncated;
    }
}
