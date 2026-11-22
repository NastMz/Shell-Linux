package com.osproject.shell.server.process;

import com.osproject.shell.server.protocol.ErrorCode;
import com.osproject.shell.server.protocol.RequestException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Sends signals with the {@code kill} command, falling back to {@link ProcessHandle} when the
 * command is unavailable. Refuses PID 1 and below and the server's own PID.
 */
public final class LinuxProcessController implements ProcessController {

    private static final long KILL_TIMEOUT_SECONDS = 5;

    private final long ownPid;

    /**
     * Creates a controller that protects the current JVM.
     */
    public LinuxProcessController() {
        this(ProcessHandle.current().pid());
    }

    /**
     * Creates a controller that protects the given PID.
     *
     * @param ownPid the server's own process id
     */
    public LinuxProcessController(long ownPid) {
        this.ownPid = ownPid;
    }

    @Override
    public void kill(long pid, Signal signal) {
        if (pid <= 1) {
            throw new RequestException(ErrorCode.BAD_REQUEST, "Refusing to signal PID " + pid);
        }
        if (pid == ownPid) {
            throw new RequestException(ErrorCode.BAD_REQUEST, "Refusing to signal the server itself (PID " + pid + ")");
        }
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty()) {
            throw notFound(pid);
        }
        try {
            runKill(pid, signal);
        } catch (IOException e) {
            killWithHandle(handle.get(), signal);
        }
    }

    private static void runKill(long pid, Signal signal) throws IOException {
        Process process = new ProcessBuilder("kill", "-" + signal.name(), Long.toString(pid))
                .redirectErrorStream(true)
                .start();
        process.getOutputStream().close();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        int exitCode;
        try {
            if (!process.waitFor(KILL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new RequestException(ErrorCode.INTERNAL, "kill did not finish in time");
            }
            exitCode = process.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RequestException(ErrorCode.INTERNAL, "Interrupted while signalling PID " + pid);
        }
        if (exitCode == 0) {
            return;
        }
        String lower = output.toLowerCase(Locale.ROOT);
        if (lower.contains("no such process")) {
            throw notFound(pid);
        }
        if (lower.contains("not permitted") || lower.contains("permission denied")) {
            throw new RequestException(ErrorCode.PERMISSION_DENIED, "Not allowed to signal PID " + pid);
        }
        throw new RequestException(ErrorCode.INTERNAL, "kill failed for PID " + pid + ": " + output);
    }

    private static void killWithHandle(ProcessHandle handle, Signal signal) {
        if (!handle.isAlive()) {
            throw notFound(handle.pid());
        }
        boolean requested = signal == Signal.KILL ? handle.destroyForcibly() : handle.destroy();
        if (!requested) {
            if (!handle.isAlive()) {
                throw notFound(handle.pid());
            }
            throw new RequestException(ErrorCode.PERMISSION_DENIED, "Not allowed to signal PID " + handle.pid());
        }
    }

    private static RequestException notFound(long pid) {
        return new RequestException(ErrorCode.NOT_FOUND, "No process with PID " + pid);
    }
}
