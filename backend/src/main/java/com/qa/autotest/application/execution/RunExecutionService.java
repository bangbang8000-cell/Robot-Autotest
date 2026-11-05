package com.qa.autotest.application.execution;

import com.qa.autotest.application.repository.RunArtifactRepository;
import com.qa.autotest.application.repository.TestResultWriter;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.domain.execution.FailureClass;
import com.qa.autotest.domain.execution.RunRecord;
import com.qa.autotest.domain.execution.RunState;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 运行执行编排（EXEC-09）：创建 -> 预检 -> 建目录 -> 申请租约 -> RUNNING -> pabot -> 校验 -> 终态 -> 释放租约。
 *
 * <p>租约按 run 维度覆盖本次运行要占用的资源键集合：有设备范围时用「设备+端口」键，
 * 缺省退化为环境（{@code environment:<id>}）或项目（{@code project:<id>}）独占键。
 * 申请失败不进入 RUNNING，直接 failed(lease_timeout)；任何离开执行段的路径都释放租约。
 *
 * <p><b>键口径</b>：只有请求显式给出 {@code deviceKeys}（FR-01 设备范围）时才按「设备+端口」互斥；
 * 未声明设备范围的运行（工作区与端口已按 run 隔离）用 {@code run:<runId>} 自持，
 * 避免 MAX_PARALLEL 内的并发运行被一条项目级租约串行化甚至超时失败。
 */
@Service
public class RunExecutionService {

    /** {@code resource_lease.resource_key} 列宽（V1 基线 128）。 */
    static final int MAX_RESOURCE_KEY_LENGTH = 128;

    private final RunService runs;
    private final ExecutionProperties props;
    private final ExecutionPrecheck precheck;
    private final LeaseService leases;
    private final ExecutionWorkspaceMaterializer workspaces;
    private final PabotCommandBuilder commands;
    private final PabotOrchestrator orchestrator;
    private final RunArtifactRepository artifacts;
    private final TestResultWriter results;
    private final FailureClassifier classifier;
    private final RetryPolicy retryPolicy;
    private final Clock clock;
    private final LeaseWaitRegistry leaseWaits;

    public RunExecutionService(RunService runs,
                               ExecutionProperties props,
                               ExecutionPrecheck precheck,
                               LeaseService leases,
                               ExecutionWorkspaceMaterializer workspaces,
                               PabotCommandBuilder commands,
                               PabotOrchestrator orchestrator,
                               RunArtifactRepository artifacts,
                               TestResultWriter results,
                               FailureClassifier classifier,
                               RetryPolicy retryPolicy,
                               Clock clock,
                               LeaseWaitRegistry leaseWaits) {
        this.runs = runs;
        this.props = props;
        this.precheck = precheck;
        this.leases = leases;
        this.workspaces = workspaces;
        this.commands = commands;
        this.orchestrator = orchestrator;
        this.artifacts = artifacts;
        this.results = results;
        this.classifier = classifier;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
        this.leaseWaits = leaseWaits;
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

        // EXEC-05：数据库 robot_file 是脚本真源，先落盘到本次 run 的独立工作区；
        // 数据库为空时回落到 execution.workspace-root，兼容仍在磁盘维护脚本的项目。
        Path workDir;
        boolean materialized;
        try {
            Optional<Path> copy = workspaces.materialize(created.projectId(), runDir);
            materialized = copy.isPresent();
            workDir = copy.orElseGet(() -> ExecutionPaths.resolve(props.getWorkspaceRoot()));
        } catch (Exception ex) {
            return fail(created, "workspace_materialize_failed",
                    "执行工作区落盘失败: " + ex.getMessage());
        }
        // FR-v0.4-24 单用例超时：RF 没有全局 CLI 开关，只能在套件 `*** Settings ***` 注入
        // `Test Timeout`。只改本 run 的副本（materialize 成功时），
        // 不碰数据库脚本与真实 workspace-root（回落分支下那是用户原脚本）。
        // 本次触发显式给了 testTimeoutSeconds 就用它，否则沿用全局默认。
        if (materialized) {
            int testTimeout = cmd.testTimeoutSeconds() > 0
                    ? cmd.testTimeoutSeconds() : props.getTestTimeoutSeconds();
            TestTimeoutInjector.inject(workDir, cmd.suitePaths(), testTimeout);
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

        // EXEC-09：进入 RUNNING 之前必须先持有租约（状态机的前置条件由 RunService 强制校验）。
        List<String> resourceKeys;
        try {
            resourceKeys = resourceKeys(created.runId(), cmd);
        } catch (IllegalArgumentException invalidKey) {
            return fail(created, "lease_key_invalid", invalidKey.getMessage());
        }
        long jobId;
        try {
            jobId = runs.leaseJobId(created.runId());
        } catch (BusinessException missingJob) {
            return fail(created, "lease_job_missing",
                    "找不到执行作业记录，无法申请租约: " + missingJob.getMessage());
        }
        List<LeaseService.LeaseRequest> requests = new ArrayList<>();
        for (String key : resourceKeys) {
            requests.add(new LeaseService.LeaseRequest(
                    key, jobId, created.runId(), created.projectId()));
        }
        Duration maxWait = Duration.ofSeconds(Math.max(0, props.getLeaseMaxWaitSeconds()));
        boolean acquired;
        // FR-08：等租约期间登记「等待资源」，接口据此显示 waiting；结束（成功/超时/异常）都回写耗时。
        leaseWaits.begin(created.runId());
        try {
            acquired = leases.acquireAll(requests, maxWait);
        } catch (RuntimeException acquireFailed) {
            runs.recordLeaseWait(created.runId(), leaseWaits.end(created.runId()));
            return fail(created, "lease_acquire_failed",
                    "申请租约异常: " + acquireFailed.getMessage());
        }
        runs.recordLeaseWait(created.runId(), leaseWaits.end(created.runId()));
        if (!acquired) {
            return fail(created, "lease_timeout", FailureClass.INFRA,
                    "等待资源 " + String.join(", ", resourceKeys) + " 超过 "
                            + maxWait.toSeconds() + " 秒仍被占用，本次运行终止");
        }

        String releaseReason = "failed";
        try {
            runs.transition(created.runId(), RunState.RUNNING,
                    new RunService.TransitionContext(clock.instant(), null, null, null, null, true, true));

            int processes = Math.min(cmd.concurrency(), cmd.suitePaths().size());
            PabotCommandBuilder.ShardSpec shard = new PabotCommandBuilder.ShardSpec(
                    0, cmd.suitePaths(), processes, cmd.includeTag(),
                    "run-" + created.runId(), cmd.testNames());
            PabotCommandBuilder.PabotCommand command = commands.build(shard, props, runDir, workDir);
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
                releaseReason = "finished";
                return runs.transition(created.runId(), RunState.FINISHED,
                        new RunService.TransitionContext(null, clock.instant(), null, null, null,
                                true, true));
            }
            results.write(created.projectId(), created.id(), "run-" + created.runId(),
                    "FAIL", 0, result.reason(), 1);
            FailureClass cls = classify(result.reason(), result.success());
            return runs.transition(created.runId(), RunState.FAILED,
                    new RunService.TransitionContext(null, clock.instant(), cls.name(),
                            result.reason(), "分片执行失败: " + result.reason(), true, true));
        } finally {
            // 成功、失败、异常、并发取消（transition 抛错）都走同一条释放路径。
            leases.releaseRun(created.runId(), releaseReason);
        }
    }

    /**
     * 本次运行的资源键：声明了设备范围就用「设备+端口」键（FR-v0.4-06），否则用 {@code run:<runId>} 自持。
     *
     * <p>未声明设备范围时不做互斥：执行工作区已按 run 隔离（EXEC-05），端口由预检保证，
     * 若再叠加环境/项目级独占键，会让 {@code MAX_PARALLEL} 内的并发运行互相等待直至 lease_timeout。
     */
    static List<String> resourceKeys(String runId, RunService.CreateRunCommand cmd) {
        List<String> keys = new ArrayList<>();
        if (cmd.deviceKeys() != null) {
            for (String raw : cmd.deviceKeys()) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String key = raw.trim();
                if (key.length() > MAX_RESOURCE_KEY_LENGTH) {
                    throw new IllegalArgumentException(
                            "资源键超过 " + MAX_RESOURCE_KEY_LENGTH + " 字符: " + key);
                }
                if (!keys.contains(key)) {
                    keys.add(key);
                }
            }
        }
        if (keys.isEmpty()) {
            keys.add("run:" + runId);
        }
        return keys;
    }

    private RunRecord fail(RunRecord created, String reason, String message) {
        return fail(created, reason, classify(reason, false), message);
    }

    private RunRecord fail(RunRecord created, String reason, FailureClass cls, String message) {
        results.write(created.projectId(), created.id(), "run-" + created.runId(),
                "FAIL", 0, reason, 1);
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
