package com.qa.autotest.application.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.qa.autotest.application.iam.AuditService;
import com.qa.autotest.application.repository.SystemSettingRepository;
import com.qa.autotest.common.BusinessException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 设置分级（NAV-02）的白名单、校验、审计与只读端口。 */
class SystemSettingServiceTest {

    private static class FakeRepository implements SystemSettingRepository {
        final Map<String, String> system = new LinkedHashMap<>();
        final Map<String, String> project = new LinkedHashMap<>();

        @Override
        public Map<String, String> findAllSystem() {
            return new LinkedHashMap<>(system);
        }

        @Override
        public Optional<String> findSystem(String key) {
            return Optional.ofNullable(system.get(key));
        }

        @Override
        public void upsertSystem(String key, String value, Long actorId) {
            system.put(key, value);
        }

        @Override
        public Map<String, String> findAllProject(long projectId) {
            return new LinkedHashMap<>(project);
        }

        @Override
        public Optional<String> findProject(long projectId, String key) {
            return Optional.ofNullable(project.get(key));
        }

        @Override
        public void upsertProject(long projectId, String key, String value, Long actorId) {
            project.put(key, value);
        }
    }

    private FakeRepository repository;
    private AuditService audit;
    private SystemSettingService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        audit = mock(AuditService.class);
        service = new SystemSettingService(repository, audit);
    }

    @Test
    @DisplayName("默认值齐备，端口是只读展示项且来自配置")
    void defaults() {
        Map<String, String> values = service.platform();
        assertEquals("4", values.get(SystemSettingService.K_MAX_PARALLEL));
        assertEquals("1800", values.get(SystemSettingService.K_DEFAULT_TIMEOUT));
        assertEquals("90", values.get(SystemSettingService.K_RETENTION_DAYS));
        assertEquals("false", values.get(SystemSettingService.K_AI_ENABLED));
        assertEquals("8001", values.get(SystemSettingService.K_ENTRY_PORT));
    }

    @Test
    @DisplayName("非管理员改平台设置一律 403，且不写库、不写审计")
    void adminOnly() {
        assertThrows(BusinessException.class,
                () -> service.putPlatform(Map.of("ai.enabled", "true"), false));
        assertTrue(repository.system.isEmpty());
        verify(audit, times(0)).recordChange(anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    @DisplayName("非法值拒绝：超范围整数、非布尔、未知键、只读端口")
    void rejectsInvalidValues() {
        assertThrows(BusinessException.class,
                () -> service.putPlatform(Map.of("execution.maxParallel", "999"), true));
        assertThrows(BusinessException.class,
                () -> service.putPlatform(Map.of("ai.enabled", "yes"), true));
        assertThrows(BusinessException.class,
                () -> service.putPlatform(Map.of("unknown.key", "x"), true));
        assertThrows(BusinessException.class,
                () -> service.putPlatform(Map.of("entry.port", "9000"), true));
        assertThrows(BusinessException.class,
                () -> service.putProject(1L, Map.of("kernel.default", "junit")));
        assertTrue(repository.system.isEmpty(), "校验失败不得留下半写入状态");
    }

    @Test
    @DisplayName("合法写入落库 + 每个键写一条变更审计（含前后值）")
    void writesAndAudits() {
        Map<String, String> updated = service.putPlatform(
                Map.of("execution.maxParallel", "8", "ai.enabled", "true"), true);
        assertEquals("8", updated.get("execution.maxParallel"));
        assertEquals("true", updated.get("ai.enabled"));
        verify(audit).recordChange(eq("system_setting:update"), eq("system_setting"),
                eq("execution.maxParallel"), eq("4"), eq("8"));
        verify(audit).recordChange(eq("system_setting:update"), eq("system_setting"),
                eq("ai.enabled"), eq("false"), eq("true"));
    }

    @Test
    @DisplayName("项目设置与平台设置互不串台")
    void projectIsolation() {
        service.putPlatform(Map.of("execution.defaultTimeoutSeconds", "600"), true);
        Map<String, String> project = service.putProject(7L,
                Map.of("kernel.default", "pytest", "execution.timeoutSeconds", "300"));
        assertEquals("600", service.platform().get("execution.defaultTimeoutSeconds"));
        assertEquals("pytest", project.get("kernel.default"));
        assertEquals("300", project.get("execution.timeoutSeconds"));
        verify(audit).recordChange(eq("project_setting:update"), eq("project_setting"),
                eq("7:kernel.default"), eq("robot"), eq("pytest"));
    }
}
