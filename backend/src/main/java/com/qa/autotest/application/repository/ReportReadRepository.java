package com.qa.autotest.application.repository;

import com.qa.autotest.domain.report.CaseLogRef;
import com.qa.autotest.domain.report.CaseVerdict;
import com.qa.autotest.domain.report.KeywordRow;
import com.qa.autotest.domain.report.RunCaseRow;
import com.qa.autotest.domain.report.RunSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 报告域只读仓储（RPT-02 / RPT-03）。
 *
 * <p>与 RPT-01 的写入仓储 {@link ReportRepository} 分开，避免给已有实现（含测试伪造）增加无谓负担。
 * 所有查询都以 run_id（业务键）入口，内部 join run 表做 project_id 租户校验。
 */
public interface ReportReadRepository {

    /** 按 RUN_ID 取运行快照。 */
    Optional<RunSnapshot> findRun(long projectId, String runId);

    /**
     * 自动基线：同项目 + 同 DUT/LINK + started_at 早于本轮 + state=finished 中最近一条。
     *
     * @param before        本轮开始时间，为 null 时返回空
     * @param excludeRunId  本轮 RUN_ID（避免把自己当基线）
     */
    Optional<RunSnapshot> findBaseline(long projectId, String dut, String link,
                                       Instant before, String excludeRunId);

    /** 某次运行的全部用例结论（RPT-03 双列数据源）。 */
    List<CaseVerdict> listCaseVerdicts(long projectId, String runId);

    /** 某次运行的全部用例（RPT-02 套件树扁平来源）。 */
    List<RunCaseRow> listCases(long projectId, String runId);

    /** 某次运行的全部关键字（RPT-02 套件树步骤来源）。 */
    List<KeywordRow> listKeywords(long projectId, String runId);

    /** 按用例连接键取日志定位。 */
    Optional<CaseLogRef> findCaseLog(long projectId, String runId, String caseKey);
}
