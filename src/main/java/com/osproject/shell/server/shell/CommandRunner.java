package com.osproject.shell.server.shell;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Runs shell commands for a session.
 */
@FunctionalInterface
public interface CommandRunner {

    /**
     * Runs {@code command} and waits for it to finish or time out.
     *
     * @param command the shell command line
     * @param cwd     working directory to start in
     * @param timeout maximum run time
     * @return the result, including the working directory after the command
     * @throws InterruptedException when the calling thread is interrupted (the command is killed)
     */
    ExecutionResult run(String command, Path cwd, Duration timeout) throws InterruptedException;
}
