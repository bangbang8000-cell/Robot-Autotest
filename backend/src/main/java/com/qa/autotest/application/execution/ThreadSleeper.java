package com.qa.autotest.application.execution;

import java.time.Duration;

/** 生产用等待实现；中断时恢复中断标记并立即返回，避免吞掉取消信号。 */
public final class ThreadSleeper implements Sleeper {

    @Override
    public void sleep(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
