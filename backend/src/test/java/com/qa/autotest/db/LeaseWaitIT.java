package com.qa.autotest.db;

import static org.assertj.core.api.Assertions.assertThat;

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
 * 租约等待耗时回读的真实 SQL 覆盖（EXEC-09 残余：{@code lease_wait_ms} 已回写但接口未回读）。
 *
 * <p>单测只能证明服务层拼装，回答不了「NULL 会不会被读成 0 秒、跨项目会不会串数据」；
 * 这里连真实 MariaDB（不用 H2，ADR-0002），本类会 clean 整个 schema，库名必须 {@code *_it}。
 */
@Tag("it")
class LeaseWaitIT {

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
        jdbc.update("DELETE FROM project");
        jdbc.update("INSERT INTO project(code,name) VALUES ('IT-LA','租约A'),('IT-LB','租约B')");
        projectA = 项目ID("IT-LA");
        projectB = 项目ID("IT-LB");
    }

    @Test
    @DisplayName("未回写时返回空：NULL 不能被当成「等待 0 秒」")
    void 未回写返回空() {
        建运行(projectA, "IT-L1");

        assertThat(runs.findLeaseWaitMs(projectA, "IT-L1")).isEmpty();
    }

    @Test
    @DisplayName("回写后可回读；0 秒也是有效值，与 NULL 区分")
    void 回写后可回读() {
        建运行(projectA, "IT-L1");

        assertThat(runs.recordLeaseWait("IT-L1", 2500)).isEqualTo(1);
        assertThat(runs.findLeaseWaitMs(projectA, "IT-L1")).contains(2500L);

        // 设备空闲时等待 0 秒是真结果，不能被当成「还没回写」
        assertThat(runs.recordLeaseWait("IT-L1", 0)).isEqualTo(1);
        assertThat(runs.findLeaseWaitMs(projectA, "IT-L1")).contains(0L);
    }

    @Test
    @DisplayName("跨项目查不到：B 项目不能读到 A 项目同编号运行的等待耗时")
    void 跨项目隔离() {
        建运行(projectA, "IT-L1");
        runs.recordLeaseWait("IT-L1", 3200);

        assertThat(runs.findLeaseWaitMs(projectB, "IT-L1")).isEmpty();
    }

    // ---------------------------------------------------------------- 内部工具

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
