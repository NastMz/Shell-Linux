package com.osproject.shell.server.metrics;

/**
 * One entry of the {@code processes.list} response.
 *
 * @param pid           process id
 * @param ppid          parent process id
 * @param name          command name
 * @param user          owner user name (numeric uid when unknown)
 * @param state         one-letter state
 * @param cpuPercent    CPU usage relative to one core, between the last two sampler ticks
 * @param memoryBytes   resident set size
 * @param memoryPercent resident set size relative to physical memory
 * @param threads       number of threads
 * @param startTime     start time, epoch millis
 * @param command       full command line, or {@code [name]} for kernel threads
 */
public record ProcessInfo(long pid, long ppid, String name, String user, String state, double cpuPercent,
                          long memoryBytes, double memoryPercent, int threads, long startTime, String command) {
}
