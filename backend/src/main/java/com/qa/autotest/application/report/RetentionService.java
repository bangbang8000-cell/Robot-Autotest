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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 产物保留策略（RPT-06 / DEV-PLAN §6）。
 *
 * <p>破坏性边界写死在这里：
 * <ul>
 *   <li>只删 {@code run_artifact} 登记的文件，随后把该行置 {@code availability='purged'}；</li>
 *   <li><b>绝不删除 {@code run} / {@code test_result} 记录</b>，结论与指标永久可查；</li>
 *   <li>dry-run 只算清单，不调用文件删除、不改任何行；</li>
 *   <li>默认 dryRun=true、定时任务默认 enabled=false，避免测试环境误删；</li>
 *   <li>「置 purged」与「写审计」在同一数据库事务内，文件删除失败不影响库侧一致性。</li>
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
    private final TransactionTemplate tx;

    /** Spring 装配入口：库侧写入（purged 标记 + 审计）放进同一个事务。 */
    @Autowired
    public RetentionService(RetentionRepository retention,
                            ArtifactFileStore fileStore,
                            AuditRepository audit,
                            RetentionProperties properties,
                            AccessControlService access,
                            Clock executionClock,
                            PlatformTransactionManager txManager) {
        this(retention, fileStore, audit, properties, access, executionClock,
                new TransactionTemplate(txManager));
    }

    /** 无事务装配（计划/单测）：库侧写入直接执行。 */
    public RetentionService(RetentionRepository retention,
                            ArtifactFileStore fileStore,
                            AuditRepository audit,
                            RetentionProperties properties,
                            AccessControlService access,
                            Clock executionClock) {
        this(retention, fileStore, audit, properties, access, executionClock, (TransactionTemplate) null);
    }

    /** 测试装配入口：{@code tx=null} 表示不分事务（单测不依赖数据库）。 */
    public RetentionService(RetentionRepository retention,
                            ArtifactFileStore fileStore,
                            AuditRepository audit,
                            RetentionProperties properties,
                            AccessControlService access,
                            Clock executionClock,
                            TransactionTemplate tx) {
        this.retention = retention;
        this.fileStore = fileStore;
        this.audit = audit;
        this.properties = properties;
        this.access = access;
        this.clock = executionClock;
        this.tx = tx;
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

    /**
     * 实际执行：删文件 → 同一事务内「置 purged + 写审计」；run / test_result 一行不动。
     *
     * <p>文件系统不能参与数据库事务，因此顺序只能是「先删文件、后写库」。这两步之间若事务失败，
     * 结果是文件已删而产物行仍是 {@code ok}——下一次清理时 {@link ArtifactFileStore#delete}
     * 会返回 false（文件本就不存在），但该行仍会被置 purged 并补写审计，即下一次自愈。
     * 库侧的两处写入必须同事务，杜绝「已标 purged 却没有审计」这种查不回来的中间态。
     */
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
            markAndAudit(projectId, item, purgedIds, operator);
        }
        log.info("产物清理完成 projectId={} runs={} bytes={} files={} operator={}",
                projectId, plan.items().size(), plan.totalBytes(), deletedFiles, operator);
        return result(false, plan, projectBytes, deletedFiles, properties.getKeepRecentRuns());
    }

    /** purged 标记与审计必须同时成立；失败时抛出让事务回滚，文件缺失由下次清理自愈。 */
    private void markAndAudit(long projectId, RetentionPlanner.Item item,
                              List<Long> purgedIds, String operator) {
        Runnable write = () -> {
            retention.markPurged(projectId, item.runDbId(), purgedIds);
            audit.insert(projectId, TenantContext.userId(), AUDIT_ACTION, "run", item.runId(),
                    detail(item.runId(), item.bytes(), operator));
        };
        if (tx == null) {
            write.run();
            return;
        }
        tx.executeWithoutResult(status -> write.run());
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
