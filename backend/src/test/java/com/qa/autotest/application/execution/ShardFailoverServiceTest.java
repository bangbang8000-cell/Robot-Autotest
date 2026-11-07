package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.ExecutionNodeRepository;
import com.qa.autotest.domain.node.ExecutionNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 故障转移调度（W3）：只把 OFFLINE 节点交给分片侧收尾；没有离线节点时不写库。
 * 重启入口单独覆盖（ApplicationReadyEvent）。
 */
@ExtendWith(MockitoExtension.class)
class ShardFailoverServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T05:00:00Z");

    @Mock
    private ExecutionNodeRepository nodeRepository;
    @Mock
    private ShardService shards;

    @Test
    void sweepReleasesLeasesOnlyForOfflineNodes() {
        when(nodeRepository.list()).thenReturn(List.of(
                node(11L, ExecutionNode.ONLINE),
                node(12L, ExecutionNode.OFFLINE),
                node(13L, ExecutionNode.OFFLINE)));
        when(shards.releaseLeasesOfNodes(anyList())).thenReturn(1);

        new ShardFailoverService(nodeRepository, shards).sweepOfflineNodes();

        verify(shards).releaseLeasesOfNodes(List.of(12L, 13L));
    }

    @Test
    void sweepDoesNothingWhenAllNodesHealthy() {
        when(nodeRepository.list()).thenReturn(List.of(node(11L, ExecutionNode.ONLINE)));

        new ShardFailoverService(nodeRepository, shards).sweepOfflineNodes();

        verify(shards, never()).releaseLeasesOfNodes(anyList());
    }

    @Test
    void restartRecoveryDelegatesToShardService() {
        when(shards.releaseAllLeasesOnRestart()).thenReturn(2);

        new ShardFailoverService(nodeRepository, shards).recoverOnRestart();

        verify(shards).releaseAllLeasesOnRestart();
    }

    private static ExecutionNode node(long id, String status) {
        return new ExecutionNode(id, "node-" + id, "hash", status,
                true, false, 1, NOW, NOW, null);
    }
}
