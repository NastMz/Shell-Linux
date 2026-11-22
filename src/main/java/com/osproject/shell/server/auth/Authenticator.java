package com.osproject.shell.server.auth;

/**
 * Checks user credentials.
 */
@FunctionalInterface
public interface Authenticator {

    /**
     * Checks whether the credentials are valid.
     *
     * @param username the user name
     * @param password the password
     * @return {@code true} when the user exists and the password matches
     */
    boolean authenticate(String username, char[] password);
}
