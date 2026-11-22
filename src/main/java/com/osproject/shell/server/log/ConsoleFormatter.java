package com.osproject.shell.server.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Formats log records in the server's console style:
 * {@code server@linux~$ 2026-11-22 10:15:02 INFO  message}, optionally with ANSI colors.
 */
public final class ConsoleFormatter extends Formatter {

    static final String PROMPT = "server@linux~$ ";

    private static final String RESET = "\033[0m";
    private static final String BLUE = "\033[0;34m";
    private static final String RED = "\033[0;31m";
    private static final String YELLOW = "\033[0;33m";
    private static final String CYAN = "\033[0;36m";
    private static final String WHITE = "\033[0;37m";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final boolean colors;
    private final ZoneId zone;

    /**
     * Creates a formatter.
     *
     * @param colors whether to emit ANSI color codes
     * @param zone   time zone for timestamps
     */
    public ConsoleFormatter(boolean colors, ZoneId zone) {
        this.colors = colors;
        this.zone = zone;
    }

    @Override
    public String format(LogRecord record) {
        StringBuilder line = new StringBuilder(128);
        line.append(color(BLUE)).append(PROMPT)
                .append(color(WHITE)).append(TIME.format(Instant.ofEpochMilli(record.getMillis()).atZone(zone)))
                .append(' ')
                .append(color(levelColor(record.getLevel())))
                .append(String.format("%-5s ", levelName(record.getLevel())))
                .append(formatMessage(record))
                .append(color(RESET))
                .append(System.lineSeparator());
        if (record.getThrown() != null) {
            StringWriter trace = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(trace));
            line.append(trace);
        }
        return line.toString();
    }

    private String color(String code) {
        return colors ? code : "";
    }

    private static String levelColor(Level level) {
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return RED;
        }
        if (level.intValue() >= Level.WARNING.intValue()) {
            return YELLOW;
        }
        return level.intValue() >= Level.INFO.intValue() ? CYAN : WHITE;
    }

    private static String levelName(Level level) {
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return "ERROR";
        }
        if (level.intValue() >= Level.WARNING.intValue()) {
            return "WARN";
        }
        return level.intValue() >= Level.INFO.intValue() ? "INFO" : "DEBUG";
    }
}
