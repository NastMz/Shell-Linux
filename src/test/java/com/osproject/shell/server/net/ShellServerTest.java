package com.osproject.shell.server.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(20)
class ShellServerTest {

    private static final String LOGIN = "{\"id\":1,\"type\":\"auth\",\"payload\":{\"username\":\"admin\",\"password\":\"secret\"}}";

    private final ObjectMapper mapper = new ObjectMapper();
    private ShellServer server;
    private Socket socket;
    private BufferedReader in;
    private OutputStream out;

    @BeforeEach
    void start() throws IOException {
        server = new ShellServer(InetAddress.getLoopbackAddress(), 0, new FakeServices().services());
        server.start();
        socket = new Socket(InetAddress.getLoopbackAddress(), server.port());
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = socket.getOutputStream();
    }

    @AfterEach
    void stop() throws IOException {
        socket.close();
        server.close();
    }

    private void write(String line) throws IOException {
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private JsonNode read() throws IOException {
        String line = in.readLine();
        return line == null ? null : mapper.readTree(line);
    }

    @Test
    void answersRequestsOverTcp() throws IOException {
        write("{\"id\":1,\"type\":\"ping\"}");
        JsonNode pong = read();
        assertEquals(1, pong.get("id").asInt());
        assertEquals(42, pong.at("/data/serverTime").asLong());

        write(LOGIN);
        JsonNode login = read();
        assertTrue(login.get("ok").asBoolean());
        assertEquals("admin", login.at("/data/username").asText());
        assertEquals("ubuntu-srv", login.at("/data/system/hostname").asText());
        assertEquals(16_000_000_000L, login.at("/data/system/memoryTotalBytes").asLong());

        write("{\"id\":2,\"type\":\"metrics\"}");
        JsonNode metrics = read();
        assertEquals(12.5, metrics.at("/data/cpu/usagePercent").asDouble());
        assertEquals(3, metrics.at("/data/loadAverage").size());
        assertTrue(metrics.at("/data/network").has("rxBytesPerSec"));

        write("{\"id\":3,\"type\":\"processes.list\"}");
        assertEquals("systemd", read().at("/data/processes/0/name").asText());
    }

    @Test
    void longCommandDoesNotBlockOtherRequests() throws IOException {
        write(LOGIN);
        read();

        write("{\"id\":10,\"type\":\"shell.exec\",\"payload\":{\"command\":\"sleep\"}}");
        write("{\"id\":11,\"type\":\"metrics\"}");

        assertEquals(11, read().get("id").asInt());
        JsonNode exec = read();
        assertEquals(10, exec.get("id").asInt());
        assertEquals("out:sleep", exec.at("/data/stdout").asText());
        assertFalse(exec.at("/data/timedOut").asBoolean());
    }

    @Test
    void malformedLineIsAnsweredWithMinusOne() throws IOException {
        write("this is not json");

        JsonNode response = read();
        assertEquals(-1, response.get("id").asInt());
        assertEquals("BAD_REQUEST", response.at("/error/code").asText());
        assertTrue(response.has("error"));
        assertFalse(response.has("data"));
    }

    @Test
    void closesAfterThreeFailedLogins() throws IOException {
        String wrong = "{\"id\":1,\"type\":\"auth\",\"payload\":{\"username\":\"admin\",\"password\":\"nope\"}}";
        for (int i = 0; i < 3; i++) {
            write(wrong);
            assertEquals("AUTH_FAILED", read().at("/error/code").asText());
        }

        assertNull(in.readLine());
    }

    @Test
    void byeClosesTheConnection() throws IOException {
        write("{\"id\":7,\"type\":\"bye\"}");

        JsonNode response = read();
        assertEquals(7, response.get("id").asInt());
        assertTrue(response.get("ok").asBoolean());
        assertNull(in.readLine());
    }

    @Test
    void oversizedLineIsRejectedAndClosed() throws IOException {
        byte[] huge = new byte[ClientSession.MAX_LINE_BYTES + 10];
        java.util.Arrays.fill(huge, (byte) 'a');
        try {
            out.write(huge);
            out.flush();
        } catch (IOException e) {
            // The server may close the socket before the whole line is sent.
        }

        JsonNode response = read();
        assertEquals(-1, response.get("id").asInt());
        assertEquals("BAD_REQUEST", response.at("/error/code").asText());
    }
}
