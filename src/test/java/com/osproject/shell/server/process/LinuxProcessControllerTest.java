package com.osproject.shell.server.process;

import com.osproject.shell.server.process.ProcessController.Signal;
import com.osproject.shell.server.protocol.ErrorCode;
import com.osproject.shell.server.protocol.RequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
class LinuxProcessControllerTest {

    private final LinuxProcessController controller = new LinuxProcessController();

    @Test
    void refusesInitAndBelow() {
        for (long pid : new long[] {1, 0, -5}) {
            RequestException e = assertThrows(RequestException.class, () -> controller.kill(pid, Signal.KILL));
            assertEquals(ErrorCode.BAD_REQUEST, e.code());
        }
    }

    @Test
    void refusesOwnPid() {
        RequestException e = assertThrows(RequestException.class,
                () -> controller.kill(ProcessHandle.current().pid(), Signal.TERM));

        assertEquals(ErrorCode.BAD_REQUEST, e.code());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void unknownPidIsNotFound() {
        RequestException e = assertThrows(RequestException.class, () -> controller.kill(4_194_000, Signal.TERM));

        assertEquals(ErrorCode.NOT_FOUND, e.code());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void terminatesAndKillsProcesses() throws Exception {
        Process term = new ProcessBuilder("sleep", "60").start();
        Process kill = new ProcessBuilder("bash", "-c", "trap '' TERM; sleep 60").start();

        controller.kill(term.pid(), Signal.TERM);
        controller.kill(kill.pid(), Signal.KILL);

        assertTrue(term.waitFor(10, TimeUnit.SECONDS));
        assertEquals(128 + 15, term.exitValue());
        assertTrue(kill.waitFor(10, TimeUnit.SECONDS));
        assertEquals(128 + 9, kill.exitValue());
    }
}
