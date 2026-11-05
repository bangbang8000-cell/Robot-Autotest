package com.qa.autotest.application.execution;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 租约等待登记（FR-08 等待态可视化）。
 *
 * <p>执行线程在申请设备租约前登记、拿到（或超时）后注销；期间接口
 * {@code /runs/{runId}/pending-activity} 返回 {@code waiting}，界面据此显示「等待资源」，
 * 而不是把「设备被占」误显示成「运行中」。注销时顺带给出实际等待毫秒数，由调用方回写
 * {@code run.lease_wait_ms}。
 *
 * <p>只做运行期展示，不落内存以外的状态：进程重启后登记自然丢失，接口退化为按
 * {@code run_status} 推断，不会影响执行本身。
 */
public class LeaseWaitRegistry {

    private final Map<String, Instant> waiting = new ConcurrentHashMap<>();
    private final Clock clock;

    public LeaseWaitRegistry(Clock clock) {
        this.clock = clock;
    }

    /** 开始等待租约。重复登记以最新一次为准。 */
    public void begin(String runId) {
        waiting.put(runId, clock.instant());
    }

    /** 结束等待并返回等待毫秒数；未登记过返回 0。 */
    public long end(String runId) {
        Instant start = waiting.remove(runId);
        return start == null ? 0 : Math.max(0, Duration.between(start, clock.instant()).toMillis());
    }

    /** 当前是否仍在等租约。 */
    public boolean waiting(String runId) {
        return waiting.containsKey(runId);
    }
}
