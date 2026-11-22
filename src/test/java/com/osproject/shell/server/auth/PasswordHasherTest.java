package com.osproject.shell.server.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(1000);

    @Test
    void hashUsesDocumentedFormat() {
        String encoded = hasher.hash("secret".toCharArray());

        assertTrue(encoded.matches("pbkdf2-sha256\\$1000\\$[A-Za-z0-9+/=]+\\$[A-Za-z0-9+/=]+"), encoded);
    }

    @Test
    void verifiesMatchingPasswordOnly() {
        String encoded = hasher.hash("secret".toCharArray());

        assertTrue(hasher.verify("secret".toCharArray(), encoded));
        assertFalse(hasher.verify("Secret".toCharArray(), encoded));
        assertFalse(hasher.verify("".toCharArray(), encoded));
    }

    @Test
    void saltsEveryHash() {
        assertNotEquals(hasher.hash("secret".toCharArray()), hasher.hash("secret".toCharArray()));
    }

    @Test
    void verifiesHashesWithOtherIterationCounts() {
        String encoded = new PasswordHasher(1500).hash("pw".toCharArray());

        assertTrue(hasher.verify("pw".toCharArray(), encoded));
    }

    @Test
    void rejectsMalformedHashes() {
        assertFalse(hasher.verify("pw".toCharArray(), null));
        assertFalse(hasher.verify("pw".toCharArray(), "plain-text"));
        assertFalse(hasher.verify("pw".toCharArray(), "pbkdf2-sha256$abc$AAAA$AAAA"));
        assertFalse(hasher.verify("pw".toCharArray(), "pbkdf2-sha256$1000$!!!$AAAA"));
        assertFalse(hasher.verify("pw".toCharArray(), "sha1$1000$AAAA$AAAA"));
    }
}
