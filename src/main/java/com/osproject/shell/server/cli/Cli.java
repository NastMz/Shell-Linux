package com.osproject.shell.server.cli;

import com.osproject.shell.server.BuildInfo;
import com.osproject.shell.server.auth.PasswordHasher;
import com.osproject.shell.server.auth.UserStore;
import com.osproject.shell.server.log.LogSetup;
import com.osproject.shell.server.metrics.ProcFs;
import com.osproject.shell.server.metrics.SystemInfo;
import com.osproject.shell.server.metrics.SystemInfoReader;
import com.osproject.shell.server.metrics.SystemSampler;
import com.osproject.shell.server.net.ServerServices;
import com.osproject.shell.server.net.ShellServer;
import com.osproject.shell.server.process.LinuxProcessController;
import com.osproject.shell.server.shell.CommandExecutor;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Command line interface: {@code serve}, {@code useradd}, {@code userdel} and {@code users}.
 */
public final class Cli {

    /** Returned by {@link #run(String[])} when the server keeps running in background threads. */
    public static final int KEEP_RUNNING = -1;

    static final int OK = 0;
    static final int FAILURE = 1;
    static final int USAGE = 2;

    private static final Logger LOG = Logger.getLogger(Cli.class.getName());

    private final PrintStream out;
    private final PrintStream err;
    private final InputStream in;
    private final Console console;
    private final PasswordHasher hasher;

    /**
     * Creates a CLI bound to the given streams.
     *
     * @param out     standard output
     * @param err     standard error
     * @param in      standard input (password fallback)
     * @param console interactive console, or {@code null} when not attached to a terminal
     * @param hasher  password hasher for new accounts
     */
    public Cli(PrintStream out, PrintStream err, InputStream in, Console console, PasswordHasher hasher) {
        this.out = out;
        this.err = err;
        this.in = in;
        this.console = console;
        this.hasher = hasher;
    }

    /**
     * Returns a CLI bound to the process streams and console.
     *
     * @return the CLI
     */
    public static Cli system() {
        return new Cli(System.out, System.err, System.in, System.console(), new PasswordHasher());
    }

    /**
     * Runs a command.
     *
     * @param args command line arguments
     * @return the exit status, or {@link #KEEP_RUNNING} when the server was started
     */
    public int run(String[] args) {
        CliArguments arguments;
        try {
            arguments = CliArguments.parse(args);
        } catch (CliArguments.UsageException e) {
            err.println("Error: " + e.getMessage());
            err.println("Run with --help for usage.");
            return USAGE;
        }
        if (arguments.help()) {
            out.print(usage());
            return OK;
        }
        if (arguments.version()) {
            out.println("shell-server " + BuildInfo.version());
            return OK;
        }
        try {
            return switch (arguments.command()) {
                case "useradd" -> userAdd(arguments);
                case "userdel" -> userDel(arguments);
                case "users" -> listUsers(arguments);
                default -> serve(arguments);
            };
        } catch (UncheckedIOException e) {
            err.println("Error: " + e.getMessage() + ": " + e.getCause().getMessage());
            return FAILURE;
        }
    }

    private int userAdd(CliArguments arguments) {
        if (!checkUserCommand(arguments)) {
            return USAGE;
        }
        String username = arguments.positionals().get(0);
        if (!UserStore.isValidUsername(username)) {
            err.println("Error: invalid user name '" + username
                    + "': use 1-32 letters, digits, '.', '_' or '-', starting with a letter or digit");
            return FAILURE;
        }
        UserStore store = new UserStore(arguments.usersFile(), hasher);
        if (store.exists(username)) {
            err.println("Error: user '" + username + "' already exists in " + arguments.usersFile());
            return FAILURE;
        }
        char[] password = readNewPassword(username, arguments.passwordStdin());
        if (password == null) {
            return FAILURE;
        }
        try {
            store.add(username, password);
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            return FAILURE;
        } finally {
            Arrays.fill(password, '\0');
        }
        out.println("User '" + username + "' added to " + arguments.usersFile());
        return OK;
    }

    private int userDel(CliArguments arguments) {
        if (arguments.passwordStdin()) {
            err.println("Error: --password-stdin is only valid with useradd");
            return USAGE;
        }
        if (!checkUserCommand(arguments)) {
            return USAGE;
        }
        String username = arguments.positionals().get(0);
        if (!new UserStore(arguments.usersFile(), hasher).remove(username)) {
            err.println("Error: user '" + username + "' does not exist in " + arguments.usersFile());
            return FAILURE;
        }
        out.println("User '" + username + "' removed from " + arguments.usersFile());
        return OK;
    }

    private int listUsers(CliArguments arguments) {
        if (!arguments.positionals().isEmpty() || arguments.passwordStdin() || arguments.explicitServeOptions()) {
            err.println("Error: 'users' takes no arguments other than --users");
            return USAGE;
        }
        new UserStore(arguments.usersFile(), hasher).usernames().forEach(out::println);
        return OK;
    }

    private boolean checkUserCommand(CliArguments arguments) {
        if (arguments.explicitServeOptions()) {
            err.println("Error: --port and --bind are only valid with serve");
            return false;
        }
        if (arguments.positionals().size() != 1) {
            err.println("Error: '" + arguments.command() + "' requires exactly one user name");
            err.println("Usage: java -jar shell-server.jar " + arguments.command() + " <username> [--users FILE]");
            return false;
        }
        return true;
    }

    private char[] readNewPassword(String username, boolean fromStdin) {
        if (!fromStdin && console != null) {
            char[] first = console.readPassword("Password for '%s': ", username);
            char[] second = first == null ? null : console.readPassword("Retype password: ");
            if (first == null || second == null) {
                err.println("Error: no password entered");
                return null;
            }
            boolean match = Arrays.equals(first, second);
            Arrays.fill(second, '\0');
            if (!match) {
                Arrays.fill(first, '\0');
                err.println("Error: passwords do not match");
                return null;
            }
            return nonEmpty(first);
        }
        try {
            String line = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).readLine();
            return nonEmpty(line == null ? new char[0] : line.toCharArray());
        } catch (IOException e) {
            err.println("Error: cannot read password from standard input: " + e.getMessage());
            return null;
        }
    }

    private char[] nonEmpty(char[] password) {
        if (password.length == 0) {
            err.println("Error: the password must not be empty");
            return null;
        }
        return password;
    }

    private int serve(CliArguments arguments) {
        if (!arguments.positionals().isEmpty() || arguments.passwordStdin()) {
            err.println("Error: 'serve' takes only --port, --bind and --users");
            return USAGE;
        }
        UserStore store = new UserStore(arguments.usersFile(), hasher);
        if (store.usernames().isEmpty()) {
            err.println("Error: no users configured in " + arguments.usersFile().toAbsolutePath());
            err.println("Create one first:  java -jar shell-server.jar useradd <username> --users "
                    + arguments.usersFile());
            return FAILURE;
        }
        InetAddress bindAddress;
        try {
            bindAddress = InetAddress.getByName(arguments.bind());
        } catch (IOException e) {
            err.println("Error: invalid bind address '" + arguments.bind() + "'");
            return USAGE;
        }

        LogSetup.configure(out, LogSetup.colorsEnabled());
        if (!Files.isDirectory(Path.of("/proc"))) {
            LOG.warning("/proc is not available: metrics and the process list will be empty (Linux required)");
        }
        SystemSampler sampler = SystemSampler.forSystem();
        sampler.start();
        SystemInfo systemInfo = new SystemInfoReader(ProcFs.system()).read();
        Path home = Path.of(System.getProperty("user.home"));
        ServerServices services = new ServerServices(store, () -> systemInfo, sampler, new LinuxProcessController(),
                new CommandExecutor(home), home, Clock.systemUTC());
        ShellServer server;
        try {
            server = new ShellServer(bindAddress, arguments.port(), services);
        } catch (IOException e) {
            sampler.close();
            err.println("Error: cannot listen on " + arguments.bind() + ":" + arguments.port() + ": " + e.getMessage());
            return FAILURE;
        }
        LOG.info(() -> "Shell server " + BuildInfo.version() + " on " + systemInfo.hostname() + " ("
                + systemInfo.os() + "), " + store.usernames().size() + " user(s) in "
                + arguments.usersFile().toAbsolutePath());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down");
            server.close();
            sampler.close();
        }, "shutdown"));
        return KEEP_RUNNING;
    }

    private static String usage() {
        return """
                Shell server %s - remote shell and resource monitor for Linux

                Usage:
                  java -jar shell-server.jar [serve] [--port PORT] [--bind ADDRESS] [--users FILE]
                  java -jar shell-server.jar useradd <username> [--password-stdin] [--users FILE]
                  java -jar shell-server.jar userdel <username> [--users FILE]
                  java -jar shell-server.jar users [--users FILE]

                Commands:
                  serve      Start the server (default command)
                  useradd    Create an account; prompts for the password twice
                  userdel    Delete an account
                  users      List account names

                Options:
                  --port PORT        TCP port to listen on (default %d)
                  --bind ADDRESS     Address to listen on (default %s)
                  --users FILE       Accounts file (default %s)
                  --password-stdin   useradd: read the password from the first line of stdin
                  -h, --help         Show this help
                  -V, --version      Show the version

                Environment:
                  NO_COLOR           Disable colored log output
                """.formatted(BuildInfo.version(), CliArguments.DEFAULT_PORT, CliArguments.DEFAULT_BIND,
                CliArguments.DEFAULT_USERS_FILE);
    }
}
