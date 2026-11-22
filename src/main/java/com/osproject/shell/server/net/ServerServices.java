package com.osproject.shell.server.net;

import com.osproject.shell.server.auth.Authenticator;
import com.osproject.shell.server.metrics.MetricsSource;
import com.osproject.shell.server.metrics.SystemInfo;
import com.osproject.shell.server.process.ProcessController;
import com.osproject.shell.server.shell.CommandRunner;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The services the request handlers depend on.
 *
 * @param authenticator credential check
 * @param systemInfo    static host facts
 * @param metrics       latest resource sample
 * @param processes     process signalling
 * @param commands      shell command execution
 * @param homeDirectory initial working directory of new sessions
 * @param clock         clock for {@code ping}
 */
public record ServerServices(Authenticator authenticator, Supplier<SystemInfo> systemInfo, MetricsSource metrics,
                             ProcessController processes, CommandRunner commands, Path homeDirectory,
                             Clock clock) {

    /**
     * Validates that every service is present.
     */
    public ServerServices {
        Objects.requireNonNull(authenticator, "authenticator");
        Objects.requireNonNull(systemInfo, "systemInfo");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(processes, "processes");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(homeDirectory, "homeDirectory");
        Objects.requireNonNull(clock, "clock");
    }
}
