package com.osproject.shell.server.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.Objects;

/**
 * A server response. Exactly one of {@code data} and {@code error} is present.
 *
 * @param id    the id of the request being answered, or {@link #UNKNOWN_ID} when it could not be parsed
 * @param ok    whether the request succeeded
 * @param data  result object of a successful request
 * @param error error details of a failed request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Response(long id, boolean ok, Object data, Error error) {

    /** Id used when the request could not be parsed. */
    public static final long UNKNOWN_ID = -1;

    /**
     * Error details of a failed request.
     *
     * @param code    protocol error code
     * @param message human readable message
     */
    public record Error(ErrorCode code, String message) {
    }

    /**
     * Creates a successful response.
     *
     * @param id   request id
     * @param data result object; {@code null} is sent as an empty object
     * @return the response
     */
    public static Response success(long id, Object data) {
        return new Response(id, true, data == null ? Map.of() : data, null);
    }

    /**
     * Creates a failed response.
     *
     * @param id      request id
     * @param code    error code
     * @param message human readable message
     * @return the response
     */
    public static Response failure(long id, ErrorCode code, String message) {
        return new Response(id, false, null, new Error(Objects.requireNonNull(code), message));
    }
}
