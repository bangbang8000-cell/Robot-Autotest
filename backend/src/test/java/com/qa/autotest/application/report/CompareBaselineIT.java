package com.qa.autotest.application.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.infrastructure.ProjectRepositoryJdbc;
import com.qa.autotest.infrastructure.ReportReadRepositoryJdbc;
import com.qa.autotest.infrastructure.UserRepositoryJdbc;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 手动基线候选的真库一致性测试（RPT-03 补口），必须连真实 MySQL。
 *
 * <p><b>为什么必须连真库</b>：候选名单完全由一条 SQL 决定 —— finished 过滤、DUT/LINK 的
 * NULL 口径（{@code COALESCE}）、排除本轮、{@code started_at} 为 NULL 时的排序。这些点用替身
 * 仓储怎么写都自洽，写错了不会抛异常，只会安静地少给一条或多给一条，前端下拉看不出问题。
 *
 * <p>连库方式与 {@code OverviewRepositoryIT} / {@code CaseImportIT} 一致（ADR-0002：不用 H2），
 * 库名必须以 {@code _it} 结尾，防止误清开发库。
 */
@Tag("it")
class CompareBaselineIT {

    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "robot_it");
    private static final String DUT = "GPU-01";
    private static final String LINK = "GPU-01/mlx5_0 -> SW-01 Eth1/1";

    @Test
    @DisplayName("手动基线候选：同项目同环境 finished 倒序、排除本轮、NULL 开始时间排最后、跨项目 403")
    void 手动基线候选与真库一致() {
        assertThat(DB_NAME).as("集成测试库名必须以 _it 结尾，防止误清开发库").endsWith("_it");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        CompareService service = new CompareService(
                new ReportReadRepositoryJdbc(jdbc),
                new AccessControlService(new UserRepositoryJdbc(jdbc), new ProjectRepositoryJdbc(jdbc)));

        String stamp = String.valueOf(System.currentTimeMillis());
        String username = "it-cmp-" + stamp;
        String projectCode = "IT-CMP-" + stamp;
        String otherCode = "IT-CMP-OTHER-" + stamp;
        Long userId = null;
        Long projectId = null;
        Long otherProjectId = null;
        try {
            userId = seedUser(jdbc, username);
            projectId = seedProject(jdbc, projectCode, userId);
            otherProjectId = seedProject(jdbc, otherCode, null);
            seedMember(jdbc, projectId, userId);
            TenantContext.set(userId, username, null);

            // 本轮：同环境，但不该出现在自己的候选里
            String current = "IT-CMP-CUR-" + stamp;
            seedRun(jdbc, projectId, current, "finished", DUT, LINK, "NOW(3)");
            // 三条合法候选：按开始时间 3 天前 / 1 天前 / 昨天 + 未记录开始时间
            seedRun(jdbc, projectId, "IT-CMP-OLD-" + stamp, "finished", DUT, LINK,
                    "TIMESTAMPADD(DAY, -3, NOW(3))");
            seedRun(jdbc, projectId, "IT-CMP-NEAR-" + stamp, "finished", DUT, LINK,
                    "TIMESTAMPADD(DAY, -1, NOW(3))");
            seedRun(jdbc, projectId, "IT-CMP-NULL-" + stamp, "finished", DUT, LINK, "NULL");
            // 三条干扰：未完成 / 换 DUT / 换 LINK
            seedRun(jdbc, projectId, "IT-CMP-RUN-" + stamp, "running", DUT, LINK, "NOW(3)");
            seedRun(jdbc, projectId, "IT-CMP-DUT-" + stamp, "finished", "GPU-02", LINK, "NOW(3)");
            seedRun(jdbc, projectId, "IT-CMP-LNK-" + stamp, "finished", DUT, "GPU-01/mlx5_1", "NOW(3)");
            // 跨项目同环境：不能串台
            seedRun(jdbc, otherProjectId, "IT-CMP-OTHER-" + stamp, "finished", DUT, LINK, "NOW(3)");

            List<CompareService.BaselineCandidate> candidates = service.candidates(projectId, current);

            assertThat(candidates.stream().map(CompareService.BaselineCandidate::runId).toList())
                    .as("N4：同项目同环境优先（开始时间倒序、NULL 最后），跨环境候选随后，排除本轮/未完成/跨项目")
                    .containsExactly(
                            "IT-CMP-NEAR-" + stamp,
                            "IT-CMP-OLD-" + stamp,
                            "IT-CMP-NULL-" + stamp,
                            "IT-CMP-LNK-" + stamp,
                            "IT-CMP-DUT-" + stamp);
            assertThat(candidates.get(0).startedAt()).isNotNull();
            assertThat(candidates.get(2).startedAt()).as("未记录开始时间的运行按 null 返回").isNull();
            assertThat(candidates.get(0).crossEnvironment()).isFalse();
            assertThat(candidates.get(2).crossEnvironment()).isFalse();
            assertThat(candidates.get(3).crossEnvironment()).as("跨 LINK 候选要标记").isTrue();
            assertThat(candidates.get(4).crossEnvironment()).as("跨 DUT 候选要标记").isTrue();

            // 上限：仓储层按传入 limit 截断
            List<com.qa.autotest.domain.report.RunSnapshot> limited =
                    new ReportReadRepositoryJdbc(jdbc).listBaselineCandidates(
                            projectId, DUT, LINK, current, 2);
            assertThat(limited).as("limit=2 只给最近两条").hasSize(2);

            // 跨项目：当前用户不是 other 项目成员 → 403
            final Long foreignProjectId = otherProjectId;
            final String foreignRun = "IT-CMP-OTHER-" + stamp;
            assertThatThrownBy(() -> service.candidates(foreignProjectId, foreignRun))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(ErrorCode.FORBIDDEN));

            System.out.println("[RPT-03] 手动基线候选=" + candidates.stream()
                    .map(CompareService.BaselineCandidate::runId).toList()
                    + " limit2=" + limited.size()
                    + " 跨项目=403");
        } finally {
            TenantContext.clear();
            cleanup(jdbc, projectId, otherProjectId, userId, projectCode, otherCode, username);
        }
    }

    private static DataSource dataSource() {
        String host = System.getenv().getOrDefault("DB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("DB_PORT", "3306");
        String user = System.getenv().getOrDefault("DB_USERNAME", "robot");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "");
        // 驱动是 MariaDB Java client（pom 无 MySQL Connector/J），前缀必须是 jdbc:mariadb://
        String url = "jdbc:mariadb://" + host + ":" + port + "/" + DB_NAME
                + "?useUnicode=true&characterEncoding=utf8";
        return new DriverManagerDataSource(url, user, password);
    }

    private static Long seedUser(JdbcTemplate jdbc, String username) {
        jdbc.update("INSERT INTO app_user (username, password_hash, display_name, status) "
                + "VALUES (?, ?, ?, 1)", username, "$2a$10$it-not-a-real-hash", "RPT-03 IT");
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username);
    }

    private static Long seedProject(JdbcTemplate jdbc, String code, Long ownerId) {
        jdbc.update("INSERT INTO project (code, name, owner_id, status) VALUES (?, ?, ?, 1)",
                code, "RPT-03 手动基线集成测试", ownerId);
        return jdbc.queryForObject("SELECT id FROM project WHERE code = ?", Long.class, code);
    }

    private static void seedMember(JdbcTemplate jdbc, Long projectId, Long userId) {
        jdbc.update("INSERT INTO project_member (project_id, user_id, member_role) "
                + "VALUES (?, ?, 'engineer')", projectId, userId);
    }

    /**
     * @param startedAtSql 直接拼进 SQL 的表达式（`NOW(3)` / `TIMESTAMPADD(...)` / `NULL`），
     *                     避免在 Java 侧造时间再被时区与毫秒精度扰动
     */
    private static void seedRun(JdbcTemplate jdbc, Long projectId, String runId, String state,
                                String dut, String link, String startedAtSql) {
        jdbc.update("INSERT INTO run (run_id, project_id, run_status, enqueued_at, started_at, "
                + "total_tests, passed_tests, failed_tests, dut, `link`, trigger_type) "
                + "VALUES (?, ?, ?, NOW(3), " + startedAtSql + ", 0, 0, 0, ?, ?, 'manual')",
                runId, projectId, state, dut, link);
    }

    private static void cleanup(JdbcTemplate jdbc, Long projectId, Long otherProjectId,
                                Long userId, String projectCode, String otherCode, String username) {
        try {
            for (Long id : new Long[]{projectId, otherProjectId}) {
                if (id == null) {
                    continue;
                }
                jdbc.update("DELETE FROM run WHERE project_id = ?", id);
                jdbc.update("DELETE FROM project_member WHERE project_id = ?", id);
                jdbc.update("DELETE FROM project WHERE id = ?", id);
            }
            if (userId != null) {
                jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
                jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
            }
        } catch (RuntimeException ex) {
            System.err.println("[CompareBaselineIT] 清理失败，残留 project=" + projectCode
                    + "/" + otherCode + " user=" + username + "：" + ex.getMessage());
        }
    }
}
