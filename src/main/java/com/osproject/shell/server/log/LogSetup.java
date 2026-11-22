package com.osproject.shell.server.log;

import java.io.PrintStream;
import java.time.ZoneId;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

/**
 * Routes {@code java.util.logging} to the console with {@link ConsoleFormatter}.
 */
public final class LogSetup {

    private LogSetup() {
    }

    /**
     * Returns whether colors should be used: a console is attached and {@code NO_COLOR} is unset.
     *
     * @return {@code true} when ANSI colors are appropriate
     */
    public static boolean colorsEnabled() {
        String noColor = System.getenv("NO_COLOR");
        return System.console() != null && (noColor == null || noColor.isEmpty());
    }

    /**
     * Replaces the root handlers with one console handler.
     *
     * @param out    destination stream
     * @param colors whether to emit ANSI colors
     */
    public static void configure(PrintStream out, boolean colors) {
        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        Handler handler = new StreamHandler(out, new ConsoleFormatter(colors, ZoneId.systemDefault())) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        handler.setLevel(Level.ALL);
        root.addHandler(handler);
        root.setLevel(Level.INFO);
    }
}
