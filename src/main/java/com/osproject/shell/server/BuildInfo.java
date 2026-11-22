package com.osproject.shell.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Build metadata of the server, filtered from the Maven project at build time.
 */
public final class BuildInfo {

    private static final String VERSION = loadVersion();

    private BuildInfo() {
    }

    /**
     * Returns the server version, for example {@code 1.0.0}.
     *
     * @return the project version the jar was built from
     */
    public static String version() {
        return VERSION;
    }

    private static String loadVersion() {
        try (InputStream in = BuildInfo.class.getResourceAsStream("/shell-server.properties")) {
            if (in == null) {
                return "unknown";
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", "unknown");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read build metadata", e);
        }
    }
}
