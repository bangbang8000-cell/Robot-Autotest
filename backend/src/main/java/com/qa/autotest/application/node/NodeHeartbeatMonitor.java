package com.qa.autotest.application.node;

import com.qa.autotest.application.repository.ExecutionNodeRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 节点离线监视器（NODX-01）。
 *
 * <p>口径：心跳周期 {@code node.heartbeat-interval-seconds}（默认 30s），
 * **连续 3 个周期**没有心跳判离线——单次网络抖动不该把节点打掉，
 * 3 个周期（默认 90s）是「agent 真挂了」的最低置信度。
 *
 * <p>只把 ONLINE 置 OFFLINE：NEW（从没心跳过）保持 NEW——那是「注册了还没部署 agent」，
 * 和「agent 挂了」是两回事，界面要能区分（NODX-04 的四态之一）。
 */
@Component
public class NodeHeartbeatMonitor {

    private static final Logger log = LoggerFactory.getLogger(NodeHeartbeatMonitor.class);

    private final ExecutionNodeRepository nodes;
    private final Clock clock;
    private final int heartbeatIntervalSeconds;
    private final int missedLimit;

    public NodeHeartbeatMonitor(ExecutionNodeRepository nodes, Clock clock,
                                @Value("${node.heartbeat-interval-seconds:30}")
                                int heartbeatIntervalSeconds,
                                @Value("${node.offline-after-misses:3}") int missedLimit) {
        this.nodes = nodes;
        this.clock = clock;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.missedLimit = missedLimit;
    }

    /** 每 15 秒扫一次；离线阈值 90s，扫描间隔远小于阈值，不会漏判。 */
    @Scheduled(fixedDelay = 15_000, initialDelay = 20_000)
    public void sweep() {
        Instant staleBefore = clock.instant()
                .minusSeconds((long) heartbeatIntervalSeconds * missedLimit);
        int marked = nodes.markStaleOffline(staleBefore);
        if (marked > 0) {
            // 单行日志说明判了几个离线；不带节点令牌相关任何信息
            log.info("node-monitor: marked OFFLINE: {}", marked);
        }
    }
}
