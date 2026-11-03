package com.qa.autotest.application.asset;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.LabelRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import java.util.List;
import org.springframework.stereotype.Service;

/** 技术域标签（AST-04）：设备与用例共用同一套标签，避免两处口径漂移。 */
@Service
public class LabelService {

    private final LabelRepository labels;
    private final AccessControlService access;
    private final AuditService audit;

    public LabelService(LabelRepository labels, AccessControlService access, AuditService audit) {
        this.labels = labels;
        this.access = access;
        this.audit = audit;
    }

    public List<String> values(long projectId, String labelKey) {
        access.bindProject(projectId);
        access.require(PermissionResource.ASSET, PermissionAction.READ);
        return labels.values(projectId, requireKey(labelKey));
    }

    public void add(long projectId, String labelKey, String labelValue, String description) {
        access.bindProject(projectId);
        access.require(PermissionResource.ASSET, PermissionAction.UPDATE);
        String key = requireKey(labelKey);
        if (labelValue == null || labelValue.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "标签值不能为空");
        }
        if (labels.exists(projectId, key, labelValue)) {
            throw new BusinessException(ErrorCode.CONFLICT, "标签已存在：" + labelValue);
        }
        labels.add(projectId, key, labelValue, description, TenantContext.userId());
        audit.record("label.add", "project_label", projectId + ":" + key, labelValue);
    }

    public void remove(long projectId, String labelKey, String labelValue) {
        access.bindProject(projectId);
        access.require(PermissionResource.ASSET, PermissionAction.UPDATE);
        labels.remove(projectId, requireKey(labelKey), labelValue);
        audit.record("label.remove", "project_label", projectId + ":" + labelKey, labelValue);
    }

    private static String requireKey(String labelKey) {
        if (labelKey == null || labelKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "标签键不能为空");
        }
        return labelKey;
    }
}
