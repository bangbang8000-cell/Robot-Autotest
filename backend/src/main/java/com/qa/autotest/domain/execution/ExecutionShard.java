package com.qa.autotest.domain.execution;

import java.time.Instant;
import java.util.List;

/**
 * 运行分片（W2，FR-v2.2-01..05）。
 *
 * <p>一轮运行按套件切分成若干分片，每片可由不同执行节点领取并回写；
 * 分片不是消息队列里的一条消息，而是库里的**一行带租约的状态**——
 * 主控与 agent 共用同一个库，谁抢到靠 {@code PENDING + lease_until} 的 CAS，
 * 不引入 MQ（口径见规划文档 §4 W2）。
 *
 * <p>四态：
 * <ul>
 *   <li>{@link #PENDING}：可领取；</li>
 *   <li>{@link #LEASED}：已被某节点领取，{@code leaseUntil} 到期前不可抢；到期后
 *       可被重抢（{@code fencingToken} + 1，旧持有者晚到的回写被拒）；</li>
 *   <li>{@link #DONE}：执行完成且无失败；</li>
 *   <li>{@link #FAILED}：执行完成但有失败用例；</li>
 *   <li>{@link #LOST}：租约超时后由主控判失（W3 据此重派）。</li>
 * </ul>
 */
public record ExecutionShard(long id,
                             String runId,
                             long projectId,
                             Long nodeId,
                             int shardIndex,
                             List<String> suitePaths,
                             String state,
                             String leaseOwner,
                             Instant leaseUntil,
                             long fencingToken,
                             int attempt,
                             int testCount,
                             int failCount,
                             Instant startedAt,
                             Instant finishedAt) {

    public static final String PENDING = "PENDING";
    public static final String LEASED = "LEASED";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";
    public static final String LOST = "LOST";

    public boolean terminal() {
        return DONE.equals(state) || FAILED.equals(state) || LOST.equals(state);
    }

    /** 一片的通过数 = 用例数 - 失败数（分片没有「跳过」口径，缺省按失败计）。 */
    public int passedCount() {
        return Math.max(0, testCount - failCount);
    }
}
