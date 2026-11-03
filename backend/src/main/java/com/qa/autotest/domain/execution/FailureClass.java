package com.qa.autotest.domain.execution;

/**
 * 失败分类。仅环境问题（ENV）允许重试一次，断言失败绝不重试。
 */
public enum FailureClass {
    ASSERT,
    ENV,
    INFRA,
    CANCELED;

    public boolean retryable() {
        return this == ENV;
    }
}
