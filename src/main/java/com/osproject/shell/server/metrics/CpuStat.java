package com.osproject.shell.server.metrics;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed contents of {@code /proc/stat}.
 *
 * @param total           the aggregate {@code cpu} line
 * @param cores           one entry per {@code cpuN} line, in order
 * @param bootTimeSeconds the {@code btime} value (epoch seconds), or 0 when absent
 */
public record CpuStat(CpuTimes total, List<CpuTimes> cores, long bootTimeSeconds) {

    /**
     * Copies the core list.
     */
    public CpuStat {
        cores = List.copyOf(cores);
    }

    /**
     * Parses {@code /proc/stat}.
     *
     * @param content file contents
     * @return the parsed counters
     * @throws IllegalArgumentException when the aggregate {@code cpu} line is missing
     */
    public static CpuStat parse(String content) {
        CpuTimes total = null;
        List<CpuTimes> cores = new ArrayList<>();
        long bootTime = 0;
        for (String line : content.split("\n")) {
            String[] fields = line.trim().split("\\s+");
            if (fields[0].equals("cpu")) {
                total = times(fields);
            } else if (fields[0].startsWith("cpu")) {
                cores.add(times(fields));
            } else if (fields[0].equals("btime") && fields.length > 1) {
                bootTime = Long.parseLong(fields[1]);
            }
        }
        if (total == null) {
            throw new IllegalArgumentException("No aggregate cpu line in /proc/stat");
        }
        return new CpuStat(total, cores, bootTime);
    }

    private static CpuTimes times(String[] fields) {
        // user nice system idle iowait irq softirq steal [guest guest_nice]; guest time is
        // already included in user/nice, so only the first eight columns are summed.
        long total = 0;
        int columns = Math.min(fields.length - 1, 8);
        for (int i = 1; i <= columns; i++) {
            total += Long.parseLong(fields[i]);
        }
        long idle = Long.parseLong(fields[4]) + (fields.length > 5 ? Long.parseLong(fields[5]) : 0);
        return new CpuTimes(idle, total);
    }
}
