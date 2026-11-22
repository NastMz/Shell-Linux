package com.osproject.shell.server.net;

import com.osproject.shell.server.metrics.MetricsSnapshot;
import com.osproject.shell.server.metrics.MetricsSource;
import com.osproject.shell.server.metrics.ProcessInfo;
import com.osproject.shell.server.metrics.SystemInfo;
import com.osproject.shell.server.process.ProcessController;
import com.osproject.shell.server.protocol.ErrorCode;
import com.osproject.shell.server.protocol.RequestException;
import com.osproject.shell.server.shell.ExecutionResult;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory services for dispatcher and server tests.
 */
final class FakeServices {

    static final SystemInfo SYSTEM = new SystemInfo("ubuntu-srv", "Ubuntu 24.04.1 LTS", "6.8.0-45-generic", "amd64",
            "Test CPU", 2, 16_000_000_000L, 1_732_290_000_000L);
    static final MetricsSnapshot METRICS = new MetricsSnapshot(1_732_300_000_000L, 10_000, List.of(0.5, 0.4, 0.3), 1,
            new MetricsSnapshot.Cpu(12.5, List.of(10.0, 15.0)),
            new MetricsSnapshot.Memory(16, 6, 10, 2, 1), List.of(),
            new MetricsSnapshot.Network(1.0, 2.0, 3, 4));
    static final ProcessInfo INIT = new ProcessInfo(1, 0, "systemd", "root", "S", 0.0, 13, 0.08, 1,
            1_732_290_000_000L, "/sbin/init");
    static final Path HOME = Path.of("/home/admin");

    final List<String> kills = new CopyOnWriteArrayList<>();
    final List<String> commands = new CopyOnWriteArrayList<>();

    ServerServices services() {
        MetricsSource metrics = new MetricsSource() {
            @Override
            public MetricsSnapshot metrics() {
                return METRICS;
            }

            @Override
            public List<ProcessInfo> processes() {
                return List.of(INIT);
            }
        };
        ProcessController processes = (pid, signal) -> {
            if (pid == 404) {
                throw new RequestException(ErrorCode.NOT_FOUND, "No process with PID 404");
            }
            if (pid == 500) {
                throw new IllegalStateException("boom");
            }
            kills.add(pid + ":" + signal);
        };
        Map<String, String> directories = Map.of("cd /tmp", "/tmp");
        return new ServerServices(
                (user, password) -> user.equals("admin") && new String(password).equals("secret"),
                () -> SYSTEM, metrics, processes,
                (command, cwd, timeout) -> {
                    commands.add(command + "|" + cwd + "|" + timeout.toSeconds());
                    if (command.equals("sleep")) {
                        Thread.sleep(1500);
                    }
                    return new ExecutionResult("out:" + command, "", 0,
                            directories.getOrDefault(command, cwd.toString()), 1, false, false);
                },
                HOME,
                Clock.fixed(Instant.ofEpochMilli(42), ZoneOffset.UTC));
    }
}
