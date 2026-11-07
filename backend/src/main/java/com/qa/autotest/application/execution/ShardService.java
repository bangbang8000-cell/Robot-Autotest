package com.qa.autotest.application.execution;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.node.ExecutionNodeService;
import com.qa.autotest.application.repository.ExecutionShardRepository;
import com.qa.autotest.application.repository.RunRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.ExecutionShard;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.node.ExecutionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 运行分片服务（W2，FR-v2.2-01..05）：规划 / 领取 / 回写 / 聚合。
 *
 * <p>与 v2.1 的关系：不分片时（套件 &lt; 2 或节点 &lt; 2）由 {@link ShardPolicy}
 * 退化为 1 片，本服务仍可用；但**默认执行链路不经过本服务**——本地 runner 照旧整轮跑，
 * 分片是显式规划（{@code POST /runs/{id}/shards/plan}）或节点 agent 主动领取时才启用。
 *
 * <p>聚合口径：全部分片进终态后求和回写 run；任一 FAILED / LOST → run failed，
 * 否则 passed。run 已终态时不重复跃迁（幂等），只回读视图。
 */
@Service
public class ShardService {

    private static final Logger LOG = LoggerFactory.getLogger(ShardService.class);

    /** 租约时长下限 / 上限（秒）：太短会误判失，太长故障恢复慢。 */
    private static final int MIN_LEASE_SECONDS = 30;
    private static final int MAX_LEASE_SECONDS = 3600;

    private final ExecutionShardRepository shards;
    private final RunRepository runs;
    private final RunService runService;
    private final ExecutionNodeService nodes;
    private final AccessControlService access;
    private final AuditService audit;
    private final Clock clock;
    /** 同一分片最多派发次数（W3）：LOST 且 attempt 用尽的不再重派，按失败收口。 */
    private final int maxAttempts;

    public ShardService(ExecutionShardRepository shards, RunRepository runs,
                        RunService runService, ExecutionNodeService nodes,
                        AccessControlService access, AuditService audit, Clock clock,
                        @Value("${shard.max-attempts:3}") int maxAttempts) {
        this.shards = shards;
        this.runs = runs;
        this.runService = runService;
        this.nodes = nodes;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    /** 分片视图（前端分片看板直接消费）。 */
    public record ShardView(int shardIndex, String state, long nodeId, String leaseOwner,
                            String leaseUntil, long fencingToken, int attempt,
                            int testCount, int failCount, List<String> suitePaths,
                            String startedAt, String finishedAt) {
    }

    /** 一轮运行的分片总览。 */
    public record RunShardsView(String runId, List<ShardView> shards, int totalShards,
                                int terminalShards, int testCount, int failCount,
                                boolean complete, String aggregateState) {
    }

    /** agent 领取结果；无分片可领时返回 null。 */
    public record ClaimResult(int shardIndex, List<String> suitePaths, long fencingToken,
                              int attempt, String leaseUntil) {
    }

    // ---------------------------------------------------------------- 规划（平台侧）

    /** 规划分片：幂等重建（同 run 重复调用以最后一次为准），需 EXECUTION:EXECUTE。 */
    @Transactional
    public RunShardsView plan(long projectId, String runId, List<String> suitePaths,
                              Integer nodeCount, Long operatorId) {
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.EXECUTE);
        RunRecord run = runs.findByRunId(projectId, runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "运行不存在: " + runId));
        List<ExecutionShardRepository.ShardDraft> drafts = ShardPolicy.plan(
                suitePaths, nodeCount == null ? ShardPolicy.MAX_NODES : nodeCount,
                ShardPolicy.MAX_NODES);
        shards.replacePlan(runId, projectId, drafts, operatorId);
        audit.record("SHARD_PLAN", "run", runId, "shards=" + drafts.size());
        LOG.info("run {} 规划 {} 个分片（nodeCount={}）", runId, drafts.size(), nodeCount);
        return view(projectId, runId);
    }

    /** 只读视图（EXECUTION:READ）。 */
    public RunShardsView view(long projectId, String runId) {
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);
        return viewInternal(projectId, runId);
    }

    private RunShardsView viewInternal(long projectId, String runId) {
        List<ExecutionShard> rows = shards.listByRun(projectId, runId);
        List<ShardView> views = rows.stream().map(ShardService::toView).toList();
        int terminal = (int) rows.stream().filter(ExecutionShard::terminal).count();
        int testCount = rows.stream().mapToInt(ExecutionShard::testCount).sum();
        int failCount = rows.stream().mapToInt(ExecutionShard::failCount).sum();
        String aggregate = aggregateState(rows);
        return new RunShardsView(runId, views, rows.size(), terminal, testCount, failCount,
                !rows.isEmpty() && settled(rows), aggregate);
    }

    /**
     * 聚合口径（W3 起区分「可重派」）：
     * <ul>
     *   <li>有分片未进终态 → IN_PROGRESS；</li>
     *   <li>全终态，但存在 LOST 且 attempt 未用尽的分片能重派 → RETRYING（不算做完，先不跃迁）；</li>
     *   <li>全终态且无重派可能 → FAILED（有 FAILED/LOST）或 DONE。</li>
     * </ul>
     */
    private String aggregateState(List<ExecutionShard> rows) {
        if (rows.isEmpty() || rows.stream().anyMatch(row -> !row.terminal())) {
            return "IN_PROGRESS";
        }
        if (retryPending(rows)) {
            return "RETRYING";
        }
        boolean anyFailure = rows.stream().anyMatch(row ->
                ExecutionShard.FAILED.equals(row.state()) || ExecutionShard.LOST.equals(row.state()));
        return anyFailure ? "FAILED" : "DONE";
    }

    /** 是否还有 LOST 分片能被重派（attempt 未用尽）。 */
    private boolean retryPending(List<ExecutionShard> rows) {
        return rows.stream().anyMatch(row -> ExecutionShard.LOST.equals(row.state())
                && row.attempt() < maxAttempts);
    }

    private boolean settled(List<ExecutionShard> rows) {
        return rows.stream().allMatch(ExecutionShard::terminal) && !retryPending(rows);
    }

    // ---------------------------------------------------------------- agent 侧

    /**
     * 领取下一个分片：验节点令牌 → 短路租约 → CAS 抢片。
     * 节点停用 / 令牌错一律拒绝（与心跳同一鉴权口径）。
     */
    @Transactional
    public ClaimResult claim(long nodeId, String token, long projectId, String runId,
                             int leaseSeconds) {
        ExecutionNode node = nodes.authenticate(nodeId, token);
        int lease = Math.max(MIN_LEASE_SECONDS, Math.min(leaseSeconds, MAX_LEASE_SECONDS));
        Instant now = clock.instant();
        Instant until = now.plusSeconds(lease);
        return asProject(projectId, "shard-claim", () -> {
            ExecutionShard claimed = shards
                    .claimNext(projectId, runId, node.id(), node.name(), now, until, maxAttempts)
                    .orElse(null);
            if (claimed == null) {
                return null;
            }
            LOG.info("节点 {} 领取 run {} 分片 {}（attempt={}，fencing={}）",
                    node.name(), runId, claimed.shardIndex(), claimed.attempt(),
                    claimed.fencingToken());
            return new ClaimResult(claimed.shardIndex(), claimed.suitePaths(),
                    claimed.fencingToken(), claimed.attempt(),
                    claimed.leaseUntil() == null ? null : claimed.leaseUntil().toString());
        });
    }

    /**
     * 回写分片结果：必须带领取时拿到的 {@code fencingToken}。
     * 守卫顺序 = 验节点 → 查分片归属 → CAS 回写（令牌/租约不符 0 行 → 409）。
     * 回写成功后尝试聚合；聚合失败不回滚分片结果（结果比汇总重要）。
     */
    @Transactional
    public RunShardsView complete(long nodeId, String token, long projectId, String runId,
                                  int shardIndex, long fencingToken, boolean success,
                                  int testCount, int failCount) {
        ExecutionNode node = nodes.authenticate(nodeId, token);
        return asProject(projectId, "shard-complete", () -> {
            ExecutionShard current = shards.find(projectId, runId, shardIndex)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "分片不存在: " + runId + "#" + shardIndex));
            if (current.nodeId() == null || current.nodeId() != node.id()) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "分片不属于节点 " + node.name());
            }
            if (!ExecutionShard.LEASED.equals(current.state())) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "分片不在租约中: " + current.state());
            }
            int affected = shards.markDone(projectId, runId, shardIndex, fencingToken,
                    success ? ExecutionShard.DONE : ExecutionShard.FAILED,
                    Math.max(0, testCount), Math.max(0, failCount), clock.instant());
            if (affected != 1) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "分片租约已被重派，本次回写被拒（fencing_token 过期）");
            }
            aggregateInternal(projectId, runId);
            return viewInternal(projectId, runId);
        });
    }

    /** 主控侧聚合入口（W3 重派前也用它把过期租约判失再算总数）。 */
    @Transactional
    public RunShardsView aggregate(long projectId, String runId) {
        return asProject(projectId, "shard-aggregate", () -> {
            aggregateInternal(projectId, runId);
            return viewInternal(projectId, runId);
        });
    }

    /**
     * 故障转移（W3）：节点判 OFFLINE 后，把它名下的 LEASED 分片立即置 LOST，
     * 不等租约自然到期；受影响的 run 逐个重算聚合（此时仍可重派 → RETRYING）。
     *
     * @return 受影响的分片数（供日志与测试断言）
     */
    @Transactional
    public int releaseLeasesOfNodes(List<Long> nodeIds) {
        List<ExecutionShardRepository.AffectedRun> affected =
                shards.markLostForNodes(nodeIds, clock.instant());
        if (affected.isEmpty()) {
            return 0;
        }
        aggregateAffected(affected);
        LOG.warn("故障转移：节点 {} 的租约判 LOST，涉及 {} 个 run", nodeIds, affected.size());
        return affected.size();
    }

    /** 主控重启恢复（W3）：上一进程的 LEASED 一律判 LOST，重算受影响的 run。 */
    @Transactional
    public int releaseAllLeasesOnRestart() {
        List<ExecutionShardRepository.AffectedRun> affected =
                shards.markLostAllLeased(clock.instant());
        if (affected.isEmpty()) {
            return 0;
        }
        aggregateAffected(affected);
        LOG.warn("重启恢复：{} 个 run 的遗留租约判 LOST", affected.size());
        return affected.size();
    }

    private void aggregateAffected(List<ExecutionShardRepository.AffectedRun> affected) {
        affected.stream().distinct()
                .forEach(run -> aggregate(run.projectId(), run.runId()));
    }

    /**
     * 聚合：先把过期租约判 LOST → 全终态才回写 run。
     * 只对 RUNNING 的 run 跃迁，其余状态只更新计数（避免与既有执行链路抢状态机）。
     */
    private void aggregateInternal(long projectId, String runId) {
        shards.expireLeases(projectId, runId, clock.instant());
        List<ExecutionShard> rows = shards.listByRun(projectId, runId);
        if (rows.isEmpty() || !settled(rows)) {
            // 未进终态或还有可重派的分片：先不跃迁，等重派结果回来再收口
            return;
        }
        int total = rows.stream().mapToInt(ExecutionShard::testCount).sum();
        int failed = rows.stream().mapToInt(ExecutionShard::failCount).sum();
        int passed = Math.max(0, total - failed);
        RunRecord run = runs.findByRunId(projectId, runId).orElse(null);
        if (run == null) {
            return;
        }
        // updateCounts 的 artifacts_dir 传当前值：JDBC 实现是直接覆盖，传 null 会把产物目录清掉
        runService.updateCounts(runId, total, passed, failed, run.artifactsDir());
        if (run.state() != RunState.RUNNING) {
            return;
        }
        boolean anyFailure = rows.stream().anyMatch(row ->
                ExecutionShard.FAILED.equals(row.state()) || ExecutionShard.LOST.equals(row.state()));
        if (anyFailure) {
            int lost = (int) rows.stream()
                    .filter(row -> ExecutionShard.LOST.equals(row.state())).count();
            runService.transition(runId, RunState.FAILED, new RunService.TransitionContext(
                    null, null, "ENV", "分片未全部完成",
                    lost > 0 ? "有 " + lost + " 个分片租约超时未回写" : "有分片执行失败",
                    false, true));
        } else {
            runService.transition(runId, RunState.FINISHED, RunService.TransitionContext.plain());
        }
    }

    private static ShardView toView(ExecutionShard row) {
        return new ShardView(row.shardIndex(), row.state(),
                row.nodeId() == null ? 0 : row.nodeId(), row.leaseOwner(),
                row.leaseUntil() == null ? null : row.leaseUntil().toString(),
                row.fencingToken(), row.attempt(), row.testCount(), row.failCount(),
                row.suitePaths(),
                row.startedAt() == null ? null : row.startedAt().toString(),
                row.finishedAt() == null ? null : row.finishedAt().toString());
    }

    /**
     * agent 侧没有用户会话：显式补项目上下文后执行（{@link TenantContext#runAs} 只收
     * {@link Runnable}，这里用一格数组把返回值带出来）。
     */
    private static <T> T asProject(long projectId, String actor,
                                   java.util.function.Supplier<T> supplier) {
        Object[] holder = new Object[1];
        TenantContext.runAs(null, actor, projectId, () -> holder[0] = supplier.get());
        @SuppressWarnings("unchecked")
        T value = (T) holder[0];
        return value;
    }
}
