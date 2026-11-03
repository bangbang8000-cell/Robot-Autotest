package com.qa.autotest.application.iam;

import com.qa.autotest.application.repository.ProjectRepository;
import com.qa.autotest.application.repository.UserRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.domain.iam.PermissionAction;
import com.qa.autotest.domain.iam.PermissionMatrix;
import com.qa.autotest.domain.iam.PermissionResource;
import com.qa.autotest.domain.iam.Project;
import com.qa.autotest.domain.iam.ProjectMember;
import com.qa.autotest.domain.iam.RoleCode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 权限判定与项目访问校验（IAM-03 / IAM-04 的应用层实现）。
 *
 * <p>两层口径：接口注解做粗粒度拦截；这里做资源级校验（项目是否属于当前用户），
 * 同一接口被跨项目调用时只有这一层能拦住。
 */
@Service
public class AccessControlService {

    private static final List<RoleCode> PRIORITY =
            List.of(RoleCode.ADMIN, RoleCode.PM, RoleCode.ENGINEER, RoleCode.VIEWER);

    private final UserRepository users;
    private final ProjectRepository projects;

    public AccessControlService(UserRepository users, ProjectRepository projects) {
        this.users = users;
        this.projects = projects;
    }

    /** 当前请求用户是否是全局管理员。 */
    public boolean isAdmin() {
        return isAdmin(TenantContext.requireUserId());
    }

    public boolean isAdmin(long userId) {
        return users.roleCodesOf(userId).stream()
                .anyMatch(code -> code.equalsIgnoreCase(RoleCode.ADMIN.code()));
    }

    /** 当前用户在指定项目上的有效角色：全局管理员优先，其次项目成员角色，再次全局最高角色。 */
    public RoleCode effectiveRole(long userId, Long projectId) {
        List<String> globalCodes = users.roleCodesOf(userId);
        if (globalCodes.stream().anyMatch(code -> code.equalsIgnoreCase(RoleCode.ADMIN.code()))) {
            return RoleCode.ADMIN;
        }
        if (projectId != null) {
            Optional<ProjectMember> member = projects.findMember(projectId, userId);
            if (member.isPresent()) {
                return RoleCode.from(member.get().roleCode());
            }
        }
        return globalCodes.stream()
                .map(code -> {
                    try {
                        return RoleCode.from(code);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .min(Comparator.comparingInt(PRIORITY::indexOf))
                .orElse(null);
    }

    /** 按当前上下文（用户 + 项目）判权。 */
    public void require(PermissionResource resource, PermissionAction action) {
        long userId = TenantContext.requireUserId();
        Long projectId = TenantContext.projectId();
        RoleCode role = effectiveRole(userId, projectId);
        if (role == null || !PermissionMatrix.allows(role, resource, action)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "无权限：" + resource.name() + ":" + action.name());
        }
    }

    /**
     * 项目资源级校验：管理员跨项目放行（设计如此），其他人必须是项目成员。
     * <b>返回 403 而不是 404</b>，且消息不含项目是否存在的信息，防止枚举探测。
     */
    public void requireProjectAccess(long projectId) {
        long userId = TenantContext.requireUserId();
        if (isAdmin(userId)) {
            return;
        }
        Optional<Project> project = projects.findById(projectId);
        if (project.isEmpty() || !projects.findMember(projectId, userId).isPresent()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该项目");
        }
    }

    /**
     * 绑定路径上的项目到当前请求上下文（项目内接口统一走这里）。
     *
     * <p>路径参数是权威来源，不依赖请求头 {@code X-Project-Id}；非成员一律 403。
     */
    public void bindProject(long projectId) {
        requireProjectAccess(projectId);
        TenantContext.setProjectId(projectId);
    }

    /** 当前用户可见的项目。 */
    public List<Project> visibleProjects() {
        long userId = TenantContext.requireUserId();
        if (isAdmin(userId)) {
            return projects.page(null, 1, 1000).items();
        }
        return projects.findByMember(userId);
    }
}
