package com.qa.autotest.application.execution;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 进程树终止：Windows 上起一个长时间 ping，kill 后必须确实退出。 */
class ProcessTreeKillerTest {

    @Test
    void 终止运行中的进程树() throws Exception {
        Process process = new ProcessBuilder("cmd", "/c", "ping -n 60 127.0.0.1").start();
        assertTrue(process.isAlive());

        ProcessTreeKiller.KillOutcome outcome =
                new ProcessTreeKiller().kill(process, Duration.ofSeconds(3));

        assertTrue(outcome.terminated());
        assertFalse(process.isAlive());
    }

    @Test
    void 空进程直接返回已终止() {
        ProcessTreeKiller.KillOutcome outcome =
                new ProcessTreeKiller().kill(null, Duration.ofSeconds(1));
        assertTrue(outcome.terminated());
        assertFalse(outcome.forced());
    }
}
