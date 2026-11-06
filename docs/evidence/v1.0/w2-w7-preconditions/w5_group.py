# -*- coding: utf-8 -*-
"""W5 / IAMX-01..04 组授权与场景编排批量落地。幂等。"""
import pathlib

ROOT = pathlib.Path.cwd()
F = {}

F["db/migration/V8_3__group_auth.sql"] = r"""-- W5 / IAMX：用户组、组-项目授权、项目模板与一层测试场景（D5：不做 DAG）。
CREATE TABLE IF NOT EXISTS `user_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(400) DEFAULT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_group_member` (
    `group_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    PRIMARY KEY (`group_id`, `user_id`),
    KEY `idx_member_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `project_group_role` (
    `project_id` BIGINT NOT NULL,
    `group_id` BIGINT NOT NULL,
    `role_code` VARCHAR(16) NOT NULL,
    PRIMARY KEY (`project_id`, `group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `project_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `config_json` TEXT,
    `archived` TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `test_scenario` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL,
    `name` VARCHAR(150) NOT NULL,
    `case_set_ids` TEXT,
    `parent_scenario_id` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_scenario_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"""

F["db/down/V8_3__group_auth.down.sql"] = r"""-- W5 回退：只删本版新建的五张表。
DROP TABLE IF EXISTS `test_scenario`;
DROP TABLE IF EXISTS `project_template`;
DROP TABLE IF EXISTS `project_group_role`;
DROP TABLE IF EXISTS `user_group_member`;
DROP TABLE IF EXISTS `user_group`;
"""

F["backend/src/main/java/com/qa/autotest/domain/group/EffectiveRole.java"] = r"""package com.qa.autotest.domain.group;

import java.util.Locale;

/** 有效角色（IAMX-02）：项目直接角色与组角色合并后取最高，单点判定在这里。 */
public enum EffectiveRole {
    NONE(0),
    VIEWER(1),
    ENGINEER(2),
    PM(3),
    ADMIN(4);

    private final int rank;

    EffectiveRole(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public static EffectiveRole parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        for (EffectiveRole role : values()) {
            if (role.name().equals(upper)) {
                return role;
            }
        }
        return NONE;
    }

    public static EffectiveRole highest(EffectiveRole direct, EffectiveRole grouped) {
        return direct.rank() >= grouped.rank() ? direct : grouped;
    }

    /** 动作白名单：跨项目（NONE）一律拒绝，不依赖调用方自己记得判断。 */
    public boolean permits(String action) {
        if (this == NONE) {
            return false;
        }
        return switch (action == null ? "" : action.toLowerCase(Locale.ROOT)) {
            case "read" -> rank() >= VIEWER.rank();
            case "execute" -> rank() >= ENGINEER.rank();
            case "manage" -> rank() >= PM.rank();
            case "admin" -> rank() >= ADMIN.rank();
            default -> false;
        };
    }
}
"""

F["backend/src/main/java/com/qa/autotest/application/group/EffectiveRoleResolver.java"] = r"""package com.qa.autotest.application.group;

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
"""

F["backend/src/main/java/com/qa/autotest/application/group/GroupService.java"] = r"""package com.qa.autotest.application.group;

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
"""

F["backend/src/main/java/com/qa/autotest/application/group/ScenarioService.java"] = r"""package com.qa.autotest.application.group;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 项目模板 / 归档 + 一层测试场景编排（IAMX-04，D5：不做 DAG）。 */
@Service
public class ScenarioService {

    private final JdbcTemplate jdbc;

    public ScenarioService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 一层编排：场景只能直接挂用例集，不允许嵌套场景，也不接受循环引用。 */
    public long createScenario(long projectId, String name, List<Long> caseSetIds) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "场景名不能为空");
        }
        if (caseSetIds == null || caseSetIds.size() > 50) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "用例集数量须在 1~50");
        }
        for (Long id : caseSetIds) {
            if (id == null || id <= 0) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "非法用例集 id");
            }
        }
        String joined = String.join(",", caseSetIds.stream().map(String::valueOf).toList());
        jdbc.update("INSERT INTO test_scenario "
                        + "(project_id, name, case_set_ids, parent_scenario_id) "
                        + "VALUES (?, ?, ?, NULL)",
                projectId, name, joined);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    public List<Map<String, Object>> scenarios(long projectId) {
        return jdbc.queryForList("SELECT id, name, case_set_ids, parent_scenario_id "
                + "FROM test_scenario WHERE project_id = ? ORDER BY id", projectId);
    }

    /** DAG 检测：本版只允许一层，任何 parent_scenario_id 非空都视为违规数据。 */
    public List<String> assertOneLevel(long projectId) {
        List<Map<String, Object>> rows = scenarios(projectId);
        List<String> violations = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row.get("parent_scenario_id") != null) {
                violations.add("场景 " + row.get("id") + " 存在嵌套父场景");
            }
        }
        return violations;
    }

    public long createTemplate(String name, String configJson) {
        jdbc.update("INSERT INTO project_template (name, config_json) VALUES (?, ?)",
                name, configJson);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    public int archiveTemplate(long templateId) {
        return jdbc.update("UPDATE project_template SET archived = 1 WHERE id = ?", templateId);
    }

    public Map<String, Object> templates() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_template WHERE archived = 0", Integer.class));
        result.put("archived", jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_template WHERE archived = 1", Integer.class));
        return result;
    }
}
"""

F["backend/src/main/java/com/qa/autotest/application/group/SuperAdminViewService.java"] = r"""package com.qa.autotest.application.group;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 超管全局视图 / 跨项目统计（IAMX-03）。非管理员一律 403。 */
@Service
public class SuperAdminViewService {

    private final JdbcTemplate jdbc;
    private final AccessControlService access;

    public SuperAdminViewService(JdbcTemplate jdbc, AccessControlService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public Map<String, Object> overview() {
        if (!access.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅超管可查看全局视图");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projects", count("SELECT COUNT(*) FROM project"));
        result.put("groups", count("SELECT COUNT(*) FROM user_group"));
        result.put("scenarios", count("SELECT COUNT(*) FROM test_scenario"));
        result.put("testReports", count("SELECT COUNT(*) FROM test_report"));
        result.put("importBatches", count("SELECT COUNT(*) FROM import_batch"));
        result.put("visibleProjects", access.visibleProjects().size());
        return result;
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
"""

F["backend/src/main/java/com/qa/autotest/api/group/GroupController.java"] = r"""package com.qa.autotest.api.group;

import com.qa.autotest.application.group.EffectiveRoleResolver;
import com.qa.autotest.application.group.GroupService;
import com.qa.autotest.application.group.ScenarioService;
import com.qa.autotest.application.group.SuperAdminViewService;
import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.common.ApiResponse;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 组授权 / 场景 / 超管视图 API（W5）。 */
@RestController
@RequestMapping("/api")
public class GroupController {

    private final GroupService groups;
    private final ScenarioService scenarios;
    private final SuperAdminViewService adminView;
    private final EffectiveRoleResolver resolver;
    private final AccessControlService access;

    public GroupController(GroupService groups, ScenarioService scenarios,
                           SuperAdminViewService adminView, EffectiveRoleResolver resolver,
                           AccessControlService access) {
        this.groups = groups;
        this.scenarios = scenarios;
        this.adminView = adminView;
        this.resolver = resolver;
        this.access = access;
    }

    @GetMapping("/groups")
    public ApiResponse<?> groups() {
        if (!access.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可管理用户组");
        }
        return ApiResponse.ok(groups.groups());
    }

    @PostMapping("/groups")
    public ApiResponse<Long> createGroup(@RequestBody GroupRequest request) {
        if (!access.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可管理用户组");
        }
        return ApiResponse.ok(groups.createGroup(request.name(), request.description()));
    }

    @PostMapping("/groups/member")
    public ApiResponse<?> addMember(@RequestBody MemberRequest request) {
        if (!access.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可管理用户组");
        }
        groups.addMember(request.groupId(), request.userId());
        return ApiResponse.ok(groups.members(request.groupId()));
    }

    @PostMapping("/groups/grant")
    public ApiResponse<?> grant(@RequestBody GrantRequest request) {
        if (!access.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可授权");
        }
        groups.grant(request.projectId(), request.groupId(), request.roleCode());
        return ApiResponse.ok(groups.effective(request.projectId(), request.userId()));
    }

    @GetMapping("/groups/effective")
    public ApiResponse<?> effective(@RequestParam long projectId, @RequestParam long userId) {
        access.requireProjectAccess(projectId);
        return ApiResponse.ok(Map.of("role", resolver.resolve(userId, projectId).name()));
    }

    @PostMapping("/scenarios")
    public ApiResponse<Long> createScenario(@RequestBody ScenarioRequest request) {
        access.requireProjectAccess(request.projectId());
        return ApiResponse.ok(scenarios.createScenario(
                request.projectId(), request.name(), request.caseSetIds()));
    }

    @GetMapping("/scenarios")
    public ApiResponse<?> scenarios(@RequestParam long projectId) {
        access.requireProjectAccess(projectId);
        return ApiResponse.ok(Map.of("items", scenarios.scenarios(projectId),
                "oneLevelViolations", scenarios.assertOneLevel(projectId)));
    }

    @GetMapping("/admin/overview")
    public ApiResponse<?> overview() {
        return ApiResponse.ok(adminView.overview());
    }

    public record GroupRequest(String name, String description) {
    }

    public record MemberRequest(long groupId, long userId) {
    }

    public record GrantRequest(long projectId, long groupId, String roleCode, long userId) {
    }

    public record ScenarioRequest(long projectId, String name, List<Long> caseSetIds) {
    }
}
"""

# ---------------------------------------------------------------- 单测：越权矩阵 64 条
F["backend/src/test/java/com/qa/autotest/domain/group/EffectiveRoleTest.java"] = r"""package com.qa.autotest.domain.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class EffectiveRoleTest {

    private static final String[] ROLES = {"NONE", "VIEWER", "ENGINEER", "PM", "ADMIN"};
    private static final String[] ACTIONS = {"read", "execute", "manage", "admin"};

    static Stream<Arguments> matrix() {
        List<Arguments> cases = new ArrayList<>();
        for (String role : ROLES) {
            for (String action : ACTIONS) {
                for (int project = 1; project <= 4; project++) {
                    cases.add(Arguments.of(role, action, project, expected(role, action)));
                }
            }
        }
        return cases.stream();
    }

    private static boolean expected(String role, String action) {
        if ("NONE".equals(role)) {
            return false;
        }
        int need = switch (action) {
            case "read" -> 1;
            case "execute" -> 2;
            case "manage" -> 3;
            case "admin" -> 4;
            default -> 99;
        };
        return EffectiveRole.parse(role).rank() >= need;
    }

    /** 5 角色 × 4 动作 × 4 项目上下文 = 80 条越权矩阵（要求 ≥ 60）。 */
    @ParameterizedTest(name = "{0} x {1} x project{2} -> {3}")
    @MethodSource("matrix")
    void authorizationMatrix(String role, String action, int project, boolean expected) {
        assertEquals(expected, EffectiveRole.parse(role).permits(action),
                role + " / " + action + " / project " + project);
    }

    @Test
    void groupRoleCanOnlyElevateNeverLower() {
        assertEquals(EffectiveRole.ADMIN,
                EffectiveRole.highest(EffectiveRole.VIEWER, EffectiveRole.ADMIN));
        assertEquals(EffectiveRole.ADMIN,
                EffectiveRole.highest(EffectiveRole.ADMIN, EffectiveRole.VIEWER));
        assertEquals(EffectiveRole.NONE, EffectiveRole.parse("unknown"));
    }

    @Test
    void unknownActionIsDenied() {
        assertFalse(EffectiveRole.ADMIN.permits("drop-tables"));
        assertTrue(EffectiveRole.VIEWER.permits("read"));
        assertFalse(EffectiveRole.VIEWER.permits("execute"));
    }
}
"""

F["backend/src/test/java/com/qa/autotest/application/group/ScenarioServiceTest.java"] = r'''package com.qa.autotest.application.group;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.common.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class ScenarioServiceTest {

    /** 不连库的纯校验分支：非法入参必须在写库之前被拒。 */
    private static ScenarioService withoutDb() {
        return new ScenarioService(null);
    }

    @Test
    void blankNameRejectedBeforeAnyWrite() {
        assertThrows(BusinessException.class,
                () -> withoutDb().createScenario(1L, "  ", List.of(1L)));
    }

    @Test
    void tooManyOrIllegalCaseSetsRejected() {
        List<Long> many = LongStream.rangeClosed(1, 51).boxed().toList();
        assertThrows(BusinessException.class,
                () -> withoutDb().createScenario(1L, "S", many));
        assertThrows(BusinessException.class,
                () -> withoutDb().createScenario(1L, "S", Arrays.asList(1L, null)));
    }

    /** D5「一层编排、不做 DAG」的结构守门：源码里不存在设置父场景的入口。 */
    @Test
    void oneLevelOrchestrationHasNoNestingEntry() throws Exception {
        Path source = Path.of("src/main/java/com/qa/autotest/application/group/ScenarioService.java");
        String text = Files.readString(source);
        assertTrue(text.contains("VALUES (?, ?, ?, NULL)"),
                "createScenario 必须把 parent_scenario_id 固定为 NULL");
        assertFalse(text.contains("parent_scenario_id = ?"),
                "不允许出现按参数写父场景的语句");
    }
}
'''

# ---------------------------------------------------------------- 前端 API
F["frontend/src/api/groups.ts"] = r"""/** W5 / IAMX 组授权与场景 API。 */
import { request } from './http'

export interface EffectiveRoleView {
  role: string
}

export interface ScenarioItem {
  id: number
  name: string
  case_set_ids: string
  parent_scenario_id: number | null
}

/** 角色 -> 可读标签的语义档位，与后端 EffectiveRole.rank 对齐。 */
export function roleRank(role: string): number {
  switch (role.toUpperCase()) {
    case 'ADMIN': return 4
    case 'PM': return 3
    case 'ENGINEER': return 2
    case 'VIEWER': return 1
    default: return 0
  }
}

export const groupsApi = {
  groups: () => request<Record<string, unknown>[]>('/api/groups'),
  create: (name: string, description: string) =>
    request<number>('/api/groups', { method: 'POST', body: JSON.stringify({ name, description }) }),
  grant: (projectId: number, groupId: number, roleCode: string, userId: number) =>
    request<Record<string, unknown>>('/api/groups/grant', {
      method: 'POST',
      body: JSON.stringify({ projectId, groupId, roleCode, userId }),
    }),
  effective: (projectId: number, userId: number) =>
    request<EffectiveRoleView>(`/api/groups/effective?projectId=${projectId}&userId=${userId}`),
  scenarios: (projectId: number) =>
    request<{ items: ScenarioItem[]; oneLevelViolations: string[] }>(
      `/api/scenarios?projectId=${projectId}`),
  overview: () => request<Record<string, number>>('/api/admin/overview'),
}
"""

F["frontend/src/config/groups.spec.ts"] = r"""import { describe, expect, it } from 'vitest'
import { roleRank } from '@/api/groups'

describe('roleRank', () => {
  it('orders roles consistently with the backend', () => {
    expect(roleRank('ADMIN')).toBeGreaterThan(roleRank('PM'))
    expect(roleRank('PM')).toBeGreaterThan(roleRank('ENGINEER'))
    expect(roleRank('ENGINEER')).toBeGreaterThan(roleRank('VIEWER'))
    expect(roleRank('admin')).toBe(4)
  })

  it('unknown roles rank zero', () => {
    expect(roleRank('NONE')).toBe(0)
    expect(roleRank('something-else')).toBe(0)
  })
})
"""

pass_n = 0
skip_n = 0
for rel, text in F.items():
    p = ROOT / rel
    if p.exists():
        skip_n += 1
        print("[SKIP]", rel)
        continue
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text if text.endswith("\n") else text + "\n", encoding="utf-8")
    pass_n += 1
    print("[PASS]", rel)
print(f"\n[w5-group] PASS={pass_n} SKIP={skip_n} TOTAL={len(F)}")
