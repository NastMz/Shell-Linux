package com.osproject.shell.server.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * A decoded client request.
 *
 * @param id      client-chosen correlation id
 * @param type    request type, for example {@code metrics}
 * @param payload request arguments, never {@code null} (empty object when omitted)
 */
public record Request(long id, String type, ObjectNode payload) {

    /**
     * Validates and normalizes the request.
     */
    public Request {
        Objects.requireNonNull(type, "type");
        payload = payload == null ? JsonNodeFactory.instance.objectNode() : payload;
    }

    /**
     * Returns a required, non-blank string field of the payload.
     *
     * @param field field name
     * @return the field value
     * @throws RequestException with {@link ErrorCode#BAD_REQUEST} when missing or not a string
     */
    public String requireString(String field) {
        JsonNode node = payload.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw badPayload(quote(field) + " must be a non-empty string");
        }
        return node.asText();
    }

    /**
     * Returns an optional string field of the payload.
     *
     * @param field        field name
     * @param defaultValue value used when the field is absent or null
     * @return the field value or the default
     */
    public String optionalString(String field, String defaultValue) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        if (!node.isTextual()) {
            throw badPayload(quote(field) + " must be a string");
        }
        return node.asText();
    }

    /**
     * Returns a required integral field of the payload.
     *
     * @param field field name
     * @return the field value
     */
    public long requireLong(String field) {
        JsonNode node = payload.get(field);
        if (node == null || !node.isNumber() || !node.canConvertToExactIntegral() || !node.canConvertToLong()) {
            throw badPayload(quote(field) + " must be an integer");
        }
        return node.asLong();
    }

    /**
     * Returns an optional integral field of the payload.
     *
     * @param field        field name
     * @param defaultValue value used when the field is absent or null
     * @return the field value or the default
     */
    public long optionalLong(String field, long defaultValue) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        return requireLong(field);
    }

    private static String quote(String field) {
        return "'" + field + "'";
    }

    private static RequestException badPayload(String message) {
        return new RequestException(ErrorCode.BAD_REQUEST, "Invalid payload: " + message);
    }
}
