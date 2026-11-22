package com.osproject.shell.server.metrics;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Looks up the size and usage of a mounted filesystem.
 */
@FunctionalInterface
public interface DiskUsageProbe {

    /**
     * Size and usage of a filesystem.
     *
     * @param totalBytes size
     * @param usedBytes  space in use
     */
    record Usage(long totalBytes, long usedBytes) {
    }

    /**
     * Returns the usage of the filesystem mounted at {@code mount}.
     *
     * @param mount the mount point
     * @return the usage, or empty when it cannot be determined
     */
    Optional<Usage> probe(String mount);

    /**
     * Returns a probe backed by {@link FileStore} (statvfs on Linux). Used space is
     * {@code total - free}, as reported by {@code df}.
     *
     * @return the probe
     */
    static DiskUsageProbe fileStore() {
        return mount -> {
            try {
                FileStore store = Files.getFileStore(Path.of(mount));
                long total = store.getTotalSpace();
                return Optional.of(new Usage(total, Math.max(0, total - store.getUnallocatedSpace())));
            } catch (IOException | RuntimeException e) {
                return Optional.empty();
            }
        };
    }
}
