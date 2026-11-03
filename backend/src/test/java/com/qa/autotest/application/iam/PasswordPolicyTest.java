package com.qa.autotest.application.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 密码策略单测（FR-v0.2-05，TC-v0.2-005~008）。 */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    @DisplayName("合规口令通过")
    void 合规口令通过() {
        policy.validate("Abcdef12345", "engineer01");
    }

    @Test
    @DisplayName("长度不足 10 位被拒")
    void 长度不足被拒() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> policy.validate("Abc12345", "engineer01"));
        assertEquals(ErrorCode.INVALID_ARGUMENT, ex.errorCode());
    }

    @Test
    @DisplayName("缺大写 / 小写 / 数字分别被拒")
    void 字符类别缺失被拒() {
        assertThrows(BusinessException.class, () -> policy.validate("abcdef12345", "engineer01"));
        assertThrows(BusinessException.class, () -> policy.validate("ABCDEF12345", "engineer01"));
        assertThrows(BusinessException.class, () -> policy.validate("Abcdefghijk", "engineer01"));
    }

    @Test
    @DisplayName("口令不得等于用户名")
    void 不得等于用户名() {
        assertThrows(BusinessException.class, () -> policy.validate("Engineer01", "engineer01"));
    }

    @Test
    @DisplayName("90 天过期判定")
    void 过期判定() {
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        assertTrue(policy.expired(now.minusSeconds(1), now));
        assertFalse(policy.expired(policy.expiresAt(now), now));
        assertFalse(policy.expired(null, now));
        assertEquals(90, PasswordPolicy.EXPIRY_DAYS);
    }
}
