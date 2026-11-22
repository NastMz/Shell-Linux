package com.osproject.shell.server.metrics;

/**
 * Cumulative network byte counters summed over all non-loopback interfaces.
 *
 * @param rxBytes received bytes
 * @param txBytes transmitted bytes
 */
public record NetCounters(long rxBytes, long txBytes) {

    /** Counters used before the first sample. */
    public static final NetCounters ZERO = new NetCounters(0, 0);

    /**
     * Parses {@code /proc/net/dev}, skipping the {@code lo} interface.
     *
     * @param content file contents
     * @return the summed counters
     */
    public static NetCounters parse(String content) {
        long rx = 0;
        long tx = 0;
        for (String line : content.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim();
            if (name.equals("lo")) {
                continue;
            }
            String[] fields = line.substring(colon + 1).trim().split("\\s+");
            if (fields.length < 9) {
                continue;
            }
            rx += Long.parseLong(fields[0]);
            tx += Long.parseLong(fields[8]);
        }
        return new NetCounters(rx, tx);
    }

    /**
     * Computes receive and transmit rates since an earlier sample.
     *
     * @param previous      the earlier sample
     * @param elapsedMillis time between the samples
     * @return {@code [rxBytesPerSec, txBytesPerSec]}; zero for no elapsed time or counter resets
     */
    public double[] ratesSince(NetCounters previous, long elapsedMillis) {
        if (elapsedMillis <= 0) {
            return new double[] {0.0, 0.0};
        }
        double seconds = elapsedMillis / 1000.0;
        return new double[] {
            Math.max(0, rxBytes - previous.rxBytes) / seconds,
            Math.max(0, txBytes - previous.txBytes) / seconds
        };
    }
}
