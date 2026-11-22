package com.osproject.shell.server.metrics;

import java.util.HashMap;
import java.util.Map;

/**
 * Parsed contents of {@code /proc/meminfo}, in bytes.
 *
 * @param totalBytes     {@code MemTotal}
 * @param availableBytes {@code MemAvailable} (estimated from free + buffers + cached on old kernels)
 * @param swapTotalBytes {@code SwapTotal}
 * @param swapFreeBytes  {@code SwapFree}
 */
public record MemInfo(long totalBytes, long availableBytes, long swapTotalBytes, long swapFreeBytes) {

    /**
     * Parses {@code /proc/meminfo}.
     *
     * @param content file contents
     * @return the parsed values
     */
    public static MemInfo parse(String content) {
        Map<String, Long> values = new HashMap<>();
        for (String line : content.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String[] fields = line.substring(colon + 1).trim().split("\\s+");
            long value = Long.parseLong(fields[0]);
            boolean kibibytes = fields.length > 1 && fields[1].equalsIgnoreCase("kB");
            values.put(line.substring(0, colon).trim(), kibibytes ? value * 1024 : value);
        }
        long total = values.getOrDefault("MemTotal", 0L);
        Long available = values.get("MemAvailable");
        if (available == null) {
            available = values.getOrDefault("MemFree", 0L) + values.getOrDefault("Buffers", 0L)
                    + values.getOrDefault("Cached", 0L);
        }
        return new MemInfo(total, Math.min(available, total),
                values.getOrDefault("SwapTotal", 0L), values.getOrDefault("SwapFree", 0L));
    }
}
