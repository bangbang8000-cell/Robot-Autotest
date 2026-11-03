package com.qa.autotest.application.iam;

import com.qa.autotest.api.AuthInterceptor;
import com.qa.autotest.api.GlobalExceptionHandler;
import com.qa.autotest.api.iam.AuditController;
import com.qa.autotest.application.repository.AuditRepository;
import com.qa.autotest.application.repository.ProjectRepository;
import com.qa.autotest.application.repository.SessionTokenRepository;
import com.qa.autotest.application.repository.UserRepository;
import com.qa.autotest.domain.common.PageResult;
import com.qa.autotest.domain.iam.AppUser;
import com.qa.autotest.domain.iam.AuditEntry;
import com.qa.autotest.domain.iam.Project;
import com.qa.autotest.domain.iam.ProjectMember;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * HRD-02：审计查询权限、筛选、分页与 CSV 导出。
 *
 * <p>仅 ADMIN 或项目所有者可查；其他角色 403。审计接口只读，DELETE 不被接受。
 */
class AuditQueryTest {

    private static final long ALPHA = 7L;
    private static final long ADMIN = 1L;
    private static final long PM = 2L;
    private static final long ENGINEER = 3L;

    private static final Object UNHANDLED = new Object();

    private MockMvc mockMvc;
    private FakeSessions sessions;
    private RecordingAudit auditRepo;
    private AuditQueryService queryService;

    private Map<Long, AppUser> users;
    private Map<Long, List<String>> globalRoles;
    private Map<Long, Map<Long, String>> memberships;
    private Map<Long, Project> projects;

    @BeforeEach
    void setUp() {
        users = new LinkedHashMap<>();
        users.put(ADMIN, user(ADMIN, "admin"));
        users.put(PM, user(PM, "pm"));
        users.put(ENGINEER, user(ENGINEER, "engineer"));
        globalRoles = new LinkedHashMap<>();
        globalRoles.put(ADMIN, List.of("admin"));
        globalRoles.put(PM, List.of("pm"));
        globalRoles.put(ENGINEER, List.of("engineer"));
        memberships = new LinkedHashMap<>();
        memberships.put(ALPHA, new LinkedHashMap<>(Map.of(PM, "pm", ENGINEER, "engineer")));
        projects = new LinkedHashMap<>();
        projects.put(ALPHA, new Project(ALPHA, "alpha", "Alpha", "基线项目", true, PM, "private"));

        sessions = new FakeSessions();
        auditRepo = new RecordingAudit();

        AccessControlService access = new AccessControlService(userRepository(), projectRepository());
        queryService = new AuditQueryService(auditRepo, projectRepository(), access);
        AuthService authService = new AuthService(userRepository(), sessions, new PasswordPolicy(),
                new AuditService(auditRepo, Runnable::run));
        mockMvc = MockMvcBuilders.standaloneSetup(new AuditController(queryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthInterceptor(authService))
                .build();
    }

    @Test
    @DisplayName("ADMIN 可不带 projectId 查全局")
    void adminCanQueryGlobal() throws Exception {
        sessions.userId = ADMIN;
        MvcResult result = mockMvc.perform(get("/api/audit")
                .header("Authorization", "Bearer t")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        String body = body(result);
        assertTrue(body.contains("\"code\":0"));
        assertTrue(body.contains("\"total\":2"), body);
    }

    @Test
    @DisplayName("非 ADMIN 且非项目所有者 403")
    void nonOwnerForbidden() throws Exception {
        sessions.userId = ENGINEER;
        MvcResult result = mockMvc.perform(get("/api/audit")
                .param("projectId", String.valueOf(ALPHA))
                .header("Authorization", "Bearer t")).andReturn();
        assertEquals(403, result.getResponse().getStatus());
        assertFalse(body(result).contains("Alpha"), "403 不应泄露项目名");
    }

    @Test
    @DisplayName("非 ADMIN 不带 projectId 403")
    void nonAdminWithoutProjectForbidden() throws Exception {
        sessions.userId = PM;
        MvcResult result = mockMvc.perform(get("/api/audit")
                .header("Authorization", "Bearer t")).andReturn();
        assertEquals(403, result.getResponse().getStatus());
    }

    @Test
    @DisplayName("项目所有者 PM 可查本项目")
    void ownerCanQueryProject() throws Exception {
        sessions.userId = PM;
        MvcResult result = mockMvc.perform(get("/api/audit")
                .param("projectId", String.valueOf(ALPHA))
                .header("Authorization", "Bearer t")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(body(result).contains("\"total\":2"));
    }

    @Test
    @DisplayName("筛选 action / from / to 生效并归一化分页")
    void filtersAndPagination() throws Exception {
        sessions.userId = ADMIN;
        mockMvc.perform(get("/api/audit")
                .param("projectId", String.valueOf(ALPHA))
                .param("action", "user:create")
                .param("from", "2026-11-01 00:00:00")
                .param("to", "2026-11-02")
                .param("page", "0")
                .param("size", "1000")
                .header("Authorization", "Bearer t")).andReturn();

        AuditQuery captured = auditRepo.lastQuery;
        assertNotNull(captured);
        assertEquals(ALPHA, captured.projectId());
        assertEquals("user:create", captured.action());
        assertEquals(1, captured.page(), "page=0 应归一化为 1");
        assertEquals(100, captured.size(), "size 上限应为 100");
        assertEquals(Instant.parse("2026-10-31T16:00:00Z"), captured.from());
        assertNotNull(captured.to());
    }

    @Test
    @DisplayName("CSV 导出含固定表头与数据行")
    void csvExport() throws Exception {
        sessions.userId = ADMIN;
        MvcResult result = mockMvc.perform(get("/api/audit/export")
                .param("projectId", String.valueOf(ALPHA))
                .header("Authorization", "Bearer t")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("text/csv;charset=UTF-8", result.getResponse().getContentType());
        String csv = body(result);
        String[] lines = csv.split("\n");
        assertEquals(3, lines.length, "表头 + 2 行数据");
        assertEquals("时间,操作人,动作,资源类型,资源ID,变更摘要,IP", lines[0]);
        assertTrue(lines[1].contains("user:create"));
        assertTrue(lines[2].contains("credential:upsert"));
    }

    @Test
    @DisplayName("审计接口只增不改：DELETE 不被接受")
    void auditIsAppendOnly() throws Exception {
        sessions.userId = ADMIN;
        MvcResult result = mockMvc.perform(delete("/api/audit")
                .header("Authorization", "Bearer t")).andReturn();
        assertTrue(result.getResponse().getStatus() == 405
                || result.getResponse().getStatus() == 404,
                "审计不应提供删除接口，实际 " + result.getResponse().getStatus());
    }

    // ------------------------------------------------------------------

    private static AppUser user(long id, String name) {
        return new AppUser(id, name, name, null, null, null, true, null, 0, null, null);
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private UserRepository userRepository() {
        return stub(UserRepository.class, (name, args) -> switch (name) {
            case "findById" -> Optional.ofNullable(users.get(((Number) args[0]).longValue()));
            case "roleCodesOf" -> globalRoles.getOrDefault(((Number) args[0]).longValue(), List.of());
            case "rolesOf" -> List.of();
            default -> UNHANDLED;
        });
    }

    private ProjectRepository projectRepository() {
        return stub(ProjectRepository.class, (name, args) -> switch (name) {
            case "findById" -> Optional.ofNullable(projects.get(((Number) args[0]).longValue()));
            case "findMember" -> {
                Map<Long, String> members = memberships.get(((Number) args[0]).longValue());
                long uid = ((Number) args[1]).longValue();
                if (members == null || !members.containsKey(uid)) {
                    yield Optional.empty();
                }
                yield Optional.of(new ProjectMember(null, ((Number) args[0]).longValue(), uid,
                        users.get(uid).username(), members.get(uid)));
            }
            case "findByMember" -> List.of();
            default -> UNHANDLED;
        });
    }

    private static final class RecordingAudit implements AuditRepository {
        private final List<AuditEntry> rows = List.of(
                new AuditEntry(1L, 7L, 1L, "admin", "user:create", "app_user", "99", null,
                        "created=true", "127.0.0.1", Instant.parse("2026-11-01T10:00:00Z")),
                new AuditEntry(2L, 7L, 1L, "admin", "credential:upsert", "credential", "21", null,
                        "configured=true", "127.0.0.1", Instant.parse("2026-11-01T11:00:00Z")));
        private AuditQuery lastQuery;

        @Override
        public void insert(Long projectId, Long actorId, String action, String resourceType,
                           String resourceId, String detail) {
        }

        @Override
        public PageResult<AuditEntry> page(AuditQuery query) {
            lastQuery = query;
            return new PageResult<>(rows, 2, query.page(), query.size());
        }

        @Override
        public List<AuditEntry> export(AuditQuery query, int limit) {
            lastQuery = query;
            return rows;
        }

        @Override
        public List<String> recentActions(Long projectId, int limit) {
            return List.of();
        }
    }

    private static final class FakeSessions implements SessionTokenRepository {
        private long userId;

        @Override
        public void insert(long userId, String tokenHash, Instant expiresAt) {
        }

        @Override
        public Optional<Long> findValidUserId(String tokenHash, Instant now) {
            return Optional.of(userId);
        }

        @Override
        public void touch(String tokenHash, Instant usedAt) {
        }

        @Override
        public void revoke(String tokenHash, Instant revokedAt) {
        }
    }

    @FunctionalInterface
    interface Handler {
        Object handle(String name, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    Object result = handler.handle(method.getName(),
                            args == null ? new Object[0] : args);
                    if (result != UNHANDLED) {
                        return result;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == Optional.class) {
                        return Optional.empty();
                    }
                    if (returnType == List.class) {
                        return List.of();
                    }
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }
}
