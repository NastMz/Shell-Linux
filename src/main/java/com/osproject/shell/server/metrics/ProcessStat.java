package com.osproject.shell.server.metrics;

/**
 * Fields of {@code /proc/[pid]/stat} used by the process list.
 *
 * @param pid        process id
 * @param name       command name ({@code comm}), may contain spaces and parentheses
 * @param state      one-letter state, for example {@code S}
 * @param ppid       parent process id
 * @param cpuTicks   user plus system time, in clock ticks
 * @param threads    number of threads
 * @param startTicks start time after boot, in clock ticks
 */
public record ProcessStat(long pid, String name, String state, long ppid, long cpuTicks, int threads,
                          long startTicks) {

    /**
     * Parses {@code /proc/[pid]/stat}.
     *
     * @param content file contents
     * @return the parsed fields
     * @throws IllegalArgumentException when the content is not a valid stat line
     */
    public static ProcessStat parse(String content) {
        int open = content.indexOf('(');
        int close = content.lastIndexOf(')');
        if (open < 0 || close < open) {
            throw new IllegalArgumentException("Malformed /proc/[pid]/stat: " + content);
        }
        long pid = Long.parseLong(content.substring(0, open).trim());
        String name = content.substring(open + 1, close);
        // Fields after the command name, numbered from 3 (state) as in proc(5).
        String[] rest = content.substring(close + 1).trim().split("\\s+");
        if (rest.length < 20) {
            throw new IllegalArgumentException("Truncated /proc/[pid]/stat: " + content);
        }
        String state = rest[0];
        long ppid = Long.parseLong(rest[1]);
        long utime = Long.parseLong(rest[11]);
        long stime = Long.parseLong(rest[12]);
        int threads = Integer.parseInt(rest[17]);
        long startTicks = Long.parseLong(rest[19]);
        return new ProcessStat(pid, name, state, ppid, utime + stime, threads, startTicks);
    }
}
