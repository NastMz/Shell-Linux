package com.osproject.shell.server.auth;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * User accounts stored in a properties file: {@code username=pbkdf2-sha256$...}.
 *
 * <p>The file is re-read when its modification time changes, so accounts added with the
 * {@code useradd} command become valid without restarting the server. Writes are atomic
 * (temporary file plus move) and, on POSIX systems, readable only by the owner.</p>
 */
public final class UserStore implements Authenticator {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,31}");

    private final Path file;
    private final PasswordHasher hasher;
    private final String dummyHash;

    private Map<String, String> users = Map.of();
    private FileTime loadedModified;

    /**
     * Creates a store backed by {@code file}. The file does not need to exist yet.
     *
     * @param file   the properties file
     * @param hasher the password hasher
     */
    public UserStore(Path file, PasswordHasher hasher) {
        this.file = file;
        this.hasher = hasher;
        this.dummyHash = hasher.hash("timing-equalizer".toCharArray());
    }

    /**
     * Returns whether {@code username} is acceptable as an account name.
     *
     * @param username candidate name
     * @return {@code true} for 1-32 characters of letters, digits, '.', '_' or '-', not starting with a symbol
     */
    public static boolean isValidUsername(String username) {
        return username != null && USERNAME.matcher(username).matches();
    }

    /**
     * Returns the backing file.
     *
     * @return the properties file path
     */
    public Path file() {
        return file;
    }

    @Override
    public boolean authenticate(String username, char[] password) {
        String encoded = snapshot().get(username);
        if (encoded == null) {
            // Spend the same time as a real check so user names cannot be probed by timing.
            hasher.verify(password, dummyHash);
            return false;
        }
        return hasher.verify(password, encoded);
    }

    /**
     * Returns the account names in alphabetical order.
     *
     * @return the user names
     */
    public List<String> usernames() {
        return List.copyOf(snapshot().keySet());
    }

    /**
     * Returns whether an account exists.
     *
     * @param username the user name
     * @return {@code true} when present
     */
    public boolean exists(String username) {
        return snapshot().containsKey(username);
    }

    /**
     * Adds an account.
     *
     * @param username a valid, not yet existing user name
     * @param password a non-empty password
     * @throws IllegalArgumentException when the name is invalid, already exists, or the password is empty
     */
    public synchronized void add(String username, char[] password) {
        if (!isValidUsername(username)) {
            throw new IllegalArgumentException("Invalid user name '" + username
                    + "': use 1-32 letters, digits, '.', '_' or '-', starting with a letter or digit");
        }
        if (password.length == 0) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        Map<String, String> updated = new TreeMap<>(snapshot());
        if (updated.containsKey(username)) {
            throw new IllegalArgumentException("User '" + username + "' already exists");
        }
        updated.put(username, hasher.hash(password));
        write(updated);
    }

    /**
     * Removes an account.
     *
     * @param username the user name
     * @return {@code true} when the account existed
     */
    public synchronized boolean remove(String username) {
        Map<String, String> updated = new TreeMap<>(snapshot());
        if (updated.remove(username) == null) {
            return false;
        }
        write(updated);
        return true;
    }

    private synchronized Map<String, String> snapshot() {
        try {
            FileTime modified = Files.getLastModifiedTime(file);
            if (!modified.equals(loadedModified)) {
                users = read();
                loadedModified = modified;
            }
        } catch (NoSuchFileException e) {
            users = Map.of();
            loadedModified = null;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read users file " + file, e);
        }
        return users;
    }

    private Map<String, String> read() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Map<String, String> loaded = new TreeMap<>();
        properties.stringPropertyNames().forEach(name -> loaded.put(name, properties.getProperty(name).trim()));
        return Collections.unmodifiableMap(loaded);
    }

    private void write(Map<String, String> accounts) {
        try {
            Path directory = file.toAbsolutePath().getParent();
            Files.createDirectories(directory);
            Path temp = Files.createTempFile(directory, ".users", ".tmp");
            try {
                restrictPermissions(temp);
                try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                    writer.write("# Shell server accounts: username=pbkdf2-sha256$iterations$salt$hash\n");
                    writer.write("# Manage with: java -jar shell-server.jar useradd|userdel <username>\n");
                    for (Map.Entry<String, String> entry : accounts.entrySet()) {
                        writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
                    }
                }
                moveIntoPlace(temp);
            } finally {
                Files.deleteIfExists(temp);
            }
            users = Collections.unmodifiableMap(new TreeMap<>(accounts));
            loadedModified = Files.getLastModifiedTime(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write users file " + file, e);
        }
    }

    private void moveIntoPlace(Path temp) throws IOException {
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void restrictPermissions(Path path) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        }
    }
}
