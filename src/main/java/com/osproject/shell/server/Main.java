package com.osproject.shell.server;

import com.osproject.shell.server.cli.Cli;

/**
 * Entry point of {@code shell-server.jar}.
 */
public final class Main {

    private Main() {
    }

    /**
     * Runs the command line interface. Commands that finish exit with their status code;
     * {@code serve} returns {@link Cli#KEEP_RUNNING} and the JVM stays alive until shutdown.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        int status = Cli.system().run(args);
        if (status != Cli.KEEP_RUNNING) {
            System.exit(status);
        }
    }
}
