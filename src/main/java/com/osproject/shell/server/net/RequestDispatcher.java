package com.osproject.shell.server.net;

import com.osproject.shell.server.BuildInfo;
import com.osproject.shell.server.metrics.ProcessInfo;
import com.osproject.shell.server.metrics.SystemInfo;
import com.osproject.shell.server.process.ProcessController.Signal;
import com.osproject.shell.server.protocol.ErrorCode;
import com.osproject.shell.server.protocol.Request;
import com.osproject.shell.server.protocol.RequestException;
import com.osproject.shell.server.protocol.Response;
import com.osproject.shell.server.shell.ExecutionResult;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes decoded requests: enforces authentication and maps every request type of the
 * protocol to its service. Thread-safe; one instance serves all connections.
 */
public final class RequestDispatcher {

    /** Failed {@code auth} attempts after which the connection is closed. */
    public static final int MAX_FAILED_AUTH = 3;

    static final long DEFAULT_TIMEOUT_SECONDS = 30;
    static final long MAX_TIMEOUT_SECONDS = 300;

    private static final Logger LOG = Logger.getLogger(RequestDispatcher.class.getName());
    private static final Set<String> PUBLIC_TYPES = Set.of("auth", "ping", "bye");

    private final ServerServices services;

    /** {@code ping} result. @param serverTime epoch millis */
    record Pong(long serverTime) {
    }

    /** {@code auth} result. @param username user @param serverVersion version @param system host facts */
    record Login(String username, String serverVersion, SystemInfo system) {
    }

    /** {@code processes.list} result. @param processes the processes */
    record ProcessList(List<ProcessInfo> processes) {
    }

    /**
     * Creates a dispatcher.
     *
     * @param services the services used by the handlers
     */
    public RequestDispatcher(ServerServices services) {
        this.services = services;
    }

    /**
     * Returns whether a request must be handled in order with the reading of the connection
     * (login and connection control), rather than concurrently.
     *
     * @param session the session
     * @param request the request
     * @return {@code true} for {@code auth}, {@code bye} and anything before login
     */
    public boolean isSequential(SessionState session, Request request) {
        return session.user().isEmpty() || request.type().equals("auth") || request.type().equals("bye");
    }

    /**
     * Executes a request. After this returns, {@link SessionState#closeRequested()} tells whether
     * the connection must be closed once the response is sent.
     *
     * @param session the requesting session
     * @param request the request
     * @return the response, never {@code null}
     */
    public Response dispatch(SessionState session, Request request) {
        try {
            if (session.user().isEmpty() && !PUBLIC_TYPES.contains(request.type())) {
                throw new RequestException(ErrorCode.UNAUTHENTICATED, "Log in with 'auth' first");
            }
            return Response.success(request.id(), handle(session, request));
        } catch (RequestException e) {
            return Response.failure(request.id(), e.code(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.failure(request.id(), ErrorCode.INTERNAL, "Request interrupted");
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Request '" + request.type() + "' from " + session.remote() + " failed", e);
            return Response.failure(request.id(), ErrorCode.INTERNAL, "Internal server error");
        }
    }

    private Object handle(SessionState session, Request request) throws InterruptedException {
        return switch (request.type()) {
            case "ping" -> new Pong(services.clock().millis());
            case "bye" -> {
                session.requestClose();
                yield Map.of();
            }
            case "auth" -> authenticate(session, request);
            case "system.info" -> services.systemInfo().get();
            case "metrics" -> services.metrics().metrics();
            case "processes.list" -> new ProcessList(services.metrics().processes());
            case "process.kill" -> kill(session, request);
            case "shell.exec" -> exec(session, request);
            default -> throw new RequestException(ErrorCode.BAD_REQUEST, "Unknown request type '" + request.type() + "'");
        };
    }

    private Login authenticate(SessionState session, Request request) {
        String username = request.requireString("username");
        char[] password = request.requireString("password").toCharArray();
        try {
            if (services.authenticator().authenticate(username, password)) {
                session.authenticated(username);
                LOG.info(() -> "User '" + username + "' logged in from " + session.remote());
                return new Login(username, BuildInfo.version(), services.systemInfo().get());
            }
        } finally {
            Arrays.fill(password, '\0');
        }
        int failures = session.recordFailedAuth();
        LOG.warning(() -> "Failed login for '" + username + "' from " + session.remote()
                + " (attempt " + failures + " of " + MAX_FAILED_AUTH + ")");
        if (failures >= MAX_FAILED_AUTH) {
            session.requestClose();
            LOG.warning(() -> "Closing " + session.remote() + " after " + failures + " failed logins");
            throw new RequestException(ErrorCode.AUTH_FAILED, "Invalid username or password; closing connection");
        }
        throw new RequestException(ErrorCode.AUTH_FAILED, "Invalid username or password");
    }

    private Map<String, Object> kill(SessionState session, Request request) {
        long pid = request.requireLong("pid");
        Signal signal = parseSignal(request.optionalString("signal", "TERM"));
        services.processes().kill(pid, signal);
        LOG.info(() -> user(session) + " sent SIG" + signal + " to PID " + pid);
        return Map.of();
    }

    private ExecutionResult exec(SessionState session, Request request) throws InterruptedException {
        String command = request.requireString("command");
        long seconds = Math.max(1, Math.min(MAX_TIMEOUT_SECONDS,
                request.optionalLong("timeoutSeconds", DEFAULT_TIMEOUT_SECONDS)));
        Path cwd = session.cwd();
        LOG.info(() -> user(session) + " [" + cwd + "] $ " + command);
        ExecutionResult result = services.commands().run(command, cwd, Duration.ofSeconds(seconds));
        session.cwd(Path.of(result.cwd()));
        if (result.timedOut()) {
            LOG.warning(() -> user(session) + " command timed out after " + seconds + " s: " + command);
        }
        return result;
    }

    private static Signal parseSignal(String value) {
        String name = value.trim().toUpperCase(Locale.ROOT);
        if (name.startsWith("SIG")) {
            name = name.substring(3);
        }
        try {
            return Signal.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new RequestException(ErrorCode.BAD_REQUEST, "Invalid payload: 'signal' must be TERM or KILL");
        }
    }

    private static String user(SessionState session) {
        return session.user().orElse("?") + "@" + session.remote();
    }
}
