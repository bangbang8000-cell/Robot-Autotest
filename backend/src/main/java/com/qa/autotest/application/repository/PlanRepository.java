package com.qa.autotest.application.repository;

import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.plan.PlanLastRun;
import com.qa.autotest.domain.plan.PlanRecord;
import com.qa.autotest.domain.plan.PlanStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 执行计划仓储（PLN-01 / PLN-02）。所有查询都带 project_id 条件（TenantGuard 口径）。
 *
 * <p>调度相关内容：
 * <ul>
 *   <li>{@link #lockDue(Instant, int)} 必须在事务内调用，用 {@code FOR UPDATE SKIP LOCKED}
 *       领取到点计划，防止多实例重复触发；</li>
 *   <li>{@link #hasActiveRun} / {@link #countActiveScheduledRuns} 支撑重叠与并发上限判定。</li>
 * </ul>
 */
public interface PlanRepository {

    PageResult<PlanRecord> page(long projectId, int page, int size);

    Optional<PlanRecord> find(long projectId, long id);

    long insert(PlanRecord draft, Long operatorId);

    void update(PlanRecord plan, Long operatorId);

    void delete(long projectId, long id);

    int updateStatus(long projectId, long id, PlanStatus status);

    int updateSchedule(long projectId, long id, Instant lastFireAt, Instant nextFireAt);

    List<PlanRecord> lockDue(Instant now, int limit);

    boolean hasActiveRun(long projectId, long planId);

    int countActiveScheduledRuns(long projectId);

    List<Long> dependencies(long projectId, long planId);

    Map<Long, List<Long>> allDependencies(long projectId);

    void replaceDependencies(long projectId, long planId, List<Long> dependsOn, Long operatorId);

    void deleteDependencies(long projectId, long planId);

    /** 删除计划时清掉两个方向的边（plan_id 与 depends_on_plan_id 都命中），避免外键阻塞删除。 */
    void deleteAllEdges(long projectId, long planId);

    /** 最近一次由该计划触发的运行（列表展示「最近结果」）。 */
    Optional<PlanLastRun> latestRun(long projectId, long planId);

    /**
     * 每个计划的历史运行时长 P50（秒），键为计划 id；无历史运行的计划不出现在结果里。
     *
     * <p>N8：依赖图上的「预计时长」不再只拿超时上限当权重——超时是**熔断值**，不是预期耗时。
     * 只用 passed/failed 的 finished 运行，取消/超时（无 finished_at）不计入样本。
     * 中位数口径：样本数为偶数时取中间两个的均值（MySQL 窗口函数里已算好）。
     */
    Map<Long, Long> medianDurationSeconds(long projectId);
}
