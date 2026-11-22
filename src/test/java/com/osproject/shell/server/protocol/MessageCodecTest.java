package com.osproject.shell.server.protocol;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCodecTest {

    private final MessageCodec codec = new MessageCodec();

    @Test
    void decodesFullEnvelope() {
        Request request = codec.decode("{\"id\": 7, \"type\": \"shell.exec\", \"payload\": {\"command\": \"ls\"}}");

        assertEquals(7, request.id());
        assertEquals("shell.exec", request.type());
        assertEquals("ls", request.requireString("command"));
    }

    @Test
    void missingPayloadBecomesEmptyObject() {
        Request request = codec.decode("{\"id\":1,\"type\":\"ping\"}");

        assertTrue(request.payload().isEmpty());
    }

    @Test
    void malformedJsonIsAnsweredWithUnknownId() {
        MalformedRequestException e = assertThrows(MalformedRequestException.class, () -> codec.decode("{nope"));

        assertEquals(Response.UNKNOWN_ID, e.requestId());
        assertEquals(ErrorCode.BAD_REQUEST, e.code());
    }

    @Test
    void nonObjectAndBadIdAreAnsweredWithUnknownId() {
        assertEquals(Response.UNKNOWN_ID,
                assertThrows(MalformedRequestException.class, () -> codec.decode("[1,2]")).requestId());
        assertEquals(Response.UNKNOWN_ID,
                assertThrows(MalformedRequestException.class,
                        () -> codec.decode("{\"id\":\"7\",\"type\":\"ping\"}")).requestId());
        assertEquals(Response.UNKNOWN_ID,
                assertThrows(MalformedRequestException.class,
                        () -> codec.decode("{\"id\":1.5,\"type\":\"ping\"}")).requestId());
    }

    @Test
    void missingTypeOrBadPayloadKeepsTheId() {
        assertEquals(9, assertThrows(MalformedRequestException.class,
                () -> codec.decode("{\"id\":9}")).requestId());
        assertEquals(9, assertThrows(MalformedRequestException.class,
                () -> codec.decode("{\"id\":9,\"type\":\"ping\",\"payload\":[]}")).requestId());
    }

    @Test
    void encodesSuccessWithoutErrorField() {
        String json = codec.encode(Response.success(7, Map.of("serverTime", 5)));

        assertEquals("{\"id\":7,\"ok\":true,\"data\":{\"serverTime\":5}}", json);
    }

    @Test
    void encodesEmptyDataAsObject() {
        assertEquals("{\"id\":3,\"ok\":true,\"data\":{}}", codec.encode(Response.success(3, null)));
    }

    @Test
    void encodesFailureOnOneLine() {
        String json = codec.encode(Response.failure(7, ErrorCode.NOT_FOUND, "No process\nwith PID 4242"));

        assertEquals("{\"id\":7,\"ok\":false,\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"No process\\nwith PID 4242\"}}",
                json);
        assertFalse(json.contains("\n"));
    }

    @Test
    void payloadAccessorsRejectWrongTypes() {
        Request request = codec.decode("{\"id\":1,\"type\":\"x\",\"payload\":{\"pid\":\"12\",\"n\":3}}");

        assertThrows(RequestException.class, () -> request.requireLong("pid"));
        assertThrows(RequestException.class, () -> request.requireString("n"));
        assertEquals(3, request.optionalLong("n", 0));
        assertEquals(30, request.optionalLong("missing", 30));
        assertEquals("TERM", request.optionalString("missing", "TERM"));
    }
}
