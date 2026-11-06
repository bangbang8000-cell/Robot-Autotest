package com.qa.autotest.application.group;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.domain.group.EffectiveRole;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 有效角色解析单点（IAMX-02）。
 *
 * <p>直接角色复用 v1.0 的 {@link AccessControlService#effectiveRole(long, Long)}，
 * 组角色从 {@code project_group_role} 叠加，两者取最高；结果按 (user, project) 缓存，
 * 任何组 / 成员 / 授权变更都必须调 {@link #invalidate(long)}，否则权限会「看起来没生效」。
 */
@Service
public class EffectiveRoleResolver {

    private final JdbcTemplate jdbc;
    private final AccessControlService access;
    private final Map<String, EffectiveRole> cache = new ConcurrentHashMap<>();

    public EffectiveRoleResolver(JdbcTemplate jdbc, AccessControlService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public EffectiveRole resolve(long userId, long projectId) {
        return cache.computeIfAbsent(userId + ":" + projectId,
                key -> compute(userId, projectId));
    }

    private EffectiveRole compute(long userId, long projectId) {
        EffectiveRole direct = EffectiveRole.parse(
                access.effectiveRole(userId, projectId).name());
        List<String> groupRoles = jdbc.queryForList(
                "SELECT r.role_code FROM project_group_role r "
                        + "JOIN user_group_member m ON m.group_id = r.group_id "
                        + "WHERE r.project_id = ? AND m.user_id = ?",
                String.class, projectId, userId);
        EffectiveRole grouped = EffectiveRole.NONE;
        for (String raw : groupRoles) {
            grouped = EffectiveRole.highest(grouped, EffectiveRole.parse(raw));
        }
        return EffectiveRole.highest(direct, grouped);
    }

    public void invalidate(long projectId) {
        cache.keySet().removeIf(key -> key.endsWith(":" + projectId));
    }

    public void invalidateAll() {
        cache.clear();
    }

    public boolean permitted(long userId, long projectId, String action) {
        return resolve(userId, projectId).permits(action);
    }
}
