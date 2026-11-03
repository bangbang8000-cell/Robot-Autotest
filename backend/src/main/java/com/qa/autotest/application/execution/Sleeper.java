package com.qa.autotest.application.execution;

import java.time.Duration;

/** 可替换的等待实现：租约重试在测试里必须可控（不能真的睡 60 秒）。 */
public interface Sleeper {

    void sleep(Duration duration);
}
