package com.qa.autotest.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.qa.autotest.domain.caseasset.CapabilityStat;
import com.qa.autotest.domain.caseasset.TestCase;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.infrastructure.TestCaseRepositoryJdbc;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 能力索引反查的真实 SQL 覆盖（CAS-05 残余：按能力反查非空需真实数据验证）。
 *
 * <p>单测只能证明服务层拼装，回答不了「NULL / 空串会不会被算成一个能力桶」「同一能力跨项目会不会叠加」
 * 「按能力下钻能不能真的取到用例」；这里连真实 MariaDB（不用 H2，ADR-0002），
 * 本类会 clean 整个 schema，库名必须 {@code *_it}。
 */
@Tag("it")
class CapabilityIndexIT {

    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "robot_it");

    private static JdbcTemplate jdbc;
    private static TestCaseRepositoryJdbc cases;

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
        cases = new TestCaseRepositoryJdbc(jdbc);
    }

    @BeforeEach
    void 重置数据() {
        jdbc.update("DELETE FROM test_case_step");
        jdbc.update("DELETE FROM test_case");
        jdbc.update("DELETE FROM project");
        jdbc.update("INSERT INTO project(code,name) VALUES ('IT-CA','能力A'),('IT-CB','能力B')");
        projectA = 项目ID("IT-CA");
        projectB = 项目ID("IT-CB");
    }

    @Test
    @DisplayName("按能力反查非空：计数倒序，且按能力下钻能取到对应用例")
    void 反查非空并可按能力下钻() {
        建用例(projectA, "IT-C1", "roce-connectivity");
        建用例(projectA, "IT-C2", "roce-connectivity");
        建用例(projectA, "IT-C3", "pfc-backpressure");

        List<CapabilityStat> stats = cases.capabilities(projectA);

        assertThat(stats).extracting(CapabilityStat::key)
                .containsExactly("roce-connectivity", "pfc-backpressure");
        assertThat(stats).extracting(CapabilityStat::count).containsExactly(2L, 1L);

        // 反查出来的能力必须真能下钻，否则索引只是好看的数字
        PageResult<TestCase> drill = cases.page(projectA, null, null, null, null,
                "roce-connectivity", null, 1, 20);
        assertThat(drill.total()).isEqualTo(2);
        assertThat(drill.items()).extracting(TestCase::caseCode)
                .containsExactlyInAnyOrder("IT-C1", "IT-C2");
    }

    @Test
    @DisplayName("空能力不产生条目：NULL、空串、纯空白都不算一个能力桶")
    void 空能力不产生条目() {
        建用例(projectA, "IT-C4", null);
        建用例(projectA, "IT-C5", "");
        建用例(projectA, "IT-C6", "  ");

        // 纯空白串在库里既不等于 NULL 也不等于 ''，不按 trim 判空就会渲染出一个空标签能力桶
        assertThat(cases.capabilities(projectA)).isEmpty();
    }

    @Test
    @DisplayName("跨项目隔离：同一能力在两个项目各算各的，不叠加、不串桶")
    void 跨项目隔离() {
        建用例(projectA, "IT-C7", "roce-connectivity");
        建用例(projectA, "IT-C8", "roce-connectivity");
        建用例(projectB, "IT-C9", "roce-connectivity");
        建用例(projectB, "IT-D1", "ib-connectivity");

        assertThat(cases.capabilities(projectA)).extracting(CapabilityStat::key)
                .containsExactly("roce-connectivity");
        assertThat(cases.capabilities(projectA)).extracting(CapabilityStat::count)
                .containsExactly(2L);
        // 计数并列时按能力名字典序兜底，保证同数据多次查询顺序稳定
        assertThat(cases.capabilities(projectB)).extracting(CapabilityStat::key)
                .containsExactly("ib-connectivity", "roce-connectivity");
        assertThat(cases.capabilities(projectB)).extracting(CapabilityStat::count)
                .containsExactly(1L, 1L);
    }

    // ---------------------------------------------------------------- 内部工具

    private void 建用例(long projectId, String caseCode, String capability) {
        jdbc.update("INSERT INTO test_case(project_id, case_code, title, capability) VALUES (?,?,?,?)",
                projectId, caseCode, "能力索引用例 " + caseCode, capability);
    }

    private long 项目ID(String code) {
        return jdbc.queryForObject("SELECT id FROM project WHERE code=?", Long.class, code);
    }

    private static String env(String key, String fallback) {
        return System.getenv().getOrDefault(key, fallback);
    }
}
