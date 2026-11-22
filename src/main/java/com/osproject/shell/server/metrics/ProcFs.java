package com.osproject.shell.server.metrics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read access to {@code /proc} and {@code /etc} below a configurable root, so that
 * fixture directories can stand in for a real Linux system in tests.
 */
public final class ProcFs {

    private final Path root;

    /**
     * Creates an accessor rooted at {@code root} ({@code /} on a real system).
     *
     * @param root filesystem root containing {@code proc/} and {@code etc/}
     */
    public ProcFs(Path root) {
        this.root = root;
    }

    /**
     * Returns an accessor for the real system root.
     *
     * @return the accessor
     */
    public static ProcFs system() {
        return new ProcFs(Path.of("/"));
    }

    /**
     * Reads a file as UTF-8 text.
     *
     * @param relativePath path below the root, for example {@code proc/stat}
     * @return the contents, or empty when the file does not exist or cannot be read
     */
    public Optional<String> read(String relativePath) {
        try {
            return Optional.of(new String(Files.readAllBytes(root.resolve(relativePath)), StandardCharsets.UTF_8));
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
    }

    /**
     * Lists the numeric entries of {@code proc/}.
     *
     * @return process ids in ascending order
     */
    public List<Long> pids() {
        List<Long> pids = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root.resolve("proc"))) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (!name.isEmpty() && name.chars().allMatch(Character::isDigit)) {
                    pids.add(Long.parseLong(name));
                }
            }
        } catch (IOException e) {
            return List.of();
        }
        pids.sort(null);
        return pids;
    }
}
