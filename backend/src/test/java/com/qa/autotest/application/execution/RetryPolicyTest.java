package com.qa.autotest.application.execution;

import com.qa.autotest.domain.execution.FailureClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR-v0.4-28：只有 ENV 且未用满尝试次数才允许重试。 */
class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy();

    @Test
    void 仅环境失败且未超次数时允许重试() {
        assertTrue(policy.shouldRetry(FailureClass.ENV, 0, 1));
        assertTrue(policy.shouldRetry(FailureClass.ENV, 0, 2));
        assertTrue(policy.shouldRetry(FailureClass.ENV, 1, 2));
    }

    @Test
    void 断言基础设施与取消均不重试() {
        assertFalse(policy.shouldRetry(FailureClass.ASSERT, 0, 3));
        assertFalse(policy.shouldRetry(FailureClass.INFRA, 0, 3));
        assertFalse(policy.shouldRetry(FailureClass.CANCELED, 0, 3));
    }

    @Test
    void 达到最大尝试次数后不再重试() {
        assertFalse(policy.shouldRetry(FailureClass.ENV, 1, 1));
        assertFalse(policy.shouldRetry(FailureClass.ENV, 2, 2));
    }

    @Test
    void 无参构造默认最多重试一次() {
        RetryPolicy defaults = new RetryPolicy();
        assertTrue(defaults.shouldRetry(FailureClass.ENV, 0));
        assertFalse(defaults.shouldRetry(FailureClass.ENV, 1));
    }

    @Test
    void 显式最大尝试次数生效() {
        RetryPolicy twice = new RetryPolicy(2);
        assertTrue(twice.shouldRetry(FailureClass.ENV, 0));
        assertTrue(twice.shouldRetry(FailureClass.ENV, 1));
        assertFalse(twice.shouldRetry(FailureClass.ENV, 2));
        assertFalse(twice.shouldRetry(FailureClass.ASSERT, 0));
    }
}
