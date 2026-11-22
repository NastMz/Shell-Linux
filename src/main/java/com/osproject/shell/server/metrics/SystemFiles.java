package com.osproject.shell.server.metrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parsers for small system files: {@code /proc/loadavg}, {@code /proc/uptime},
 * {@code /etc/os-release}, {@code /proc/cpuinfo} and {@code /etc/passwd}.
 */
public final class SystemFiles {

    private SystemFiles() {
    }

    /**
     * Parses {@code /proc/loadavg}.
     *
     * @param content file contents
     * @return the 1, 5 and 15 minute load averages
     */
    public static List<Double> loadAverage(String content) {
        String[] fields = content.trim().split("\\s+");
        List<Double> load = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            load.add(Double.parseDouble(fields[i]));
        }
        return List.copyOf(load);
    }

    /**
     * Parses {@code /proc/uptime}.
     *
     * @param content file contents
     * @return whole seconds since boot
     */
    public static long uptimeSeconds(String content) {
        return (long) Double.parseDouble(content.trim().split("\\s+")[0]);
    }

    /**
     * Extracts the distribution name from {@code /etc/os-release}.
     *
     * @param content file contents
     * @return {@code PRETTY_NAME}, or {@code NAME VERSION}, when present
     */
    public static Optional<String> osPrettyName(String content) {
        Map<String, String> values = new HashMap<>();
        for (String line : content.split("\n")) {
            int equals = line.indexOf('=');
            if (equals > 0 && !line.startsWith("#")) {
                values.put(line.substring(0, equals).trim(), unquote(line.substring(equals + 1).trim()));
            }
        }
        String pretty = values.get("PRETTY_NAME");
        if (pretty != null && !pretty.isBlank()) {
            return Optional.of(pretty);
        }
        String name = values.get("NAME");
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String version = values.get("VERSION");
        return Optional.of(version == null || version.isBlank() ? name : name + " " + version);
    }

    /**
     * Extracts the CPU model from {@code /proc/cpuinfo}.
     *
     * @param content file contents
     * @return the first {@code model name} (x86) or {@code Model}/{@code Hardware} (ARM) value
     */
    public static Optional<String> cpuModel(String content) {
        Map<String, String> first = new HashMap<>();
        for (String line : content.split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                first.putIfAbsent(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        for (String key : List.of("model name", "Model", "Hardware", "cpu model", "cpu")) {
            String value = first.get(key);
            if (value != null && !value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /**
     * Counts logical processors in {@code /proc/cpuinfo}.
     *
     * @param content file contents
     * @return the number of {@code processor} entries
     */
    public static int cpuCount(String content) {
        int count = 0;
        for (String line : content.split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equals("processor")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Parses {@code /etc/passwd} into a uid to user name map.
     *
     * @param content file contents
     * @return user names keyed by uid (first entry wins)
     */
    public static Map<Long, String> userNames(String content) {
        Map<Long, String> users = new HashMap<>();
        for (String line : content.split("\n")) {
            String[] fields = line.split(":", -1);
            if (fields.length >= 3 && !line.startsWith("#")) {
                try {
                    users.putIfAbsent(Long.parseLong(fields[2]), fields[0]);
                } catch (NumberFormatException ignored) {
                    // Skip malformed lines such as NIS "+" entries.
                }
            }
        }
        return Map.copyOf(users);
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
