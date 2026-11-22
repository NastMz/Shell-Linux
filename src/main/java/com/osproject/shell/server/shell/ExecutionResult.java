package com.osproject.shell.server.shell;

/**
 * The {@code shell.exec} response.
 *
 * @param stdout         captured standard output (UTF-8, at most 1 MiB)
 * @param stderr         captured standard error (UTF-8, at most 1 MiB)
 * @param exitCode       exit status, or -1 when the command timed out
 * @param cwd            the session working directory after the command
 * @param durationMillis wall time of the command
 * @param timedOut       whether the command was killed because of the timeout
 * @param truncated      whether stdout or stderr was cut at the size cap
 */
public record ExecutionResult(String stdout, String stderr, int exitCode, String cwd, long durationMillis,
                              boolean timedOut, boolean truncated) {
}
