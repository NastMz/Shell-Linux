package com.osproject.shell.server.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserStoreTest {

    private final PasswordHasher hasher = new PasswordHasher(1000);

    @TempDir
    Path dir;

    @Test
    void missingFileMeansNoUsers() {
        UserStore store = new UserStore(dir.resolve("users.properties"), hasher);

        assertEquals(List.of(), store.usernames());
        assertFalse(store.authenticate("admin", "x".toCharArray()));
    }

    @Test
    void roundTripsAccountsThroughTheFile() throws IOException {
        Path file = dir.resolve("users.properties");
        UserStore store = new UserStore(file, hasher);
        store.add("admin", "secret".toCharArray());
        store.add("bob", "hunter2".toCharArray());

        UserStore reloaded = new UserStore(file, hasher);
        assertEquals(List.of("admin", "bob"), reloaded.usernames());
        assertTrue(reloaded.authenticate("admin", "secret".toCharArray()));
        assertFalse(reloaded.authenticate("admin", "hunter2".toCharArray()));
        assertFalse(reloaded.authenticate("carol", "secret".toCharArray()));
        String content = Files.readString(file);
        assertTrue(content.contains("admin=pbkdf2-sha256$1000$"), content);
        assertFalse(content.contains("secret"));
    }

    @Test
    void removesAccounts() {
        UserStore store = new UserStore(dir.resolve("users.properties"), hasher);
        store.add("admin", "secret".toCharArray());

        assertTrue(store.remove("admin"));
        assertFalse(store.remove("admin"));
        assertFalse(store.authenticate("admin", "secret".toCharArray()));
    }

    @Test
    void rejectsDuplicatesInvalidNamesAndEmptyPasswords() {
        UserStore store = new UserStore(dir.resolve("users.properties"), hasher);
        store.add("admin", "secret".toCharArray());

        assertThrows(IllegalArgumentException.class, () -> store.add("admin", "other".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> store.add("bad name", "pw".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> store.add("a=b", "pw".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> store.add("-x", "pw".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> store.add("eve", new char[0]));
    }

    @Test
    void picksUpChangesMadeByAnotherProcess() throws IOException {
        Path file = dir.resolve("users.properties");
        UserStore server = new UserStore(file, hasher);
        assertFalse(server.authenticate("admin", "secret".toCharArray()));

        new UserStore(file, hasher).add("admin", "secret".toCharArray());
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5000));

        assertTrue(server.authenticate("admin", "secret".toCharArray()));
    }
}
