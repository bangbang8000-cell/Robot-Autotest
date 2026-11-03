package com.qa.autotest.application.iam;

import com.qa.autotest.application.repository.ProjectRepository;
import com.qa.autotest.application.repository.UserRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.iam.Project;
import com.qa.autotest.domain.iam.ProjectMember;
import com.qa.autotest.domain.iam.RoleCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 项目与成员管理（IAM-01 / IAM-03）。 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final UserRepository users;
    private final AccessControlService access;
    private final AuditService audit;

    public ProjectService(ProjectRepository projects, UserRepository users,
                          AccessControlService access, AuditService audit) {
        this.projects = projects;
        this.users = users;
        this.access = access;
        this.audit = audit;
    }

    /** 当前用户可见的项目（管理员全部，其他人按成员关系）。 */
    public List<Project> visible() {
        return access.visibleProjects();
    }

    public Project get(long projectId) {
        access.bindProject(projectId);
        return projects.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目不存在"));
    }

    @Audited(action = "project:create", resource = "project")
    @Transactional
    public Project create(String code, String name, String description, String visibility) {
        access.require(PermissionResource.PROJECT, PermissionAction.CREATE);
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "项目编码不能为空");
        }
        if (projects.codeExists(code.trim(), null)) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目编码已存在：" + code);
        }
        long ownerId = TenantContext.requireUserId();
        long id = projects.insert(new Project(null, code.trim(), name, description, true,
                ownerId, visibility == null || visibility.isBlank() ? "private" : visibility));
        projects.addMember(id, ownerId, RoleCode.ADMIN.code(), ownerId);
        audit.recordChange("project:create", "project", String.valueOf(id), null,
                "code=" + code + "; name=" + name + "; owner=" + ownerId);
        return projects.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目不存在"));
    }

    public Project update(long projectId, String name, String description, boolean enabled,
                          Long ownerId, String visibility) {
        access.bindProject(projectId);
        access.require(PermissionResource.PROJECT, PermissionAction.UPDATE);
        projects.update(projectId, name, description, enabled, ownerId, visibility,
                TenantContext.userId());
        audit.record("project.update", "project", String.valueOf(projectId), "name=" + name);
        return get(projectId);
    }

    @Audited(action = "project:delete", resource = "project")
    public void delete(long projectId) {
        access.bindProject(projectId);
        access.require(PermissionResource.PROJECT, PermissionAction.DELETE);
        Project existing = projects.findById(projectId).orElse(null);
        projects.delete(projectId);
        audit.recordChange("project:delete", "project", String.valueOf(projectId),
                existing == null ? null : "code=" + existing.code() + "; name=" + existing.name(),
                null);
    }

    public List<ProjectMember> members(long projectId) {
        access.bindProject(projectId);
        access.require(PermissionResource.PROJECT, PermissionAction.READ);
        return projects.members(projectId);
    }

    @Audited(action = "member:update", resource = "project_member")
    @Transactional
    public ProjectMember addMember(long projectId, long userId, String roleCode) {
        access.bindProject(projectId);
        access.require(PermissionResource.PROJECT, PermissionAction.UPDATE);
        users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        String role = normalizeRole(roleCode);
        if (projects.findMember(projectId, userId).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "该用户已是项目成员");
        }
        projects.addMember(projectId, userId, role, TenantContext.userId());
        audit.recordChange("member:update", "project_member", projectId + ":" + userId,
                "role=none", "role=" + role);
        return projects.findMember(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "成员不存在"));
    }

    @Audited(action = "member:update", resource = "project_member")
    public ProjectMember updateMemberRole(long projectId, long userId, String roleCode) {
        access.bindProject(projectId);
        access.require(PermissionResource.PROJECT, PermissionAction.UPDATE);
        ProjectMember member = projects.findMember(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "成员不存在"));
        String role = normalizeRole(roleCode);
        projects.updateMemberRole(projectId, userId, role, TenantContext.userId());
        audit.recordChange("member:update", "project_member", projectId + ":" + userId,
                "role=" + member.roleCode(), "role=" + role);
        return projects.findMember(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "成员不存在"));
    }

    @Transactional
    public void removeMember(long projectId, long userId) {
        access.bindProject(projectId);
        access.require(PermissionResource.PROJECT, PermissionAction.UPDATE);
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目不存在"));
        if (project.ownerId() != null && project.ownerId() == userId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "不能移除项目所有者");
        }
        projects.removeMember(projectId, userId);
        audit.record("project.member-remove", "project_member",
                projectId + ":" + userId, "");
    }

    private static String normalizeRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "项目角色不能为空");
        }
        try {
            return RoleCode.from(roleCode).code();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "非法项目角色：" + roleCode);
        }
    }
}
