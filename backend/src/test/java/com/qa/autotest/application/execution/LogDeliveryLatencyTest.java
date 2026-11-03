package com.qa.autotest.application.execution;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实时日志延迟采样（EXE-04 证据）。
 *
 * <p>本机采样：按 100ms 周期 flush，统计「publish 时间戳 → 送达订阅者」的耗时分布。
 * 数字仅代表本机单元测试环境，不代表 149 生产环境；证据文件里会写明。
 */
class LogDeliveryLatencyTest {

    @Test
    void 推送延迟采样并打印分位数() throws Exception {
        RunLogService logs = new RunLogService(Clock.systemDefaultZone());
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        logs.subscribe("RUN-L", batch ->
                batch.forEach(line -> latencies.add(Duration.between(line.tsGen(), Instant.now()).toMillis())));

        for (int cycle = 0; cycle < 5; cycle++) {
            for (int i = 0; i < 8; i++) {
                logs.publish("RUN-L", "pabot", "cycle-" + cycle + "-line-" + i);
            }
            // 模拟调度器的 100ms 周期：先等一个周期再 flush，测的是最坏等待而非即时送达。
            Thread.sleep(100);
            logs.flush();
        }
        logs.complete("RUN-L");

        assertTrue(latencies.size() >= 40, "采样点不足: " + latencies.size());
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        System.out.println("[log-latency] samples=" + sorted.size()
                + " p50=" + percentile(sorted, 50)
                + " p95=" + percentile(sorted, 95)
                + " p99=" + percentile(sorted, 99)
                + " max=" + sorted.get(sorted.size() - 1) + " ms");
    }

    private static long percentile(List<Long> sorted, int percent) {
        int index = (int) Math.ceil(percent / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
