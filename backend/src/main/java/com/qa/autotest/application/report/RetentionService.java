package com.qa.autotest.application.report;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.repository.AuditRepository;
import com.qa.autotest.application.repository.RetentionRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.report.RetentionArtifact;
import com.qa.autotest.domain.report.RetentionCandidate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 产物保留策略（RPT-06 / DEV-PLAN §6）。
 *
 * <p>破坏性边界写死在这里：
 * <ul>
 *   <li>只删 {@code run_artifact} 登记的文件，随后把该行置 {@code availability='purged'}；</li>
 *   <li><b>绝不删除 {@code run} / {@code test_result} 记录</b>，结论与指标永久可查；</li>
 *   <li>dry-run 只算清单，不调用文件删除、不改任何行；</li>
 *   <li>默认 dryRun=true、定时任务默认 enabled=false，避免测试环境误删。</li>
 * </ul>
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    /** 审计动作名（与设计文档一致）。 */
    static final String AUDIT_ACTION = "artifact_purge";

    /** dry-run / 执行结果的单行清单。 */
    public record RetentionItem(String runId, long bytes, String reason) {
    }

    public record RetentionResult(boolean dryRun,
                                  int runCount,
                                  long totalBytes,
                                  long projectBytes,
                                  int keptRecentRuns,
                                  int deletedFiles,
                                  List<RetentionItem> items) {
    }

    private final RetentionRepository retention;
    private final ArtifactFileStore fileStore;
    private final AuditRepository audit;
    private final RetentionProperties properties;
    private final AccessControlService access;
    private final Clock clock;

    public RetentionService(RetentionRepository retention,
                            ArtifactFileStore fileStore,
                            AuditRepository audit,
                            RetentionProperties properties,
                            AccessControlService access,
                            Clock executionClock) {
        this.retention = retention;
        this.fileStore = fileStore;
        this.audit = audit;
        this.properties = properties;
        this.access = access;
        this.clock = executionClock;
    }

    /**
     * 管理员入口（API 用）：先做项目绑定与权限判定，再按 {@code dryRun} 分流。
     *
     * @throws BusinessException 非管理员为 {@link ErrorCode#FORBIDDEN}
     */
    public RetentionResult run(long projectId, boolean dryRun) {
        if (projectId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "projectId 非法");
        }
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);
        if (!access.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可清理产物");
        }
        return dryRun ? plan(projectId) : execute(projectId);
    }

    /** 只计算候选清单与总空间，不删文件、不改库。 */
    public RetentionResult plan(long projectId) {
        long projectBytes = retention.totalBytes(projectId);
        RetentionPlanner.Plan plan = planOf(projectId, projectBytes);
        return result(true, plan, projectBytes, 0, properties.getKeepRecentRuns());
    }

    /** 实际执行：删文件 → availability='purged' → 写审计；run / test_result 一行不动。 */
    public RetentionResult execute(long projectId) {
        long projectBytes = retention.totalBytes(projectId);
        RetentionPlanner.Plan plan = planOf(projectId, projectBytes);
        String operator = TenantContext.username() == null ? "system" : TenantContext.username();
        int deletedFiles = 0;

        for (RetentionPlanner.Item item : plan.items()) {
            List<RetentionArtifact> artifacts = retention.listArtifacts(projectId, item.runDbId());
            List<Long> purgedIds = new ArrayList<>();
            for (RetentionArtifact artifact : artifacts) {
                if (artifact.protectedFile()) {
                    continue;
                }
                if (fileStore.delete(artifact.path())) {
                    deletedFiles++;
                }
                purgedIds.add(artifact.id());
            }
            if (purgedIds.isEmpty()) {
                continue;
            }
            retention.markPurged(projectId, item.runDbId(), purgedIds);
            audit.insert(projectId, TenantContext.userId(), AUDIT_ACTION, "run", item.runId(),
                    detail(item.runId(), item.bytes(), operator));
        }
        log.info("产物清理完成 projectId={} runs={} bytes={} files={} operator={}",
                projectId, plan.items().size(), plan.totalBytes(), deletedFiles, operator);
        return result(false, plan, projectBytes, deletedFiles, properties.getKeepRecentRuns());
    }

    /**
     * 定时任务入口：遍历启用项目逐个清理，身份记为 {@code system}。
     * 只在 {@code report.retention.enabled=true} 时由调度器调用。
     */
    public void executeAllProjects() {
        for (Long projectId : retention.listProjectIds()) {
            TenantContext.runAs(null, "system", projectId, () -> {
                try {
                    execute(projectId);
                } catch (RuntimeException ex) {
                    log.warn("定时清理项目失败 projectId={}: {}", projectId, ex.getMessage());
                }
            });
        }
    }

    private RetentionPlanner.Plan planOf(long projectId, long projectBytes) {
        List<RetentionCandidate> candidates = retention.listCandidates(projectId);
        return RetentionPlanner.plan(candidates, projectBytes, clock.instant(),
                properties.getMaxDays(), properties.getMaxProjectGb(), properties.getKeepRecentRuns());
    }

    private static RetentionResult result(boolean dryRun, RetentionPlanner.Plan plan,
                                          long projectBytes, int deletedFiles, int keptRecentRuns) {
        List<RetentionItem> items = new ArrayList<>(plan.items().size());
        for (RetentionPlanner.Item item : plan.items()) {
            items.add(new RetentionItem(item.runId(), item.bytes(), item.reason()));
        }
        return new RetentionResult(dryRun, items.size(), plan.totalBytes(), projectBytes,
                keptRecentRuns, deletedFiles, items);
    }

    private static String detail(String runId, long bytes, String operator) {
        return "{\"runId\":\"" + escape(runId) + "\",\"bytes\":" + bytes
                + ",\"operator\":\"" + escape(operator) + "\"}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
