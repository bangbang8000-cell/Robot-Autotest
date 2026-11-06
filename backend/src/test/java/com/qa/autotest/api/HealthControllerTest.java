package com.qa.autotest.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.qa.autotest.common.ApiResponse;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** ENVX-02 健康检查：存活探针不碰库，就绪探针真查库且失败不抛。 */
class HealthControllerTest {

    @Test
    @DisplayName("/healthz 不查库（DB 抖动不得触发无意义重启）")
    @SuppressWarnings("unchecked")
    void livenessDoesNotTouchDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ApiResponse<Map<String, Object>> response = new HealthController(jdbc).live();
        assertEquals("UP", response.data().get("status"));
        assertEquals("liveness", response.data().get("check"));
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("/api/health 查库正常返回 UP")
    @SuppressWarnings("unchecked")
    void readinessUp() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        ApiResponse<Map<String, Object>> response = new HealthController(jdbc).ready();
        assertEquals("UP", response.data().get("status"));
        assertEquals("UP", response.data().get("database"));
    }

    @Test
    @DisplayName("/api/health DB 异常时标 DEGRADED，不抛 500")
    @SuppressWarnings("unchecked")
    void readinessDegraded() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(
                new IllegalStateException("connection refused"));
        ApiResponse<Map<String, Object>> response = new HealthController(jdbc).ready();
        assertEquals("DEGRADED", response.data().get("status"));
        assertEquals("DOWN", response.data().get("database"));
    }
}
