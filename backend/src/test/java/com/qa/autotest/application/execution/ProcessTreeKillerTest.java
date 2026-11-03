package com.qa.autotest.application.execution;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 进程树终止：起一个长时间运行的外部进程，kill 后必须确实退出。
 *
 * <p>原先硬编码 {@code cmd /c ping}，在 Linux 上直接 {@code Cannot run program "cmd"} —— 而
 * {@link ProcessTreeKiller} 本身用 {@code ProcessHandle} 实现、完全跨平台，Linux 又是部署平台。
 * 所以这里是**测试不可移植**，不是产品缺陷；按平台选长跑命令，两侧都真跑。
 */
class ProcessTreeKillerTest {

    @Test
    void 终止运行中的进程树() throws Exception {
        Process process = new ProcessBuilder(longRunning()).start();
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

    private static List<String> longRunning() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                .startsWith("windows");
        return windows
                ? List.of("cmd", "/c", "ping -n 60 127.0.0.1")
                : List.of("sh", "-c", "sleep 60");
    }
}
