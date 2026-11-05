package com.qa.autotest.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** V4 迁移完整性单测：文件存在且关键字段齐全。 */
class MigrationFileTest {

    private static final Path ROOT = locateRoot();

    @Test
    @DisplayName("V4_1/V4_2/V4_3 与 down 脚本齐全")
    void 文件存在() {
        for (String name : List.of(
                "migration/V4_1__lease.sql",
                "migration/V4_2__run_artifact.sql",
                "migration/V4_3__failure_class.sql",
                "down/V4_1__lease.down.sql",
                "down/V4_2__run_artifact.down.sql",
                "down/V4_3__failure_class.down.sql")) {
            assertTrue(Files.exists(ROOT.resolve(name)), "缺少 " + name);
        }
    }

    @Test
    @DisplayName("V4_1 含幂等键、租约历史与关键列")
    void 字段齐全() throws IOException {
        String sql = read("migration/V4_1__lease.sql");
        for (String token : List.of("run_request_id", "uk_run_project_request", "priority",
                "enqueued_at", "concurrency", "total_tests", "artifacts_dir", "failure_reason",
                "attempt_no", "resource_lease_history", "last_heartbeat_at", "release_reason")) {
            assertTrue(sql.contains(token), "V4_1 缺少 " + token);
        }
        assertTrue(!sql.contains("leased_state"), "V4_1 不得保留列名笔误 leased_state");
    }

    @Test
    @DisplayName("V4_2 含产物分片与项目归属")
    void 产物字段() throws IOException {
        String sql = read("migration/V4_2__run_artifact.sql");
        for (String token : List.of("project_id", "shard_no", "line_count", "note")) {
            assertTrue(sql.contains(token), "V4_2 缺少 " + token);
        }
    }

    @Test
    @DisplayName("V4_3 含失败分类与尝试表")
    void 失败分类字段() throws IOException {
        String sql = read("migration/V4_3__failure_class.sql");
        for (String token : List.of("failure_class", "failure_message", "retry_of_run_id",
                "run_attempt", "uk_run_attempt")) {
            assertTrue(sql.contains(token), "V4_3 缺少 " + token);
        }
    }

    @Test
    @DisplayName("V7_4 运行设备范围与 down 脚本齐全")
    void 设备范围字段() throws IOException {
        assertTrue(Files.exists(ROOT.resolve("migration/V7_4__run_device_scope.sql")),
                "缺少 migration/V7_4__run_device_scope.sql");
        assertTrue(Files.exists(ROOT.resolve("down/V7_4__run_device_scope.down.sql")),
                "缺少 down/V7_4__run_device_scope.down.sql");

        String sql = read("migration/V7_4__run_device_scope.sql");
        assertTrue(sql.contains("`device_scope`"), "V7_4 缺少 device_scope 列");
        assertTrue(sql.contains("VARCHAR(512)"), "V7_4 列宽必须与 RunService 常量一致");
        String down = read("down/V7_4__run_device_scope.down.sql");
        assertTrue(down.contains("DROP COLUMN `device_scope`"), "V7_4 down 缺少列删除");
    }

    @Test
    @DisplayName("V5_1 报告字段与 down 脚本齐全")
    void 报告字段() throws IOException {
        assertTrue(Files.exists(ROOT.resolve("migration/V5_1__report_fields.sql")),
                "缺少 migration/V5_1__report_fields.sql");
        assertTrue(Files.exists(ROOT.resolve("down/V5_1__report_fields.down.sql")),
                "缺少 down/V5_1__report_fields.down.sql");

        String sql = read("migration/V5_1__report_fields.sql");
        for (String token : List.of("`dut`", "`link`", "`sw_build`", "`baseline`", "`operator`",
                "`case_kind`", "`metadata_json`", "`metadata_missing`", "`exportable`",
                "`pass_count`", "`fail_count`", "`skip_count`",
                "`case_key`", "`log_offset`", "`failure_kind`",
                "`arg_count`", "`log_span`")) {
            assertTrue(sql.contains(token), "V5_1 缺少 " + token);
        }

        String down = read("down/V5_1__report_fields.down.sql");
        // 2026-11-04 G1 #4 现场复跑：目标库（MySQL 8 / MariaDB）不支持条件化 DROP，
        // 原断言把 `DROP COLUMN IF EXISTS` 写成了规范，恰恰是 ERROR 1064 的根因。
        // 改为「直接 DROP 必须存在」+「条件化 DROP 一律不得出现」双向守门。
        for (String token : List.of("DROP COLUMN `case_key`",
                "DROP COLUMN `failure_kind`",
                "DROP COLUMN `arg_count`",
                "DROP COLUMN `log_span`",
                "DROP COLUMN `exportable`",
                "DROP COLUMN `metadata_missing`")) {
            assertTrue(down.contains(token), "V5_1 down 缺少 " + token);
        }
        // 忽略注释行：脚本头会引用这个不兼容写法作为说明，不能算命中
        assertTrue(down.lines().noneMatch(line -> !line.stripLeading().startsWith("--")
                        && line.contains("DROP COLUMN IF EXISTS")),
                "down 脚本不得使用目标库不支持的 DROP COLUMN IF EXISTS（G1 #4 现场 ERROR 1064）");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
    }

    private static Path locateRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && current != null; i++) {
            Path candidate = current.resolve("db");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            if (Files.isDirectory(current.resolve("..").resolve("db"))) {
                return current.resolve("..").resolve("db").normalize();
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未找到 db 目录");
    }
}
