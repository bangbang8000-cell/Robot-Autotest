package com.qa.autotest.application.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qa.autotest.application.repository.UserPreferenceRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.infrastructure.UserPreferenceRepositoryJdbc;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 用户偏好集成测试（UX-01 / FR-v0.2-33），必须连真实 MySQL。
 *
 * <p><b>为什么要有这一条</b>：主题偏好是"看着没问题但换设备就丢"的典型。
 * 用替身仓储测「set 之后 get 能拿到」几乎是废话——真正的风险在 SQL 上：
 * {@code ON DUPLICATE KEY UPDATE} 写错就变成"每次切换都插一行"，
 * 唯一键写错就变成"改 A 用户的偏好把 B 用户的也改了"。这两种错误单测都抓不到。
 *
 * <p><b>本测试不做的事</b>：不起 Spring 上下文，所以不验 {@code @Transactional}；
 * 不验接口层 401（那是 AuthInterceptor 的职责，已有测试覆盖）。
 */
@Tag("it")
class UserPreferenceIT {

    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "robot_it");

    private JdbcTemplate jdbc;
    private UserPreferenceService service;
    private Long userId;
    private Long otherUserId;
    private String username;
    private String otherUsername;

    @BeforeEach
    void setUp() {
        assertThat(DB_NAME).as("集成测试库名必须以 _it 结尾，防止误清开发库").endsWith("_it");
        jdbc = new JdbcTemplate(dataSource());
        UserPreferenceRepository repository = new UserPreferenceRepositoryJdbc(jdbc);
        service = new UserPreferenceService(repository);

        String stamp = String.valueOf(System.currentTimeMillis());
        username = "it-pref-" + stamp;
        otherUsername = "it-pref-other-" + stamp;
        userId = seedUser(jdbc, username);
        otherUserId = seedUser(jdbc, otherUsername);
    }

    @AfterEach
    void tearDown() {
        // 偏好表有 ON DELETE CASCADE，删用户即清偏好，但仍显式删一遍便于排查残留
        for (Long id : new Long[]{userId, otherUserId}) {
            if (id != null) {
                jdbc.update("DELETE FROM user_preference WHERE user_id = ?", id);
                jdbc.update("DELETE FROM app_user WHERE id = ?", id);
            }
        }
    }

    @Test
    @DisplayName("没存过偏好：返回空 Map（不是 null，前端不用判空）")
    void 空偏好返回空Map() {
        Map<String, String> prefs = service.get(userId);
        assertThat(prefs).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("存了再读：theme 能取回；再改成别的值只更新一行，不新增行")
    void 写入与更新只留一行() {
        service.put(userId, Map.of("theme", "light"));
        assertThat(service.get(userId)).containsEntry("theme", "light");

        service.put(userId, Map.of("theme", "dark"));
        assertThat(service.get(userId)).containsEntry("theme", "dark");

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_preference WHERE user_id = ? AND pref_key = 'theme'",
                Integer.class, userId);
        assertThat(rows).as("同一个 key 只能有一行（唯一键 + upsert 生效）").isEqualTo(1);
    }

    @Test
    @DisplayName("多键并存：改主题不影响语言")
    void 多键互不影响() {
        service.put(userId, Map.of("theme", "light", "locale", "en-US"));
        service.put(userId, Map.of("theme", "dark"));

        Map<String, String> prefs = service.get(userId);
        assertThat(prefs).containsEntry("theme", "dark").containsEntry("locale", "en-US");
    }

    @Test
    @DisplayName("跨用户隔离：改 A 的偏好不影响 B")
    void 跨用户隔离() {
        service.put(userId, Map.of("theme", "light"));
        service.put(otherUserId, Map.of("theme", "dark"));

        assertThat(service.get(userId)).containsEntry("theme", "light");
        assertThat(service.get(otherUserId)).containsEntry("theme", "dark");
    }

    @Test
    @DisplayName("非法键/值被拒且不落库（白名单不是摆设）")
    void 非法键值被拒() {
        assertThatThrownBy(() -> service.put(userId, Map.of("is_admin", "true")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.INVALID_ARGUMENT));

        assertThatThrownBy(() -> service.put(userId, Map.of("theme", "rainbow")))
                .isInstanceOf(BusinessException.class);

        assertThat(service.get(userId)).as("被拒的写入不能留下任何痕迹").isEmpty();
    }

    @Test
    @DisplayName("超过 10 个键被拒")
    void 超量键被拒() {
        Map<String, String> tooMany = new HashMap<>();
        for (int i = 0; i < 11; i++) {
            tooMany.put("key-" + i, "value");
        }
        assertThatThrownBy(() -> service.put(userId, tooMany))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("偏好项过多");
    }

    @Test
    @DisplayName("空 Map 是幂等无操作，不报错也不清空已有偏好")
    void 空Map不报错() {
        service.put(userId, Map.of("theme", "light"));
        Map<String, String> after = service.put(userId, Map.of());
        assertThat(after).containsEntry("theme", "light");
    }

    @Test
    @DisplayName("删用户级联清理偏好（不留孤儿行）")
    void 删用户级联清理() {
        service.put(userId, Map.of("theme", "light"));
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_preference WHERE user_id = ?", Integer.class, userId);
        assertThat(rows).as("FK ON DELETE CASCADE 应清掉").isZero();
        userId = null; // tearDown 不再重复删
    }

    private static Long seedUser(JdbcTemplate jdbc, String username) {
        jdbc.update("INSERT INTO app_user (username, password_hash, display_name, status) "
                + "VALUES (?, ?, ?, 1)", username, "$2a$10$it-not-a-real-hash", "UX-01 IT");
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username);
    }

    private static javax.sql.DataSource dataSource() {
        String host = System.getenv().getOrDefault("DB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("DB_PORT", "3306");
        String user = System.getenv().getOrDefault("DB_USERNAME", "robot");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "");
        // 驱动是 MariaDB Java client，协议前缀必须是 jdbc:mariadb://（见 CaseImportIT 注释）
        String url = "jdbc:mariadb://" + host + ":" + port + "/" + DB_NAME
                + "?useUnicode=true&characterEncoding=utf8";
        return new DriverManagerDataSource(url, user, password);
    }
}
