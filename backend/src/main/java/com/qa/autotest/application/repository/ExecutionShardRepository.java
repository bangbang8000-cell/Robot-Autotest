package com.qa.autotest.application.repository;

import com.qa.autotest.domain.execution.ExecutionShard;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 运行分片仓储（W2）。
 *
 * <p>所有方法都带 {@code project_id} 条件——分片是 run 的子行，租户维度与 run 一致，
 * 实现里必须过 {@code TenantGuard.requireProjectScope}（平台级表才有豁免）。
 *
 * <p>并发模型：领取用「查候选 + 条件 UPDATE」的 CAS，回写必须带 {@code fencing_token}；
 * 令牌对不上返回 0 行，调用方据此判定「陈旧写入」（W3 故障注入依赖这一条）。
 */
public interface ExecutionShardRepository {

    /** 分片规划草稿：序号 + 本片套件路径。 */
    record ShardDraft(int shardIndex, List<String> suitePaths) {
    }

    /** 受影响的运行（故障转移后需要重算聚合的项目 + run）。 */
    record AffectedRun(long projectId, String runId) {
    }

    /** 幂等重建分片计划：先删掉该 run 的旧分片，再按草稿批量插入（一轮只规划一次）。 */
    int replacePlan(String runId, long projectId, List<ShardDraft> drafts, Long operatorId);

    List<ExecutionShard> listByRun(long projectId, String runId);

    Optional<ExecutionShard> find(long projectId, String runId, int shardIndex);

    /**
     * 抢下一个分片：候选 = PENDING，或 LEASED 且租约已过期，或 LOST 且 {@code attempt < maxAttempts}
     * （W3 重派）；抢到则 {@code fencing_token + 1}、{@code attempt + 1}、置 LEASED 并写租约。
     * 返回空表示本轮没有可领取的分片。
     */
    Optional<ExecutionShard> claimNext(long projectId, String runId, long nodeId,
                                       String leaseOwner, Instant now, Instant leaseUntil,
                                       int maxAttempts);

    /**
     * 回写分片终态：必须带当前 {@code fencingToken}，过期令牌返回 0 行。
     * {@code state} 只接受 DONE / FAILED。
     */
    int markDone(long projectId, String runId, int shardIndex, long fencingToken,
                 String state, int testCount, int failCount, Instant finishedAt);

    /** 把租约已过期仍停在 LEASED 的分片置 LOST，返回影响行数（主控聚合/重派前调用）。 */
    int expireLeases(long projectId, String runId, Instant now);

    /**
     * 故障转移：把落在指定节点上的 LEASED 分片立即置 LOST（节点判 OFFLINE 时调用），
     * 不等租约自然到期。返回受影响的运行，供调用方重算聚合。
     */
    List<AffectedRun> markLostForNodes(List<Long> nodeIds, Instant now);

    /**
     * 主控重启：上一进程留下的 LEASED 分片一律置 LOST（新进程不可能持有旧租约）。
     * 返回受影响的运行，供调用方重算聚合。
     */
    List<AffectedRun> markLostAllLeased(Instant now);

    /** 项目内分片按状态计数（W4 指标）；默认空实现便于测试假仓储不感知。 */
    default java.util.Map<String, Integer> countByState(long projectId) {
        return java.util.Map.of();
    }

    /** 项目内因重派多消耗的尝试次数（Σ attempt - 分片数），W4 指标。 */
    default int recoveredAttempts(long projectId) {
        return 0;
    }
}
