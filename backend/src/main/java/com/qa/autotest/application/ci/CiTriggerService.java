package com.qa.autotest.application.ci;

import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.plan.PlanScheduleService;
import com.qa.autotest.application.repository.CiTriggerRepository;
import com.qa.autotest.application.repository.PlanRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.ci.CiTriggerRecord;
import com.qa.autotest.domain.ci.CiWritebackStatus;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.iam.ProjectApiToken;
import com.qa.autotest.domain.plan.PlanRecord;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CI 触发服务（PLN-03）。
 *
 * <p>语义（DEV-PLAN §4）：
 * <ol>
 *   <li>令牌已由 {@link CiTokenService} 认证：必须绑定项目且含 {@code ci:trigger}；</li>
 *   <li>同令牌每窗口 {@code ci.trigger-per-minute} 次，超出 429；</li>
 *   <li>计划必须属于令牌绑定的项目，否则 403（不泄露计划是否存在）；</li>
 *   <li>{@code (plan_id, commit_sha)} 幂等：重复触发返回 202 + 已有 runId + duplicated=true，
 *       不新建 run；并发插入靠唯一键冲突兜底；</li>
 *   <li>新建时复用 PLN-01 的计划触发路径（trigger_type='ci'），run 创建后<b>立即返回</b>，
 *       执行在单线程派发器里异步进行，接口不等待执行结果。</li>
 * </ol>
 */
@Service
public class CiTriggerService {

    private static final Logger LOG = LoggerFactory.getLogger(CiTriggerService.class);
    private static final String REQUIRED_SCOPE = "ci:trigger";

    private final PlanRepository plans;
    private final PlanScheduleService schedules;
    private final CiTriggerRepository triggers;
    private final AuditService audit;
    private final TokenRateLimiter rateLimiter;
    private final CiProperties properties;
    private final Clock clock;

    public CiTriggerService(PlanRepository plans,
                            PlanScheduleService schedules,
                            CiTriggerRepository triggers,
                            AuditService audit,
                            TokenRateLimiter rateLimiter,
                            CiProperties properties,
                            Clock clock) {
        this.plans = plans;
        this.schedules = schedules;
        this.triggers = triggers;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 触发一次 CI 运行。
     *
     * @throws BusinessException 400 参数非法 / 401 令牌已在认证层拦截 / 403 无 scope 或计划不属于项目 / 429 超限
     */
    @Transactional
    public CiTriggerResult trigger(CiTriggerCommand cmd, ProjectApiToken token) {
        TenantContext.set(token.userId(), "ci:" + token.name(), token.projectId());
        try {
            if (!token.hasScope(REQUIRED_SCOPE)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "令牌缺少 ci:trigger scope");
            }
            if (!rateLimiter.tryAcquire("ci-token:" + token.id())) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "触发频率超限：每令牌每分钟最多 " + properties.getTriggerPerMinute() + " 次");
            }
            CiTriggerCommand normalized = normalize(cmd);
            PlanRecord plan = plans.find(token.projectId(), normalized.planId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN,
                            "计划不存在或不属于该令牌绑定的项目: " + normalized.planId()));

            Optional<CiTriggerRecord> existing =
                    triggers.findByPlanAndCommit(plan.projectId(), plan.id(), normalized.commitSha());
            if (existing.isPresent()) {
                return duplicate(existing.get(), token);
            }

            CiTriggerRecord draft = new CiTriggerRecord(0L, plan.projectId(), plan.id(),
                    normalized.commitSha(), normalized.ref(), normalized.build(), normalized.repo(),
                    null, CiWritebackStatus.PENDING, null, clock.instant());
            try {
                long triggerId = triggers.insert(draft, token.userId());
                RunRecord run = schedules.triggerCi(plan, requestId(plan.id(), normalized.commitSha()));
                triggers.updateRunId(plan.projectId(), triggerId, run.runId());
                audit.record("CI_TRIGGER", "ci_trigger", Long.toString(triggerId),
                        detail(token, normalized, run.runId()));
                return new CiTriggerResult(run.runId(), false,
                        CiWritebackSupport.preview(normalized.repo(), properties));
            } catch (DataIntegrityViolationException race) {
                // 并发同 commit：唯一键冲突后回读胜者，返回 duplicated 而不是 500。
                CiTriggerRecord winner = triggers
                        .findByPlanAndCommit(plan.projectId(), plan.id(), normalized.commitSha())
                        .orElseThrow(() -> race);
                LOG.info("CI 触发并发冲突，回读已有记录 plan={} commit={}", plan.id(),
                        shortSha(normalized.commitSha()));
                return duplicate(winner, token);
            }
        } finally {
            TenantContext.clear();
        }
    }

    private CiTriggerResult duplicate(CiTriggerRecord record, ProjectApiToken token) {
        audit.record("CI_TRIGGER_DUPLICATED", "ci_trigger", Long.toString(record.id()),
                "token=" + token.name() + ", plan=" + record.planId()
                        + ", commit=" + record.commitSha() + ", run=" + record.runId());
        return new CiTriggerResult(record.runId(), true,
                CiWritebackSupport.preview(record.repo(), properties));
    }

    private CiTriggerCommand normalize(CiTriggerCommand cmd) {
        if (cmd == null || cmd.planId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "planId 非法");
        }
        String sha = cmd.commitSha() == null ? "" : cmd.commitSha().trim().toLowerCase(Locale.ROOT);
        if (sha.isEmpty() || sha.length() > 64 || !sha.matches("[0-9a-f]+")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "commitSha 必须是 1~64 位十六进制字符串");
        }
        String build = cmd.build() == null ? "" : cmd.build().trim();
        if (build.isEmpty()) {
            build = sha.substring(0, Math.min(8, sha.length()));
        }
        if (build.length() > 128) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "build 超过 128 字符");
        }
        String ref = trimToNull(cmd.ref());
        if (ref != null && ref.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "ref 超过 255 字符");
        }
        String repo = trimToNull(cmd.repo());
        if (repo != null && repo.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "repo 超过 255 字符");
        }
        return new CiTriggerCommand(cmd.planId(), sha, ref, build, repo);
    }

    private String requestId(long planId, String commitSha) {
        // 与 ci_trigger 唯一键同源，长度 ≤ 64，保证 RunService 自身幂等也命中同一 run。
        return "ci-" + planId + "-" + commitSha;
    }

    private String detail(ProjectApiToken token, CiTriggerCommand cmd, String runId) {
        return "token=" + token.name() + ", token_id=" + token.id() + ", plan=" + cmd.planId()
                + ", commit=" + cmd.commitSha() + ", build=" + cmd.build() + ", run=" + runId;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String shortSha(String sha) {
        return sha == null ? "null" : sha.substring(0, Math.min(8, sha.length()));
    }
}
