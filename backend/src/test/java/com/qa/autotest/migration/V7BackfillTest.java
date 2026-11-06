package com.qa.autotest.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** V7_7 / V7_8 两条数据回填迁移的静态守门（PRJ-01 案 A、AST-04 案 A-②）。 */
class V7BackfillTest {

    private static final Path ROOT = locateRoot();

    @Test
    @DisplayName("V7_7 项目 owner 回填：up/down 齐全、不写死 id、只补空值")
    void ownerBackfill() throws IOException {
        Path up = ROOT.resolve("db/migration/V7_7__project_owner_backfill.sql");
        assertTrue(Files.exists(up));
        assertTrue(Files.exists(ROOT.resolve("db/down/V7_7__project_owner_backfill.down.sql")));
        // SQL 里标识符带反引号，断言前统一去掉，避免把格式差异误判成缺规则
        String sql = Files.readString(up, StandardCharsets.UTF_8).replace("`", "");
        assertTrue(sql.contains("member_role = 'admin'"), "旧 owner 角色必须归一");
        assertTrue(sql.contains("MIN(user_id)"), "必须按项目选 admin 中不写死的最小 user_id");
        assertTrue(sql.contains("owner_id IS NULL"), "只补空值，重复执行零影响");
        assertTrue(!sql.contains("WHERE id = 1"), "不得残留演示库写死的 project id");
    }

    @Test
    @DisplayName("V7_8 技术域回填：13 类映射齐全、只填空值、不覆盖人工标注")
    void domainBackfill() throws IOException {
        Path up = ROOT.resolve("db/migration/V7_8__domain_tag_backfill.sql");
        assertTrue(Files.exists(up));
        assertTrue(Files.exists(ROOT.resolve("db/down/V7_8__domain_tag_backfill.down.sql")));
        // SQL 里标识符带反引号，断言前统一去掉，避免把格式差异误判成缺规则
        String sql = Files.readString(up, StandardCharsets.UTF_8).replace("`", "");
        assertTrue(sql.contains("domain_tag IS NULL OR domain_tag = ''"), "只填空值");
        for (String kind : new String[]{
                "基本功能测试", "性能测试", "压力测试", "混流测试", "稳定性测试",
                "网卡专用工具测试", "结构测试", "散热测试", "SI测试", "EMC", "SIV", "Thermal", "REL"}) {
            assertTrue(sql.contains(kind), "映射缺少一级分类：" + kind);
        }
        assertTrue(sql.contains("ELSE 'general'"), "必须有兜底，保证非空率 100%");
    }

    private static Path locateRoot() {
        Path p = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++) {
            if (Files.exists(p.resolve("db/migration/V1__baseline.sql"))) return p;
            p = p.getParent();
        }
        throw new IllegalStateException("找不到项目根目录");
    }
}
