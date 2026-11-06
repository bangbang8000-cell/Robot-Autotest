package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.PlanGroupRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import com.qa.autotest.domain.plan.PlanGroup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 计划组仓储实现（ORCH-01）。边不在这里写，见 {@link PlanRepositoryJdbc}。 */
@Repository
public class PlanGroupRepositoryJdbc implements PlanGroupRepository {

    private static final RowMapper<PlanGroup> MAPPER = (rs, rowNum) -> new PlanGroup(
            rs.getLong("id"),
            rs.getLong("project_id"),
            rs.getString("name"),
            rs.getString("description"),
            List.of(),
            rs.getObject("created_by") == null ? null : rs.getLong("created_by"),
            JdbcSupport.instant(rs.getTimestamp("created_at")));

    private static final String SELECT = """
            SELECT id, project_id, name, description, created_by, created_at
              FROM plan_group
            """;

    /**
     * 成员查询 join execution_plan 取名称：成环报错要报「计划名」而不是 id，
     * 只存 id 的话报错还得再查一轮，循环里容易漏。
     */
    private static final String SELECT_ITEMS = """
            SELECT i.plan_id, p.name AS plan_name, i.sort_order
              FROM plan_group_item i
              JOIN execution_plan p ON p.id = i.plan_id
             WHERE i.project_id = ? AND i.group_id = ?
             ORDER BY i.sort_order ASC, i.id ASC
            """;

    private final JdbcTemplate jdbc;

    public PlanGroupRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PlanGroup> listByProject(long projectId) {
        String sql = SELECT + " WHERE project_id = ? ORDER BY id ASC";
        TenantGuard.requireProjectScope(sql, "plan_group");
        List<PlanGroup> groups = jdbc.query(sql, MAPPER, projectId);
        List<PlanGroup> withItems = new ArrayList<>(groups.size());
        for (PlanGroup group : groups) {
            withItems.add(new PlanGroup(group.id(), group.projectId(), group.name(),
                    group.description(), items(projectId, group.id()),
                    group.createdBy(), group.createdAt()));
        }
        return withItems;
    }

    @Override
    public Optional<PlanGroup> find(long projectId, long id) {
        String sql = SELECT + " WHERE project_id = ? AND id = ?";
        TenantGuard.requireProjectScope(sql, "plan_group");
        return JdbcSupport.first(jdbc.query(sql, MAPPER, projectId, id))
                .map(group -> new PlanGroup(group.id(), group.projectId(), group.name(),
                        group.description(), items(projectId, group.id()),
                        group.createdBy(), group.createdAt()));
    }

    @Override
    public Optional<PlanGroup> findByName(long projectId, String name) {
        String sql = SELECT + " WHERE project_id = ? AND name = ?";
        TenantGuard.requireProjectScope(sql, "plan_group");
        return JdbcSupport.first(jdbc.query(sql, MAPPER, projectId, name));
    }

    @Override
    public long insert(PlanGroup draft, Long operatorId) {
        String sql = """
                INSERT INTO plan_group (project_id, name, description, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?)
                """;
        TenantGuard.requireProjectScope(sql, "plan_group");
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, draft.projectId());
            ps.setString(2, draft.name());
            ps.setString(3, draft.description());
            ps.setObject(4, operatorId, java.sql.Types.BIGINT);
            ps.setObject(5, operatorId, java.sql.Types.BIGINT);
            return ps;
        }, key);
        return key.getKey() == null ? 0 : key.getKey().longValue();
    }

    @Override
    public void update(long projectId, long id, String name, String description, Long operatorId) {
        String sql = """
                UPDATE plan_group
                   SET name = ?, description = ?, updated_by = ?
                 WHERE project_id = ? AND id = ?
                """;
        TenantGuard.requireProjectScope(sql, "plan_group");
        jdbc.update(sql, name, description, operatorId, projectId, id);
    }

    @Override
    public void delete(long projectId, long id) {
        String sql = "DELETE FROM plan_group WHERE project_id = ? AND id = ?";
        TenantGuard.requireProjectScope(sql, "plan_group");
        jdbc.update(sql, projectId, id);
    }

    /**
     * 追加到末尾。用 INSERT IGNORE 命中唯一键 (group_id, plan_id) 即跳过，
     * 这样「重复加入」天然幂等，不需要先查一遍再决定插不插（并发下查了也不准）。
     *
     * @return 实际新增条数；已在组内的不计
     */
    @Override
    public int appendItems(long projectId, long groupId, List<Long> planIds, Long operatorId) {
        if (planIds == null || planIds.isEmpty()) {
            return 0;
        }
        int nextOrder = nextSortOrder(projectId, groupId);
        String sql = """
                INSERT IGNORE INTO plan_group_item
                    (project_id, group_id, plan_id, sort_order, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        TenantGuard.requireProjectScope(sql, "plan_group_item");

        List<Object[]> batch = new ArrayList<>(planIds.size());
        int offset = 0;
        for (Long planId : planIds) {
            batch.add(new Object[]{projectId, groupId, planId, nextOrder + offset, operatorId, operatorId});
            offset++;
        }
        int[] affected = jdbc.batchUpdate(sql, batch);
        int inserted = 0;
        for (int row : affected) {
            // INSERT IGNORE 命中重复键时返回 0；Statement.SUCCESS_NO_INFO(-2) 视为成功。
            if (row > 0 || row == Statement.SUCCESS_NO_INFO) {
                inserted++;
            }
        }
        return inserted;
    }

    @Override
    public void removeItem(long projectId, long groupId, long planId) {
        String sql = "DELETE FROM plan_group_item WHERE project_id = ? AND group_id = ? AND plan_id = ?";
        TenantGuard.requireProjectScope(sql, "plan_group_item");
        jdbc.update(sql, projectId, groupId, planId);
    }

    /**
     * 全量重排：先删掉不在目标列表里的成员，再把目标列表按顺序写回 sort_order。
     * 用两条语句而不是「逐条 update」，是因为顺序是整体属性——只改一条会与其它条撞序。
     */
    @Override
    public void reorder(long projectId, long groupId, List<Long> planIds, Long operatorId) {
        List<Long> target = planIds == null ? List.of() : planIds;
        List<Long> existing = jdbc.query(SELECT_ITEMS, (rs, rowNum) -> rs.getLong("plan_id"),
                projectId, groupId);
        for (Long planId : existing) {
            if (!target.contains(planId)) {
                removeItem(projectId, groupId, planId);
            }
        }
        // 先落后再重排：sort_order 上没有唯一键，直接覆盖即可，但要保证新成员的顺序也正确。
        appendItems(projectId, groupId, target, operatorId);
        String sql = """
                UPDATE plan_group_item
                   SET sort_order = ?, updated_by = ?
                 WHERE project_id = ? AND group_id = ? AND plan_id = ?
                """;
        TenantGuard.requireProjectScope(sql, "plan_group_item");
        List<Object[]> batch = new ArrayList<>(target.size());
        for (int i = 0; i < target.size(); i++) {
            batch.add(new Object[]{i, operatorId, projectId, groupId, target.get(i)});
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(sql, batch);
        }
    }

    @Override
    public boolean existsInOtherProject(long projectId, long planId) {
        // 条件里刻意写 project_id <> ? 而不是不带 project_id：
        // 一是语义就是「属于别的项目」，二是 TenantGuard 要求 SQL 带租户条件。
        String sql = "SELECT COUNT(*) FROM execution_plan WHERE id = ? AND project_id <> ?";
        TenantGuard.requireProjectScope(sql, "execution_plan");
        Long count = jdbc.queryForObject(sql, Long.class, planId, projectId);
        return count != null && count > 0;
    }

    private List<PlanGroup.PlanGroupItem> items(long projectId, long groupId) {
        TenantGuard.requireProjectScope(SELECT_ITEMS, "plan_group_item");
        return jdbc.query(SELECT_ITEMS, (rs, rowNum) -> new PlanGroup.PlanGroupItem(
                rs.getLong("plan_id"),
                rs.getString("plan_name"),
                rs.getInt("sort_order")), projectId, groupId);
    }

    private int nextSortOrder(long projectId, long groupId) {
        String sql = """
                SELECT COALESCE(MAX(sort_order), -1) + 1 FROM plan_group_item
                 WHERE project_id = ? AND group_id = ?
                """;
        TenantGuard.requireProjectScope(sql, "plan_group_item");
        Integer next = jdbc.queryForObject(sql, Integer.class, projectId, groupId);
        return next == null ? 0 : next;
    }
}
