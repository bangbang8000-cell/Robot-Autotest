package com.qa.autotest.application.caseasset;

import static org.assertj.core.api.Assertions.assertThat;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.TestCaseRepository;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.infrastructure.AuditRepositoryJdbc;
import com.qa.autotest.infrastructure.ProjectRepositoryJdbc;
import com.qa.autotest.infrastructure.TestCaseRepositoryJdbc;
import com.qa.autotest.infrastructure.UserRepositoryJdbc;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 台账导入端到端集成测试（CAS-03 的真判据），必须连真实 MySQL。
 *
 * <p><b>为什么要有这一条</b>：CAS-03 的判据是「{@code preview} 能读该结构（N 行、0 错误）」。
 * 在此之前只有两样东西顶替它：静态对账（{@code tools/check_import_header.py}，只比对表头与别名，
 * 不证明解析、不证明校验）和单测（替身仓储）。这两样都不能证明「真台账真能导进来」——
 * 尤其这份 226 行台账的 14 列里有 8 列不映射、其中 4 列还是命令类，只有真跑才能确认
 * 那些列不会把行判错。所以 <b>静态对账 PASS ≠ CAS-03 通过</b>，本类才是。
 *
 * <p>连库方式与 {@code SchemaIT} 一致（ADR-0002：不用 H2）。库名必须以 {@code _it} 结尾。
 *
 * <p><b>本测试不做的事（别读成做了）</b>：
 * <ul>
 *   <li>不起 Spring 上下文，所以 {@code @Transactional} 不生效 —— 本测试验证的是解析、校验、
 *       逐行插入与重复检测，<b>不验证 commit 的「整批回滚」</b>（那需要真事务代理）。</li>
 *   <li>不验证权限拒绝路径（403）、不验证并发导入。</li>
 *   <li>不做任何 SSH / 设备操作，不违反 ADR-0013 的开发期禁跑令。</li>
 * </ul>
 *
 * <p>每次运行用带时间戳的唯一项目编号，跑完清理，可重复执行。
 */
@Tag("it")
class CaseImportIT {

    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "robot_it");
    /** 台账实际条数；改这个值等于改判据，必须同时改 docs/evidence 里的记录。 */
    private static final int EXPECTED_ROWS = 226;

    @Test
    @DisplayName("226 行台账：preview 零错误 → commit 全部入库 → 重导逐行报编号已存在")
    void 台账226行全流程() throws Exception {
        assertThat(DB_NAME).as("集成测试库名必须以 _it 结尾，防止误清开发库").endsWith("_it");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        TestCaseRepository cases = new TestCaseRepositoryJdbc(jdbc);
        AccessControlService access = new AccessControlService(
                new UserRepositoryJdbc(jdbc), new ProjectRepositoryJdbc(jdbc));
        AuditService audit = new AuditService(new AuditRepositoryJdbc(jdbc));
        CaseImportService service = new CaseImportService(cases, access, audit);

        String stamp = String.valueOf(System.currentTimeMillis());
        String username = "it-cas03-" + stamp;
        String projectCode = "IT-CAS03-" + stamp;
        Long userId = null;
        Long projectId = null;
        try {
            userId = seedUser(jdbc, username);
            projectId = seedProject(jdbc, projectCode, userId);
            seedMember(jdbc, projectId, userId);
            TenantContext.set(userId, username, null);

            // ---- 1. preview：226 行、0 错误 ----
            CaseImportService.ImportPreview preview;
            try (InputStream in = Files.newInputStream(ledger())) {
                preview = service.preview(projectId, in);
            }
            assertThat(preview.totalRows())
                    .as("台账总行数（数据行，不含表头与前两行说明）")
                    .isEqualTo(EXPECTED_ROWS);
            assertThat(preview.errors())
                    .as("preview 的行错误（有则说明该行被跳过，导入不完整）")
                    .isEmpty();
            assertThat(preview.validRows()).isEqualTo(EXPECTED_ROWS);

            // 未命中的 8 列不该影响任何一行：抽查首尾两条的关键字段确实解析出来了
            CaseImportService.ImportRow first = preview.rows().get(0);
            CaseImportService.ImportRow last = preview.rows().get(EXPECTED_ROWS - 1);
            assertThat(first.caseCode()).isEqualTo("TN-IMP-001");
            assertThat(first.title()).isNotBlank();
            assertThat(last.caseCode()).isEqualTo("TN-IMP-226");
            assertThat(last.title()).isNotBlank();

            // ---- 2. commit：226 条入库 ----
            CaseImportService.ImportResult result = service.commit(projectId, preview.rows());
            assertThat(result.imported()).isEqualTo(EXPECTED_ROWS);
            assertThat(result.skipped()).isZero();
            assertThat(cases.page(projectId, null, null, null, null, null, 1, 1).total())
                    .as("库里实际落下的用例数")
                    .isEqualTo(EXPECTED_ROWS);

            // ---- 3. 重导：每行都报「编号已存在」，validRows 归零（幂等判据）----
            CaseImportService.ImportPreview again;
            try (InputStream in = Files.newInputStream(ledger())) {
                again = service.preview(projectId, in);
            }
            assertThat(again.totalRows()).isEqualTo(EXPECTED_ROWS);
            assertThat(again.validRows()).isZero();
            assertThat(again.errors()).hasSize(EXPECTED_ROWS);
            assertThat(again.errors().get(0).message()).contains("编号已存在于项目");

            // 证据日志里要能看见数字，不能只有一句 BUILD SUCCESS
            System.out.println("[CAS-03] preview totalRows=" + preview.totalRows()
                    + " validRows=" + preview.validRows() + " errors=" + preview.errors().size()
                    + " | commit imported=" + result.imported() + " skipped=" + result.skipped()
                    + " | 重导 validRows=" + again.validRows() + " errors=" + again.errors().size()
                    + " | 首条=" + first.caseCode() + " 末条=" + last.caseCode());
        } finally {
            TenantContext.clear();
            cleanup(jdbc, projectId, userId, projectCode, username);
        }
    }

    private static javax.sql.DataSource dataSource() {
        String host = System.getenv().getOrDefault("DB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("DB_PORT", "3306");
        String user = System.getenv().getOrDefault("DB_USERNAME", "robot");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "");
        // 驱动是 MariaDB Java client（pom 里没有 MySQL Connector/J），所以协议前缀必须是
        // jdbc:mariadb://。写成 jdbc:mysql:// 会得到 "No suitable driver found"，
        // 而这个报错看着像「驱动没引」，实际是前缀不对。
        String url = "jdbc:mariadb://" + host + ":" + port + "/" + DB_NAME
                + "?useUnicode=true&characterEncoding=utf8";
        return new DriverManagerDataSource(url, user, password);
    }

    /**
     * 定位台账固件。
     *
     * <p>surefire 的工作目录是 {@code backend/}，所以优先 {@code ../fixtures}；
     * 也接受从仓库根目录执行的情况。找不到就明确报出试过的路径，不静默跳过 ——
     * 静默跳过会让「没跑」看起来像「通过」。
     */
    private static Path ledger() {
        List<Path> candidates = List.of(
                Path.of("..", "fixtures", "case-ledger", "ledger-sample-226.xlsx"),
                Path.of("fixtures", "case-ledger", "ledger-sample-226.xlsx"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("找不到台账固件，试过：" + candidates
                + "；cwd=" + Path.of("").toAbsolutePath());
    }

    private static Long seedUser(JdbcTemplate jdbc, String username) {
        jdbc.update("INSERT INTO app_user (username, password_hash, display_name, status) "
                + "VALUES (?, ?, ?, 1)", username, "$2a$10$it-not-a-real-hash", "CAS-03 IT");
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username);
    }

    private static Long seedProject(JdbcTemplate jdbc, String code, Long ownerId) {
        jdbc.update("INSERT INTO project (code, name, owner_id, status) VALUES (?, ?, ?, 1)",
                code, "CAS-03 导入集成测试", ownerId);
        return jdbc.queryForObject("SELECT id FROM project WHERE code = ?", Long.class, code);
    }

    private static void seedMember(JdbcTemplate jdbc, Long projectId, Long userId) {
        jdbc.update("INSERT INTO project_member (project_id, user_id, member_role) "
                + "VALUES (?, ?, 'admin')", projectId, userId);
    }

    /** 清理：先删有外键指向 project 的行，再删 project，最后删用户。 */
    private static void cleanup(JdbcTemplate jdbc, Long projectId, Long userId,
                                String projectCode, String username) {
        try {
            if (projectId != null) {
                jdbc.update("DELETE FROM test_case WHERE project_id = ?", projectId);
                jdbc.update("DELETE FROM project_member WHERE project_id = ?", projectId);
                jdbc.update("DELETE FROM audit_log WHERE project_id = ?", projectId);
                jdbc.update("DELETE FROM project WHERE id = ?", projectId);
            }
            if (userId != null) {
                jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
                jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
            }
        } catch (RuntimeException ex) {
            // 清理失败不该把测试结论改写成失败，但必须让人看见残留
            System.err.println("[CaseImportIT] 清理失败，残留 project=" + projectCode
                    + " user=" + username + "：" + ex.getMessage());
        }
    }
}
