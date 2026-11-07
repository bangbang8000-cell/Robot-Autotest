package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.ExecutionNodeRepository;
import com.qa.autotest.domain.node.ExecutionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分片故障转移守护（W3，FR-v2.2.1-01/03）。
 *
 * <p>两个触发点：
 * <ol>
 *   <li><b>节点判 OFFLINE 后</b>：按固定周期扫描 OFFLINE 节点，把它们名下的 LEASED
 *       分片立即判 LOST（不等租约自然到期），另一节点可马上重派（attempt + 1）；</li>
 *   <li><b>主控重启后</b>：新进程不可能持有旧租约，遗留 LEASED 一律判 LOST，
 *       重算受影响 run 的聚合，避免看板长期停在「租约中」。</li>
 * </ol>
 *
 * <p>扫描周期默认 15s；阈值与节点心跳口径一致（3 周期判离线由
 * {@code NodeHeartbeatMonitor} 负责，这里只做分片侧收尾）。
 */
@Service
public class ShardFailoverService {

    private static final Logger LOG = LoggerFactory.getLogger(ShardFailoverService.class);

    private final ExecutionNodeRepository nodeRepository;
    private final ShardService shards;

    public ShardFailoverService(ExecutionNodeRepository nodeRepository, ShardService shards) {
        this.nodeRepository = nodeRepository;
        this.shards = shards;
    }

    /** 主控重启：清掉上一进程的遗留租约（幂等，无遗留时不写库）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnRestart() {
        try {
            int affected = shards.releaseAllLeasesOnRestart();
            if (affected > 0) {
                LOG.warn("重启恢复完成：{} 个 run 的遗留分片租约已判 LOST", affected);
            }
        } catch (RuntimeException e) {
            // 恢复失败不应阻断服务启动（例如库暂不可用、租户守卫拦下脏 SQL）。
            LOG.error("重启恢复分片失败，服务继续启动（租约到期后仍会被自然回收）", e);
        }
    }

    /** 周期扫描：OFFLINE 节点名下的租约立即判 LOST，让分片可以被别的节点重派。 */
    @Scheduled(fixedDelayString = "${shard.failover-sweep-ms:15000}",
            initialDelayString = "${shard.failover-initial-ms:25000}")
    public void sweepOfflineNodes() {
        List<Long> offline = nodeRepository.list().stream()
                .filter(node -> ExecutionNode.OFFLINE.equals(node.status()))
                .map(ExecutionNode::id)
                .toList();
        if (offline.isEmpty()) {
            return;
        }
        int affected = shards.releaseLeasesOfNodes(offline);
        if (affected > 0) {
            LOG.warn("故障转移：OFFLINE 节点 {} 的 {} 个 run 分片租约已判 LOST", offline, affected);
        }
    }
}
