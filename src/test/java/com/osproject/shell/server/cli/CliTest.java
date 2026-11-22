package com.osproject.shell.server.cli;

import com.osproject.shell.server.auth.PasswordHasher;
import com.osproject.shell.server.auth.UserStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliTest {

    private final PasswordHasher hasher = new PasswordHasher(1000);
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @TempDir
    Path dir;

    private int run(String stdin, String... args) {
        Cli cli = new Cli(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)), null, hasher);
        return cli.run(args);
    }

    private String users() {
        return dir.resolve("users.properties").toString();
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @Test
    void printsHelpAndVersion() {
        assertEquals(0, run("", "--help"));
        assertTrue(stdout().contains("useradd <username>"), stdout());
        assertTrue(stdout().contains("--port PORT"), stdout());

        assertEquals(0, run("", "--version"));
        assertTrue(stdout().contains("shell-server "), stdout());
    }

    @Test
    void addsListsAndDeletesUsers() {
        assertEquals(0, run("secret\n", "useradd", "admin", "--password-stdin", "--users", users()));
        assertEquals(0, run("hunter2\n", "useradd", "--users=" + users(), "bob"));
        assertTrue(new UserStore(Path.of(users()), hasher).authenticate("admin", "secret".toCharArray()));

        out.reset();
        assertEquals(0, run("", "users", "--users", users()));
        assertEquals("admin" + System.lineSeparator() + "bob" + System.lineSeparator(), stdout());

        assertEquals(0, run("", "userdel", "bob", "--users", users()));
        assertFalse(new UserStore(Path.of(users()), hasher).exists("bob"));
    }

    @Test
    void reportsUserErrors() {
        run("secret\n", "useradd", "admin", "--password-stdin", "--users", users());

        assertEquals(1, run("other\n", "useradd", "admin", "--password-stdin", "--users", users()));
        assertTrue(stderr().contains("already exists"), stderr());
        assertEquals(1, run("\n", "useradd", "carol", "--password-stdin", "--users", users()));
        assertTrue(stderr().contains("must not be empty"), stderr());
        assertEquals(1, run("x\n", "useradd", "bad name", "--users", users()));
        assertEquals(1, run("", "userdel", "nobody", "--users", users()));
        assertTrue(stderr().contains("does not exist"), stderr());
        assertEquals(2, run("", "useradd", "--users", users()));
        assertEquals(2, run("", "userdel", "admin", "--port", "1", "--users", users()));
    }

    @Test
    void rejectsUnknownCommandsAndOptions() {
        assertEquals(2, run("", "launch"));
        assertTrue(stderr().contains("Unknown command 'launch'"), stderr());
        assertEquals(2, run("", "--verbose"));
        assertEquals(2, run("", "serve", "--port", "70000"));
        assertEquals(2, run("", "serve", "--port"));
    }

    @Test
    void serveWithoutUsersExplainsUseradd() {
        assertEquals(1, run("", "--users", users()));
        assertTrue(stderr().contains("no users configured"), stderr());
        assertTrue(stderr().contains("useradd <username>"), stderr());
    }
}
