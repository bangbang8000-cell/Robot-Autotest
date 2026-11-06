package com.qa.autotest.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.qa.autotest.application.repository.RunRetryOrigin;
import com.qa.autotest.domain.execution.RunState;
import com.qa.autotest.infrastructure.RunRepositoryJdbc;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 重试来源的真实 SQL 覆盖（EXEC-13 残余：{@code retry_of_run_id} 自连接此前没有自动化验证）。
 *
 * <p>单测只在服务层打桩，回答不了「自连接写对没有、跨项目会不会串数据」；
 * 这里连真实 MariaDB（不用 H2，ADR-0002），本类会 clean 整个 schema，库名必须 {@code *_it}。
 */
@Tag("it")
class RetryOriginIT {

    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "robot_it");

    private static JdbcTemplate jdbc;
    private static RunRepositoryJdbc runs;

    private long projectA;
    private long projectB;

    @BeforeAll
    static void migrateFromScratch() {
        assertThat(DB_NAME).as("集成测试库名必须以 _it 结尾，防止误清开发库").endsWith("_it");
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:mariadb://" + env("DB_HOST", "127.0.0.1") + ":" + env("DB_PORT", "3306") + "/" + DB_NAME,
                env("DB_USERNAME", "robot"),
                env("DB_PASSWORD", ""));
        jdbc = new JdbcTemplate(ds);
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations(env("DB_MIGRATION_PATH", "filesystem:../db/migration"))
                .cleanDisabled(false)
                .placeholderReplacement(false)
                .load();
        flyway.clean();
        flyway.migrate();
        runs = new RunRepositoryJdbc(jdbc);
    }

    @BeforeEach
    void 重置数据() {
        jdbc.update("DELETE FROM run_metadata");
        jdbc.update("DELETE FROM run");
        jdbc.update("DELETE FROM test_set");
        jdbc.update("DELETE FROM project");
        jdbc.update("INSERT INTO project(code,name) VALUES ('IT-RA','重试A'),('IT-RB','重试B')");
        projectA = 项目ID("IT-RA");
        projectB = 项目ID("IT-RB");
    }

    @Test
    @DisplayName("重试来源：自连接带出原运行的失败结论，attempt_no 一并落库")
    void 重试来源带出失败结论() {
        造原运行(projectA, "IT-R1");
        建运行(projectA, "IT-R2");

        assertThat(runs.markAttempt("IT-R2", 2, "IT-R1")).isEqualTo(1);

        RunRetryOrigin origin = runs.findRetryOrigin(projectA, "IT-R2").orElseThrow();
        assertThat(origin.runId()).isEqualTo("IT-R1");
        assertThat(origin.state()).isEqualTo(RunState.FAILED);
        assertThat(origin.failureClass()).isEqualTo("ENV");
        assertThat(origin.failureReason()).isEqualTo("dut_offline");
        assertThat(origin.failureMessage()).isEqualTo("ping timeout");
        assertThat(origin.finishedAt()).isNotNull();
        assertThat(runs.findByRunId(projectA, "IT-R2").orElseThrow().attemptNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("首次尝试没有来源：返回空而不是报错")
    void 首次尝试返回空() {
        建运行(projectA, "IT-R1");

        assertThat(runs.findRetryOrigin(projectA, "IT-R1")).isEmpty();
    }

    @Test
    @DisplayName("跨项目引用不拼接：B 的运行指向 A 的编号时查不到")
    void 跨项目引用不拼接() {
        造原运行(projectA, "IT-R1");
        建运行(projectB, "IT-R2");
        jdbc.update("UPDATE run SET retry_of_run_id='IT-R1' WHERE run_id='IT-R2'");

        assertThat(runs.findRetryOrigin(projectB, "IT-R2")).isEmpty();
    }

    // ---------------------------------------------------------------- 内部工具

    private void 造原运行(long projectId, String runId) {
        jdbc.update("""
                INSERT INTO run(run_id, project_id, run_status, failure_class, failure_reason,
                                failure_message, finished_at, attempt_no)
                VALUES (?, ?, 'failed', 'ENV', 'dut_offline', 'ping timeout',
                        CURRENT_TIMESTAMP(3), 1)
                """, runId, projectId);
    }

    private void 建运行(long projectId, String runId) {
        jdbc.update("INSERT INTO run(run_id, project_id) VALUES (?, ?)", runId, projectId);
    }

    private long 项目ID(String code) {
        return jdbc.queryForObject("SELECT id FROM project WHERE code=?", Long.class, code);
    }

    private static String env(String key, String fallback) {
        return System.getenv().getOrDefault(key, fallback);
    }
}
