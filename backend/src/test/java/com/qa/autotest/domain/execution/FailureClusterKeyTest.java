package com.qa.autotest.domain.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FailureClusterKey — 簇键归一化与截断（N8）")
class FailureClusterKeyTest {

    @Test
    @DisplayName("同前缀的超长失败文本截断后仍可区分（不再直接 substring 40）")
    void 截断不并簇() {
        String commonPrefix = "AssertionError: expected value differs from actual for case ";
        String left = FailureClusterKey.of(commonPrefix + "alpha payload");
        String right = FailureClusterKey.of(commonPrefix + "beta payload");

        assertNotEquals(left, right);
        assertTrue(left.length() <= FailureClusterKey.MAX_KEY_LENGTH);
        assertTrue(right.length() <= FailureClusterKey.MAX_KEY_LENGTH);
        assertTrue(left.startsWith("AssertionError: expected"));
        assertTrue(left.contains("…"), "截断标记必须存在，便于一眼看出是缩写");
    }

    @Test
    @DisplayName("同一输入两次截断结果逐字节一致（确定性）")
    void 截断确定性() {
        String raw = "assertion failed: " + "x".repeat(60);
        assertEquals(FailureClusterKey.of(raw), FailureClusterKey.of(raw));
    }

    @Test
    @DisplayName("短文本不受影响")
    void 短文本原样返回() {
        String key = FailureClusterKey.of("connection refused");
        assertEquals("connection refused", key);
        assertTrue(key.length() <= FailureClusterKey.MAX_KEY_LENGTH);
    }

    @Test
    @DisplayName("路径/IP/数字归一化保持不变")
    void 归一化回归() {
        assertEquals("Assertion <n> != <n> at <path>",
                FailureClusterKey.of("Assertion 3 != 5 at /home/qa/robot-autotest/tests/a.robot"));
        assertEquals("<ip> timeout", FailureClusterKey.of("192.168.110.149:22 timeout"));
        assertEquals(FailureClusterKey.UNCLASSIFIED, FailureClusterKey.of("   "));
    }
}
