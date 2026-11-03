package com.qa.autotest.application.iam;

import com.qa.autotest.application.repository.AuditRepository;
import com.qa.autotest.application.repository.ProjectRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.iam.AuditEntry;
import com.qa.autotest.domain.iam.Project;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 审计查询（HRD-02）。
 *
 * <p>权限口径：<b>仅全局管理员或项目所有者</b>。其他角色一律 403，且消息不含项目是否存在，
 * 防止用审计接口枚举项目。审计接口只读，不提供删改。
 */
@Service
public class AuditQueryService {

    /** 导出上限，防止一次拉爆内存。 */
    static final int EXPORT_LIMIT = 10000;

    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private static final String CSV_HEADER = "时间,操作人,动作,资源类型,资源ID,变更摘要,IP";

    private final AuditRepository audit;
    private final ProjectRepository projects;
    private final AccessControlService access;

    public AuditQueryService(AuditRepository audit, ProjectRepository projects,
                             AccessControlService access) {
        this.audit = audit;
        this.projects = projects;
        this.access = access;
    }

    public PageResult<AuditEntry> page(AuditQuery query) {
        authorize(query.projectId());
        return audit.page(query.normalized());
    }

    public String exportCsv(AuditQuery query) {
        authorize(query.projectId());
        List<AuditEntry> rows = audit.export(query.normalized(), EXPORT_LIMIT);
        StringBuilder sb = new StringBuilder(CSV_HEADER).append('\n');
        for (AuditEntry row : rows) {
            // 只对摘要做脱敏：动作名如 credential:upsert 会被敏感正则误伤，其余字段本身无敏感值
            sb.append(csv(timeText(row))).append(',')
                    .append(csv(row.operator())).append(',')
                    .append(csv(row.action(), false)).append(',')
                    .append(csv(row.resourceType(), false)).append(',')
                    .append(csv(row.resourceId(), false)).append(',')
                    .append(csv(AuditService.sanitize(row.summary()), false)).append(',')
                    .append(csv(row.ip(), false)).append('\n');
        }
        return sb.toString();
    }

    /** 管理员可查全局；其他人必须是该项目所有者。 */
    void authorize(Long projectId) {
        long userId = TenantContext.requireUserId();
        if (access.isAdmin(userId)) {
            return;
        }
        if (projectId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "无权限：审计查询仅限管理员或项目所有者");
        }
        Optional<Project> project = projects.findById(projectId);
        boolean owner = project.isPresent() && project.get().ownerId() != null
                && project.get().ownerId().longValue() == userId;
        if (!owner) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "无权限：审计查询仅限管理员或项目所有者");
        }
    }

    private static String csv(String value) {
        return csv(value, true);
    }

    private static String csv(String value, boolean mask) {
        if (value == null) {
            return "";
        }
        String text = mask ? AuditService.sanitize(value) : value;
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    /** 行内时间文本，供 CSV 使用。 */
    private static String timeText(AuditEntry row) {
        Instant at = row.occurredAt();
        return at == null ? "" : TIME.format(at);
    }
}
