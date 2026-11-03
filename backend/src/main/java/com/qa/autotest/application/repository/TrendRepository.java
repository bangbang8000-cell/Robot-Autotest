package com.qa.autotest.application.repository;

import com.qa.autotest.domain.report.PassRateCount;
import com.qa.autotest.domain.report.RunSnapshot;
import com.qa.autotest.domain.report.TrendPoint;
import java.util.List;
import java.util.Optional;

/**
 * 趋势与通过率只读仓储（RPT-04）。
 *
 * <p>所有查询都必须带 {@code project_id}（经 {@code run} 关联），禁止跨项目泄漏；
 * test_result 没有 project_id 列，统一 join run 取租户条件（与 RPT-02/03 口径一致）。
 */
public interface TrendRepository {

    /** 按 RUN_ID 取运行快照（含 DUT/LINK，用于确定当前环境）。 */
    Optional<RunSnapshot> findRun(long projectId, String runId);

    /** 当前运行里某条用例的趋势点；该运行没跑这条用例时返回空。 */
    Optional<TrendPoint> findCasePoint(long projectId, String runId, String caseKey);

    /**
     * 某条用例最近 N 次运行的趋势点，<b>按时间升序</b>返回。
     *
     * <p>不同环境（DUT+LINK 不同）的点一并返回，由调用方按 envKey 分组断开。
     */
    List<TrendPoint> listCasePoints(long projectId, String caseKey, int limit);

    /**
     * 同环境（DUT+LINK）+ 同用例的历史耗时（排除当前 run），按时间倒序取最近 limit 条。
     * 用于计算性能基线中位数，避免「最近 N 个趋势点被其他环境挤占」导致基线缺失。
     */
    List<Long> listCaseDurations(long projectId, String caseKey, String dut, String link,
                                 String excludeRunId, int limit);

    /**
     * 按天 / 周聚合通过率，返回最近 limit 个时间桶，<b>按时间升序</b>。
     *
     * @param granularity {@code day} 或 {@code week}
     */
    List<PassRateCount> listPassRate(long projectId, String granularity, int limit);
}
