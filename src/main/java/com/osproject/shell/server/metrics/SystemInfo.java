package com.osproject.shell.server.metrics;

/**
 * The {@code system.info} response: static facts about the host.
 *
 * @param hostname         host name
 * @param os               distribution name, for example {@code Ubuntu 24.04.1 LTS}
 * @param kernel           kernel release
 * @param architecture     JVM architecture name, for example {@code amd64}
 * @param cpuModel         CPU model name
 * @param cpuCores         logical processor count
 * @param memoryTotalBytes physical memory
 * @param bootTime         boot time, epoch millis
 */
public record SystemInfo(String hostname, String os, String kernel, String architecture, String cpuModel,
                         int cpuCores, long memoryTotalBytes, long bootTime) {
}
