package com.qa.autotest.application.execution;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 进程树终止：先礼后兵。先 destroy 子孙与自身，等 grace；仍在则 destroyForcibly。
 *
 * <p>只杀单进程会留下 pabot 派生的 robot 子进程占着设备，因此必须先取子孙快照。
 */
public final class ProcessTreeKiller {

    public KillOutcome kill(Process process, Duration grace) {
        if (process == null) {
            return new KillOutcome(true, false, 0);
        }
        long started = System.nanoTime();
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();

        boolean exited = waitFor(process, grace);
        boolean forced = false;
        if (!exited) {
            descendants.forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            waitFor(process, grace);
            forced = true;
        }
        long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        return new KillOutcome(!process.isAlive(), forced, waitedMs);
    }

    private boolean waitFor(Process process, Duration grace) {
        long millis = grace == null ? 0 : Math.max(0, grace.toMillis());
        try {
            return process.waitFor(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return !process.isAlive();
        }
    }

    public record KillOutcome(boolean terminated, boolean forced, long waitedMs) {
    }
}
