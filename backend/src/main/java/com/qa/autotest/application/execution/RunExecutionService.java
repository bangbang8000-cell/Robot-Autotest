package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.RunArtifactRepository;
import com.qa.autotest.application.repository.TestResultWriter;
import com.qa.autotest.domain.execution.FailureClass;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import com.qa.autotest.domain.execution.RunStateMachine;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * 运行执行编排（W2 最小闭环）：创建 -> 预检 -> 建目录 -> RUNNING -> pabot -> 校验 -> 终态。
 * 租约（EXE-02）与实时日志（EXE-04）在后续任务接入。
 */
@Service
public class RunExecutionService {

    private final RunService runs;
    private final ExecutionProperties props;
    private final ExecutionPrecheck precheck;
    private final PabotCommandBuilder commands;
    private final PabotOrchestrator orchestrator;
    private final RunArtifactRepository artifacts;
    private final TestResultWriter results;
    private final FailureClassifier classifier;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public RunExecutionService(RunService runs,
                               ExecutionProperties props,
                               ExecutionPrecheck precheck,
                               PabotCommandBuilder commands,
                               PabotOrchestrator orchestrator,
                               RunArtifactRepository artifacts,
                               TestResultWriter results,
                               FailureClassifier classifier,
                               RetryPolicy retryPolicy,
                               Clock clock) {
        this.runs = runs;
        this.props = props;
        this.precheck = precheck;
        this.commands = commands;
        this.orchestrator = orchestrator;
        this.artifacts = artifacts;
        this.results = results;
        this.classifier = classifier;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    public RunRecord execute(RunService.CreateRunCommand cmd) {
        RunRecord created = runs.create(cmd);
        return execute(created, cmd);
    }

    /**
     * 执行一条已创建的 run（PLN-01 计划调度用）。
     *
     * <p>调度器先把 run 以 trigger_type='schedule' + plan_id 落库（短事务提交），
     * 再通过单线程 TaskExecutor 调本方法，避免把长耗时的 pabot 执行包在调度事务里。
     */
    public RunRecord execute(RunRecord created, RunService.CreateRunCommand cmd) {
        runs.transition(created.runId(), RunState.PREPARING, RunService.TransitionContext.plain());

        Path artifactsRoot = ExecutionPaths.resolve(props.getArtifactsRoot());
        Path runDir = artifactsRoot.resolve(created.runId());
        try {
            Files.createDirectories(runDir);
        } catch (Exception ex) {
            return fail(created, "artifact_dir_failed", "创建产物目录失败: " + ex.getMessage());
        }

        List<ExecutionPrecheck.Violation> violations = precheck.check(
                new ExecutionPrecheck.PrecheckRequest(
                        ExecutionPaths.resolve(props.getVenvBin()),
                        artifactsRoot,
                        cmd.suitePaths(),
                        cmd.concurrency(),
                        props.getAvailablePorts(),
                        props.getMinFreeBytes(),
                        props.getPabotVersionPrefix(),
                        List.of()));
        if (!violations.isEmpty()) {
            String reason = "precheck_" + violations.get(0).code().toLowerCase();
            String message = violations.stream()
                    .map(v -> v.code() + ": " + v.message())
                    .reduce((a, b) -> a + "; " + b).orElse("");
            return fail(created, reason, message);
        }

        // W2 最小闭环：无租约服务，前置条件以「产物目录已建」为准；W3 接入租约后改为两者都校验。
        runs.transition(created.runId(), RunState.RUNNING,
                new RunService.TransitionContext(clock.instant(), null, null, null, null, true, true));

        int processes = Math.min(cmd.concurrency(), cmd.suitePaths().size());
        PabotCommandBuilder.ShardSpec shard = new PabotCommandBuilder.ShardSpec(
                0, cmd.suitePaths(), processes, cmd.includeTag(),
                "run-" + created.runId());
        PabotCommandBuilder.PabotCommand command = commands.build(shard, props, runDir);
        PabotOrchestrator.ShardResult result = orchestrator.runShard(
                created.runId(), command, Duration.ofSeconds(cmd.timeoutSeconds()), line -> {
                });

        registerArtifacts(created.projectId(), created.id(), command, result);

        int total = result.totalTests();
        int passed = result.success() ? total : 0;
        int failed = total - passed;
        runs.updateCounts(created.runId(), total, passed, failed, runDir.toString());

        if (result.success()) {
            results.write(created.projectId(), created.id(), "run-" + created.runId(),
                    "PASS", 0, null, 1);
            return runs.transition(created.runId(), RunState.FINISHED,
                    new RunService.TransitionContext(null, clock.instant(), null, null, null, true, true));
        }
        results.write(created.projectId(), created.id(), "run-" + created.runId(),
                "FAIL", 0, result.reason(), 1);
        FailureClass cls = classify(result.reason(), result.success());
        return runs.transition(created.runId(), RunState.FAILED,
                new RunService.TransitionContext(null, clock.instant(), cls.name(), result.reason(),
                        "分片执行失败: " + result.reason(), true, true));
    }

    private RunRecord fail(RunRecord created, String reason, String message) {
        results.write(created.projectId(), created.id(), "run-" + created.runId(),
                "FAIL", 0, reason, 1);
        FailureClass cls = classify(reason, false);
        return runs.transition(created.runId(), RunState.FAILED,
                new RunService.TransitionContext(null, clock.instant(), cls.name(), reason,
                        message, false, false));
    }

    /** 分类只依赖 reason 与产物结论；分片文本已由 {@link PabotOrchestrator} 落到 run.log。 */
    private FailureClass classify(String reason, boolean artifactOk) {
        return classifier.classify(new FailureClassifier.FailureInput(
                false, reason, null, null, null, null, artifactOk));
    }

    private void registerArtifacts(long projectId, long runDbId,
                                   PabotCommandBuilder.PabotCommand command,
                                   PabotOrchestrator.ShardResult result) {
        Path outputDir = command.outputDir();
        register(projectId, runDbId, "output_xml", result.outputXml(), 0);
        register(projectId, runDbId, "stdout_log", outputDir.resolve("stdout.log"), 0);
        register(projectId, runDbId, "stderr_log", outputDir.resolve("robot_stderr.out"), 0);
    }

    private void register(long projectId, long runDbId, String kind, Path path, int shardNo) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            String sha = HexFormat.of().formatHex(digest.digest());
            artifacts.upsert(projectId, runDbId, kind, path.toString(), sha,
                    Files.size(path), shardNo, null);
        } catch (Exception ignored) {
            // 产物登记失败不影响主流程；日志文件仍在磁盘上。
        }
    }
}
