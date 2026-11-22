package com.osproject.shell.server.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Encodes and decodes newline-delimited JSON messages.
 *
 * <p>Instances are thread-safe.</p>
 */
public final class MessageCodec {

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /**
     * Decodes one line into a request.
     *
     * @param line a single JSON object without the trailing newline
     * @return the request
     * @throws MalformedRequestException when the line is not a valid request envelope
     */
    public Request decode(String line) {
        JsonNode root;
        try {
            root = mapper.readTree(line);
        } catch (JsonProcessingException e) {
            throw new MalformedRequestException(Response.UNKNOWN_ID, "Malformed JSON");
        }
        if (root == null || !root.isObject()) {
            throw new MalformedRequestException(Response.UNKNOWN_ID, "Message must be a JSON object");
        }
        JsonNode idNode = root.get("id");
        if (idNode == null || !idNode.isNumber() || !idNode.canConvertToExactIntegral()
                || !idNode.canConvertToLong()) {
            throw new MalformedRequestException(Response.UNKNOWN_ID, "Field 'id' must be an integer");
        }
        long id = idNode.asLong();
        JsonNode typeNode = root.get("type");
        if (typeNode == null || !typeNode.isTextual() || typeNode.asText().isBlank()) {
            throw new MalformedRequestException(id, "Field 'type' must be a non-empty string");
        }
        JsonNode payloadNode = root.get("payload");
        if (payloadNode != null && !payloadNode.isNull() && !payloadNode.isObject()) {
            throw new MalformedRequestException(id, "Field 'payload' must be an object");
        }
        ObjectNode payload = payloadNode instanceof ObjectNode objectNode ? objectNode : null;
        return new Request(id, typeNode.asText(), payload);
    }

    /**
     * Encodes a response as a single line of JSON, without the trailing newline.
     *
     * @param response the response
     * @return the JSON text
     */
    public String encode(Response response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize response " + response.id(), e);
        }
    }
}
