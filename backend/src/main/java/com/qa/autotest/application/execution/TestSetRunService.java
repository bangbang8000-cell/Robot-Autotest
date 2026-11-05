package com.qa.autotest.application.execution;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.TestSetRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.caseasset.SafetyLevel;
import com.qa.autotest.domain.caseasset.TestCase;
import com.qa.autotest.domain.caseasset.TestSet;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用例集立即执行（EXEC-01 / FR-v0.2-63；EXEC-03 起测试级）。
 *
 * <p>解析口径复用 {@link CaseScriptResolver}（EXEC-02 起统一）：脚本测试的
 * {@code [Tags]} 含 {@code case:<case_code>}，或测试名与用例编号完全一致。
 * 同一次执行按套件去重、按测试名去重，执行粒度为<b>测试</b>（{@code --test}）——
 * 集合只勾一条时，同一套件里的其它测试不会被执行。
 *
 * <p>只要集合内存在未绑定用例，就整单拒绝并列出编号 —— 不静默跳过、不猜路径。
 */
@Service
public class TestSetRunService {

    private final TestSetRepository sets;
    private final AccessControlService access;
    private final CaseScriptResolver resolver;
    private final QuickRunService quickRuns;

    public TestSetRunService(TestSetRepository sets, AccessControlService access,
                             CaseScriptResolver resolver, QuickRunService quickRuns) {
        this.sets = sets;
        this.access = access;
        this.resolver = resolver;
        this.quickRuns = quickRuns;
    }

    public RunRecord run(TestSetRunCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请求为空");
        }
        access.bindProject(cmd.projectId());
        access.require(PermissionResource.EXECUTION, PermissionAction.EXECUTE);

        TestSet set = sets.find(cmd.projectId(), cmd.testSetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "用例集不存在: " + cmd.testSetId()));
        List<TestCase> cases = sets.items(cmd.projectId(), cmd.testSetId());
        if (cases.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "用例集为空，无法触发执行");
        }
        List<Long> blank = cases.stream()
                .filter(testCase -> testCase.caseCode() == null || testCase.caseCode().isBlank())
                .map(TestCase::id)
                .toList();
        if (!blank.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "用例集 " + set.setCode() + " 存在 " + blank.size()
                            + " 条用例编号为空（id=" + joinIds(blank) + "），无法解析脚本绑定");
        }

        // G2-3 / ADR-0013：集合内存在 L2 高危或 LX 未判定用例即整单拒绝，不静默剔除
        List<String> blocked = cases.stream()
                .filter(testCase -> SafetyLevel.from(testCase.safetyLevel()).blocksExecution())
                .map(testCase -> testCase.caseCode().trim() + "("
                        + SafetyLevel.from(testCase.safetyLevel()).name() + ")")
                .toList();
        if (!blocked.isEmpty()) {
            throw new BusinessException(ErrorCode.SAFETY_LEVEL_BLOCKED,
                    "用例集 " + set.setCode() + " 含禁止自动执行的用例（L2 高危 / LX 未判定按 L2 对待）："
                            + blockedSummary(blocked));
        }

        Resolution resolution = resolve(cmd.projectId(), cases);
        if (!resolution.unbound().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "用例集 " + set.setCode() + " 存在未绑定脚本的用例（"
                            + resolution.unbound().size() + " 条）："
                            + String.join(", ", resolution.unbound())
                            + "。请在脚本测试的 [Tags] 中写入 case:<用例编号>，"
                            + "或让测试名与用例编号一致后再执行");
        }

        return quickRuns.trigger(new QuickRunService.QuickRunCommand(
                cmd.projectId(), cmd.testSetId(), resolution.suitePaths(),
                cmd.environmentId(), cmd.deviceKeys(), cmd.concurrency(),
                cmd.timeoutSeconds(), cmd.testTimeoutSeconds(), cmd.includeTag(), cmd.runRequestId(),
                resolution.testNames(), "test-set"));
    }

    private static String joinIds(List<Long> ids) {
        List<String> values = new ArrayList<>();
        ids.forEach(id -> values.add(String.valueOf(id)));
        return String.join(", ", values);
    }

    /** 拒绝文案：超过 10 条只列前 10 条。 */
    private static String blockedSummary(List<String> blocked) {
        int limit = 10;
        String head = String.join(", ", blocked.subList(0, Math.min(limit, blocked.size())));
        return blocked.size() <= limit ? head : head + " 等 " + blocked.size() + " 条";
    }

    /** 用例编号 → 套件路径与测试名；未命中即为未绑定。 */
    Resolution resolve(long projectId, List<TestCase> cases) {
        Set<String> wanted = new LinkedHashSet<>();
        for (TestCase testCase : cases) {
            if (testCase.caseCode() != null && !testCase.caseCode().isBlank()) {
                wanted.add(testCase.caseCode().trim());
            }
        }
        Map<String, List<CaseScriptResolver.Binding>> bindings = resolver.resolve(projectId, wanted);

        List<String> unbound = new ArrayList<>();
        Set<String> suites = new LinkedHashSet<>();
        Set<String> testNames = new LinkedHashSet<>();
        for (String code : wanted) {
            List<CaseScriptResolver.Binding> hit = bindings.get(code);
            if (hit == null || hit.isEmpty()) {
                unbound.add(code);
            } else {
                hit.forEach(binding -> {
                    suites.add(binding.suitePath());
                    testNames.add(binding.testName());
                });
            }
        }
        return new Resolution(new ArrayList<>(suites), new ArrayList<>(testNames), unbound);
    }

    record Resolution(List<String> suitePaths, List<String> testNames, List<String> unbound) {
    }

    public record TestSetRunCommand(long projectId,
                                    long testSetId,
                                    Long environmentId,
                                    List<String> deviceKeys,
                                    Integer concurrency,
                                    Integer timeoutSeconds,
                                    Integer testTimeoutSeconds,
                                    String includeTag,
                                    String runRequestId) {
    }
}
