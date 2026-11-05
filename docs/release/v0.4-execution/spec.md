# v0.4 执行域 · 实现规格（Spec）

**文档编号** RA-REL-2026-004-SPEC ｜ **版本** v0.4 ｜ **门禁** G4

**范围** EXE-01 ~ EXE-08（WBS #30 ~ #37）+ EXEC-02（WBS #83）+ EXEC-03（WBS #84）+ EXEC-04（WBS #85）+ EXEC-05（WBS #86）+ EXEC-06（WBS #87）+ EXEC-07（WBS #88）+ EXEC-08（WBS #89）+ EXEC-09（WBS #90）+ EXEC-10（WBS #91）+ EXEC-11（WBS #92） ｜ **上游** `PRD.md` / `DEV-PLAN.md` / `TEST-PLAN.md`

**本规格的地位**：本文件是 v0.4 执行域的「决策完整」实现契约。所有类名、方法签名、表字段、测试判据、证据文件名以本文件为准；实现过程中如发现与上游冲突，先改本文件并重新评审，不直接改代码。

---

## 1. 目标与边界

### 1.1 本版要交付的能力

| 能力 | 判定 |
|---|---|
| 运行状态机 | `queued → preparing → running → finished / failed / canceled`，非法跃迁抛错 |
| 租约互斥 | `resource_key` 唯一即互斥；100 线程抢同 key 成功数恰为 1 |
| pabot 编排 | PATH 前置 venv/bin、双日志、退出码不可信、四条件产物校验 |
| 实时日志 | 按 run 分频道、RingBuffer 2000 行、100ms 合并 ≤50 行、重连补 200 行、全量落盘 |
| 取消/超时/幂等/恢复 | 杀进程树、释放租约、同 `run_request_id` 只创建 1 个 run、重启无僵尸 |
| 失败分类 | `assert / env / infra / canceled` 四类，30 条样本准确率 100%，仅 env 重试 1 次 |
| DUT 采集 | SSH 采集 `ip -br addr` / `ethtool -S` / `/proc/interrupts`，失败分类与建议，凭据不进日志 |
| 4 并发压测 | 固定 fixture + 可复跑脚本，20 轮报告（本机只交付脚本，实跑留本地 WSL） |

### 1.2 明确不做

- 不做执行器池与负载均衡（PRD §6）。
- 不做分布式多执行节点、容器化执行。
- 不做定时/CI 触发（v0.6）、报告渲染与导出（v0.5）。
- 不做 run 明细的完整前端（本版只做运行记录 + 实时日志面板）。
- 不在本机连 MariaDB、不跑 pabot、不连 DUT。

### 1.3 环境事实与降级口径

| 项 | 本机（Windows） | 本地 WSL（验收环境） |
|---|---|---|
| MariaDB | 无 | 有，`make db-reset` / `make db-audit` |
| RF / pabot | 无 | `~/rf-stack/.venv`（RF 7.5 + pabot 5.2.2） |
| DUT | 不可达 | 经 `192.168.180.2` 到 `10.202.54.106/107` |
| 单测 | `mvn -B test`（不含 `it`） | `mvn -B -Pit test` |
| EXE-08 | 只做回放样本与分类 | 实网跑 1 条用例 |

**诚实约束**：DUT 实网未跑时，证据首行必须是 `[REPLAY] 非实网`，WBS 状态只能是 `待验`，不得写「实网通过」。

---

## 2. 架构决策（需批准项）

### 2.1 新增依赖（需批准）

| 依赖 | 用途 | 理由 |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-websocket` | EXE-04 实时日志 WebSocket | `spring-websocket` 本地 m2 缺失；Maven Central 实测可达，随构建拉取 |

**不新增** JSch / Mina：DUT SSH 走系统 OpenSSH（见 §9），避免为一条采集链路引入 SSH 库并把口令搬进进程内存之外的临时文件。

### 2.2 配额与状态落点

- **运行状态唯一落点 = `run.run_status`**，取值扩为 `queued/preparing/running/finished/failed/canceled`，不新增第二套状态字段。
- `execution_job` 保持「一次提交」语义：创建 run 时同步插一条 `execution_job`（`job_no = run_id`），租约 `job_id` 指向它，兼容既有 `fk_resource_lease_job`。
- `execution_queue` 本版**不写**（队列表留给 v0.6 计划域）；队列排序由 `run.priority + run.enqueued_at` 承担，减少一套写路径。

### 2.3 权限资源（需批准）

新增 `PermissionResource.EXECUTION("执行")`：

| 角色 | 动作 |
|---|---|
| ADMIN | 全部（`values()` 自动覆盖） |
| PM | READ / CREATE / UPDATE / EXECUTE / DELETE |
| ENGINEER | READ / CREATE / UPDATE / EXECUTE |
| VIEWER | READ |

取消与强制释放属 `UPDATE`；查看运行与日志属 `READ`。

### 2.4 前端范围（需批准）

只实现 `RunsView.vue` 的运行记录 + 实时日志面板（核心场景 1~7 的可视化面），不做执行计划页。

### 2.5 DUT 认证方式（需批准）

- 默认公钥：`ssh -i <keyPath> -J <jumpUser>@<jumpHost> ...`。
- 口令：`sshpass -e ssh ...`，口令经 ProcessBuilder 环境变量 `SSHPASS` 注入，**argv 与日志均不出现明文**。
- 若目标要求口令但 `sshpass` 不存在：预检直接拒绝并给出安装建议；不在临时文件落盘口令。

---

## 3. 包与文件清单（精确到路径）

### 3.1 领域层 `backend/src/main/java/com/qa/autotest/domain/execution/`

| 文件 | 内容 |
|---|---|
| `RunState.java` | `enum RunState { QUEUED, PREPARING, RUNNING, FINISHED, FAILED, CANCELED }`；`boolean terminal()`；`boolean active()`（PREPARING/RUNNING）；`String dbValue()` 小写 |
| `IllegalStateTransition.java` | `RuntimeException`，字段 `from` / `to`，消息含两端状态 |
| `RunStateMachine.java` | 持有当前状态；`static Map<RunState, Set<RunState>> ALLOWED`；`RunState state()`；`void transition(RunState to)`；`void transition(RunState to, boolean precondition, String preconditionName)`（条件不满足抛 `IllegalStateTransition` 的变体消息） |
| `FailureClass.java` | `enum FailureClass { ASSERT, ENV, INFRA, CANCELED }`；`boolean retryable()` 仅 ENV |
| `Lease.java` | `record Lease(String resourceKey, long jobId, String runId, long projectId, Instant acquiredAt, Instant expiresAt)` |
| `RunRecord.java` | `record RunRecord(long id, String runId, long projectId, Long testSetId, RunState state, int priority, int concurrency, int attemptNo, int totalTests, int passedTests, int failedTests, String failureClass, String failureReason, String failureMessage, String artifactsDir, Instant enqueuedAt, Instant startedAt, Instant finishedAt)` |
| `DutCollection.java` | `record CommandOutput(String name, int exitCode, String stdout, String stderr)`；`record DutCollection(String targetLabel, List<CommandOutput> outputs, Instant startedAt, Instant finishedAt)` |

**约束**：本包不得 import `org.springframework..`、`jakarta..`、`com.fasterxml.jackson..`，不得 import `..application..` / `..api..` / `..infrastructure..`（ArchUnit R1/R2）。

### 3.2 应用层 `backend/src/main/java/com/qa/autotest/application/execution/`

| 文件 | 关键 API |
|---|---|
| `ExecutionProperties.java` | `@ConfigurationProperties(prefix="execution")` + `@Component`；字段：`workspaceRoot`、`artifactsRoot`、`venvBin`、`maxParallel`、`leaseTtlSeconds`、`leaseHeartbeatSeconds`、`leaseReclaimSeconds`、`leaseMaxWaitSeconds`、`runTimeoutSeconds`、`inactivityTimeoutSeconds`、`minFreeBytes`、`pabotVersionPrefix`、`List<FailureRuleConfig> failureRules`、`DutConfig dut` |
| `RunIdGenerator.java` | `String next(String caseId, Instant now)` → `RUN-<yyyyMMddHHmmss>-<6 hex>`（`ZoneId.of("Asia/Shanghai")`）；`String nextRequestId()` → UUID |
| `RunQueuePolicy.java` | `Comparator<RunRecord> order()`（priority desc → enqueuedAt asc → id asc）；`List<RunRecord> pick(List<RunRecord> pending, int globalRunning)`（`maxParallel - globalRunning` 为上限，跨项目不互相阻塞：按 projectId 分组，每组独立按序取，直到全局配额用尽） |
| `RunService.java` | `@Service`；`RunRecord create(CreateRunCommand cmd)`；`RunRecord get(long projectId, String runId)`；`PageResult<RunRecord> page(long projectId, RunState state, int page, int size)`；`RunRecord transition(String runId, RunState to, TransitionContext ctx)`；内部 `@Transactional` 保证「状态 + 时间戳 + 失败字段」同事务 |
| 　`CreateRunCommand`（内嵌 record） | `(long projectId, Long testSetId, List<String> suitePaths, int concurrency, Long environmentId, List<String> deviceKeys, String runRequestId, int priority, int timeoutSeconds, String includeTag)` |
| 　`TransitionContext`（内嵌 record） | `(Instant startedAt, Instant finishedAt, String failureClass, String failureReason, String failureMessage, boolean leaseHeld, boolean artifactsReady)` |
| `RunLogService.java` | `void publish(String runId, String source, String line)`；`List<LogLine> recent(String runId, int max)`；`List<LogLine> drain(String runId, int max)`；`Registration subscribe(String runId, Consumer<List<LogLine>> subscriber)`；`void complete(String runId)`；内嵌 `record LogLine(long seq, Instant tsGen, String source, String level, String text)` |
| `LogRingBuffer.java` | 容量 2000；`synchronized void add(LogLine)`；`List<LogLine> recent(int n)`；`long total()`；`long dropped()` |
| `LogBatcher.java` | `void offer(LogLine)`；`List<LogLine> drain(int max)`；超出 `MAX_PENDING=20000` 丢最旧并生成「已省略 N 行」提示行；`List<LogLine> reconnect(int n)`（最近 200） |
| `PabotCommandBuilder.java` | `PabotCommand build(ShardSpec spec, ExecutionProperties props, Path artifactsRoot)`；`record ShardSpec(int shardNo, List<String> suitePaths, int processes, String includeTag, String displayName)`；`record PabotCommand(List<String> argv, Map<String,String> environment, Path workDir, Path outputDir)`；env 必须含 `PATH=venvBin+File.pathSeparator+原PATH`、`TZ=Asia/Shanghai`、`LANG=C.UTF-8`、`PYTHONUNBUFFERED=1` |
| `OutputXmlProbe.java` | `Probe probe(Path xml)`；`record Probe(boolean parsed, int totalTests, String error)`；JDK DOM 解析，禁用 DTD/外部实体；只统计 `test` 元素 |
| `ArtifactVerifier.java` | `VerifyResult verify(Path outputXml)`；`record VerifyResult(boolean success, String reason, int totalTests, long sizeBytes)`；失败原因枚举字符串：`missing / too_small / parse_failed / no_tests_executed / ok`；成功 = 存在 + `size>1024` + 可解析 + `totalTests>0` |
| `FileSystemProbe.java` | 接口 `exists(Path)` / `executable(Path)` / `writable(Path)` / `usableSpace(Path)`；实现 `NioFileSystemProbe`（本包内，纯 JDK） |
| `PabotVersionProbe.java` | 接口 `Optional<String> version(Path pabot)` |
| `ExecutionPrecheck.java` | `List<Violation> check(PrecheckRequest req)`；`record PrecheckRequest(Path venvBin, Path artifactsRoot, List<String> suitePaths, int requestedConcurrency, int availablePorts, long minFreeBytes, List<String> deviceProblems)`；`record Violation(String code, String message)`；码：`PABOT_NOT_FOUND / PABOT_VERSION / ARTIFACT_ROOT_NOT_WRITABLE / DISK_LOW / EMPTY_SUITES / PORT_SHORTAGE / DEVICE_UNAVAILABLE` |
| `ProcessLauncher.java` | 接口 `ExecutionHandle launch(PabotCommand cmd)`；`ExecutionHandle` 提供 `long pid()`、`int waitFor(Duration)`、`InputStream stdout()`、`InputStream stderr()`、`Process process()` |
| `ProcessRegistry.java` | `@Component`；`register(String runId, Process)`、`unregister(String runId)`、`List<Process> processes(String runId)` |
| `ProcessTreeKiller.java` | `KillOutcome kill(Process process, Duration grace)`；`record KillOutcome(boolean terminated, boolean forced, long waitedMs)`；先 `descendants().forEach(ProcessHandle::destroy)` + 自身 destroy，等 grace，仍在则 `destroyForcibly` |
| `PabotOrchestrator.java` | `@Service`；`ShardResult runShard(String runId, PabotCommand cmd, Duration timeout, Consumer<String> logSink)`；读 stdout/stderr → `logSink` + 写 `stdout.log`/`robot_stderr.out`；退出后调 `ArtifactVerifier`；`record ShardResult(boolean success, String reason, int totalTests, int exitCode, Path outputXml)` |
| `RunExecutionService.java` | `@Service`；`RunRecord execute(CreateRunCommand cmd)`：创建 run → 预检 → 申请租约（全有或全无）→ 建产物目录 → 置 RUNNING → 逐分片跑 `PabotOrchestrator` → 汇总 `test_result` → 置 FINISHED/FAILED；失败走 `FailureClassifier` + `RetryPolicy` |
| `FailureRuleSet.java` | `record Rule(FailureClass cls, List<String> keywords, List<String> regex)`；`static FailureRuleSet defaults()`；`static FailureRuleSet from(List<FailureRuleConfig>)` |
| `FailureClassifier.java` | `FailureClass classify(FailureInput input)`；优先级 `canceled > env > infra > assert`；`record FailureInput(boolean canceled, String reasonHint, String stdout, String stderr, String pabotManagerLog, String robotStderr, boolean artifactOk)` |
| `RetryPolicy.java` | `boolean shouldRetry(FailureClass cls, int attemptNo, int maxAttempts)`；仅 ENV 且 `attemptNo < maxAttempts`（默认 1） |
| `LeaseService.java` | `LeaseService(ResourceLeaseRepository repo, Clock clock, Duration ttl, Duration heartbeat, Sleeper sleeper)`；`boolean acquire(LeaseRequest req)`；`boolean acquireAll(List<LeaseRequest> reqs, Duration maxWait)`（任一失败则释放本批已拿到的全部并返回 false）；`int heartbeat(long jobId)`；`void release(long jobId, String reason)`；`List<Lease> reclaimExpired(Instant now)` |
| `Sleeper.java` | 接口 `void sleep(Duration)`；默认实现 `Thread::sleep` |
| `CancelService.java` | `@Service`；`RunRecord cancel(long projectId, String runId, String operatorReason)`：非终态才可取消；杀进程树 → 释放租约 → 置 CANCELED + finishedAt → 审计 |
| `RunTimeoutMonitor.java` | `@Service`；`List<String> check(Instant now)`：`running` 且 `startedAt + runTimeout > now` → 杀进程、释放租约、置 `FAILED(failure_reason=timeout)` |
| `RestartRecoveryService.java` | `@Service`；`@EventListener(ApplicationReadyEvent.class)`；`RecoveryReport recover()`：扫 `preparing/running` → `FAILED(failure_reason=interrupted)` + 释放租约 + 审计；`record RecoveryReport(int scanned, int recovered, int leasesReleased)` |
| `LeaseView.java` | `record LeaseView(String resourceKey, String runId, long jobId, long projectId, Instant acquiredAt, Instant expiresAt, long remainingSeconds, String state)`；只读视图（FR-v0.4-09） |
| `DutTarget.java` | `record DutTarget(String host, int port, String user, String password, String privateKeyPath, String jumpHost, String jumpUser)`；`toString()` 只输出 `user@host via jump`，**绝不含 password** |
| `SshClient.java` | 接口 `CommandResult execute(DutTarget target, String command, Duration connectTimeout, Duration commandTimeout)`；`record CommandResult(int exitCode, String stdout, String stderr, Duration elapsed)` |
| `DutCollectorService.java` | `@Service`；`DutCollection collect(DutTarget target, String iface)`；固定三条命令（`ip -br addr`、`ethtool -S <iface>`、`cat /proc/interrupts`）；`iface` 仅允许 `[A-Za-z0-9_.:-]+`；失败映射 `unreachable / auth_failed / cmd_not_found / command_failed`，每类带 `advice` |
| `DutOutcome.java` | `record DutOutcome(String kind, String advice, DutCollection collection)`；`kind` 取值同上 |
| `PageResult` 复用 | 用现有 `domain/common/PageResult` |

### 3.3 仓储接口 `application/repository/`

| 文件 | 方法 |
|---|---|
| `RunRepository.java` | `RunRecord insert(RunRecord draft, String jobNo, String runRequestId, Long operatorId)`；`Optional<RunRecord> findByRunId(long projectId, String runId)`；`Optional<RunRecord> findByRequestId(long projectId, String requestId)`；`int updateState(String runId, RunState from, RunState to, Instant startedAt, Instant finishedAt, String failureClass, String failureReason, String failureMessage)`；`int updateCounts(String runId, int total, int passed, int failed, String artifactsDir)`；`List<RunRecord> findActive()`（SQL 带 `project_id` 条件）；`PageResult<RunRecord> page(long projectId, RunState state, int page, int size)`；`int markAttempt(String runId, int attemptNo, String retryOf)` |
| `ResourceLeaseRepository.java` | `boolean tryAcquire(Lease lease)`；`int renew(long jobId, Instant expiresAt)`；`int release(long jobId, String reason)`；`int releaseOne(String resourceKey, long jobId, String reason)`；`List<Lease> findByJob(long jobId)`；`List<Lease> findExpired(Instant now)`；`List<LeaseView> listByProject(long projectId)`；`void recordHistory(Lease lease, Instant releasedAt, String state, String reason)` |
| `RunArtifactRepository.java` | `void upsert(long projectId, long runId, String kind, String path, String sha256, long sizeBytes, int shardNo, String note)`；`List<RunArtifact> list(long projectId, long runId)`（`RunArtifact` 为应用层 record） |
| `TestResultWriter.java` | `void write(long projectId, long runDbId, String caseKey, String verdict, long durationMs, String failureMsg, int roundNo)`；由 `OutputXmlProbe` 产出的简化结果驱动 |

### 3.4 基础设施层 `backend/src/main/java/com/qa/autotest/infrastructure/`

| 文件 | 说明 |
|---|---|
| `RunRepositoryJdbc.java` | 实现 `RunRepository`；所有 SQL 走 `TenantGuard.requireProjectScope(sql, "run")`；insert 同事务写 `execution_job`（`job_no=runId`）与 `run`；`findActive` SQL 必须含 `project_id`；捕获 `DuplicateKeyException` 由上层幂等判定 |
| `ResourceLeaseRepositoryJdbc.java` | `tryAcquire` 直接 INSERT，捕获 `DuplicateKeyException` 返回 `false`；`release` 必须 `WHERE job_id = ?`；`findExpired` 带 `project_id`；`recordHistory` 写 `resource_lease_history` |
| `RunArtifactRepositoryJdbc.java` | 实现 `RunArtifactRepository` |
| `TestResultWriterJdbc.java` | 写 `test_result`（`run_id` 传 run 表主键，不是 `run_id` 字符串） |
| `ProcessLauncherImpl.java` | `ProcessBuilder`：`directory(cmd.workDir())`、`environment()` 全量写入、`redirectErrorStream(false)`；返回句柄 |
| `PabotVersionProbeImpl.java` | 执行 `pabot --version`，10s 超时，失败返回 `Optional.empty()` |
| `SystemSshClient.java` | 组装 `ssh` / `sshpass -e ssh`；`-o ConnectTimeout=5`、`-o ServerAliveInterval=10`、`-o BatchMode`（公钥模式）；`-J jumpUser@jumpHost`；口令只进 `PB.environment().put("SSHPASS", password)`；命令与输出不进日志的 `password` 字段 |
| `ExecutionSchedulerConfig.java` | `@Configuration` + `@EnableScheduling`；三个 `@Scheduled`：心跳 40s、回收 60s、超时检查 60s；**测试环境不启动调度**（`@Profile("!test")` 或 `@ConditionalOnProperty(execution.scheduling.enabled=true)`） |
| `ExecutionBeansConfig.java` | 装配 `Clock`（`Clock.system(ZoneId.of("Asia/Shanghai"))`）、`Sleeper`、`ProcessLauncher`、`PabotVersionProbe`、`SshClient`、`FileSystemProbe` 默认实现；只在 `@Profile("!test")` 生效 |

### 3.5 接口层 `backend/src/main/java/com/qa/autotest/api/execution/`

| 文件 | 端点 |
|---|---|
| `RunController.java` | `POST /api/execution/runs`；`GET /api/execution/runs/{runId}`；`GET /api/execution/runs`；`POST /api/execution/runs/{runId}/cancel`；`GET /api/execution/runs/{runId}/log` |
| `LeaseController.java` | `GET /api/execution/leases?projectId=`（只读，FR-v0.4-09） |
| `DutController.java` | `POST /api/execution/dut/collect`（EXE-08 采集入口，`credentialId` 由服务层解密） |
| `RunLogWebSocketHandler.java` | `TextWebSocketHandler`；路径 `/ws/runs/{runId}`；握手查询参数 `token` + `projectId`；`afterConnectionEstablished` 校验令牌、`TenantContext.runAs` 后订阅；每 100ms 推送 `drain(50)`；关闭时退订 |
| `ExecutionWebSocketConfig.java` | `@Configuration implements WebSocketConfigurer`，注册 handler 到 `/ws/runs/*`；不覆盖 `/api/**` 拦截器 |

**统一响应**：所有 REST 端点返回 `ApiResponse<T>`；错误走 `GlobalExceptionHandler`。新增错误码不必须；状态非法用 `ErrorCode.CONFLICT`，参数用 `INVALID_ARGUMENT`，权限用 `FORBIDDEN`。

### 3.6 前端

| 文件 | 改动 |
|---|---|
| `frontend/src/api/execution.ts`（新建） | `executionApi.createRun(payload)`、`getRun(projectId, runId)`、`listRuns(projectId, state, page, size)`、`cancelRun(projectId, runId)`、`recentLog(projectId, runId, since)`；`runLogSocketUrl(projectId, runId, token)` |
| `frontend/src/views/RunsView.vue`（改写） | 顶部创建表单（用例集 ID、suite 路径多行、并发 1/2/4、优先级）；运行列表（状态色：排队灰/运行蓝/通过绿/失败红/取消黄）；取消按钮 + 二次确认；右侧日志面板（WebSocket 订阅、断线 3s 重连、补最近 200 行、FAIL 过滤、自动滚动） |
| `frontend/src/locales/zh-CN.ts` / `en-US.ts` | 补 `execution.*` 键（与 EDT-07 语言包风格一致，zh/en 键集合一致） |

### 3.7 数据库迁移

#### `db/migration/V4_1__lease.sql`

```sql
-- run：状态机 / 幂等 / 队列 / 计数 / 产物目录
ALTER TABLE `run`
  MODIFY COLUMN `run_status` VARCHAR(32) NOT NULL DEFAULT 'queued'
    COMMENT 'queued/preparing/running/finished/failed/canceled',
  ADD COLUMN `run_request_id` VARCHAR(64) NULL COMMENT '幂等键' AFTER `run_id`,
  ADD COLUMN `priority` INT NOT NULL DEFAULT 0 AFTER `trigger_type`,
  ADD COLUMN `enqueued_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER `priority`,
  ADD COLUMN `concurrency` INT NOT NULL DEFAULT 1 AFTER `enqueued_at`,
  ADD COLUMN `total_tests` INT NULL AFTER `finished_at`,
  ADD COLUMN `passed_tests` INT NULL AFTER `total_tests`,
  ADD COLUMN `failed_tests` INT NULL AFTER `passed_tests`,
  ADD COLUMN `artifacts_dir` VARCHAR(512) NULL AFTER `failed_tests`,
  ADD COLUMN `workspace_dir` VARCHAR(512) NULL AFTER `artifacts_dir`,
  ADD COLUMN `shard_count` INT NULL AFTER `workspace_dir`,
  ADD COLUMN `lease_wait_ms` BIGINT NULL AFTER `shard_count`,
  ADD COLUMN `failure_reason` VARCHAR(64) NULL AFTER `lease_wait_ms`,
  ADD COLUMN `attempt_no` INT NOT NULL DEFAULT 0 AFTER `failure_reason`,
  ADD UNIQUE KEY `uk_run_project_request` (`project_id`, `run_request_id`);

-- resource_lease：补 run_id / project_id / 心跳 / 释放原因
ALTER TABLE `resource_lease`
  ADD COLUMN `run_id` VARCHAR(64) NULL AFTER `job_id`,
  ADD COLUMN `project_id` BIGINT NULL AFTER `run_id`,
  ADD COLUMN `last_heartbeat_at` DATETIME(3) NULL AFTER `leased_at`,
  ADD COLUMN `release_reason` VARCHAR(64) NULL AFTER `lease_state`,
  ADD KEY `idx_resource_lease_project` (`project_id`, `lease_state`),
  ADD KEY `idx_resource_lease_expires` (`lease_state`, `expires_at`);

-- 租约历史：0 冲突的证明来源
CREATE TABLE `resource_lease_history` (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  resource_key  VARCHAR(128) NOT NULL,
  job_id        BIGINT       NOT NULL,
  run_id        VARCHAR(64)  NOT NULL,
  project_id    BIGINT       NOT NULL,
  acquired_at   DATETIME(3)  NOT NULL,
  released_at   DATETIME(3)  NULL,
  lease_state   VARCHAR(16)  NOT NULL COMMENT 'released/expired/revoked',
  release_reason VARCHAR(64) NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY `idx_lease_history_key_acquired` (`resource_key`, `acquired_at`),
  KEY `idx_lease_history_run` (`run_id`),
  KEY `idx_lease_history_project` (`project_id`, `leased_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租约历史（冲突判定证据）';
```

> 注：`idx_lease_history_project` 列名以最终落盘为准（`lease_state`），实现时不得保留 `leased_state` 笔误。

#### `db/migration/V4_2__run_artifact.sql`

```sql
ALTER TABLE `run_artifact`
  ADD COLUMN `shard_no` INT NULL AFTER `kind`,
  ADD COLUMN `line_count` BIGINT NULL AFTER `size_bytes`,
  ADD COLUMN `note` VARCHAR(255) NULL AFTER `availability`,
  ADD KEY `idx_run_artifact_shard` (`run_id`, `shard_no`);
```

#### `db/migration/V4_3__failure_class.sql`

```sql
ALTER TABLE `run`
  ADD COLUMN `failure_class` VARCHAR(16) NULL AFTER `failure_reason`,
  ADD COLUMN `failure_message` TEXT NULL AFTER `failure_class`,
  ADD COLUMN `retry_of_run_id` VARCHAR(64) NULL AFTER `failure_message`;

CREATE TABLE `run_attempt` (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  project_id    BIGINT      NOT NULL,
  run_id        VARCHAR(64) NOT NULL,
  attempt_no    INT         NOT NULL,
  started_at    DATETIME(3) NULL,
  finished_at   DATETIME(3) NULL,
  verdict       VARCHAR(16) NULL COMMENT 'PASS/FAIL/SKIP/ERROR',
  failure_class VARCHAR(16) NULL,
  failure_msg   TEXT        NULL,
  retried       TINYINT     NOT NULL DEFAULT 0,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY `uk_run_attempt` (`project_id`, `run_id`, `attempt_no`),
  KEY `idx_run_attempt_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行尝试记录（重试可追溯）';
```

#### down 脚本

- `db/down/V4_1__lease.down.sql`：删 `resource_lease_history`、还原 `resource_lease` 与 `run` 新增列/索引、`run_status` 默认还原 `created`。
- `db/down/V4_2__run_artifact.down.sql`：删 `run_artifact` 三列与索引。
- `db/down/V4_3__failure_class.down.sql`：删 `run_attempt`、删 `run` 三列。

**手写 down 规则**沿用 `docs/adr/0003-handwritten-down-migrations.md`，不做自动生成。

---

## 4. 关键行为规格

### 4.1 状态机（EXE-01）

合法边（与 DEV-PLAN §2.1 一致）：

| from | 允许 to |
|---|---|
| QUEUED | PREPARING, CANCELED |
| PREPARING | RUNNING, FAILED, CANCELED |
| RUNNING | FINISHED, FAILED, CANCELED |
| FINISHED / FAILED / CANCELED | 无 |

- 非法跃迁抛 `IllegalStateTransition`，**不得静默返回 false**。
- `PREPARING → RUNNING` 必须传前置条件：`leaseHeld && artifactsReady`，缺一即抛。
- 状态变更与 `started_at/finished_at`、失败字段在同一 `RunService.transition` 调用内落库（`@Transactional`）。

### 4.2 幂等（EXE-05）

- `CreateRunCommand.runRequestId` 为空时服务端生成 UUID。
- 创建前先 `findByRequestId`：命中直接返回既有 run（`idempotent=true` 标记由调用方按「返回记录 + 未新建」判断）。
- 并发下依赖 `uk_run_project_request` 唯一键；捕获 `DuplicateKeyException` 后回查返回既有记录。
- TC-v0.4-506：同 `run_request_id` 提交 5 次 → 恰 1 条 `run`。

### 4.3 租约（EXE-02）

- `tryAcquire` 是纯 INSERT，**不做 SELECT 预判**；`DuplicateKeyException` = 被占用。
- 释放 SQL 必须 `WHERE job_id = ?`；`releaseOne` 同时带 `resource_key + job_id`。
- `acquireAll` 全有或全无：任一失败，释放本批已拿到的（按 job 释放），返回 false，调用方标 `FAILED(failure_reason=lease_timeout)`。
- 心跳 40s 一次 `UPDATE expires_at = now + TTL WHERE job_id=?`；租约 TTL 120s。
- 回收：`findExpired(now)` → 删除/置 `expired` + 写历史 + 对应 run 标 `FAILED(failure_reason=lease_expired)`。
- 历史表在释放/回收/强制释放三处写入，用于「0 冲突」SQL 证明。

### 4.4 pabot 编排（EXE-03）

- `PATH = venvBin + File.pathSeparator + 原 PATH`；缺 venv 或 pabot 不可执行 → 预检拒绝。
- 命令固定含 `--outputdir <artifacts>/<runId>/shard-<n>`、`--processes <n>`、`--name`，可选 `--include`。
- 双日志：进程 `stdout/stderr` 落 `shard-<n>/stdout.log`、`robot_stderr.out`；pabot 管理器日志路径 `<outputdir>/pabot_results/pabot_manager.log`，失败诊断顺序固定为 manager → robot_stderr → stdout。
- 成功四条件：`exitCode==0` && exists && `size>1024` && `parsed && totalTests>0`。
- 预检：pabot 可执行 + 版本前缀匹配 + 产物根可写 + 磁盘 ≥ `minFreeBytes`（默认 5GiB）+ suite 非空 + 端口数 ≥ 并发 + 设备非维修/下线。

### 4.5 实时日志（EXE-04）

- 每 run 一个 `LogRingBuffer(2000)` + `LogBatcher`。
- 推送每 100ms `drain(50)`；超过 `MAX_PENDING=20000` 丢最旧并插「已省略 N 行」。
- 重连补最近 200 行（`recent(200)`），不要求全量。
- 全量落盘 `<artifacts>/<runId>/run.log`，不受限流影响。
- `LogLine` 带 `tsGen`（平台侧生成毫秒），供前端算 P95（TC-v0.4-401/402）。

### 4.6 取消 / 超时 / 恢复（EXE-05）

- 取消：`ProcessTreeKiller` 先对 descendants + 自身 `destroy`，等 5s，仍未退 `destroyForcibly`；随后释放租约（带 job_id）、置 `CANCELED`、写 `finished_at`、审计。
- 取消后产物目录保留，不删日志。
- 总超时：`RunTimeoutMonitor` 扫描超时 run → 杀进程、释放租约、置 `FAILED` + `failure_reason=timeout`。
- 单用例超时：由 RF/pabot 自身控制（本版只透传 `--testtimeout`，不做平台级单例中断）。
- 重启恢复：`ApplicationReadyEvent` 扫 `PREPARING/RUNNING` → `FAILED(interrupted)` + 释放租约 + 审计；返回报告。
- 越权取消：`require(EXECUTION, UPDATE)`；非项目成员 403。

### 4.7 失败分类与重试（EXE-06）

默认规则（可被 `execution.failure-rules` 覆盖）：

| 类别 | 关键词 / 正则 | 重试 |
|---|---|---|
| `CANCELED` | `canceled` / `cancelled` / `timeout`（平台侧取消或超时） | 否 |
| `ENV` | `Connection refused`、`Connection reset`、`timed out`、`No route to host`、`Temporary failure in name resolution`、`Permission denied (publickey,password)`、`SSH` 认证失败 | 是（最多 1 次） |
| `INFRA` | `output.xml` 缺失、`no tests executed`、`parse failed`、`No such file or directory: 'robot'`（PATH 类）、`Killed`、`disk full` | 否 |
| `ASSERT` | 其余含 `FAIL` / 断言文本 | 否 |

分类优先级固定 `CANCELED > ENV > INFRA > ASSERT`；`FailureClassifier` 不依赖文件系统，输入是文本与产物结论。
`RetryPolicy` 只放行 `ENV && attemptNo < maxAttempts(默认1)`；断言失败绝不重试。

### 4.8 DUT 采集（EXE-08）

- 采集命令固定三条：`ip -br addr`、`ethtool -S <iface>`、`cat /proc/interrupts`（只保留与 `iface` 相关的行，最多 20 行）。
- 连接超时 5s、单命令 15s。
- 失败分类：
  - `unreachable`：exit 255 + `timed out` / `Could not resolve` / `No route` → 建议「检查 180 段路由与跳板机」
  - `auth_failed`：`Permission denied` / `Authentication failed` → 建议「检查凭据或公钥」
  - `cmd_not_found`：`command not found` / `No such file` → 建议「确认 DUT 系统含该工具」
  - `command_failed`：其余非 0 → 建议「查看 stderr 原文」
- `DutTarget.toString()` 与日志禁止出现密码；证据扫描 `grep -F <password>` 必须 0 命中。
- 回放：`fixtures/dut-replay/` 存三份真实形态输出；`ReplaySshClient` 按命令名返回样本，证据首行 `[REPLAY] 非实网`。

---

## 5. 测试规格（对应 TEST-PLAN 编号）

### 5.1 单元测试（本机必跑，`mvn -B test`）

| 测试类 | 覆盖 | 判据 |
|---|---|---|
| `RunStateMachineTest` | TC-v0.4-? 状态机 | 全部合法边通过；`queued→running`、`finished→running`、`canceled→finished` 抛 `IllegalStateTransition`；PREPARING→RUNNING 缺租约/产物抛错 |
| `RunQueuePolicyTest` | FR-v0.4-03/05 | 优先级 + 时间排序；第 5 个任务排队；两个项目互不阻塞 |
| `LeaseConcurrencyTest` | TC-v0.4-101/102 | 100 线程同 key 成功恰 1；100 线程 10 key 每 key 恰 1、总成功 10 |
| `LeaseServiceTest` | TC-v0.4-103/104/107 | 释放后可重抢；A 释放 B 的租约影响行数 0；部分失败释放已拿到全部 |
| `PabotCommandBuilderTest` | FR-v0.4-11/12 | argv 含 `--outputdir/--processes`；env PATH 前置 venv、TZ/LANG 固定 |
| `ArtifactVerifierTest` | FR-v0.4-14 / TC-v0.4-302 | 缺失/过小/解析失败/0 用例分别给出对应 reason；正常样本成功 |
| `ExecutionPrecheckTest` | TC-v0.4-304/305/307/308 | 不可写/磁盘低/端口不足/版本不匹配各自拒绝码 |
| `LogRingBufferTest` | FR-v0.4-19 | 2000 行滚动、丢弃计数、近期顺序 |
| `LogBatcherTest` | FR-v0.4-19/18 | 每批 ≤50；超限生成「已省略 N 行」；reconnect 返回 ≤200 |
| `IdempotencyTest` | TC-v0.4-506 | 5 次同 requestId → 1 条 run |
| `ProcessTreeKillerTest` | TC-v0.4-501 | 启动真实子进程后 kill，进程消失；无残留（OS 支持时运行，否则 `Assumptions` 跳过） |
| `FailureClassifierTest` | TC-v0.4-601~606 | 30 条样本分类 100%；assert 不重试；env 重试 1 次 |
| `RetryPolicyTest` | FR-v0.4-28 | 仅 ENV 且未超次数返回 true |
| `RestartRecoveryTest` | TC-v0.4-507 | 2 条 active → 2 条 failed(interrupted)，租约释放 2 条 |
| `CancelServiceTest` | TC-v0.4-501/502/508 | 非终态可取消；终态 409；越权 403；租约释放 |
| `DutCollectorServiceTest` | TC-v0.4-702~705 | 回放样本解析；三类失败各给正确 kind/advice；日志无口令 |
| `DutTargetRedactionTest` | TC-v0.4-703 | `toString` 不含 password |
| `RunExecutionServiceTest` | 最小闭环 | 假 launcher + 临时 output.xml → FINISHED + totalTests>0；空产物 → FAILED(no_tests_executed) |
| `MigrationFileTest` | 迁移完整性 | `V4_1/V4_2/V4_3` 与对应 down 文件存在；文本含关键表/列名 |

### 5.2 需要本地 WSL 的验收（本机不跑，证据标「待本地 WSL」）

- 真实 `mvn -B -Pit test` + `make db-reset` + `make db-audit`。
- TC-v0.4-301/303/306：真实 pabot 故障注入；本机只做单测等价用例。
- TC-v0.4-401~406：真实日志延迟与断线重连。
- TC-v0.4-201~206：20 轮压测与内存峰值。
- TC-v0.4-701/706：DUT 实网（本机只做 `[REPLAY]`）。

### 5.3 前端验证

- `npm run build` exit 0；`npm run lint` exit 0。
- 运行时面板需本地 WSL + 真实后端联调（本机只做构建与静态检查）。

---

## 6. 证据清单（`docs/evidence/v0.4/`）

| 文件 | 本机产出 | 内容要求 |
|---|---|---|
| `mvn-test.log` | 是 | 全量单测输出（含 `Tests run`） |
| `lease-concurrency.log` | 是 | `LeaseConcurrencyTest` 专项输出（成功 1 / 总成功 10） |
| `failure-class-30.md` | 是 | 30 条样本 → 分类结果表，100% |
| `fault-injection-8.md` | 是 | 8 条映射到单测/本地 WSL 实跑，标注哪些未实跑 |
| `bench-20rounds.txt` | 否 | 首行 `[待本地 WSL 实跑]`，附脚本命令与固定 fixture 说明 |
| `bench-2process.log` | 否 | 首行 `[待本地 WSL 实跑]` |
| `log-latency.txt` | 否 | 首行 `[待本地 WSL 实跑]` |
| `cancel-ps.txt` | 部分 | 本机子进程 kill 单测结果 + 本地 WSL 实跑待办 |
| `restart-recovery.log` | 是 | 单测恢复报告 + 本地 WSL 重启验证待办 |
| `lease-conflict-0.sql` | 否 | 提供 SQL 与执行命令，标注 `[待本地 WSL执行]` |
| `dut-real.md` | 部分 | 首行 `[REPLAY] 非实网`，回放样本 + 口令扫描 0 命中；实网待 DUT |
| `frontend-build.log` / `frontend-lint.log` | 是 | 前端构建与 lint |

---

## 7. 验证命令（本机）

```powershell
$mvn = "C:\Users\everg\.maven\maven-3.9.16\bin\mvn.cmd"
& $mvn -B -f backend/pom.xml test
& $mvn -B -f backend/pom.xml test -Dtest=LeaseConcurrencyTest
```

前端：

```powershell
cd frontend
npm run build
npm run lint
```

> 构建日志通过 PowerShell 重定向落 `docs/evidence/v0.4/`；不得手工编造 `Tests run` 数字。

---

## 8. 状态与 WBS 更新

| WBS | 任务 | 交付后状态 | 证据主文件 |
|---|---|---|---|
| #30 EXE-01 | 状态机 / 队列 / 运行骨架 | `待验` | `mvn-test.log` |
| #31 EXE-02 | 租约 | `待验` | `lease-concurrency.log` |
| #32 EXE-03 | pabot 编排 | `待验` | `fault-injection-8.md` |
| #33 EXE-04 | 实时日志 | `待验` | `log-latency.txt`（待本地 WSL） |
| #34 EXE-05 | 取消/超时/幂等/恢复 | `待验` | `cancel-ps.txt` |
| #35 EXE-06 | 失败分类 | `待验` | `failure-class-30.md` |
| #36 EXE-07 | 4 并发压测 | `待验` | `bench-20rounds.txt`（待本地 WSL） |
| #37 EXE-08 | DUT 实网 | `待验` | `dut-real.md`（`[REPLAY]`） |
| #83 EXEC-02 | 勾选台账用例直接执行 | `待验` | `docs/evidence/v0.4/exec02/README.md` |
| #84 EXEC-03 | 用例集测试级执行 | `待验` | `docs/evidence/v0.4/exec03/README.md` |
| #85 EXEC-04 | 执行前预检 | `待验` | `docs/evidence/v0.4/exec04/README.md` |
| #86 EXEC-05 | 执行工作区落盘 | `待验` | `docs/evidence/v0.4/exec05/README.md` |
| #87 EXEC-06 | 全量日志下载 | `待验` | `docs/evidence/v0.4/exec06/README.md` |
| #88 EXEC-07 | 租约可视化 + 管理员强制释放 | `待验` | `docs/evidence/v0.4/exec07/README.md` |
| #89 EXEC-08 | v0.4 PRD FR 覆盖复核 | `待验` | `docs/release/v0.4-execution/FR-COVERAGE-20261105.md` |
| #90 EXEC-09 | 租约接入执行链路（申请 / 超时拒绝 / 全路径释放） | `待验` | `docs/evidence/v0.4/exec09/README.md` |
| #91 EXEC-10 | 队列语义：按项目分道 + priority 排队 + MAX_PARALLEL 上限 | `待验` | `docs/evidence/v0.4/exec10/README.md` |
| #92 EXEC-11 | 设备范围：`run.device_scope` 落库 + 回溯 + 手动执行声明与展示 （`V7_4__run_device_scope.sql`） | `待验` | `docs/evidence/v0.4/exec11/README.md` |
| #93 EXEC-12 | 单用例超时：`Test Timeout` 注入工作区副本 + `test_timeout` 原因归类 | `待验` | `docs/evidence/v0.4/exec12/README.md` |
| #94 EXEC-13 | 运行详情显示「第 N 次尝试」（`attemptNo > 1` 才渲染，只改前端） | `待验` | `docs/evidence/v0.4/exec13/README.md` |
| #95 EXEC-14 | 日志级别过滤：ERROR / WARN / INFO 三档独立勾选，默认全开（只改前端） | `待验` | `docs/evidence/v0.4/exec14/README.md` |

汇总行从 `24 / 1 / 31 / 0` 变为 `16 / 1 / 39 / 0`；AI 不得改 `已完成`。

---

## 9. 风险与回退

| 风险 | 触发信号 | 处置 |
|---|---|---|
| 新增 websocket 依赖拉取失败 | `mvn` 报无法解析 `spring-boot-starter-websocket` | 回退为 SSE（`SseEmitter`）并记录偏差，找你确认 |
| 本机 JDK 23 与 `release 21` 不兼容 | 编译报 `--release 21` 错 | 用 JDK 21（本地 WSL 同版）或调 `maven.compiler.release`；不改业务代码 |
| 系统 `sshpass` 缺失 | 预检 `SSHPASS_NOT_AVAILABLE` | 只用公钥模式；口令模式在本地 WSL 装 `sshpass` |
| DUT 掉线 | 实网命令超时 | 回放证据 `[REPLAY]`，EXE-08 保持 `待验` |
| 真实 pabot 未装 | 本机无法跑最小闭环 | 单测用 Fake launcher + 合成 output.xml；真跑留本地 WSL |

---

## 10. 决策点（已按批准结论落地）

> 2026-11-03：以下 7 项随 v0.4 实施采纳，原意未改；第 6 项严格按承诺执行——EXE-07/08 只交付可复跑脚本与回放，证据标注 `[待本地 WSL 实跑]` / `[REPLAY]`，状态置 `待验`，未冒充实网通过。

1. 新增 `spring-boot-starter-websocket` 依赖（EXE-04 必须）。
2. 新增权限资源 `EXECUTION` 及三角色动作分配（§2.3）。
3. 队列表本版不写，队列语义落 `run.priority + enqueued_at`（§2.2）。
4. DUT 认证采用系统 OpenSSH + `sshpass -e`（口令经环境变量，不落盘），不引入 JSch。
5. 前端只做 `RunsView` 运行记录 + 实时日志面板（§2.4）。
6. 本机无法实跑 pabot/DUT/压测：EXE-07/08 证据标注 `[待本地 WSL 实跑]` / `[REPLAY]`，状态置 `待验`。
7. 全部 DB 集成验证留本地 WSL（`mvn -Pit` + `make db-*`），本机只跑无库单测。
