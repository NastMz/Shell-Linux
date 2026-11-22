package com.osproject.shell.server.metrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A mounted filesystem from {@code /proc/mounts}.
 *
 * @param device the mounted device, for example {@code /dev/sda2}
 * @param mount  the mount point
 * @param fsType the filesystem type
 */
public record MountEntry(String device, String mount, String fsType) {

    private static final Set<String> DEVICELESS_DISK_TYPES = Set.of("overlay", "zfs", "btrfs");
    private static final Set<String> READ_ONLY_IMAGE_TYPES = Set.of("squashfs", "iso9660", "udf");

    /**
     * Parses {@code /proc/mounts} and keeps only disk filesystems: block devices plus overlay,
     * excluding pseudo filesystems ({@code tmpfs}, {@code proc}, {@code sysfs}, ...) and read-only
     * images such as snap {@code squashfs} loops. A device mounted several times (bind mounts) is
     * reported once, at its shortest mount point.
     *
     * @param content file contents
     * @return the disk mounts in file order
     */
    public static List<MountEntry> parseDisks(String content) {
        Map<String, MountEntry> byDevice = new LinkedHashMap<>();
        for (String line : content.split("\n")) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 3) {
                continue;
            }
            MountEntry entry = new MountEntry(unescape(fields[0]), unescape(fields[1]), fields[2]);
            if (!entry.isDisk()) {
                continue;
            }
            byDevice.merge(entry.device, entry,
                    (kept, candidate) -> candidate.mount.length() < kept.mount.length() ? candidate : kept);
        }
        return new ArrayList<>(byDevice.values());
    }

    private boolean isDisk() {
        if (READ_ONLY_IMAGE_TYPES.contains(fsType)) {
            return false;
        }
        return device.startsWith("/dev/") || DEVICELESS_DISK_TYPES.contains(fsType);
    }

    /** Decodes the octal escapes ({@code \040} for space, ...) used by the kernel. */
    private static String unescape(String value) {
        if (value.indexOf('\\') < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && isOctal(value, i + 1)) {
                out.append((char) Integer.parseInt(value.substring(i + 1, i + 4), 8));
                i += 3;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isOctal(String value, int start) {
        if (start + 3 > value.length()) {
            return false;
        }
        for (int i = start; i < start + 3; i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '7') {
                return false;
            }
        }
        return true;
    }
}
