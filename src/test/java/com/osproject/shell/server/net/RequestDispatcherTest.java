package com.osproject.shell.server.net;

import com.osproject.shell.server.protocol.ErrorCode;
import com.osproject.shell.server.protocol.MessageCodec;
import com.osproject.shell.server.protocol.Request;
import com.osproject.shell.server.protocol.Response;
import com.osproject.shell.server.shell.ExecutionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestDispatcherTest {

    private final FakeServices fakes = new FakeServices();
    private final RequestDispatcher dispatcher = new RequestDispatcher(fakes.services());
    private final MessageCodec codec = new MessageCodec();
    private final SessionState session = new SessionState("10.0.0.2:5000", FakeServices.HOME);

    private Response send(String json) {
        return dispatcher.dispatch(session, codec.decode(json));
    }

    private void login() {
        assertTrue(send("{\"id\":1,\"type\":\"auth\",\"payload\":{\"username\":\"admin\",\"password\":\"secret\"}}").ok());
    }

    private static void assertError(ErrorCode expected, Response response) {
        assertFalse(response.ok());
        assertNull(response.data());
        assertEquals(expected, response.error().code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"system.info", "metrics", "processes.list", "process.kill", "shell.exec", "nope"})
    void everythingButAuthPingByeRequiresLogin(String type) {
        Response response = send("{\"id\":5,\"type\":\"" + type + "\",\"payload\":{\"pid\":10,\"command\":\"ls\"}}");

        assertEquals(5, response.id());
        assertError(ErrorCode.UNAUTHENTICATED, response);
        assertEquals(List.of(), fakes.kills);
        assertEquals(List.of(), fakes.commands);
    }

    @Test
    void pingWorksBeforeLogin() {
        Response response = send("{\"id\":2,\"type\":\"ping\"}");

        assertTrue(response.ok());
        assertEquals(new RequestDispatcher.Pong(42), response.data());
    }

    @Test
    void byeAnswersAndRequestsClose() {
        Response response = send("{\"id\":3,\"type\":\"bye\"}");

        assertTrue(response.ok());
        assertEquals(Map.of(), response.data());
        assertTrue(session.closeRequested());
    }

    @Test
    void successfulAuthReturnsUserVersionAndSystem() {
        Response response = send("{\"id\":1,\"type\":\"auth\",\"payload\":{\"username\":\"admin\",\"password\":\"secret\"}}");

        RequestDispatcher.Login login = (RequestDispatcher.Login) response.data();
        assertEquals("admin", login.username());
        assertEquals(FakeServices.SYSTEM, login.system());
        assertEquals("admin", session.user().orElseThrow());
    }

    @Test
    void thirdFailedAuthClosesTheConnection() {
        String wrong = "{\"id\":1,\"type\":\"auth\",\"payload\":{\"username\":\"admin\",\"password\":\"bad\"}}";

        assertError(ErrorCode.AUTH_FAILED, send(wrong));
        assertError(ErrorCode.AUTH_FAILED, send(wrong));
        assertFalse(session.closeRequested());
        assertError(ErrorCode.AUTH_FAILED, send(wrong));
        assertTrue(session.closeRequested());
        assertTrue(session.user().isEmpty());
    }

    @Test
    void authWithMissingFieldsIsBadRequestAndNotCounted() {
        assertError(ErrorCode.BAD_REQUEST, send("{\"id\":1,\"type\":\"auth\",\"payload\":{\"username\":\"admin\"}}"));
        assertError(ErrorCode.BAD_REQUEST, send("{\"id\":1,\"type\":\"auth\"}"));
        assertError(ErrorCode.BAD_REQUEST, send("{\"id\":1,\"type\":\"auth\"}"));
        assertFalse(session.closeRequested());
    }

    @Test
    void returnsSystemInfoMetricsAndProcesses() {
        login();

        assertEquals(FakeServices.SYSTEM, send("{\"id\":2,\"type\":\"system.info\"}").data());
        assertEquals(FakeServices.METRICS, send("{\"id\":3,\"type\":\"metrics\"}").data());
        assertEquals(new RequestDispatcher.ProcessList(List.of(FakeServices.INIT)),
                send("{\"id\":4,\"type\":\"processes.list\"}").data());
    }

    @Test
    void unknownTypeAfterLoginIsBadRequest() {
        login();

        Response response = send("{\"id\":9,\"type\":\"format.disk\"}");

        assertEquals(9, response.id());
        assertError(ErrorCode.BAD_REQUEST, response);
    }

    @Test
    void killDefaultsToTermAndAcceptsKill() {
        login();

        assertTrue(send("{\"id\":2,\"type\":\"process.kill\",\"payload\":{\"pid\":4242}}").ok());
        assertTrue(send("{\"id\":3,\"type\":\"process.kill\",\"payload\":{\"pid\":4243,\"signal\":\"KILL\"}}").ok());

        assertEquals(List.of("4242:TERM", "4243:KILL"), fakes.kills);
    }

    @Test
    void killErrorsMapToProtocolCodes() {
        login();

        assertError(ErrorCode.NOT_FOUND, send("{\"id\":2,\"type\":\"process.kill\",\"payload\":{\"pid\":404}}"));
        assertError(ErrorCode.BAD_REQUEST,
                send("{\"id\":3,\"type\":\"process.kill\",\"payload\":{\"pid\":10,\"signal\":\"HUP\"}}"));
        assertError(ErrorCode.BAD_REQUEST, send("{\"id\":4,\"type\":\"process.kill\",\"payload\":{\"pid\":\"10\"}}"));
        Response internal = send("{\"id\":5,\"type\":\"process.kill\",\"payload\":{\"pid\":500}}");
        assertError(ErrorCode.INTERNAL, internal);
        assertEquals("Internal server error", internal.error().message());
    }

    @Test
    void shellExecUsesAndUpdatesSessionDirectory() {
        login();

        Response cd = send("{\"id\":2,\"type\":\"shell.exec\",\"payload\":{\"command\":\"cd /tmp\"}}");
        send("{\"id\":3,\"type\":\"shell.exec\",\"payload\":{\"command\":\"pwd\",\"timeoutSeconds\":5}}");

        assertEquals("/tmp", ((ExecutionResult) cd.data()).cwd());
        assertEquals(Path.of("/tmp"), session.cwd());
        assertEquals(List.of("cd /tmp|" + FakeServices.HOME + "|30", "pwd|" + Path.of("/tmp") + "|5"), fakes.commands);
    }

    @Test
    void shellExecClampsTimeoutAndValidatesCommand() {
        login();

        send("{\"id\":2,\"type\":\"shell.exec\",\"payload\":{\"command\":\"a\",\"timeoutSeconds\":9999}}");
        send("{\"id\":3,\"type\":\"shell.exec\",\"payload\":{\"command\":\"b\",\"timeoutSeconds\":0}}");
        assertError(ErrorCode.BAD_REQUEST, send("{\"id\":4,\"type\":\"shell.exec\",\"payload\":{\"command\":\"  \"}}"));
        assertError(ErrorCode.BAD_REQUEST,
                send("{\"id\":5,\"type\":\"shell.exec\",\"payload\":{\"command\":\"c\",\"timeoutSeconds\":\"x\"}}"));

        assertEquals(List.of("a|" + FakeServices.HOME + "|300", "b|" + FakeServices.HOME + "|1"), fakes.commands);
    }

    @Test
    void sequentialRoutingCoversLoginAndControl() {
        Request metrics = codec.decode("{\"id\":1,\"type\":\"metrics\"}");
        assertTrue(dispatcher.isSequential(session, metrics));

        login();

        assertFalse(dispatcher.isSequential(session, metrics));
        assertTrue(dispatcher.isSequential(session, codec.decode("{\"id\":2,\"type\":\"bye\"}")));
        assertTrue(dispatcher.isSequential(session, codec.decode("{\"id\":3,\"type\":\"auth\"}")));
    }
}
