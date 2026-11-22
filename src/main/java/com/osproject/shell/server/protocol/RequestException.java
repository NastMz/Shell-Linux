package com.osproject.shell.server.protocol;

/**
 * Failure of a request that maps to a protocol error code.
 */
public class RequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    /**
     * Creates a failure with the given code and client-facing message.
     *
     * @param code    protocol error code
     * @param message human readable message sent to the client
     */
    public RequestException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Returns the protocol error code.
     *
     * @return the error code
     */
    public ErrorCode code() {
        return code;
    }
}
