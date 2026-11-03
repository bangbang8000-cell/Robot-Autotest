package com.qa.autotest.application.repository;

import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.report.GateResult;
import com.qa.autotest.domain.report.ParsedReport;
import com.qa.autotest.domain.report.ReportRecord;
import java.time.Instant;
import java.util.Optional;

/**
 * 报告入库与查询仓储（RPT-01）。
 */
public interface ReportRepository {

    /**
     * 用一次解析结果覆盖该运行的报告数据（幂等：先删旧的 test_result / keyword_result 再插入）。
     *
     * @param projectId  项目（用于 run 表租户校验）
     * @param runDbId    run.id（test_result / keyword_result 的 run_id 外键指向它）
     * @param parsed     解析结果
     * @param gate       metadata 门禁结果
     * @param startedAt  metadata.STARTED_AT，为 null 时保留 run 原值
     */
    void replace(long projectId, long runDbId, ParsedReport parsed, GateResult gate, Instant startedAt);

    /** 按 RUN_ID 查报告视图。 */
    Optional<ReportRecord> find(long projectId, String runId);

    /** 分页查询项目下的报告视图。 */
    PageResult<ReportRecord> page(long projectId, int page, int size);
}
