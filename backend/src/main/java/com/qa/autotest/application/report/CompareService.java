package com.qa.autotest.application.report;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.ReportReadRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.report.CaseVerdict;
import com.qa.autotest.domain.report.RunSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * 双列并置对比（RPT-03），严格遵循 DEV-PLAN §4.2 契约。
 *
 * <p>三条硬规则：
 * <ol>
 *   <li>自动基线 = 同项目 + 同 DUT/LINK + 早于本轮 + state=finished 中最近一次；找不到返回 null，不报错。</li>
 *   <li>手动 baselineRunId 必须同项目同环境，否则 400 并给中文原因，<b>绝不静默换成自动基线</b>。</li>
 *   <li>每一侧的 verdict 在「该运行没有执行这条用例」时必须是 {@code null}，
 *       禁止用另一侧的值填充。</li>
 * </ol>
 */
@Service
public class CompareService {

    /** 单侧结论；该侧未执行时为 null。 */
    public record CompareSide(String verdict, long durationMs) {
    }

    /** 一行对比结果。history/current 允许为 null，且互不兜底。 */
    public record CompareRow(String caseKey, CompareSide history, CompareSide current, String change) {
    }

    /** 汇总计数。 */
    public record CompareSummary(int newFail, int newPass, int bothFail, int notRun) {
    }

    /** 基线信息；无基线时为 null。 */
    public record CompareBaseline(String runId, Instant startedAt) {
    }

    /**
     * 对比结果。
     *
     * @param baseline 本次使用的基线（null 表示没有可用基线）
     * @param rows     按 caseKey 排序的并集
     * @param summary  四类计数
     */
    public record CompareResult(CompareBaseline baseline, List<CompareRow> rows, CompareSummary summary) {
    }

    static final String CHANGE_PASS_TO_FAIL = "PASS_TO_FAIL";
    static final String CHANGE_FAIL_TO_PASS = "FAIL_TO_PASS";
    static final String CHANGE_SAME = "SAME";
    static final String CHANGE_NOT_RUN = "NOT_RUN";

    private final ReportReadRepository read;
    private final AccessControlService access;

    public CompareService(ReportReadRepository read, AccessControlService access) {
        this.read = read;
        this.access = access;
    }

    /**
     * 手动基线候选（RPT-03 补口）：同项目同环境的 finished 运行，按开始时间倒序。
     *
     * @param startedAt 可能为 null（run 未记录开始时间），前端按「未知时间」渲染
     */
    public record BaselineCandidate(String runId, Instant startedAt) {
    }

    /** 候选上限：下拉框只需要近期的几条，不把项目全部历史拉进页面。 */
    static final int MAX_BASELINE_CANDIDATES = 20;

    public CompareResult compare(long projectId, String runId, String baselineRunId) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 非法");
        }
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "runId 不能为空");
        }
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);

        RunSnapshot current = read.findRun(projectId, runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "运行不存在: " + runId));
        Optional<RunSnapshot> baseline = resolveBaseline(projectId, current, baselineRunId);

        Map<String, CaseVerdict> currentCases = index(read.listCaseVerdicts(projectId, runId));
        Map<String, CaseVerdict> historyCases = baseline
                .map(run -> index(read.listCaseVerdicts(projectId, run.runId())))
                .orElse(Map.of());

        TreeSet<String> caseKeys = new TreeSet<>();
        caseKeys.addAll(currentCases.keySet());
        caseKeys.addAll(historyCases.keySet());

        int newFail = 0;
        int newPass = 0;
        int bothFail = 0;
        int notRun = 0;
        List<CompareRow> rows = new ArrayList<>(caseKeys.size());
        for (String caseKey : caseKeys) {
            CaseVerdict history = historyCases.get(caseKey);
            CaseVerdict currentCase = currentCases.get(caseKey);
            String change = changeOf(history, currentCase);
            if (currentCase == null) {
                notRun++;
            } else if ("FAIL".equals(currentCase.verdict())) {
                if (history != null && "FAIL".equals(history.verdict())) {
                    bothFail++;
                } else {
                    newFail++;
                }
            } else if (history != null && "FAIL".equals(history.verdict())
                    && "PASS".equals(currentCase.verdict())) {
                newPass++;
            }
            rows.add(new CompareRow(caseKey,
                    history == null ? null : new CompareSide(history.verdict(), history.durationMs()),
                    currentCase == null ? null : new CompareSide(currentCase.verdict(), currentCase.durationMs()),
                    change));
        }
        CompareBaseline baselineView = baseline
                .map(run -> new CompareBaseline(run.runId(), run.startedAt()))
                .orElse(null);
        return new CompareResult(baselineView, rows, new CompareSummary(newFail, newPass, bothFail, notRun));
    }

    /**
     * 手动基线下拉的数据源（RPT-03 补口）。
     *
     * <p>只返回与本轮同项目同环境的 finished 运行：跨项目/跨环境的运行在前端就不该出现，
     * 否则用户选完只会收到一条 400。空环境（DUT/LINK 都为 null）不做特殊放行 ——
     * 与 {@link #resolveBaseline} 的校验口径保持一致。
     */
    public List<BaselineCandidate> candidates(long projectId, String runId) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 非法");
        }
        if (runId == null || runId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "runId 不能为空");
        }
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);

        RunSnapshot current = read.findRun(projectId, runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "运行不存在: " + runId));
        return read.listBaselineCandidates(projectId, current.dut(), current.link(), current.runId(),
                        MAX_BASELINE_CANDIDATES)
                .stream()
                .map(run -> new BaselineCandidate(run.runId(), run.startedAt()))
                .toList();
    }

    private Optional<RunSnapshot> resolveBaseline(long projectId, RunSnapshot current, String baselineRunId) {
        if (baselineRunId != null && !baselineRunId.isBlank()) {
            RunSnapshot manual = read.findRun(projectId, baselineRunId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ARGUMENT,
                            "手动基线运行不存在或不属于当前项目: " + baselineRunId));
            if (!Objects.equals(current.dut(), manual.dut())
                    || !Objects.equals(current.link(), manual.link())) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "手动基线与本轮环境不一致：本轮 DUT=" + display(current.dut())
                                + " LINK=" + display(current.link())
                                + "；基线 DUT=" + display(manual.dut())
                                + " LINK=" + display(manual.link()));
            }
            return Optional.of(manual);
        }
        return read.findBaseline(projectId, current.dut(), current.link(),
                current.startedAt(), current.runId());
    }

    private static String changeOf(CaseVerdict history, CaseVerdict current) {
        if (current == null) {
            return CHANGE_NOT_RUN;
        }
        boolean currentFail = "FAIL".equals(current.verdict());
        boolean historyFail = history != null && "FAIL".equals(history.verdict());
        if (currentFail && !historyFail) {
            // 本轮失败、基线不是失败（含基线未执行/跳过/通过）：对基线而言都是新增回归。
            return CHANGE_PASS_TO_FAIL;
        }
        if (!currentFail && historyFail) {
            return CHANGE_FAIL_TO_PASS;
        }
        return CHANGE_SAME;
    }

    private static Map<String, CaseVerdict> index(List<CaseVerdict> rows) {
        Map<String, CaseVerdict> indexed = new LinkedHashMap<>();
        for (CaseVerdict row : rows) {
            indexed.put(row.caseKey(), row);
        }
        return indexed;
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "（空）" : value;
    }
}
