package com.osproject.shell.server.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parsed command line: a subcommand, its positional arguments and options.
 *
 * @param command        subcommand ({@code serve} when none is given)
 * @param positionals    positional arguments after the subcommand
 * @param port           {@code --port}
 * @param bind           {@code --bind}
 * @param usersFile      {@code --users}
 * @param passwordStdin  {@code --password-stdin}
 * @param help           {@code --help}
 * @param version        {@code --version}
 * @param explicitServeOptions whether {@code --port} or {@code --bind} was given
 */
record CliArguments(String command, List<String> positionals, int port, String bind, Path usersFile,
                    boolean passwordStdin, boolean help, boolean version, boolean explicitServeOptions) {

    static final int DEFAULT_PORT = 2704;
    static final String DEFAULT_BIND = "0.0.0.0";
    static final Path DEFAULT_USERS_FILE = Path.of("users.properties");

    private static final Set<String> COMMANDS = Set.of("serve", "useradd", "userdel", "users");

    /** Invalid command line. */
    static final class UsageException extends Exception {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }

    /**
     * Parses the arguments. Options may appear anywhere and accept {@code --name value} or
     * {@code --name=value}.
     *
     * @param args raw arguments
     * @return the parsed arguments
     * @throws UsageException when an option or command is unknown or malformed
     */
    static CliArguments parse(String[] args) throws UsageException {
        String command = null;
        List<String> positionals = new ArrayList<>();
        int port = DEFAULT_PORT;
        String bind = DEFAULT_BIND;
        Path users = DEFAULT_USERS_FILE;
        boolean passwordStdin = false;
        boolean help = false;
        boolean version = false;
        boolean serveOptions = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String name = arg;
            String inlineValue = null;
            if (arg.startsWith("--") && arg.contains("=")) {
                name = arg.substring(0, arg.indexOf('='));
                inlineValue = arg.substring(arg.indexOf('=') + 1);
            }
            switch (name) {
                case "-h", "--help" -> help = true;
                case "-V", "--version" -> version = true;
                case "--password-stdin" -> passwordStdin = true;
                case "--port" -> {
                    String value = inlineValue != null ? inlineValue : value(args, ++i, name);
                    port = parsePort(value);
                    serveOptions = true;
                }
                case "--bind" -> {
                    bind = inlineValue != null ? inlineValue : value(args, ++i, name);
                    serveOptions = true;
                }
                case "--users" -> users = Path.of(inlineValue != null ? inlineValue : value(args, ++i, name));
                default -> {
                    if (arg.startsWith("-")) {
                        throw new UsageException("Unknown option '" + arg + "'");
                    }
                    if (command == null) {
                        if (!COMMANDS.contains(arg)) {
                            throw new UsageException("Unknown command '" + arg + "'");
                        }
                        command = arg;
                    } else {
                        positionals.add(arg);
                    }
                }
            }
        }
        return new CliArguments(command == null ? "serve" : command, List.copyOf(positionals), port, bind, users,
                passwordStdin, help, version, serveOptions);
    }

    private static String value(String[] args, int index, String option) throws UsageException {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new UsageException("Option " + option + " requires a value");
        }
        return args[index];
    }

    private static int parsePort(String value) throws UsageException {
        try {
            int port = Integer.parseInt(value);
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
            // Reported below.
        }
        throw new UsageException("Invalid port '" + value + "': expected 1-65535");
    }
}
