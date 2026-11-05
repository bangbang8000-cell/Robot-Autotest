package com.qa.autotest.application.execution;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.TestCaseRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.caseasset.SafetyLevel;
import com.qa.autotest.domain.caseasset.TestCase;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 勾选台账用例直接执行（EXEC-02，v0.4 候选提前实现）。
 *
 * <p>与「按套件路径 / 按用例集」触发（EXEC-01）的区别：本包按<b>台账用例</b>解析绑定，
 * 并把命中测试名作为 {@code --test} 透传给 pabot，因此执行粒度是<b>测试</b>而不是整个套件。
 * {@code --test} 的实跑效果按 ADR-0013 不在开发期验证，留给验收人现场确认（TC-EXEC-08 同类）。
 *
 * <p>绑定规则见 {@link CaseScriptResolver}（ADR-0005 §3 的 {@code case:<case_code>} 标签，
 * 兼容测试名等于编号的兜底）。任何一条所选用例未绑定，整单 400 并列出编号，不静默跳过。
 */
@Service
public class CaseRunService {

    /** 与批量操作上限一致，挡住误全选。 */
    static final int MAX_CASES = 200;

    private final TestCaseRepository cases;
    private final AccessControlService access;
    private final CaseScriptResolver resolver;
    private final QuickRunService quickRuns;

    public CaseRunService(TestCaseRepository cases, AccessControlService access,
                          CaseScriptResolver resolver, QuickRunService quickRuns) {
        this.cases = cases;
        this.access = access;
        this.resolver = resolver;
        this.quickRuns = quickRuns;
    }

    public RunRecord run(CaseRunCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请求为空");
        }
        List<Long> ids = cmd.caseIds();
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "至少选择一条用例");
        }
        if (ids.size() > MAX_CASES) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "单次最多执行 " + MAX_CASES + " 条用例，请分批提交");
        }
        access.bindProject(cmd.projectId());
        access.require(PermissionResource.EXECUTION, PermissionAction.EXECUTE);

        Set<String> codes = new LinkedHashSet<>();
        List<String> blocked = new ArrayList<>();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            TestCase testCase = cases.find(cmd.projectId(), id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "用例不存在: " + id));
            if (testCase.caseCode() == null || testCase.caseCode().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "用例缺少编号，无法解析脚本: " + id);
            }
            SafetyLevel level = SafetyLevel.from(testCase.safetyLevel());
            if (level.blocksExecution()) {
                blocked.add(testCase.caseCode().trim() + "(" + level.name() + ")");
            }
            codes.add(testCase.caseCode().trim());
        }
        if (codes.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "至少选择一条用例");
        }
        // G2-3 / ADR-0013：L2 高危与 LX 未判定一律不得自动执行，整单拒绝并列明编号
        if (!blocked.isEmpty()) {
            throw new BusinessException(ErrorCode.SAFETY_LEVEL_BLOCKED,
                    "以下用例安全级禁止自动执行（L2 高危 / LX 未判定按 L2 对待）："
                            + blockedSummary(blocked));
        }

        Map<String, List<CaseScriptResolver.Binding>> bindings = resolver.resolve(cmd.projectId(), codes);
        List<String> unbound = new ArrayList<>();
        Set<String> suites = new LinkedHashSet<>();
        Set<String> testNames = new LinkedHashSet<>();
        for (String code : codes) {
            List<CaseScriptResolver.Binding> hit = bindings.get(code);
            if (hit == null || hit.isEmpty()) {
                unbound.add(code);
                continue;
            }
            for (CaseScriptResolver.Binding binding : hit) {
                suites.add(binding.suitePath());
                testNames.add(binding.testName());
            }
        }
        if (!unbound.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "存在未绑定脚本的用例（" + unbound.size() + " 条）："
                            + String.join(", ", unbound)
                            + "。请在脚本测试的 [Tags] 中写入 case:<用例编号>，"
                            + "或让测试名与用例编号一致后再执行");
        }

        return quickRuns.trigger(new QuickRunService.QuickRunCommand(
                cmd.projectId(), null, new ArrayList<>(suites),
                cmd.environmentId(), cmd.deviceKeys(), cmd.concurrency(),
                cmd.timeoutSeconds(), cmd.testTimeoutSeconds(), cmd.includeTag(), cmd.runRequestId(),
                new ArrayList<>(testNames), "cases"));
    }

    /** 拒绝文案：超过 10 条只列前 10 条，避免长列表撑爆界面。 */
    private static String blockedSummary(List<String> blocked) {
        int limit = 10;
        String head = String.join(", ", blocked.subList(0, Math.min(limit, blocked.size())));
        return blocked.size() <= limit ? head : head + " 等 " + blocked.size() + " 条";
    }

    public record CaseRunCommand(long projectId,
                                 List<Long> caseIds,
                                 Long environmentId,
                                 List<String> deviceKeys,
                                 Integer concurrency,
                                 Integer timeoutSeconds,
                                 Integer testTimeoutSeconds,
                                 String includeTag,
                                 String runRequestId) {
    }
}
