package com.osproject.shell.server.metrics;

import java.util.List;

/**
 * The {@code metrics} response: one immutable sample of system resource usage.
 *
 * @param timestamp     sample time, epoch millis
 * @param uptimeSeconds seconds since boot
 * @param loadAverage   1, 5 and 15 minute load averages
 * @param processCount  number of processes
 * @param cpu           CPU usage
 * @param memory        memory usage
 * @param disks         disk filesystems
 * @param network       network throughput, loopback excluded
 */
public record MetricsSnapshot(long timestamp, long uptimeSeconds, List<Double> loadAverage, int processCount,
                              Cpu cpu, Memory memory, List<Disk> disks, Network network) {

    /**
     * Copies the lists.
     */
    public MetricsSnapshot {
        loadAverage = List.copyOf(loadAverage);
        disks = List.copyOf(disks);
    }

    /**
     * CPU usage between the last two sampler ticks.
     *
     * @param usagePercent aggregate busy percentage
     * @param perCore      busy percentage of each logical core
     */
    public record Cpu(double usagePercent, List<Double> perCore) {

        /**
         * Copies the list.
         */
        public Cpu {
            perCore = List.copyOf(perCore);
        }
    }

    /**
     * Memory usage in bytes. {@code usedBytes} is {@code totalBytes - availableBytes}.
     *
     * @param totalBytes     physical memory
     * @param usedBytes      memory in use
     * @param availableBytes memory available for new allocations
     * @param swapTotalBytes swap size
     * @param swapUsedBytes  swap in use
     */
    public record Memory(long totalBytes, long usedBytes, long availableBytes, long swapTotalBytes,
                         long swapUsedBytes) {
    }

    /**
     * A disk filesystem.
     *
     * @param mount      mount point
     * @param device     device
     * @param fsType     filesystem type
     * @param totalBytes size
     * @param usedBytes  space in use
     */
    public record Disk(String mount, String device, String fsType, long totalBytes, long usedBytes) {
    }

    /**
     * Network throughput.
     *
     * @param rxBytesPerSec receive rate
     * @param txBytesPerSec transmit rate
     * @param rxTotalBytes  bytes received since boot
     * @param txTotalBytes  bytes transmitted since boot
     */
    public record Network(double rxBytesPerSec, double txBytesPerSec, long rxTotalBytes, long txTotalBytes) {
    }
}
