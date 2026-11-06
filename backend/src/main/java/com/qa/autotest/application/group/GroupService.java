package com.qa.autotest.application.group;

import com.qa.autotest.domain.group.EffectiveRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

/** 用户组 CRUD / 成员 / 组-项目授权（IAMX-01、IAMX-02）。 */
@Service
public class GroupService {

    private final JdbcTemplate jdbc;
    private final EffectiveRoleResolver resolver;

    public GroupService(JdbcTemplate jdbc, EffectiveRoleResolver resolver) {
        this.jdbc = jdbc;
        this.resolver = resolver;
    }

    public List<Map<String, Object>> groups() {
        return jdbc.queryForList("SELECT id, name, description FROM user_group ORDER BY id");
    }

    public long createGroup(String name, String description) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO user_group (name, description) VALUES (?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, description);
            return ps;
        }, keys);
        resolver.invalidateAll();
        return keys.getKey() == null ? 0L : keys.getKey().longValue();
    }

    public void addMember(long groupId, long userId) {
        jdbc.update("INSERT IGNORE INTO user_group_member (group_id, user_id) VALUES (?, ?)",
                groupId, userId);
        resolver.invalidateAll();
    }

    public List<Long> members(long groupId) {
        return jdbc.queryForList("SELECT user_id FROM user_group_member WHERE group_id = ?",
                Long.class, groupId);
    }

    public void grant(long projectId, long groupId, String roleCode) {
        if (EffectiveRole.parse(roleCode) == EffectiveRole.NONE) {
            throw new IllegalArgumentException("未知角色: " + roleCode);
        }
        jdbc.update("INSERT INTO project_group_role (project_id, group_id, role_code) "
                        + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE role_code = VALUES(role_code)",
                projectId, groupId, roleCode.trim().toUpperCase(java.util.Locale.ROOT));
        resolver.invalidate(projectId);
    }

    /** 授权即时生效的读路径：变更后缓存已失效，这里重新解析。 */
    public Map<String, Object> effective(long projectId, long userId) {
        EffectiveRole role = resolver.resolve(userId, projectId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("userId", userId);
        result.put("role", role.name());
        result.put("rank", role.rank());
        return result;
    }
}
