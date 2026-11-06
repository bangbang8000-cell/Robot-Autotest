package com.qa.autotest.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantGuard;
import com.qa.autotest.infrastructure.RunMetadataRepositoryJdbc;
import com.qa.autotest.infrastructure.RunRepositoryJdbc;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 租户隔离的真实 SQL 覆盖（IAM-04 残余：ADR-0006 的第 2 层此前只有元测试）。
 *
 * <p>与 {@code TenantGuardMetaTest} 的分工：那个测「守卫会不会拦危险 SQL（字符串级）」，
 * 这个测「真实仓储跑在真实 MariaDB 上时，A 项目的会话能否读到/改到 B 项目的行」。
 * 不用 H2（ADR-0002），必须连 {@code *_it} 库——本类会 clean 整个 schema。
 */
@Tag("it")
class TenantIsolationIT {

    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "robot_it");

    private static Flyway flyway;
    private static JdbcTemplate jdbc;
    private static RunRepositoryJdbc runs;
    private static RunMetadataRepositoryJdbc metadata;

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
        flyway = Flyway.configure()
                .dataSource(ds)
                .locations(env("DB_MIGRATION_PATH", "filesystem:../db/migration"))
                .cleanDisabled(false)
                .placeholderReplacement(false)
                .load();
        flyway.clean();
        flyway.migrate();
        runs = new RunRepositoryJdbc(jdbc);
        metadata = new RunMetadataRepositoryJdbc(jdbc);
    }

    @BeforeEach
    void 重置数据() {
        // 子表先删；run_metadata 有 ON DELETE CASCADE，但主表顺序仍要显式
        jdbc.update("DELETE FROM run_metadata");
        jdbc.update("DELETE FROM run");
        jdbc.update("DELETE FROM test_set");
        jdbc.update("DELETE FROM project");
        jdbc.update("INSERT INTO project(code,name) VALUES ('IT-A','租户A'),('IT-B','租户B')");
        projectA = 项目ID("IT-A");
        projectB = 项目ID("IT-B");
        jdbc.update("INSERT INTO run(run_id,project_id) VALUES ('IT-RUN-A',?),('IT-RUN-B',?)",
                projectA, projectB);
    }

    @Test
    @DisplayName("补录项按项目隔离：A 的会话读不到 B 的行，反之亦然")
    void 补录项按项目隔离() {
        metadata.save(projectA, "IT-RUN-A", Map.of("DUT", "GPU-A"), List.of(), 1L);
        metadata.save(projectB, "IT-RUN-B", Map.of("DUT", "GPU-B"), List.of(), 1L);

        assertThat(metadata.findAll(projectA, "IT-RUN-A")).containsExactly(Map.entry("DUT", "GPU-A"));
        assertThat(metadata.findAll(projectB, "IT-RUN-B")).containsExactly(Map.entry("DUT", "GPU-B"));
        // 用 B 的项目 id 查 A 的 run：真实 SQL 带 project_id 条件，结果为空而不是串数据
        assertThat(metadata.findAll(projectB, "IT-RUN-A")).isEmpty();
        assertThat(metadata.findAll(projectA, "IT-RUN-B")).isEmpty();
    }

    @Test
    @DisplayName("运行查询按项目隔离：同 runId 在别的项目下查不到")
    void 运行查询按项目隔离() {
        assertThat(runs.findByRunId(projectA, "IT-RUN-A")).isPresent();
        assertThat(runs.findByRunId(projectB, "IT-RUN-A")).isEmpty();
        assertThat(runs.findByRunId(projectA, "IT-RUN-B")).isEmpty();
    }

    @Test
    @DisplayName("同一 run + 同一键重复写入走 UPSERT：不新增行，只更新值")
    void 同键重复写入不新增行() {
        metadata.save(projectA, "IT-RUN-A", Map.of("DUT", "GPU-A1"), List.of(), 1L);
        metadata.save(projectA, "IT-RUN-A", Map.of("DUT", "GPU-A2"), List.of(), 2L);

        assertThat(metadata.findAll(projectA, "IT-RUN-A")).containsExactly(Map.entry("DUT", "GPU-A2"));
        assertThat(行数("SELECT COUNT(*) FROM run_metadata WHERE run_id='IT-RUN-A' AND meta_key='DUT'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("删除只作用于本项目：不会误删另一个项目同名键")
    void 删除不跨项目() {
        metadata.save(projectA, "IT-RUN-A", Map.of("DUT", "GPU-A", "BASELINE", "188 Gbps"), List.of(), 1L);
        metadata.save(projectB, "IT-RUN-B", Map.of("DUT", "GPU-B"), List.of(), 1L);

        metadata.save(projectA, "IT-RUN-A", Map.of(), List.of("DUT"), 1L);

        assertThat(metadata.findAll(projectA, "IT-RUN-A"))
                .containsExactly(Map.entry("BASELINE", "188 Gbps"));
        assertThat(metadata.findAll(projectB, "IT-RUN-B")).containsExactly(Map.entry("DUT", "GPU-B"));
    }

    @Test
    @DisplayName("删除运行记录时补录项按外键级联清理")
    void 删除运行级联清理补录项() {
        metadata.save(projectA, "IT-RUN-A", Map.of("DUT", "GPU-A"), List.of(), 1L);

        jdbc.update("DELETE FROM run WHERE run_id='IT-RUN-A'");

        assertThat(行数("SELECT COUNT(*) FROM run_metadata")).isZero();
    }

    @Test
    @DisplayName("第 2 层守卫：未带 project_id 的 SQL 在真实调用前被拦下")
    void 守卫拦截未带租户条件的SQL() {
        assertThat(TenantGuard.requiresTenant("run_metadata")).as("新表必须登记进租户黑名单").isTrue();
        assertThatThrownBy(() -> TenantGuard.requireProjectScope("SELECT * FROM run_metadata", "run_metadata"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ---------------------------------------------------------------- 内部工具

    private static String env(String key, String fallback) {
        return System.getenv().getOrDefault(key, fallback);
    }

    private long 项目ID(String code) {
        return jdbc.queryForObject("SELECT id FROM project WHERE code=?", Long.class, code);
    }

    private int 行数(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
