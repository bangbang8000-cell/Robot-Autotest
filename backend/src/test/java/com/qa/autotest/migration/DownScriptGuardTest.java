package com.qa.autotest.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * down 脚本可回滚性的静态守门（ENV-06 / WBS #78）。
 *
 * <p>2026-11-04 G1 #4 现场第一次端到端复跑 down→up，连挖三处「IT 全绿却从没暴露」的缺陷：
 * ① {@code db-undo.sh} 未显式选库 → ERROR 1046；
 * ② 4 个 down 脚本 24 处 {@code DROP COLUMN IF EXISTS} → 目标库不支持，ERROR 1064；
 * ③ {@code V2_1} 外键绑定到待删索引 → ERROR 1553。
 * 本类把三者固化成单测，随 {@code mvn test} / {@code ci-local} 自动执行。
 *
 * <p>注意：{@code DROP TABLE IF EXISTS} 是目标库支持且 V1 基线回滚必需的语法，
 * <b>不在禁止范围</b>；守门只针对列/索引级条件化 DDL，不能一刀切。
 */
class DownScriptGuardTest {

    private static final Path ROOT = locateRoot();
    private static final Path DOWN = ROOT.resolve("db/down");
    private static final Path UP = ROOT.resolve("db/migration");

    private static final Pattern CONDITIONAL_COLUMN_OR_INDEX = Pattern.compile(
            "(?i)\\b(?:DROP\\s+(?:COLUMN|INDEX|KEY)\\s+IF\\s+EXISTS"
                    + "|ADD\\s+(?:COLUMN|INDEX|KEY)\\s+IF\\s+NOT\\s+EXISTS)\\b");

    private static final Pattern USE_STATEMENT = Pattern.compile("(?im)^\\s*USE\\s+[`\\w]");

    @Test
    @DisplayName("所有 down 脚本不得含目标库不支持的列/索引级条件化 DDL")
    void 禁止条件化列索引DDL() throws IOException {
        for (Path file : downFiles()) {
            String sql = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            Matcher m = CONDITIONAL_COLUMN_OR_INDEX.matcher(sql);
            boolean found = m.find();
            assertFalse(found,
                    file.getFileName() + " 含不支持的条件化 DDL: " + (found ? m.group() : ""));
        }
    }

    @Test
    @DisplayName("down 脚本不得写死库名（选库由 db-undo.sh 统一负责）")
    void 禁止写死库名() throws IOException {
        for (Path file : downFiles()) {
            String sql = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            assertFalse(USE_STATEMENT.matcher(sql).find(),
                    file.getFileName() + " 含 USE 语句，会绕过 db-undo.sh 的 _it 安全阀");
        }
    }

    @Test
    @DisplayName("每个 down 脚本都有同名 up 脚本，且数量一一对应")
    void 上下脚本配对() throws IOException {
        List<Path> downs = downFiles();
        for (Path down : downs) {
            String name = down.getFileName().toString().replace(".down.sql", ".sql");
            assertTrue(Files.exists(UP.resolve(name)), "缺少对应的 up 脚本: " + name);
        }
        long ups;
        try (Stream<Path> s = Files.list(UP)) {
            ups = s.filter(p -> p.getFileName().toString().endsWith(".sql")).count();
        }
        assertEquals(ups, downs.size(), "down 与 up 脚本数量不一致（新增迁移未补回滚脚本）");
    }

    @Test
    @DisplayName("db-undo.sh 必须显式选库且带 _it 安全阀")
    void 回滚脚本选库与安全阀() throws IOException {
        String sh = Files.readString(ROOT.resolve("scripts/db-undo.sh"), StandardCharsets.UTF_8);
        assertTrue(sh.contains("db_exec \"$DB_NAME\""),
                "db-undo.sh 必须显式选库，否则 down SQL 的裸表名触发 ERROR 1046");
        assertTrue(Pattern.compile("DB_NAME[^\\n]*\\*_it").matcher(sh).find(),
                "db-undo.sh 必须保留 _it 库名安全阀");
    }

    @Test
    @DisplayName("V2_1 外键依赖索引：先摘外键、再删索引、最后回挂")
    void 外键顺序守门() throws IOException {
        String sql = Files.readString(DOWN.resolve("V2_1__iam_fields.down.sql"), StandardCharsets.UTF_8);
        int dropFk = sql.indexOf("DROP FOREIGN KEY fk_project_member_user");
        int dropIdx = sql.indexOf("DROP INDEX idx_project_member_user");
        int addFk = sql.indexOf("ADD CONSTRAINT fk_project_member_user");
        assertTrue(dropFk >= 0, "V2_1 缺少摘外键步骤（ERROR 1553）");
        assertTrue(dropIdx > dropFk, "V2_1 必须先摘外键再删索引");
        assertTrue(addFk > dropIdx, "V2_1 删完索引必须把外键挂回");
    }

    /** 只看有效 SQL：down 脚本里的兼容性说明注释会提到被禁语法，不能当违规命中。 */
    private static String stripComments(String sql) {
        return sql.replaceAll("(?m)--[^\\n]*", "");
    }

    private static List<Path> downFiles() throws IOException {
        try (Stream<Path> s = Files.list(DOWN)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".down.sql"))
                    .sorted().toList();
        }
    }

    private static Path locateRoot() {
        Path p = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++) {
            if (Files.exists(p.resolve("db/migration/V1__baseline.sql"))) {
                return p;
            }
            p = p.getParent();
        }
        throw new IllegalStateException("找不到项目根目录");
    }
}
