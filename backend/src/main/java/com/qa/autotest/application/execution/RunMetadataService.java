package com.qa.autotest.application.execution;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.iam.Audited;
import com.qa.autotest.application.repository.RunMetadataRepository;
import com.qa.autotest.application.repository.RunRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运行记录补录 metadata（HRD-02：{@code run:metadata-patch}）。
 *
 * <p>用途：平台触发产生的报告固定是 {@code output.xml}，metadata 可能缺失 RUN_ID / DUT 等
 * 对外报告必需项；本服务按项补录，并把「哪项从什么改成什么」写进审计。
 *
 * <p>值不允许存凭据：审计摘要与报告都会带上 metadata，这里是最后一道入口。
 */
@Service
public class RunMetadataService {

    /** 键名口径与 RF metadata 一致：全大写、下划线分隔、40 字符内。 */
    private static final Pattern KEY = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");

    private static final int MAX_VALUE = 200;

    private final RunRepository runs;
    private final RunMetadataRepository metadata;
    private final AccessControlService access;
    private final AuditService audit;

    public RunMetadataService(RunRepository runs, RunMetadataRepository metadata,
                              AccessControlService access, AuditService audit) {
        this.runs = runs;
        this.metadata = metadata;
        this.access = access;
        this.audit = audit;
    }

    /** 读取补录项（查看需要执行读权限）。 */
    public Map<String, String> list(long projectId, String runId) {
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.READ);
        requireRun(projectId, runId);
        return metadata.findAll(projectId, runId);
    }

    /**
     * 按项补录：值非空为覆盖写，值为空串表示删除该项。
     *
     * @return 补录后的全量 metadata（按写入顺序）
     */
    @Audited(action = "run:metadata-patch", resource = "run")
    @Transactional
    public Map<String, String> patch(long projectId, String runId, Map<String, String> patch) {
        if (patch == null || patch.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "metadata 不能为空");
        }
        access.bindProject(projectId);
        access.require(PermissionResource.EXECUTION, PermissionAction.UPDATE);
        requireRun(projectId, runId);

        Map<String, String> before = metadata.findAll(projectId, runId);
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> removed = new LinkedHashMap<>();
        Map<String, String> beforeChanged = new LinkedHashMap<>();
        Map<String, String> afterChanged = new LinkedHashMap<>();
        patch.forEach((rawKey, rawValue) -> {
            String key = rawKey == null ? "" : rawKey.trim();
            if (!KEY.matcher(key).matches()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "metadata 键不合法：" + rawKey + "（限 [A-Z][A-Z0-9_]{0,39}）");
            }
            String value = rawValue == null ? "" : rawValue.trim();
            if (value.length() > MAX_VALUE) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                        "metadata 值超长：" + key + "（上限 " + MAX_VALUE + " 字符）");
            }
            String old = before.get(key);
            if (value.isEmpty()) {
                if (old != null) {
                    removed.put(key, old);
                    beforeChanged.put(key, old);
                    afterChanged.put(key, "（删除）");
                }
                return;
            }
            if (value.equals(old)) {
                return; // 未变化不落库、不写审计
            }
            values.put(key, value);
            if (old != null) {
                beforeChanged.put(key, old);
            }
            afterChanged.put(key, value);
        });
        if (values.isEmpty() && removed.isEmpty()) {
            return before; // 全是无效变更，直接返回当前值
        }
        metadata.save(projectId, runId, values, removed.keySet(), TenantContext.requireUserId());
        audit.recordChange("run:metadata-patch", "run", runId,
                join(beforeChanged), join(afterChanged));
        return metadata.findAll(projectId, runId);
    }

    private void requireRun(long projectId, String runId) {
        runs.findByRunId(projectId, runId).orElseThrow(() ->
                new BusinessException(ErrorCode.NOT_FOUND, "运行不存在：" + runId));
    }

    private static String join(Map<String, String> items) {
        if (items.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        items.forEach((key, value) -> {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(key).append('=').append(value);
        });
        return sb.toString();
    }
}
