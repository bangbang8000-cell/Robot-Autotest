package com.qa.autotest.application.ci;

import com.qa.autotest.application.execution.RunCompletionListener;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.CiTriggerRepository;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.ci.CiTriggerRecord;
import com.qa.autotest.domain.ci.CiWritebackStatus;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * CI 运行结束回写（PLN-03）。
 *
 * <p>四条硬约束：
 * <ol>
 *   <li>只回写 passed（FINISHED→success）/ failed（FAILED→failure）；取消等其它终态不动；</li>
 *   <li>回写失败<b>绝不改变 run 结论</b>——本类不写 run 表，只更新 {@code ci_trigger}
 *       的 writeback_status / writeback_error，异常不外抛；</li>
 *   <li>默认 {@code ci.writeback.enabled=false}；未配置 {@code GITHUB_STATUS_TOKEN} 时只记 skipped，
 *       不发任何请求；</li>
 *   <li>群晖仓库（ssh:// / git@）记 not_supported 并写审计，<b>不得假装调用</b>。</li>
 * </ol>
 */
@Service
public class CiWritebackService implements RunCompletionListener {

    private static final Logger LOG = LoggerFactory.getLogger(CiWritebackService.class);
    private static final int ERROR_MAX = 512;
    private static final int DESCRIPTION_MAX = 140;

    private final CiTriggerRepository triggers;
    private final CiStatusClient client;
    private final GitHubTokenSource tokenSource;
    private final CiProperties properties;
    private final AuditService audit;

    public CiWritebackService(CiTriggerRepository triggers,
                              CiStatusClient client,
                              GitHubTokenSource tokenSource,
                              CiProperties properties,
                              AuditService audit) {
        this.triggers = triggers;
        this.client = client;
        this.tokenSource = tokenSource;
        this.properties = properties;
        this.audit = audit;
    }

    @Override
    public void onCompleted(RunRecord run) {
        if (run == null || run.projectId() <= 0 || run.runId() == null) {
            return;
        }
        String state = githubState(run.state());
        if (state == null) {
            return;
        }
        // 监听器在事务提交后 / 派发线程中执行，显式包裹租户上下文，避免守卫拒绝。
        TenantContext.runAs(null, "ci-writeback", run.projectId(), () -> writeback(run, state));
    }

    private void writeback(RunRecord run, String state) {
        Optional<CiTriggerRecord> found = triggers.findByRunId(run.projectId(), run.runId());
        if (found.isEmpty()) {
            return;
        }
        CiTriggerRecord trigger = found.get();
        if (!properties.getWriteback().isEnabled()) {
            mark(trigger, CiWritebackStatus.SKIPPED, "disabled", run, "skipped");
            return;
        }
        if (CiWritebackSupport.isSynologyRepo(trigger.repo())) {
            mark(trigger, CiWritebackStatus.NOT_SUPPORTED,
                    "synology_git_server_has_no_http_status_api", run, "not_supported");
            return;
        }
        String token = tokenSource.token();
        if (token == null || token.isBlank()) {
            mark(trigger, CiWritebackStatus.SKIPPED, "token_missing", run, "skipped");
            return;
        }
        String repo = CiWritebackSupport.normalizeGithubRepo(trigger.repo());
        if (repo == null) {
            mark(trigger, CiWritebackStatus.SKIPPED,
                    "repo_not_github: " + safe(trigger.repo(), 120), run, "skipped");
            return;
        }

        CiStatusClient.CiStatusRequest request = new CiStatusClient.CiStatusRequest(
                properties.getWriteback().getApiBaseUrl(),
                repo,
                trigger.commitSha(),
                state,
                targetUrl(run.runId()),
                description(run, state),
                properties.getWriteback().getContext(),
                properties.getWriteback().getTimeoutSeconds());
        try {
            CiStatusClient.CiStatusResponse response = client.post(request, token);
            if (response != null && response.accepted()) {
                mark(trigger, CiWritebackStatus.SUCCESS, null, run, "success");
            } else {
                int code = response == null ? 0 : response.statusCode();
                mark(trigger, CiWritebackStatus.FAILED, "github_http_" + code, run, "failed");
            }
        } catch (RuntimeException ex) {
            // 只记录异常类型与消息，绝不把令牌或请求头写进错误字段/日志。
            mark(trigger, CiWritebackStatus.FAILED,
                    "github_error: " + ex.getClass().getSimpleName()
                            + ": " + safe(ex.getMessage(), 200), run, "failed");
        }
    }

    private void mark(CiTriggerRecord trigger, CiWritebackStatus status, String error,
                      RunRecord run, String auditResult) {
        try {
            triggers.updateWriteback(trigger.projectId(), trigger.id(), status, error, null);
        } catch (RuntimeException ex) {
            // 状态回写落库失败也不能影响 run；只记日志。
            LOG.warn("CI 回写状态落库失败 trigger={} run={}: {}", trigger.id(), run.runId(),
                    ex.getMessage());
            return;
        }
        if (status == CiWritebackStatus.FAILED) {
            LOG.warn("CI 回写失败 run={} trigger={} reason={}", run.runId(), trigger.id(), error);
        }
        audit.record("CI_WRITEBACK", "ci_trigger", Long.toString(trigger.id()),
                "run=" + run.runId() + ", state=" + run.state().dbValue()
                        + ", result=" + auditResult + ", target=" + targetUrl(run.runId()));
    }

    /** FINISHED（用例全通过）→ success；FAILED → failure；其余（含 CANCELED）不回写。 */
    static String githubState(RunState state) {
        if (state == RunState.FINISHED) {
            return "success";
        }
        if (state == RunState.FAILED) {
            return "failure";
        }
        return null;
    }

    private String targetUrl(String runId) {
        String base = properties.getWriteback().getTargetBaseUrl() == null
                ? "" : properties.getWriteback().getTargetBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/reports/" + runId;
    }

    private static String description(RunRecord run, String state) {
        String text = "qa-platform 运行 " + run.runId() + (state.equals("success") ? " 通过" : " 失败");
        return safe(text, DESCRIPTION_MAX);
    }

    private static String safe(String value, int max) {
        if (value == null) {
            return "";
        }
        String single = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return single.length() <= max ? single : single.substring(0, max);
    }
}
