package com.qa.autotest.common.tenant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 租户守卫元测试（IAM-04，ADR-0006 第 3 层）。
 *
 * <p>证明「漏写 project_id 的 SQL 一定被拦」，而不是只证明正常 SQL 能跑。
 */
class TenantGuardMetaTest {

    @Test
    @DisplayName("危险 SQL：device 查询未带 project_id 被拦")
    void 缺租户条件被拦() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> TenantGuard.requireProjectScope("SELECT * FROM device", "device"));
        assertEquals(ErrorCode.FORBIDDEN, ex.errorCode());
    }

    @Test
    @DisplayName("危险 SQL：DELETE 也不放过")
    void 删除缺条件被拦() {
        assertThrows(BusinessException.class,
                () -> TenantGuard.requireProjectScope("DELETE FROM credential WHERE id = ?", "credential"));
    }

    @Test
    @DisplayName("合规 SQL：带 project_id 条件放行（大小写不敏感）")
    void 合规SQL放行() {
        assertDoesNotThrow(() -> TenantGuard.requireProjectScope(
                "SELECT * FROM device WHERE PROJECT_ID = ? AND id = ?", "device"));
    }

    @Test
    @DisplayName("未登记租户策略的表直接报错，避免默认放行")
    void 未登记表报错() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> TenantGuard.requireProjectScope("SELECT * FROM unknown_table", "unknown_table"));
        assertEquals(ErrorCode.INTERNAL_ERROR, ex.errorCode());
    }

    @Test
    @DisplayName("白名单表不需要租户条件")
    void 白名单表不要求() {
        assertEquals(false, TenantGuard.requiresTenant("app_user"));
        assertEquals(true, TenantGuard.requiresTenant("test_case"));
    }
}
