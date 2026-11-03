package com.qa.autotest.infrastructure;

import com.qa.autotest.application.execution.PabotCommandBuilder;
import com.qa.autotest.application.execution.PabotOrchestrator;
import com.qa.autotest.application.execution.ProcessLauncher;

import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** ProcessBuilder 启动器。环境变量全量注入，stdout/stderr 分离。 */
public class ProcessLauncherImpl implements ProcessLauncher {

    @Override
    public ExecutionHandle launch(PabotCommandBuilder.PabotCommand command) {
        ProcessBuilder builder = new ProcessBuilder(command.argv());
        builder.directory(command.workDir().toFile());
        builder.environment().putAll(command.environment());
        builder.redirectErrorStream(false);
        try {
            return new Handle(builder.start());
        } catch (Exception ex) {
            throw new IllegalStateException("启动 pabot 失败: " + ex.getMessage(), ex);
        }
    }

    private static final class Handle implements ExecutionHandle {

        private final Process process;

        private Handle(Process process) {
            this.process = process;
        }

        @Override
        public long pid() {
            return process.pid();
        }

        @Override
        public int waitFor(Duration timeout) {
            try {
                boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                return finished ? process.exitValue() : PabotOrchestrator.ExecutionHandleCodes.TIMEOUT;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return PabotOrchestrator.ExecutionHandleCodes.TIMEOUT;
            }
        }

        @Override
        public InputStream stdout() {
            return process.getInputStream();
        }

        @Override
        public InputStream stderr() {
            return process.getErrorStream();
        }

        @Override
        public Process process() {
            return process;
        }
    }
}
