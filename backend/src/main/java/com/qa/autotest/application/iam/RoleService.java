package com.qa.autotest.application.iam;

import com.qa.autotest.application.repository.RoleRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.iam.Role;
import java.util.List;
import org.springframework.stereotype.Service;

/** 角色 CRUD（IAM-01）：内置角色只读，不可删除或改 code。 */
@Service
public class RoleService {

    private final RoleRepository roles;
    private final AccessControlService access;
    private final AuditService audit;

    public RoleService(RoleRepository roles, AccessControlService access, AuditService audit) {
        this.roles = roles;
        this.access = access;
        this.audit = audit;
    }

    public List<Role> list() {
        access.require(PermissionResource.USER_ROLE, PermissionAction.READ);
        return roles.findAll();
    }

    public Role create(String code, String name, String description, String permissionsJson) {
        access.require(PermissionResource.USER_ROLE, PermissionAction.CREATE);
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "角色编码不能为空");
        }
        if (roles.codeExists(code, null)) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色编码已存在：" + code);
        }
        long id = roles.insert(new Role(null, code.trim(), name, description, false, permissionsJson));
        audit.record("role.create", "role", String.valueOf(id), "code=" + code);
        return roles.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
    }

    @Audited(action = "role:update", resource = "role")
    public Role update(long id, String name, String description, String permissionsJson) {
        access.require(PermissionResource.USER_ROLE, PermissionAction.UPDATE);
        Role role = roles.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
        if (role.builtIn()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "内置角色不允许修改");
        }
        String before = "code=" + role.code() + "; name=" + role.name();
        roles.update(id, name, description, permissionsJson, TenantContext.userId());
        audit.recordChange("role:update", "role", String.valueOf(id), before, "name=" + name);
        return roles.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
    }

    public void delete(long id) {
        access.require(PermissionResource.USER_ROLE, PermissionAction.DELETE);
        Role role = roles.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
        if (role.builtIn()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "内置角色不允许删除");
        }
        roles.delete(id);
        audit.record("role.delete", "role", String.valueOf(id), "code=" + role.code());
    }
}
