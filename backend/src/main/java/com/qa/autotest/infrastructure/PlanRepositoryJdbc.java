package com.qa.autotest.infrastructure;

import com.qa.autotest.application.repository.PlanRepository;
import com.qa.autotest.common.tenant.TenantGuard;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.plan.OverlapPolicy;
import com.qa.autotest.domain.plan.PlanLastRun;
import com.qa.autotest.domain.plan.PlanRecord;
import com.qa.autotest.domain.plan.PlanStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 执行计划仓储实现（PLN-01 / PLN-02）。 */
@Repository
public class PlanRepositoryJdbc implements PlanRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final RowMapper<PlanRecord> MAPPER = (rs, rowNum) -> new PlanRecord(
            rs.getLong("id"),
            rs.getLong("project_id"),
            rs.getString("name"),
            rs.getObject("test_set_id") == null ? null : rs.getLong("test_set_id"),
            rs.getObject("environment_id") == null ? null : rs.getLong("environment_id"),
            suites(rs.getString("suite_paths_json")),
            rs.getString("include_tag"),
            rs.getInt("concurrency"),
            rs.getInt("timeout_seconds"),
            rs.getString("cron"),
            rs.getString("timezone"),
            PlanStatus.fromDb(rs.getString("status")),
            OverlapPolicy.fromDb(rs.getString("overlap_policy")),
            JdbcSupport.instant(rs.getTimestamp("last_fire_at")),
            JdbcSupport.instant(rs.getTimestamp("next_fire_at")),
            rs.getObject("created_by") == null ? null : rs.getLong("created_by"),
            JdbcSupport.instant(rs.getTimestamp("created_at")));

    private static final String SELECT = """
            SELECT id, project_id, name, test_set_id, environment_id, suite_paths_json, include_tag,
                   concurrency, timeout_seconds, cron, timezone, status, overlap_policy,
                   last_fire_at, next_fire_at, created_by, created_at
              FROM execution_plan
            """;

    private final JdbcTemplate jdbc;

    public PlanRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<PlanRecord> page(long projectId, int page, int size) {
        String countSql = "SELECT COUNT(*) FROM execution_plan WHERE project_id = ?";
        TenantGuard.requireProjectScope(countSql, "execution_plan");
        Long total = jdbc.queryForObject(countSql, Long.class, projectId);
        String listSql = SELECT + """
                 WHERE project_id = ?
                 ORDER BY id DESC
                 LIMIT ? OFFSET ?
                """;
        TenantGuard.requireProjectScope(listSql, "execution_plan");
        List<PlanRecord> rows = jdbc.query(listSql, MAPPER,
                projectId, size, (long) (page - 1) * size);
        return new PageResult<>(rows, total == null ? 0 : total, page, size);
    }

    @Override
    public Optional<PlanRecord> find(long projectId, long id) {
        String sql = SELECT + " WHERE project_id = ? AND id = ?";
        TenantGuard.requireProjectScope(sql, "execution_plan");
        return JdbcSupport.first(jdbc.query(sql, MAPPER, projectId, id));
    }

    @Override
    public long insert(PlanRecord draft, Long operatorId) {
        String sql = """
                INSERT INTO execution_plan (project_id, name, test_set_id, environment_id,
                                            suite_paths_json, include_tag, concurrency,
                                            timeout_seconds, cron, timezone, status, overlap_policy,
                                            last_fire_at, next_fire_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        TenantGuard.requireProjectScope(sql, "execution_plan");
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, draft.projectId());
            ps.setString(2, draft.name());
            ps.setObject(3, draft.testSetId(), java.sql.Types.BIGINT);
            ps.setObject(4, draft.environmentId(), java.sql.Types.BIGINT);
            ps.setString(5, suitesJson(draft.suitePaths()));
            ps.setString(6, draft.includeTag());
            ps.setInt(7, draft.concurrency());
            ps.setInt(8, draft.timeoutSeconds());
            ps.setString(9, draft.cron());
            ps.setString(10, draft.timezone());
            ps.setString(11, draft.status().dbValue());
            ps.setString(12, draft.overlapPolicy().dbValue());
            ps.setTimestamp(13, timestamp(draft.lastFireAt()));
            ps.setTimestamp(14, timestamp(draft.nextFireAt()));
            ps.setObject(15, operatorId, java.sql.Types.BIGINT);
            ps.setObject(16, operatorId, java.sql.Types.BIGINT);
            return ps;
        }, key);
        return key.getKey() == null ? 0 : key.getKey().longValue();
    }

    @Override
    public void update(PlanRecord plan, Long operatorId) {
        String sql = """
                UPDATE execution_plan
                   SET name = ?, test_set_id = ?, environment_id = ?, suite_paths_json = ?,
                       include_tag = ?, concurrency = ?, timeout_seconds = ?, cron = ?,
                       timezone = ?, status = ?, overlap_policy = ?, last_fire_at = ?,
                       next_fire_at = ?, updated_by = ?
                 WHERE project_id = ? AND id = ?
                """;
        TenantGuard.requireProjectScope(sql, "execution_plan");
        jdbc.update(sql,
                plan.name(), plan.testSetId(), plan.environmentId(), suitesJson(plan.suitePaths()),
                plan.includeTag(), plan.concurrency(), plan.timeoutSeconds(), plan.cron(),
                plan.timezone(), plan.status().dbValue(), plan.overlapPolicy().dbValue(),
                timestamp(plan.lastFireAt()), timestamp(plan.nextFireAt()), operatorId,
                plan.projectId(), plan.id());
    }

    @Override
    public void delete(long projectId, long id) {
        String sql = "DELETE FROM execution_plan WHERE project_id = ? AND id = ?";
        TenantGuard.requireProjectScope(sql, "execution_plan");
        jdbc.update(sql, projectId, id);
    }

    @Override
    public int updateStatus(long projectId, long id, PlanStatus status) {
        String sql = "UPDATE execution_plan SET status = ? WHERE project_id = ? AND id = ?";
        TenantGuard.requireProjectScope(sql, "execution_plan");
        return jdbc.update(sql, status.dbValue(), projectId, id);
    }

    @Override
    public int updateSchedule(long projectId, long id, Instant lastFireAt, Instant nextFireAt) {
        String sql = """
                UPDATE execution_plan
                   SET last_fire_at = ?, next_fire_at = ?
                 WHERE project_id = ? AND id = ?
                """;
        TenantGuard.requireProjectScope(sql, "execution_plan");
        return jdbc.update(sql, timestamp(lastFireAt), timestamp(nextFireAt), projectId, id);
    }

    /**
     * 领取到点计划：{@code FOR UPDATE SKIP LOCKED} 保证多实例下同一条计划只会被一个 tick 拿到。
     * 必须在调度事务内调用（本方法本身不带 @Transactional，由 PlanScheduleService 决定事务边界）。
     */
    @Override
    public List<PlanRecord> lockDue(Instant now, int limit) {
        String sql = SELECT + """
                 WHERE project_id > 0
                   AND status = 'enabled'
                   AND next_fire_at IS NOT NULL
                   AND next_fire_at <= ?
                 ORDER BY next_fire_at ASC
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """;
        TenantGuard.requireProjectScope(sql, "execution_plan");
        return jdbc.query(sql, MAPPER, timestamp(now), Math.max(1, limit));
    }

    @Override
    public boolean hasActiveRun(long projectId, long planId) {
        String sql = """
                SELECT COUNT(*) FROM run
                 WHERE project_id = ? AND plan_id = ?
                   AND run_status IN ('queued', 'preparing', 'running')
                """;
        TenantGuard.requireProjectScope(sql, "run");
        Long count = jdbc.queryForObject(sql, Long.class, projectId, planId);
        return count != null && count > 0;
    }

    @Override
    public int countActiveScheduledRuns(long projectId) {
        String sql = """
                SELECT COUNT(*) FROM run
                 WHERE project_id = ? AND trigger_type = 'schedule'
                   AND run_status IN ('queued', 'preparing', 'running')
                """;
        TenantGuard.requireProjectScope(sql, "run");
        Long count = jdbc.queryForObject(sql, Long.class, projectId);
        return count == null ? 0 : count.intValue();
    }

    @Override
    public List<Long> dependencies(long projectId, long planId) {
        String sql = """
                SELECT depends_on_plan_id FROM execution_plan_dependency
                 WHERE project_id = ? AND plan_id = ?
                 ORDER BY depends_on_plan_id
                """;
        TenantGuard.requireProjectScope(sql, "execution_plan_dependency");
        return List.copyOf(jdbc.queryForList(sql, Long.class, projectId, planId));
    }

    @Override
    public Map<Long, List<Long>> allDependencies(long projectId) {
        String sql = """
                SELECT plan_id, depends_on_plan_id FROM execution_plan_dependency
                 WHERE project_id = ?
                 ORDER BY plan_id, depends_on_plan_id
                """;
        TenantGuard.requireProjectScope(sql, "execution_plan_dependency");
        Map<Long, List<Long>> edges = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            edges.computeIfAbsent(rs.getLong("plan_id"), k -> new ArrayList<>())
                    .add(rs.getLong("depends_on_plan_id"));
        }, projectId);
        return edges;
    }

    @Override
    public void replaceDependencies(long projectId, long planId, List<Long> dependsOn, Long operatorId) {
        deleteDependencies(projectId, planId);
        if (dependsOn == null || dependsOn.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO execution_plan_dependency
                    (project_id, plan_id, depends_on_plan_id, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?)
                """;
        TenantGuard.requireProjectScope(sql, "execution_plan_dependency");
        List<Object[]> args = new ArrayList<>();
        for (Long dependency : dependsOn) {
            args.add(new Object[]{projectId, planId, dependency, operatorId, operatorId});
        }
        jdbc.batchUpdate(sql, args);
    }

    @Override
    public void deleteDependencies(long projectId, long planId) {
        String sql = "DELETE FROM execution_plan_dependency WHERE project_id = ? AND plan_id = ?";
        TenantGuard.requireProjectScope(sql, "execution_plan_dependency");
        jdbc.update(sql, projectId, planId);
    }

    @Override
    public void deleteAllEdges(long projectId, long planId) {
        String sql = """
                DELETE FROM execution_plan_dependency
                 WHERE project_id = ? AND (plan_id = ? OR depends_on_plan_id = ?)
                """;
        TenantGuard.requireProjectScope(sql, "execution_plan_dependency");
        jdbc.update(sql, projectId, planId, planId);
    }

    @Override
    public Optional<PlanLastRun> latestRun(long projectId, long planId) {
        String sql = """
                SELECT run_id, run_status, finished_at FROM run
                 WHERE project_id = ? AND plan_id = ?
                 ORDER BY id DESC
                 LIMIT 1
                """;
        TenantGuard.requireProjectScope(sql, "run");
        return JdbcSupport.first(jdbc.query(sql, (rs, rowNum) -> new PlanLastRun(
                rs.getString("run_id"),
                rs.getString("run_status"),
                JdbcSupport.instant(rs.getTimestamp("finished_at"))),
                projectId, planId));
    }

    private static List<String> suites(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String suitesJson(List<String> suites) {
        try {
            return JSON.writeValueAsString(suites == null ? List.of() : suites);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
