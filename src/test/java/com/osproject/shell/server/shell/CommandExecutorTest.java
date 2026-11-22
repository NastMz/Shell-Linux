package com.osproject.shell.server.shell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
@Timeout(30)
class CommandExecutorTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    Path dir;

    private final CommandExecutor executor = new CommandExecutor(Path.of("/"));

    @Test
    void runsPipelines() throws InterruptedException {
        ExecutionResult result = executor.run("echo hello world | tr a-z A-Z", dir, TIMEOUT);

        assertEquals("HELLO WORLD\n", result.stdout());
        assertEquals("", result.stderr());
        assertEquals(0, result.exitCode());
        assertFalse(result.timedOut());
        assertFalse(result.truncated());
        assertEquals(dir.toString(), result.cwd());
    }

    @Test
    void capturesStderrAndExitCode() throws InterruptedException {
        ExecutionResult result = executor.run("echo out; echo oops >&2; exit 3", dir, TIMEOUT);

        assertEquals("out\n", result.stdout());
        assertEquals("oops\n", result.stderr());
        assertEquals(3, result.exitCode());
    }

    @Test
    void reportsFailingCommandExitCode() throws InterruptedException {
        ExecutionResult result = executor.run("ls /definitely/not/here", dir, TIMEOUT);

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("No such file"), result.stderr());
    }

    @Test
    void cdPersistsTheWorkingDirectory() throws InterruptedException {
        ExecutionResult cd = executor.run("cd /tmp", dir, TIMEOUT);
        ExecutionResult pwd = executor.run("pwd", Path.of(cd.cwd()), TIMEOUT);

        assertEquals("/tmp", cd.cwd());
        assertEquals(0, cd.exitCode());
        assertEquals("/tmp\n", pwd.stdout());
    }

    @Test
    void chainedCdAndRelativePathsWork() throws Exception {
        Files.createDirectories(dir.resolve("a/b"));

        ExecutionResult result = executor.run("cd a && cd b && touch marker", dir, TIMEOUT);

        assertEquals(dir.resolve("a/b").toString(), result.cwd());
        assertTrue(Files.exists(dir.resolve("a/b/marker")));
    }

    @Test
    void failedCdKeepsTheDirectory() throws InterruptedException {
        ExecutionResult result = executor.run("cd /no/such/dir", dir, TIMEOUT);

        assertEquals(dir.toString(), result.cwd());
        assertEquals(1, result.exitCode());
    }

    @Test
    void stdinIsClosed() throws InterruptedException {
        ExecutionResult result = executor.run("cat; echo done", dir, TIMEOUT);

        assertEquals("done\n", result.stdout());
    }

    @Test
    void timeoutKillsTheProcessTree() throws Exception {
        Path marker = dir.resolve("survived");

        ExecutionResult result = executor.run("(sleep 3; touch " + marker + ") & sleep 30", dir, Duration.ofSeconds(1));

        assertTrue(result.timedOut());
        assertEquals(-1, result.exitCode());
        assertTrue(result.durationMillis() < 5000, "took " + result.durationMillis());
        Thread.sleep(3500);
        assertFalse(Files.exists(marker), "child process survived the timeout");
    }

    @Test
    void truncatesLargeOutput() throws InterruptedException {
        CommandExecutor small = new CommandExecutor(1000, Path.of("/"));

        ExecutionResult result = small.run("head -c 5000 /dev/zero | tr '\\0' x; echo err >&2", dir, TIMEOUT);

        assertEquals(1000, result.stdout().length());
        assertEquals("err\n", result.stderr());
        assertTrue(result.truncated());
        assertEquals(0, result.exitCode());
    }

    @Test
    void defaultCapIsOneMebibyte() throws InterruptedException {
        ExecutionResult result = executor.run("head -c 2000000 /dev/zero | tr '\\0' x", dir, TIMEOUT);

        assertEquals(CommandExecutor.OUTPUT_LIMIT_BYTES, result.stdout().length());
        assertTrue(result.truncated());
    }

    @Test
    void missingDirectoryFallsBack() throws InterruptedException {
        ExecutionResult result = executor.run("pwd", dir.resolve("gone"), TIMEOUT);

        assertEquals("/\n", result.stdout());
        assertEquals("/", result.cwd());
    }
}
