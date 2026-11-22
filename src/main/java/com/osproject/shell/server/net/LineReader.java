package com.osproject.shell.server.net;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads UTF-8 lines with an upper bound on their length, so a misbehaving client cannot
 * exhaust server memory with one endless line.
 */
final class LineReader {

    /** Thrown when a line exceeds the limit. */
    static final class LineTooLongException extends IOException {
        private static final long serialVersionUID = 1L;

        LineTooLongException(int limit) {
            super("Message exceeds " + limit + " bytes");
        }
    }

    private final InputStream in;
    private final int maxBytes;

    LineReader(InputStream in, int maxBytes) {
        this.in = new BufferedInputStream(in);
        this.maxBytes = maxBytes;
    }

    /**
     * Reads the next line without its terminator ({@code \n} or {@code \r\n}).
     *
     * @return the line, or {@code null} at end of stream
     * @throws LineTooLongException when the line is longer than the limit
     * @throws IOException          on read errors
     */
    String readLine() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                return decode(line);
            }
            if (line.size() >= maxBytes) {
                throw new LineTooLongException(maxBytes);
            }
            line.write(b);
        }
        return line.size() == 0 ? null : decode(line);
    }

    private static String decode(ByteArrayOutputStream line) {
        String text = line.toString(StandardCharsets.UTF_8);
        return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
    }
}
