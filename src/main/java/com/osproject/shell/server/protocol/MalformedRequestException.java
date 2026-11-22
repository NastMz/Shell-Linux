package com.osproject.shell.server.protocol;

/**
 * A line that could not be decoded into a {@link Request}.
 */
public final class MalformedRequestException extends RequestException {

    private static final long serialVersionUID = 1L;

    private final long requestId;

    /**
     * Creates the exception.
     *
     * @param requestId the id found in the message, or {@link Response#UNKNOWN_ID}
     * @param message   description of the problem
     */
    public MalformedRequestException(long requestId, String message) {
        super(ErrorCode.BAD_REQUEST, message);
        this.requestId = requestId;
    }

    /**
     * Returns the id to answer with.
     *
     * @return the request id, or {@link Response#UNKNOWN_ID} when it could not be read
     */
    public long requestId() {
        return requestId;
    }
}
