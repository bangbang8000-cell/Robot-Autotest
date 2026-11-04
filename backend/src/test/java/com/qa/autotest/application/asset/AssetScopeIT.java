package com.qa.autotest.application.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.AuditRepository;
import com.qa.autotest.application.repository.ProjectAssetRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.infrastructure.DeviceRepositoryJdbc;
import com.qa.autotest.infrastructure.ProjectAssetRepositoryJdbc;
import com.qa.autotest.infrastructure.ProjectRepositoryJdbc;
import com.qa.autotest.infrastructure.UserRepositoryJdbc;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * PRJ-03 引用保护（FR-v0.2-51）真库验收：连 {@code robot_it}，不用替身仓储。
 *
 * <p>为什么必须连真库：409 的引用数来自 {@code project_asset} 的真实 COUNT，
 * 「删项目保留平台资产」靠 V7_3 的 {@code ON DELETE CASCADE}，这两件事用假仓储
 * 都证明不了。DEV-PLAN §10.9 步骤 4 的越权用例也在本类，判据是 403 而不是 404。
 *
 * <p>关键事实：V7_3 给所有存量私有资产回填了「本项目引用自己」，所以删除保护只对
 * {@code owner_scope=PLATFORM} 生效；私有资产删除时必须顺手清掉这条自引用，否则留幽灵行。
 */
@Tag("it")
class AssetScopeIT {

    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "robot_it");

    @Test
    @DisplayName("PRJ-03：被引用平台资产硬删 409 / 移除只删引用 / 删项目保留平台资产 / 越权读 403")
    void 引用保护与越权边界() {
        assertThat(DB_NAME).as("集成测试库名必须以 _it 结尾，防止误清开发库").endsWith("_it");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        String stamp = String.valueOf(System.currentTimeMillis());
        String username = "it-prj03-" + stamp;
        Long userId = null;
        Long p1 = null;
        Long p2 = null;
        Long p3 = null;
        Long sharedId = null;
        Long privateId = null;
        try {
            userId = seedUser(jdbc, username);
            p1 = seedProject(jdbc, "IT-PRJ03-A-" + stamp, userId);
            p2 = seedProject(jdbc, "IT-PRJ03-B-" + stamp, userId);
            p3 = seedProject(jdbc, "IT-PRJ03-C-" + stamp, userId);
            seedMember(jdbc, p1, userId);
            seedMember(jdbc, p2, userId);
            seedMember(jdbc, p3, userId);

            sharedId = seedDevice(jdbc, null, "IT-SHARED-" + stamp, "PLATFORM");
            privateId = seedDevice(jdbc, p1, "IT-PRIVATE-" + stamp, "PROJECT");
            // V7_3 存量回填：每个私有资产有一条「本项目引用自己」
            addRef(jdbc, p1, privateId);
            // 平台资产被两个项目引用
            addRef(jdbc, p1, sharedId);
            addRef(jdbc, p2, sharedId);

            // lambda 只能捕获 effectively final；造数变量在 finally 里还要参与清理，故复制一份
            final long fp1 = p1;
            final long fp3 = p3;
            final long fshared = sharedId;
            final long fprivate = privateId;
            TenantContext.set(userId, username, null);
            ProjectAssetRepositoryJdbc assets = new ProjectAssetRepositoryJdbc(jdbc);
            DeviceService devices = new DeviceService(
                    new DeviceRepositoryJdbc(jdbc), assets,
                    new AccessControlService(new UserRepositoryJdbc(jdbc),
                            new ProjectRepositoryJdbc(jdbc)),
                    new AuditService(new NoopAuditRepository(), Runnable::run));

            // ---- 1) 被引用不可硬删：409，且带引用项目数 ----
            BusinessException conflict = catchThrowableOfType(
                    () -> devices.delete(fp1, fshared), BusinessException.class);
            assertThat(conflict).as("被引用平台资产硬删必须抛业务异常").isNotNull();
            assertThat(conflict.errorCode()).isEqualTo(ErrorCode.CONFLICT);
            assertThat(conflict.getMessage()).contains("2");
            assertThat(assets.referenceCount("DEVICE", fshared)).isEqualTo(2);
            assertThat(countDevice(jdbc, fshared)).isEqualTo(1);

            // ---- 2) 项目内「移除资产」只删引用，不动物资本体 ----
            assertThat(assets.removeReference(p2, "DEVICE", sharedId)).isTrue();
            assertThat(assets.referenceCount("DEVICE", sharedId)).isEqualTo(1);
            assertThat(countDevice(jdbc, sharedId)).as("解除引用后资产本体必须还在").isEqualTo(1);

            // ---- 3) 私有资产删除：清掉回填的自引用，本体真的删掉 ----
            devices.delete(fp1, fprivate);
            assertThat(countDevice(jdbc, privateId)).isZero();
            assertThat(countRefs(jdbc, privateId)).as("不能留下 project_asset 幽灵行").isZero();

            // ---- 4) 删项目：引用级联清理，平台资产保留 ----
            jdbc.update("DELETE FROM project_member WHERE project_id = ?", p2);
            jdbc.update("DELETE FROM project WHERE id = ?", p2);
            assertThat(countRefs(jdbc, sharedId)).as("只剩 p1 的一条引用").isEqualTo(1);
            assertThat(countDevice(jdbc, sharedId)).as("删项目不得删平台资产").isEqualTo(1);

            // ---- 5) 无引用项目读平台资产：403 而不是 404（避免探测）----
            BusinessException denied = catchThrowableOfType(
                    () -> devices.get(fp3, fshared), BusinessException.class);
            assertThat(denied).isNotNull();
            assertThat(denied.errorCode()).isEqualTo(ErrorCode.FORBIDDEN);

            // ---- 6) 不存在的资产仍是 404，不能因为加了 403 就变成 403 ----
            BusinessException missing = catchThrowableOfType(
                    () -> devices.get(fp1, 999999999L), BusinessException.class);
            assertThat(missing.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        } finally {
            TenantContext.clear();
            cleanup(jdbc, p1, p2, p3, userId, sharedId, privateId);
        }
    }

    // ------------------------------------------------------------------ 造数 / 断言辅助

    private static DataSource dataSource() {
        String host = System.getenv().getOrDefault("DB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("DB_PORT", "3306");
        String user = System.getenv().getOrDefault("DB_USERNAME", "robot");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "");
        String url = "jdbc:mariadb://" + host + ":" + port + "/" + DB_NAME
                + "?useUnicode=true&characterEncoding=utf8";
        return new DriverManagerDataSource(url, user, password);
    }

    private static Long seedUser(JdbcTemplate jdbc, String username) {
        jdbc.update("INSERT INTO app_user (username, password_hash, display_name, status) "
                + "VALUES (?, ?, ?, 1)", username, "$2a$10$it-not-a-real-hash", "PRJ-03 IT");
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username);
    }

    private static Long seedProject(JdbcTemplate jdbc, String code, Long ownerId) {
        jdbc.update("INSERT INTO project (code, name, owner_id, status) VALUES (?, ?, ?, 1)",
                code, "PRJ-03 引用保护集成测试", ownerId);
        return jdbc.queryForObject("SELECT id FROM project WHERE code = ?", Long.class, code);
    }

    private static void seedMember(JdbcTemplate jdbc, Long projectId, Long userId) {
        jdbc.update("INSERT INTO project_member (project_id, user_id, member_role) "
                + "VALUES (?, ?, 'admin')", projectId, userId);
    }

    private static Long seedDevice(JdbcTemplate jdbc, Long projectId, String name, String scope) {
        jdbc.update("INSERT INTO device (project_id, owner_scope, name, status) "
                + "VALUES (?, ?, ?, 1)", projectId, scope, name);
        return jdbc.queryForObject("SELECT id FROM device WHERE name = ?", Long.class, name);
    }

    private static void addRef(JdbcTemplate jdbc, Long projectId, Long assetId) {
        jdbc.update("INSERT INTO project_asset (project_id, asset_type, asset_id) "
                + "VALUES (?, 'DEVICE', ?)", projectId, assetId);
    }

    private static int countDevice(JdbcTemplate jdbc, Long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM device WHERE id = ?", Integer.class, id);
        return n == null ? 0 : n;
    }

    private static int countRefs(JdbcTemplate jdbc, Long id) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_asset WHERE asset_type = 'DEVICE' AND asset_id = ?",
                Integer.class, id);
        return n == null ? 0 : n;
    }

    private static void cleanup(JdbcTemplate jdbc, Long p1, Long p2, Long p3, Long userId,
                                Long sharedId, Long privateId) {
        if (sharedId != null) {
            jdbc.update("DELETE FROM project_asset WHERE asset_type = 'DEVICE' AND asset_id = ?",
                    sharedId);
            jdbc.update("DELETE FROM device WHERE id = ?", sharedId);
        }
        if (privateId != null) {
            jdbc.update("DELETE FROM project_asset WHERE asset_type = 'DEVICE' AND asset_id = ?",
                    privateId);
            jdbc.update("DELETE FROM device WHERE id = ?", privateId);
        }
        for (Long p : new Long[]{p1, p2, p3}) {
            if (p != null) {
                jdbc.update("DELETE FROM project_member WHERE project_id = ?", p);
                jdbc.update("DELETE FROM project WHERE id = ?", p);
            }
        }
        if (userId != null) {
            jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        }
    }

    /** 审计只增不改；本 IT 不验审计内容，用空实现避免污染 audit_log。 */
    private static final class NoopAuditRepository implements AuditRepository {
        @Override
        public void insert(Long projectId, Long actorId, String action, String resourceType,
                           String resourceId, String detail) {
            // no-op
        }

        @Override
        public List<String> recentActions(Long projectId, int limit) {
            return List.of();
        }
    }
}
