package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.ExecutionShardRepository;
import com.qa.autotest.application.repository.ExecutionShardRepository.AffectedRun;
import com.qa.autotest.domain.execution.ExecutionShard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内存版分片仓储（测试支持）。
 *
 * <p>只实现「服务层需要看到的 CAS 语义」：PENDING 或租约过期可抢、每次领取 fencing_token +1、
 * attempt +1、令牌不符的 markDone 返回 0 行、expireLeases 把过期 LEASED 判 LOST。
 * 真实 SQL 的锁行为不在这里模拟——那由 MySQL 集成步骤与迁移往返回归覆盖。
 *
 * <p>从 {@code ShardServiceTest} 的内联实现抽出来，供「2 节点等价」用例复用，
 * 避免两套假仓储语义漂移。
 */
final class FakeShardRepository implements ExecutionShardRepository {

    final Map<String, ExecutionShard> rows = new LinkedHashMap<>();
    private long nextId = 1;

    private static String key(String runId, int index) {
        return runId + "#" + index;
    }

    @Override
    public int replacePlan(String runId, long projectId, List<ShardDraft> drafts,
                           Long operatorId) {
        rows.keySet().removeIf(k -> k.startsWith(runId + "#"));
        for (ShardDraft draft : drafts) {
            rows.put(key(runId, draft.shardIndex()), new ExecutionShard(nextId++,
                    runId, projectId, null, draft.shardIndex(), List.copyOf(draft.suitePaths()),
                    ExecutionShard.PENDING, null, null, 0L, 0, 0, 0, null, null));
        }
        return drafts.size();
    }

    @Override
    public List<ExecutionShard> listByRun(long projectId, String runId) {
        return rows.values().stream()
                .filter(row -> row.projectId() == projectId && row.runId().equals(runId))
                .sorted(Comparator.comparingInt(ExecutionShard::shardIndex))
                .toList();
    }

    @Override
    public Optional<ExecutionShard> find(long projectId, String runId, int shardIndex) {
        ExecutionShard row = rows.get(key(runId, shardIndex));
        if (row == null || row.projectId() != projectId) {
            return Optional.empty();
        }
        return Optional.of(row);
    }

    @Override
    public Optional<ExecutionShard> claimNext(long projectId, String runId, long nodeId,
                                              String leaseOwner, Instant now,
                                              Instant leaseUntil, int maxAttempts) {
        List<ExecutionShard> candidates = new ArrayList<>(listByRun(projectId, runId));
        for (ExecutionShard row : candidates) {
            boolean claimable = ExecutionShard.PENDING.equals(row.state())
                    || (ExecutionShard.LEASED.equals(row.state())
                    && (row.leaseUntil() == null || row.leaseUntil().isBefore(now)))
                    || (ExecutionShard.LOST.equals(row.state()) && row.attempt() < maxAttempts);
            if (!claimable) {
                continue;
            }
            ExecutionShard claimed = new ExecutionShard(row.id(), row.runId(),
                    row.projectId(), nodeId, row.shardIndex(), row.suitePaths(),
                    ExecutionShard.LEASED, leaseOwner, leaseUntil,
                    row.fencingToken() + 1, row.attempt() + 1,
                    row.testCount(), row.failCount(),
                    row.startedAt() == null ? now : row.startedAt(), null);
            rows.put(key(runId, row.shardIndex()), claimed);
            return Optional.of(claimed);
        }
        return Optional.empty();
    }

    @Override
    public int markDone(long projectId, String runId, int shardIndex, long fencingToken,
                        String state, int testCount, int failCount, Instant finishedAt) {
        ExecutionShard row = rows.get(key(runId, shardIndex));
        if (row == null || row.projectId() != projectId
                || row.fencingToken() != fencingToken
                || !ExecutionShard.LEASED.equals(row.state())) {
            return 0;
        }
        rows.put(key(runId, shardIndex), new ExecutionShard(row.id(), row.runId(),
                row.projectId(), row.nodeId(), row.shardIndex(), row.suitePaths(), state,
                null, null, row.fencingToken(), row.attempt(), testCount, failCount,
                row.startedAt(), finishedAt));
        return 1;
    }

    @Override
    public int expireLeases(long projectId, String runId, Instant now) {
        int affected = 0;
        for (ExecutionShard row : listByRun(projectId, runId)) {
            if (ExecutionShard.LEASED.equals(row.state())
                    && row.leaseUntil() != null && row.leaseUntil().isBefore(now)) {
                rows.put(key(runId, row.shardIndex()), new ExecutionShard(row.id(),
                        row.runId(), row.projectId(), row.nodeId(), row.shardIndex(),
                        row.suitePaths(), ExecutionShard.LOST, null, null,
                        row.fencingToken(), row.attempt(), row.testCount(), row.failCount(),
                        row.startedAt(), now));
                affected++;
            }
        }
        return affected;
    }

    @Override
    public List<AffectedRun> markLostForNodes(List<Long> nodeIds, Instant now) {
        return markLost(nodeIds, now);
    }

    @Override
    public List<AffectedRun> markLostAllLeased(Instant now) {
        return markLost(null, now);
    }

    /** nodeIds = null 表示「所有 LEASED」；只改 LEASED，已终态的不动。 */
    private List<AffectedRun> markLost(List<Long> nodeIds, Instant now) {
        List<AffectedRun> affected = new ArrayList<>();
        for (Map.Entry<String, ExecutionShard> entry : new ArrayList<>(rows.entrySet())) {
            ExecutionShard row = entry.getValue();
            if (!ExecutionShard.LEASED.equals(row.state())) {
                continue;
            }
            if (nodeIds != null && (row.nodeId() == null || !nodeIds.contains(row.nodeId()))) {
                continue;
            }
            rows.put(entry.getKey(), new ExecutionShard(row.id(), row.runId(),
                    row.projectId(), row.nodeId(), row.shardIndex(), row.suitePaths(),
                    ExecutionShard.LOST, null, null, row.fencingToken(), row.attempt(),
                    row.testCount(), row.failCount(), row.startedAt(), now));
            AffectedRun run = new AffectedRun(row.projectId(), row.runId());
            if (!affected.contains(run)) {
                affected.add(run);
            }
        }
        return affected;
    }

    /** 直接改写 attempt（模拟「已经派发过 N 次」）。 */
    void forceAttempt(long projectId, String runId, int shardIndex, int attempt) {
        ExecutionShard row = rows.get(key(runId, shardIndex));
        rows.put(key(runId, shardIndex), new ExecutionShard(row.id(), row.runId(),
                row.projectId(), row.nodeId(), row.shardIndex(), row.suitePaths(),
                row.state(), row.leaseOwner(), row.leaseUntil(), row.fencingToken(), attempt,
                row.testCount(), row.failCount(), row.startedAt(), row.finishedAt()));
    }

    /** 把租约时间改到指定时刻（既可用于「已过期」，也可用于「刚从旧节点手里过期」）。 */
    void forceExpire(long projectId, String runId, int shardIndex, Instant at) {
        ExecutionShard row = rows.get(key(runId, shardIndex));
        rows.put(key(runId, shardIndex), new ExecutionShard(row.id(), row.runId(),
                row.projectId(), row.nodeId(), row.shardIndex(), row.suitePaths(),
                row.state(), row.leaseOwner(), at, row.fencingToken(), row.attempt(),
                row.testCount(), row.failCount(), row.startedAt(), row.finishedAt()));
    }
}
