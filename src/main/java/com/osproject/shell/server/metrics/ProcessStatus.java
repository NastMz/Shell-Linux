package com.osproject.shell.server.metrics;

/**
 * Fields of {@code /proc/[pid]/status} used by the process list.
 *
 * @param uid      real user id, or -1 when absent
 * @param rssBytes resident set size ({@code VmRSS}) in bytes; 0 for kernel threads
 */
public record ProcessStatus(long uid, long rssBytes) {

    /**
     * Parses {@code /proc/[pid]/status}.
     *
     * @param content file contents
     * @return the parsed fields
     */
    public static ProcessStatus parse(String content) {
        long uid = -1;
        long rss = 0;
        for (String line : content.split("\n")) {
            if (line.startsWith("Uid:")) {
                uid = Long.parseLong(line.substring(4).trim().split("\\s+")[0]);
            } else if (line.startsWith("VmRSS:")) {
                String[] fields = line.substring(6).trim().split("\\s+");
                rss = Long.parseLong(fields[0]) * (fields.length > 1 && fields[1].equalsIgnoreCase("kB") ? 1024 : 1);
            }
        }
        return new ProcessStatus(uid, rss);
    }

    /**
     * Converts the NUL-separated contents of {@code /proc/[pid]/cmdline} into a printable command line.
     *
     * @param content file contents
     * @param name    the process name, used as {@code [name]} for kernel threads (empty cmdline)
     * @return the command line with arguments separated by spaces
     */
    public static String commandLine(String content, String name) {
        String command = content.replace('\0', ' ').strip();
        return command.isEmpty() ? "[" + name + "]" : command;
    }
}
