package com.qa.autotest.application.execution;

import com.qa.autotest.domain.execution.FailureClass;

/**
 * 重试策略：只有环境类（ENV）失败且尚未用满尝试次数时才重试；断言、基础设施、取消都绝不重试。
 */
public final class RetryPolicy {

    /** 默认最大尝试次数（含首次），即 ENV 最多重试 1 次（spec §4.7）。 */
    private final int maxAttempts;

    public RetryPolicy() {
        this(1);
    }

    public RetryPolicy(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /** 用构造时的默认次数判定。 */
    public boolean shouldRetry(FailureClass cls, int attemptNo) {
        return shouldRetry(cls, attemptNo, maxAttempts);
    }

    public boolean shouldRetry(FailureClass cls, int attemptNo, int maxAttempts) {
        return cls == FailureClass.ENV && attemptNo < maxAttempts;
    }
}
