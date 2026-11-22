package com.osproject.shell.server.protocol;

/**
 * Error codes defined by the wire protocol (see {@code docs/PROTOCOL.md}).
 */
public enum ErrorCode {
    /** Malformed JSON, unknown request type, or invalid payload. */
    BAD_REQUEST,
    /** A request other than {@code auth}/{@code ping}/{@code bye} before login. */
    UNAUTHENTICATED,
    /** Wrong username or password. */
    AUTH_FAILED,
    /** The referenced resource (for example a PID) does not exist. */
    NOT_FOUND,
    /** The operating system refused the operation. */
    PERMISSION_DENIED,
    /** Unexpected server error. */
    INTERNAL
}
