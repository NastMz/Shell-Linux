package com.osproject.shell.server.shell;

import com.osproject.shell.server.protocol.ErrorCode;
import com.osproject.shell.server.protocol.RequestException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs commands with {@code bash -c} and captures their output.
 *
 * <p>stdin is closed, stdout and stderr are drained concurrently and capped, and on timeout
 * the whole process tree is killed. To make {@code cd} persistent, the command is followed by
 * a trailer that writes the final working directory to a temporary file; the exit status of
 * the user's command is preserved.</p>
 */
public final class CommandExecutor implements CommandRunner {

    /** Default cap for stdout and stderr, each. */
    public static final int OUTPUT_LIMIT_BYTES = 1024 * 1024;

    static final String CWD_FILE_VARIABLE = "__SHELL_CWD_FILE";

    private static final String TRAILER = "\n__shell_rc=$?; pwd > \"$" + CWD_FILE_VARIABLE + "\"; exit $__shell_rc\n";
    private static final long DRAIN_GRACE_MILLIS = 2000;

    private final int outputLimit;
    private final Path fallbackDirectory;
    private final ExecutorService readers = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "shell-output-reader");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Creates an executor with the default 1 MiB output cap.
     *
     * @param fallbackDirectory directory used when the requested working directory no longer exists
     */
    public CommandExecutor(Path fallbackDirectory) {
        this(OUTPUT_LIMIT_BYTES, fallbackDirectory);
    }

    /**
     * Creates an executor with a custom output cap.
     *
     * @param outputLimit       maximum captured bytes for stdout and for stderr
     * @param fallbackDirectory directory used when the requested working directory no longer exists
     */
    public CommandExecutor(int outputLimit, Path fallbackDirectory) {
        this.outputLimit = outputLimit;
        this.fallbackDirectory = fallbackDirectory;
    }

    @Override
    public ExecutionResult run(String command, Path cwd, Duration timeout) throws InterruptedException {
        Path startDirectory = Files.isDirectory(cwd) ? cwd : fallbackDirectory;
        Path cwdFile = createCwdFile();
        try {
            return execute(command, startDirectory, timeout, cwdFile);
        } finally {
            deleteQuietly(cwdFile);
        }
    }

    private ExecutionResult execute(String command, Path cwd, Duration timeout, Path cwdFile)
            throws InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("bash", "-c", command + TRAILER).directory(cwd.toFile());
        builder.environment().put(CWD_FILE_VARIABLE, cwdFile.toString());
        long started = System.nanoTime();
        Process process;
        try {
            process = builder.start();
            process.getOutputStream().close();
        } catch (IOException e) {
            throw new RequestException(ErrorCode.INTERNAL, "Cannot start bash: " + e.getMessage());
        }
        BoundedCapture stdout = new BoundedCapture(process.getInputStream(), outputLimit);
        BoundedCapture stderr = new BoundedCapture(process.getErrorStream(), outputLimit);
        List<Future<?>> drains = List.of(readers.submit(stdout), readers.submit(stderr));

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            killTree(process);
            throw e;
        }
        if (!finished) {
            killTree(process);
        }
        awaitDrains(drains, process);
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        Path finalCwd = finished ? readCwd(cwdFile).orElse(cwd) : cwd;
        return new ExecutionResult(stdout.text(), stderr.text(), finished ? process.exitValue() : -1,
                finalCwd.toString(), duration, !finished, stdout.truncated() || stderr.truncated());
    }

    /**
     * Waits briefly for the output readers. A background job started by the command can keep the
     * pipes open after bash exits; in that case the output captured so far is returned.
     */
    private static void awaitDrains(List<Future<?>> drains, Process process) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DRAIN_GRACE_MILLIS);
        for (Future<?> drain : drains) {
            try {
                drain.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                closeQuietly(process);
                drain.cancel(true);
            } catch (ExecutionException e) {
                // BoundedCapture handles its own I/O errors; nothing else can fail here.
            }
        }
    }

    private static void killTree(Process process) {
        List<ProcessHandle> descendants = process.descendants().toList();
        process.destroyForcibly();
        descendants.forEach(ProcessHandle::destroyForcibly);
    }

    private static Optional<Path> readCwd(Path cwdFile) {
        try {
            String text = Files.readString(cwdFile, StandardCharsets.UTF_8).strip();
            if (text.isEmpty()) {
                return Optional.empty();
            }
            Path path = Path.of(text);
            return Files.isDirectory(path) ? Optional.of(path) : Optional.empty();
        } catch (IOException | InvalidPathException e) {
            return Optional.empty();
        }
    }

    private static Path createCwdFile() {
        try {
            return Files.createTempFile("shell-cwd-", ".txt");
        } catch (IOException e) {
            throw new RequestException(ErrorCode.INTERNAL, "Cannot create temporary file: " + e.getMessage());
        }
    }

    private static void closeQuietly(Process process) {
        try {
            process.getInputStream().close();
            process.getErrorStream().close();
        } catch (IOException ignored) {
            // Best effort: the reader thread ends when the background job closes the pipe.
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // The temporary directory is cleaned by the operating system.
        }
    }
}
