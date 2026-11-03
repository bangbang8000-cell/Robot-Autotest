package com.qa.autotest.application.execution;

import java.io.InputStream;
import java.time.Duration;

/**
 * 进程启动抽象，便于单测注入假实现。
 */
public interface ProcessLauncher {

    ExecutionHandle launch(PabotCommandBuilder.PabotCommand command);

    interface ExecutionHandle {

        long pid();

        int waitFor(Duration timeout);

        InputStream stdout();

        InputStream stderr();

        Process process();
    }
}
