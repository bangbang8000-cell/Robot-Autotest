package com.qa.autotest.application.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qa.autotest.application.asset.CredentialService;
import com.qa.autotest.application.asset.DeviceService;
import com.qa.autotest.application.asset.LinkService;
import com.qa.autotest.application.iam.AccessControlService;
import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.iam.PasswordPolicy;
import com.qa.autotest.application.iam.UserService;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.common.security.SecretCipher;
import com.qa.autotest.common.tenant.TenantContext;
import com.qa.autotest.infrastructure.AuditRepositoryJdbc;
import com.qa.autotest.infrastructure.CredentialRepositoryJdbc;
import com.qa.autotest.infrastructure.DeviceRepositoryJdbc;
import com.qa.autotest.infrastructure.LinkRepositoryJdbc;
import com.qa.autotest.infrastructure.ProjectAssetRepositoryJdbc;
import com.qa.autotest.infrastructure.ProjectRepositoryJdbc;
import com.qa.autotest.infrastructure.RoleRepositoryJdbc;
import com.qa.autotest.infrastructure.UserRepositoryJdbc;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UX-02b 通用批量真库验收（FR-v0.2-40 / DEV-PLAN §10.5 契约）。
 *
 * <p>与 {@code CaseBatchIT} 同一口径：连 {@code robot_it}、逐条独立事务（真
 * {@link DataSourceTransactionManager}）、部分成功 200 语义、逐条审计带 {@code batch=}。
 * 用「被引用平台资产 409」制造单条失败，正好复用 PRJ-03 的引用保护做真实冲突源，
 * 不靠 mock 造假失败。
 */
@Tag("it")
class GenericBatchIT {

    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "robot_it");

    @Test
    @DisplayName("资产批量：部分成功 / 单条失败不影响其他 / 逐条审计同批次可反查")
    void 资产部分成功与审计() {
        Fixture f = new Fixture();
        try {
            Long shared = f.seedDevice(null, "PLATFORM");
            f.addRef(shared);
            Long a = f.seedDevice(f.projectId, "PROJECT");
            Long b = f.seedDevice(f.projectId, "PROJECT");
            f.addRef(a);
            f.addRef(b);

            GenericBatchService.BatchResult result = f.service.batch(f.projectId,
                    new GenericBatchService.BatchRequest("DEVICE", "delete",
                            List.of(a, shared, b), null));

            assertThat(result.total()).isEqualTo(3);
            assertThat(result.succeeded()).as("两条私有资产删掉，被引用的平台资产失败").isEqualTo(2);
            assertThat(result.failed()).isEqualTo(1);
            GenericBatchService.BatchItemResult failed = result.items().stream()
                    .filter(it -> !it.ok()).findFirst().orElseThrow();
            assertThat(failed.id()).isEqualTo(shared);
            assertThat(failed.code()).isEqualTo(ErrorCode.CONFLICT.name());
            assertThat(f.countDevice(a)).isZero();
            assertThat(f.countDevice(b)).isZero();
            assertThat(f.countDevice(shared)).as("失败条目不得被删").isEqualTo(1);
            assertThat(f.countRefs(a)).as("成功条目的自引用必须清干净").isZero();

            // 审计：3 条都在，且 batch= 标签相同（失败条目也留痕）
            List<String> details = f.jdbc.queryForList(
                    "SELECT detail FROM audit_log WHERE action = 'batch.device.delete' "
                            + "AND resource_id IN (?, ?, ?)",
                    String.class, String.valueOf(a), String.valueOf(shared), String.valueOf(b));
            assertThat(details).hasSize(3);
            Set<String> tags = new HashSet<>();
            for (String detail : details) {
                assertThat(detail).startsWith("batch=");
                tags.add(detail.split(",")[0]);
            }
            assertThat(tags).as("同一次批量必须共享一个 batch 标签").hasSize(1);
            assertThat(details.stream().anyMatch(d -> d.contains("ok=false"))).isTrue();
        } finally {
            f.close();
        }
    }

    @Test
    @DisplayName("参数级错误才 400：空 ids / 超 200 / 未知资源 / 未知动作 / 动作不适配 / 缺 enabled")
    void 参数级四百() {
        Fixture f = new Fixture();
        try {
            List<GenericBatchService.BatchRequest> bad = List.of(
                    new GenericBatchService.BatchRequest("DEVICE", "delete", List.of(), null),
                    new GenericBatchService.BatchRequest("DEVICE", "delete",
                            LongStream.rangeClosed(1, 201).boxed().toList(), null),
                    new GenericBatchService.BatchRequest("REPORT", "delete", List.of(1L), null),
                    new GenericBatchService.BatchRequest("DEVICE", "purge", List.of(1L), null),
                    new GenericBatchService.BatchRequest("DEVICE", "setEnabled", List.of(1L), true),
                    new GenericBatchService.BatchRequest("USER", "setEnabled", List.of(1L), null));
            for (GenericBatchService.BatchRequest req : bad) {
                assertThatThrownBy(() -> f.service.batch(f.projectId, req))
                        .as("请求应被参数级拦截: " + req)
                        .isInstanceOfSatisfying(BusinessException.class,
                                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
            }
        } finally {
            f.close();
        }
    }

    @Test
    @DisplayName("用户批量删除：不能删当前登录账号构成单条失败，其余照常成功")
    void 用户部分成功() {
        Fixture f = new Fixture();
        try {
            Long u1 = f.seedUser("u1");
            Long u2 = f.seedUser("u2");

            GenericBatchService.BatchResult result = f.service.batch(f.projectId,
                    new GenericBatchService.BatchRequest("USER", "delete",
                            List.of(u1, f.userId, u2), null));

            assertThat(result.succeeded()).isEqualTo(2);
            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.items().stream().filter(it -> it.id() == f.userId)
                    .findFirst().orElseThrow().code()).isEqualTo(ErrorCode.INVALID_ARGUMENT.name());
            assertThat(f.countUser(u1)).isZero();
            assertThat(f.countUser(u2)).isZero();
            assertThat(f.countUser(f.userId)).as("当前登录账号必须保留").isEqualTo(1);
        } finally {
            f.close();
        }
    }

    // ------------------------------------------------------------------ 夹具

    /** 真库夹具：造数、装配全部服务、清理。 */
    private static final class Fixture {
        final JdbcTemplate jdbc;
        final String stamp = String.valueOf(System.currentTimeMillis()) + "-"
                + String.valueOf(System.nanoTime() % 100000);
        final String username = "it-batch-" + stamp;
        final Long userId;
        final Long projectId;
        final GenericBatchService service;
        final List<Long> deviceIds = new ArrayList<>();
        final List<Long> userIds = new ArrayList<>();

        Fixture() {
            assertThat(DB_NAME).as("集成测试库名必须以 _it 结尾").endsWith("_it");
            jdbc = new JdbcTemplate(dataSource());
            userId = seedUser(username);
            projectId = seedProject("IT-BATCH-" + stamp, userId);
            seedMember(projectId, userId);
            grantGlobalAdmin(userId);
            TenantContext.set(userId, username, null);

            AccessControlService access = new AccessControlService(
                    new UserRepositoryJdbc(jdbc), new ProjectRepositoryJdbc(jdbc));
            AuditService audit = new AuditService(new AuditRepositoryJdbc(jdbc), Runnable::run);
            ProjectAssetRepositoryJdbc assets = new ProjectAssetRepositoryJdbc(jdbc);
            DeviceRepositoryJdbc deviceRepo = new DeviceRepositoryJdbc(jdbc);
            SecretCipher cipher = SecretCipher.fromBase64Key(
                    Base64.getEncoder().encodeToString(new byte[32]));

            service = new GenericBatchService(
                    new DeviceService(deviceRepo, assets, access, audit),
                    new LinkService(new LinkRepositoryJdbc(jdbc), deviceRepo, assets, access, audit),
                    new CredentialService(new CredentialRepositoryJdbc(jdbc), cipher, assets,
                            access, audit),
                    new UserService(new UserRepositoryJdbc(jdbc), new RoleRepositoryJdbc(jdbc),
                            access, audit, new PasswordPolicy()),
                    audit,
                    new TransactionTemplate(new DataSourceTransactionManager(dataSource())));
        }

        Long seedUser(String suffix) {
            String name = "it-batch-" + suffix + "-" + stamp;
            jdbc.update("INSERT INTO app_user (username, password_hash, display_name, status) "
                    + "VALUES (?, ?, ?, 1)", name, "$2a$10$it-not-a-real-hash", "UX-02b IT");
            Long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?",
                    Long.class, name);
            userIds.add(id);
            return id;
        }

        Long seedProject(String code, Long ownerId) {
            jdbc.update("INSERT INTO project (code, name, owner_id, status) VALUES (?, ?, ?, 1)",
                    code, "UX-02b 通用批量集成测试", ownerId);
            return jdbc.queryForObject("SELECT id FROM project WHERE code = ?", Long.class, code);
        }

        void seedMember(Long pid, Long uid) {
            jdbc.update("INSERT INTO project_member (project_id, user_id, member_role) "
                    + "VALUES (?, ?, 'admin')", pid, uid);
        }

        /** 用户域是全局权限，必须给 actor 挂内置 admin 角色。 */
        void grantGlobalAdmin(Long uid) {
            List<Long> found = jdbc.queryForList(
                    "SELECT id FROM role WHERE code = 'admin' ORDER BY id LIMIT 1", Long.class);
            Long roleId = found.isEmpty() ? null : found.get(0);
            if (roleId == null) {
                jdbc.update("INSERT INTO role (code, name, built_in) "
                        + "VALUES ('admin', '管理员', 1)");
                roleId = jdbc.queryForObject("SELECT id FROM role WHERE code = 'admin'", Long.class);
            }
            jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", uid, roleId);
        }

        Long seedDevice(Long pid, String scope) {
            String name = "IT-BATCH-DEV-" + deviceIds.size() + "-" + stamp;
            jdbc.update("INSERT INTO device (project_id, owner_scope, name, status) "
                    + "VALUES (?, ?, ?, 1)", pid, scope, name);
            Long id = jdbc.queryForObject("SELECT id FROM device WHERE name = ?", Long.class, name);
            deviceIds.add(id);
            return id;
        }

        void addRef(Long assetId) {
            jdbc.update("INSERT INTO project_asset (project_id, asset_type, asset_id) "
                    + "VALUES (?, 'DEVICE', ?)", projectId, assetId);
        }

        int countDevice(Long id) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM device WHERE id = ?",
                    Integer.class, id);
            return n == null ? 0 : n;
        }

        int countRefs(Long id) {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM project_asset WHERE asset_type='DEVICE' AND asset_id=?",
                    Integer.class, id);
            return n == null ? 0 : n;
        }

        int countUser(Long id) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id = ?",
                    Integer.class, id);
            return n == null ? 0 : n;
        }

        void close() {
            TenantContext.clear();
            try {
                for (Long id : deviceIds) {
                    jdbc.update("DELETE FROM audit_log WHERE resource_type='device' "
                            + "AND resource_id = ?", String.valueOf(id));
                    jdbc.update("DELETE FROM project_asset WHERE asset_type='DEVICE' "
                            + "AND asset_id = ?", id);
                    jdbc.update("DELETE FROM device WHERE id = ?", id);
                }
                for (Long id : userIds) {
                    jdbc.update("DELETE FROM audit_log WHERE resource_type='user' "
                            + "AND resource_id = ?", String.valueOf(id));
                    jdbc.update("DELETE FROM user_role WHERE user_id = ?", id);
                    jdbc.update("DELETE FROM app_user WHERE id = ?", id);
                }
                jdbc.update("DELETE FROM audit_log WHERE resource_id = ?",
                        String.valueOf(userId));
                jdbc.update("DELETE FROM project_member WHERE project_id = ?", projectId);
                jdbc.update("DELETE FROM project WHERE id = ?", projectId);
                jdbc.update("DELETE FROM audit_log WHERE project_id = ?", projectId);
            } catch (RuntimeException ex) {
                System.err.println("[GenericBatchIT] 清理失败：" + ex.getMessage());
            }
        }
    }

    private static DataSource dataSource() {
        String host = System.getenv().getOrDefault("DB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("DB_PORT", "3306");
        String user = System.getenv().getOrDefault("DB_USERNAME", "robot");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "");
        String url = "jdbc:mariadb://" + host + ":" + port + "/" + DB_NAME
                + "?useUnicode=true&characterEncoding=utf8";
        return new DriverManagerDataSource(url, user, password);
    }
}
