package com.qa.autotest.application.execution;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.execution.RunService.CreateRunCommand;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.execution.RunOrigin;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 执行人工触发（EXEC-01 / FR-v0.2-59~61）。
 *
 * <p>职责：校验套件路径与 {@code EXECUTION:EXECUTE} 权限 → 请求期预检（EXEC-04：路径存在性、
 * 测试名跨套件重名）→ 落库 run（{@code trigger_type=manual}）
 * → 立即交给执行域公共派发器（串行、按 runId 去重）→ 写 {@code RUN_TRIGGER} 审计。
 *
 * <p>创建与派发<b>不在同一事务</b>：{@link RunService#create} 是独立代理方法，返回时 run 已提交，
 * 避免异步执行线程读不到刚建的 run。
 */
@Service
public class QuickRunService {

    private static final Logger LOG = LoggerFactory.getLogger(QuickRunService.class);

    /** 单次触发允许的套件路径上限，挡住误粘贴整棵文件树。 */
    static final int MAX_SUITE_PATHS = 200;

    private final RunService runs;
    private final RunExecutionService executions;
    private final RunDispatcher dispatcher;
    private final AccessControlService access;
    private final AuditService audit;
    private final ExecutionProperties props;
    private final ExecutionRequestPrecheck precheck;

    public QuickRunService(RunService runs, RunExecutionService executions,
                           RunDispatcher dispatcher, AccessControlService access,
                           AuditService audit, ExecutionProperties props,
                           ExecutionRequestPrecheck precheck) {
        this.runs = runs;
        this.executions = executions;
        this.dispatcher = dispatcher;
        this.access = access;
        this.audit = audit;
        this.props = props;
        this.precheck = precheck;
    }

    /**
     * 手工触发一次运行。
     *
     * <p>{@code cmd.suitePaths()} 至少一个；路径为相对执行工作区的套件文件或目录。
     */
    public RunRecord trigger(QuickRunCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请求为空");
        }
        List<String> suites = normalizeSuites(cmd.suitePaths());
        access.bindProject(cmd.projectId());
        access.require(PermissionResource.EXECUTION, PermissionAction.EXECUTE);
        precheck.requireSuitesExist(cmd.projectId(), suites);
        precheck.requireUnambiguousTestNames(cmd.projectId(), suites, cmd.testNames());

        int concurrency = cmd.concurrency() == null ? 1 : cmd.concurrency();
        int timeout = cmd.timeoutSeconds() == null || cmd.timeoutSeconds() <= 0
                ? props.getRunTimeoutSeconds() : cmd.timeoutSeconds();
        // FR-v0.4-24：本次触发可覆盖单用例超时；0 表示沿用 execution.test-timeout-seconds。
        int testTimeout = RunService.normalizeTestTimeout(cmd.testTimeoutSeconds());
        CreateRunCommand command = new CreateRunCommand(
                cmd.projectId(),
                cmd.testSetId(),
                suites,
                concurrency,
                cmd.environmentId(),
                cmd.deviceKeys() == null ? List.of() : List.copyOf(cmd.deviceKeys()),
                cmd.runRequestId(),
                0,
                timeout,
                cmd.includeTag(),
                cmd.testNames() == null ? List.of() : List.copyOf(cmd.testNames()),
                testTimeout);
        RunRecord created = runs.create(command, RunOrigin.manual());
        if (created.state() != RunState.QUEUED) {
            // 幂等重放：run 已在执行或已结束，不再派发，也不再重复记触发审计。
            LOG.info("run {} 已处于 {}，跳过重复派发", created.runId(), created.state());
            return created;
        }
        audit.record("RUN_TRIGGER", "run", created.runId(),
                "source=" + safeSource(cmd.source())
                        + ", suites=" + suites.size()
                        + (cmd.testNames() == null || cmd.testNames().isEmpty()
                                ? "" : ", tests=" + cmd.testNames().size())
                        + ", operator=" + nullToEmpty(TenantContext.username())
                        + (cmd.testSetId() == null ? "" : ", testSet=" + cmd.testSetId()));
        dispatch(created, command);
        return created;
    }

    /** 交给公共派发器；仅 QUEUED 的 run 需要派发（重复请求直接返回原记录）。 */
    public void dispatch(RunRecord created, CreateRunCommand command) {
        if (created.state() != RunState.QUEUED) {
            return;
        }
        dispatcher.submit(created.projectId(), null, created.runId(), command.priority(),
                () -> executions.execute(created, command));
    }

    /**
     * 套件路径规范化：去空行、去重、拒绝绝对路径与 {@code ..} 段。
     *
     * <p>路径最终会作为 pabot 参数在项目执行工作区内使用，不允许跳出工作区。
     */
    static List<String> normalizeSuites(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "至少选择一个套件路径");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            String path = item.trim().replace('\\', '/');
            if (path.isEmpty()) {
                continue;
            }
            if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "套件路径必须是相对路径: " + path);
            }
            for (String segment : path.split("/")) {
                if ("..".equals(segment)) {
                    throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                            "套件路径不允许包含 ..: " + path);
                }
            }
            unique.add(path);
        }
        if (unique.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "至少选择一个套件路径");
        }
        if (unique.size() > MAX_SUITE_PATHS) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "套件路径最多 " + MAX_SUITE_PATHS + " 个");
        }
        return new ArrayList<>(unique);
    }

    private static String safeSource(String source) {
        String value = source == null || source.isBlank() ? "api" : source.trim();
        return value.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 手工触发参数。
     *
     * @param timeoutSeconds     整次运行的总超时（秒）
     * @param testTimeoutSeconds 单用例超时（秒）；null / {@code <=0} 表示沿用全局默认
     * @param source             来源页面标记（如 runs-view / test-set），只进审计，不落 run 表
     */
    public record QuickRunCommand(long projectId,
                                  Long testSetId,
                                  List<String> suitePaths,
                                  Long environmentId,
                                  List<String> deviceKeys,
                                  Integer concurrency,
                                  Integer timeoutSeconds,
                                  Integer testTimeoutSeconds,
                                  String includeTag,
                                  String runRequestId,
                                  List<String> testNames,
                                  String source) {
    }
}
