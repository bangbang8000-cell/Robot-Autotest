package com.qa.autotest.application.execution;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * 单分片执行：双日志边跑边写，退出码不可信，最终以产物校验为准。
 *
 * <p>实时推送与全量落盘分开：{@code run.log} 一行不落（不受 50 行/100ms 限流影响），
 * 推送走 {@link RunLogService}。
 */
@Service
public class PabotOrchestrator {

    private final ProcessLauncher launcher;
    private final ArtifactVerifier verifier;
    private final ProcessRegistry registry;
    private final RunLogService logs;
    private final Clock clock;

    public PabotOrchestrator(ProcessLauncher launcher, ArtifactVerifier verifier,
                             ProcessRegistry registry, RunLogService logs, Clock clock) {
        this.launcher = launcher;
        this.verifier = verifier;
        this.registry = registry;
        this.logs = logs;
        this.clock = clock;
    }

    public ShardResult runShard(String runId,
                                PabotCommandBuilder.PabotCommand command,
                                Duration timeout,
                                Consumer<String> logSink) {
        Path outputDir = command.outputDir();
        Path runLog = outputDir.getParent() == null
                ? outputDir.resolve("run.log") : outputDir.getParent().resolve("run.log");
        Path stdoutFile = outputDir.resolve("stdout.log");
        Path stderrFile = outputDir.resolve("robot_stderr.out");
        try {
            Files.createDirectories(outputDir);
            Files.writeString(stdoutFile, "", StandardCharsets.UTF_8);
            Files.writeString(stderrFile, "", StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return new ShardResult(false, "artifact_dir_failed", 0, -1,
                    outputDir.resolve("output.xml"));
        }
        emit(runId, runLog, logSink, "[system] run " + runId + " 启动");

        ProcessLauncher.ExecutionHandle handle;
        try {
            handle = launcher.launch(command);
        } catch (Exception ex) {
            emit(runId, runLog, logSink, "[system] launch failed: " + ex.getMessage());
            return new ShardResult(false, "launch_failed", 0, -1, outputDir.resolve("output.xml"));
        }

        emit(runId, runLog, logSink, "[system] 开始执行 pabot: " + String.join(" ", command.argv()));
        registry.register(runId, handle.process());
        Instant started = clock.instant();
        Thread outThread = pump(runId, runLog, handle.stdout(), stdoutFile, "stdout", logSink);
        Thread errThread = pump(runId, runLog, handle.stderr(), stderrFile, "stderr", logSink);
        int exitCode = handle.waitFor(timeout);
        registry.unregister(runId);
        boolean timeoutHit = exitCode == ExecutionHandleCodes.TIMEOUT;
        if (timeoutHit && handle.process() != null) {
            handle.process().descendants().forEach(ProcessHandle::destroyForcibly);
            handle.process().destroyForcibly();
            emit(runId, runLog, logSink, "[system] 执行超时，已强制终止 PID=" + handle.pid());
        }
        join(outThread);
        join(errThread);
        emit(runId, runLog, logSink, "[system] 进程退出码=" + exitCode
                + "，耗时=" + Duration.between(started, clock.instant()));

        Path outputXml = outputDir.resolve("output.xml");
        ArtifactVerifier.VerifyResult verified = verifier.verify(outputXml);
        // 分片完整性：被杀分片的 output.xml 是 0 字节，合并后的 totalTests 只会变小、
        // 不会变错，所以必须单独查一遍分片，否则「用例凭空消失」会被判成全绿。
        ArtifactVerifier.ShardIntegrity shards = verifier.checkShards(outputDir);
        // FR-v0.4-24：单用例超时（套件级 Test Timeout）不是整体超时，但 run 原因要能区分。
        boolean testTimedOut = !timeoutHit && verifier.testTimedOut(outputXml);
        String reason = timeoutHit ? "timeout"
                : (!shards.ok() ? shards.reason()
                : (testTimedOut ? "test_timeout" : verified.reason()));
        boolean success = !timeoutHit && !testTimedOut && exitCode == 0
                && verified.success() && shards.ok();
        if (!shards.ok()) {
            emit(runId, runLog, logSink, "[system] 分片不完整: reason=" + shards.reason()
                    + ", 空分片=" + shards.emptyShards());
        }
        if (testTimedOut) {
            emit(runId, runLog, logSink,
                    "[system] 存在单用例超时（Test Timeout 命中），run 记为 test_timeout");
        }
        if (success) {
            emit(runId, runLog, logSink, "[system] 产物校验通过，totalTests=" + verified.totalTests());
        } else {
            emit(runId, runLog, logSink,
                    "[system] 产物校验失败: reason=" + reason + ", exit=" + exitCode);
            diagnose(outputDir, runId, runLog, logSink);
        }
        return new ShardResult(success, reason, verified.totalTests(), exitCode, outputXml);
    }

    private void diagnose(Path outputDir, String runId, Path runLog, Consumer<String> logSink) {
        Path manager = outputDir.resolve("pabot_results").resolve("pabot_manager.log");
        if (Files.exists(manager)) {
            emit(runId, runLog, logSink, "[system] 诊断顺序 1/3: pabot_manager.log 存在 " + manager);
        }
        Path robotErr = outputDir.resolve("robot_stderr.out");
        if (Files.exists(robotErr)) {
            emit(runId, runLog, logSink, "[system] 诊断顺序 2/3: robot_stderr.out 存在 " + robotErr);
        }
        emit(runId, runLog, logSink,
                "[system] 诊断顺序 3/3: stdout.log 存在 " + outputDir.resolve("stdout.log"));
    }

    private Thread pump(String runId, Path runLog, InputStream stream, Path target,
                        String source, Consumer<String> logSink) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
                 BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                         StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                    emit(runId, runLog, logSink, "[" + source + "] " + line);
                }
            } catch (Exception ex) {
                emit(runId, runLog, logSink, "[" + source + "] 读取失败: " + ex.getMessage());
            }
        }, "pabot-" + source);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void join(Thread thread) {
        try {
            thread.join(2000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** 一条日志三处落点：实时频道、run.log 全量、调用方 sink。 */
    private void emit(String runId, Path runLog, Consumer<String> logSink, String line) {
        if (logs != null) {
            logs.publish(runId, "pabot", line);
        }
        if (logSink != null) {
            logSink.accept(line);
        }
        append(runLog, line);
    }

    private void append(Path runLog, String line) {
        try {
            Files.writeString(runLog, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // 全量落盘失败不应中断执行；实时频道仍可用。
        }
    }

    public record ShardResult(boolean success, String reason, int totalTests, int exitCode,
                              Path outputXml) {
    }

    public static final class ExecutionHandleCodes {
        public static final int TIMEOUT = Integer.MIN_VALUE;

        private ExecutionHandleCodes() {
        }
    }
}
