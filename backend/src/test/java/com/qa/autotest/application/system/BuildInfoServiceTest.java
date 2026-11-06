package com.qa.autotest.application.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** 关于页构建信息（NAV-03）：来源标注、依赖清单规模、自检四项。 */
class BuildInfoServiceTest {

    @Test
    @DisplayName("来源只能是 build 或 runtime，且明确标注，不许伪装")
    void sourceIsExplicit() {
        Map<String, Object> info = new BuildInfoService().info();
        String source = String.valueOf(info.get("source"));
        assertTrue(source.equals("build") || source.equals("runtime"), "来源必须显式：" + source);
        assertEquals("8001", info.get("entryPort"));
        assertEquals("8081", info.get("backendPort"));
        assertTrue(String.valueOf(info.get("license")).contains("Apache-2.0"));
    }

    @Test
    @DisplayName("依赖清单至少 10 项，每项都有名称/版本/来源")
    @SuppressWarnings("unchecked")
    void dependencyList() {
        Map<String, Object> info = new BuildInfoService().info();
        List<Map<String, String>> deps = (List<Map<String, String>>) info.get("dependencies");
        assertTrue(deps.size() >= 10, "依赖清单不足 10 项：" + deps.size());
        for (Map<String, String> dep : deps) {
            assertTrue(dep.containsKey("name") && !dep.get("name").isBlank());
            assertTrue(dep.containsKey("version") && !dep.get("version").isBlank());
            assertTrue(dep.containsKey("source"));
        }
    }

    @Test
    @DisplayName("自检四项齐全；DB 挂了只标 DOWN，不抛异常")
    void selfCheck() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(
                new IllegalStateException("db down"));
        Map<String, Object> checks = new BuildInfoService().selfCheck(jdbc);
        assertEquals(4, checks.size());
        assertEquals("DOWN", checks.get("database"));
        for (String key : List.of("executor", "kernel", "aiCli")) {
            assertTrue(checks.containsKey(key), "缺自检项 " + key);
        }
    }
}
