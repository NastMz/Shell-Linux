package com.osproject.shell.server.metrics;

/**
 * Cumulative CPU time counters (in clock ticks) of one CPU line of {@code /proc/stat}.
 *
 * @param idle  idle plus iowait ticks
 * @param total sum of all non-guest ticks (user, nice, system, idle, iowait, irq, softirq, steal)
 */
public record CpuTimes(long idle, long total) {

    /**
     * Computes the busy percentage between an earlier sample and this one.
     *
     * @param previous the earlier sample
     * @return busy time in percent, between 0 and 100; 0 when no time elapsed
     */
    public double usagePercentSince(CpuTimes previous) {
        long totalDelta = total - previous.total;
        long idleDelta = idle - previous.idle;
        if (totalDelta <= 0) {
            return 0.0;
        }
        double busy = (double) (totalDelta - idleDelta) / totalDelta * 100.0;
        return Math.max(0.0, Math.min(100.0, busy));
    }
}
