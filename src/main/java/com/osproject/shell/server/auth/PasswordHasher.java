package com.osproject.shell.server.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hashes and verifies passwords with PBKDF2-HMAC-SHA256.
 *
 * <p>Encoded format: {@code pbkdf2-sha256$<iterations>$<saltBase64>$<hashBase64>}.
 * Verification uses a constant-time comparison.</p>
 */
public final class PasswordHasher {

    /** Iteration count used for new hashes (OWASP 2023 recommendation for PBKDF2-HMAC-SHA256). */
    public static final int DEFAULT_ITERATIONS = 600_000;

    private static final String SCHEME = "pbkdf2-sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final int MAX_ITERATIONS = 10_000_000;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates a hasher that uses {@link #DEFAULT_ITERATIONS} for new hashes.
     */
    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    /**
     * Creates a hasher with a custom iteration count for new hashes.
     *
     * @param iterations iteration count, at least 1
     */
    public PasswordHasher(int iterations) {
        if (iterations < 1 || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("iterations out of range: " + iterations);
        }
        this.iterations = iterations;
    }

    /**
     * Hashes a password with a fresh random salt.
     *
     * @param password the password
     * @return the encoded hash
     */
    public String hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, iterations);
        Base64.Encoder encoder = Base64.getEncoder();
        return SCHEME + "$" + iterations + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash);
    }

    /**
     * Checks a password against an encoded hash.
     *
     * @param password the candidate password
     * @param encoded  a value produced by {@link #hash(char[])}
     * @return {@code true} when the password matches; {@code false} on mismatch or malformed hash
     */
    public boolean verify(char[] password, String encoded) {
        String[] parts = encoded == null ? new String[0] : encoded.split("\\$", -1);
        if (parts.length != 4 || !SCHEME.equals(parts[0])) {
            return false;
        }
        try {
            int storedIterations = Integer.parseInt(parts[1]);
            if (storedIterations < 1 || storedIterations > MAX_ITERATIONS) {
                return false;
            }
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[2]);
            byte[] expected = decoder.decode(parts[3]);
            if (salt.length == 0 || expected.length == 0) {
                return false;
            }
            return MessageDigest.isEqual(expected, derive(password, salt, storedIterations));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " is not available", e);
        } finally {
            spec.clearPassword();
        }
    }
}
