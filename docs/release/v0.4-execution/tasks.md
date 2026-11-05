# v0.4 执行域 · 任务清单（Tasks）

**文档编号** RA-REL-2026-004-TASKS ｜ **版本** v0.4 ｜ **门禁** G4

**对应规格** `docs/release/v0.4-execution/spec.md` ｜ **范围** EXE-01 ~ EXE-08（WBS #30 ~ #37）+ EXEC-02（WBS #83）+ EXEC-03（WBS #84）+ EXEC-04（WBS #85）+ EXEC-05（WBS #86）+ EXEC-06（WBS #87）+ EXEC-07（WBS #88）+ EXEC-08（WBS #89，复核包）+ EXEC-09（WBS #90，租约接入执行链路）+ EXEC-10（WBS #91，队列语义）+ EXEC-11（WBS #92，设备范围）+ EXEC-12（WBS #93，单用例超时）+ EXEC-13（WBS #94，重试可追溯）+ EXEC-14（WBS #95，日志级别过滤；实现与证据见 `docs/evidence/v0.4/exec02/`、`exec03/`、`exec04/`、`exec05/`、`exec06/`、`exec07/`、`exec09/`、`exec10/`、`exec11/`、`exec12/`、`exec13/`、`exec14/`）

**执行原则**：一次最多并行 2 个任务包；每包先落代码与单测，再跑验证，再写证据，最后改 WBS 为 `待验`。`已完成` 只由人改。

---

## 0. 前置（批准后第 0 步）

- [x] T0-1 确认新增依赖 `spring-boot-starter-websocket` 进入 `backend/pom.xml`
- [x] T0-2 确认权限资源 `EXECUTION` 方案（ADMIN 全 / PM 全 / ENGINEER 读写执行 / VIEWER 读）
- [x] T0-3 确认 DUT 采用系统 OpenSSH + `sshpass -e`，不引入 JSch
- [x] T0-4 确认前端只做 `RunsView`（运行记录 + 实时日志面板）
- [x] T0-5 确认 EXE-07/08 本机只交付脚本与回放证据，实跑留本地 WSL

---

## W1 · EXE-01 执行域骨架（状态机 / 队列 / 运行记录）

**依赖**：无 ｜ **状态**：未开始 → 待验 ｜ **证据**：`docs/evidence/v0.4/mvn-test.log`

- [x] T1-1 新增 `domain/execution/RunState.java`
  - 判据：六状态、`terminal()`、`active()`、`dbValue()` 小写。
- [x] T1-2 新增 `domain/execution/IllegalStateTransition.java`
  - 判据：字段 `from`/`to`，消息可读，继承 `RuntimeException`。
- [x] T1-3 新增 `domain/execution/RunStateMachine.java`
  - 判据：合法边表与 DEV-PLAN §2.1 一致；`transition(to)` 非法抛错；`transition(to, precondition, name)` 条件不满足抛错。
- [x] T1-4 新增 `domain/execution/RunRecord.java`、`domain/execution/Lease.java`
  - 判据：纯 record，无框架依赖。
- [x] T1-5 新增 `application/execution/ExecutionProperties.java`
  - 判据：`@ConfigurationProperties(prefix="execution")`；默认值覆盖 spec §3.2 全部字段；`failureRules` 可空时回落默认规则。
- [x] T1-6 新增 `application/execution/RunIdGenerator.java`
  - 判据：`RUN-<yyyyMMddHHmmss>-<6hex>`；`Asia/Shanghai`；并发调用不重复（测试 1000 次集合大小 = 1000）。
- [x] T1-7 新增 `application/execution/RunQueuePolicy.java`
  - 判据：优先级降序 → `enqueuedAt` 升序 → id 升序；`maxParallel` 达上限时返回空；两项目任务不互相阻塞。
- [x] T1-8 新增 `application/repository/RunRepository.java` 接口（方法见 spec §3.3）
- [x] T1-9 新增 `infrastructure/RunRepositoryJdbc.java`
  - 判据：所有 SQL 过 `TenantGuard.requireProjectScope(sql, "run")`；insert 同事务写 `execution_job` + `run`；`findActive` SQL 含 `project_id`。
- [x] T1-10 新增 `application/execution/RunService.java`
  - 判据：`create` 幂等（先查 `findByRequestId`，唯一键兜底）；`transition` 用状态机 + 同事务落时间与失败字段；`get/page` 走租户条件；权限 `EXECUTION:CREATE` / `READ`。
- [x] T1-11 新增 `api/execution/RunController.java`
  - 判据：5 个端点（创建/详情/列表/取消/日志）；统一 `ApiResponse`；参数缺失 `INVALID_ARGUMENT`。
- [x] T1-12 新增 `domain/iam/PermissionResource` 的 `EXECUTION("执行")` 并更新 `PermissionMatrix`
  - 判据：ADMIN 全、PM 全、ENGINEER `READ/CREATE/UPDATE/EXECUTE`、VIEWER `READ`；`PermissionMatrixTest` 同步更新并通过。
- [x] T1-13 新增 `TenantGuard` 登记：`resource_lease`、`resource_lease_history`、`run_artifact`、`run_attempt`（`execution_queue` 登记后仍需带 `project_id`，本版不写）
  - 判据：`TenantGuardMetaTest` 通过；未登记表仍报错。
- [x] T1-14 新增迁移 `db/migration/V4_1__lease.sql` + `db/down/V4_1__lease.down.sql`
  - 判据：spec §3.7 字段齐全；down 可逆；`MigrationFileTest` 断言存在与关键列名。
- [x] T1-15 新增 `RunStateMachineTest`、`RunQueuePolicyTest`、`RunIdGeneratorTest`
  - 判据：全部合法边 + 3 个非法样例 + 前置条件；队列排序与上限；ID 唯一。

**验收**：`mvn -B test` 全绿；`RunStateMachineTest` 覆盖 6 状态全部合法边。

---

## W2 · EXE-03 最小闭环（pabot 编排 / 产物校验 / 预检）

**依赖**：W1 ｜ **状态**：未开始 → 待验 ｜ **证据**：`fault-injection-8.md`

- [x] T2-1 新增 `application/execution/PabotCommandBuilder.java`
  - 判据：argv 含 `--processes/--outputdir/--name`；env `PATH` 前置 venv、`TZ=Asia/Shanghai`、`LANG=C.UTF-8`、`PYTHONUNBUFFERED=1`；suite 为空或 processes 越界抛 `INVALID_ARGUMENT`。
- [x] T2-2 新增 `application/execution/OutputXmlProbe.java`
  - 判据：JDK DOM；禁用 DTD；统计 `test` 元素；坏 XML 返回 `parsed=false` + error。
- [x] T2-3 新增 `application/execution/ArtifactVerifier.java`
  - 判据：四条件；reason 精确为 `missing/too_small/parse_failed/no_tests_executed/ok`。
- [x] T2-4 新增 `application/execution/FileSystemProbe.java` + `NioFileSystemProbe`
- [x] T2-5 新增 `application/execution/PabotVersionProbe.java` + `infrastructure/PabotVersionProbeImpl.java`
  - 判据：`pabot --version` 10s 超时；失败返回空。
- [x] T2-6 新增 `application/execution/ExecutionPrecheck.java`
  - 判据：7 个违规码；默认 `minFreeBytes=5GiB`；端口数 < 并发 → `PORT_SHORTAGE`；设备问题非空 → `DEVICE_UNAVAILABLE`。
- [x] T2-7 新增 `application/execution/ProcessLauncher.java` + `infrastructure/ProcessLauncherImpl.java`
  - 判据：工作目录、环境变量、stdout/stderr 分离；不依赖 login shell。
- [x] T2-8 新增 `application/execution/PabotOrchestrator.java`
  - 判据：边跑边写 `stdout.log`/`robot_stderr.out`；退出后调 verifier；返回 `ShardResult`。
- [x] T2-9 新增 `application/execution/RunExecutionService.java`
  - 判据：最小闭环 `create → precheck → 建目录 → RUNNING → runShard → FINISHED`；空产物 → `FAILED(no_tests_executed)`；无租约/无日志（W2 阶段）。
- [x] T2-10 新增 `infrastructure/RunArtifactRepositoryJdbc.java`、`infrastructure/TestResultWriterJdbc.java` 与对应接口
  - 判据：产物登记含 sha256/size/shard；`test_result` 用 run 主键。
- [x] T2-11 新增迁移 `db/migration/V4_2__run_artifact.sql` + down
- [x] T2-12 新增测试 `PabotCommandBuilderTest`、`ArtifactVerifierTest`、`ExecutionPrecheckTest`、`RunExecutionServiceTest`
  - 判据：覆盖 spec §5.1 对应行。
- [x] T2-13 新增 `fixtures/output-xml/{ok,empty,broken}.xml` 测试样本
  - 判据：`ok` 含 ≥2 test 且 >1024 字节；`empty` 仅统计节点且 <1024 或 totalTests=0；`broken` 非法 XML。

**验收**：`mvn -B test` 全绿；`ArtifactVerifierTest` 四种失败 reason 精准；`RunExecutionServiceTest` 最小闭环 PASS。

---

## W3 · EXE-02 租约（互斥 / 心跳 / 回收 / 历史）

**依赖**：W1、W2 ｜ **状态**：未开始 → 待验 ｜ **证据**：`lease-concurrency.log`、`lease-conflict-0.sql`

- [x] T3-1 新增 `application/repository/ResourceLeaseRepository.java` 接口
- [x] T3-2 新增 `infrastructure/ResourceLeaseRepositoryJdbc.java`
  - 判据：`tryAcquire` 纯 INSERT + 捕获 `DuplicateKeyException`；`release` 带 `job_id`；`findExpired`/`listByProject` 带 `project_id`；历史写入三处。
- [x] T3-3 新增 `application/execution/LeaseService.java` + `Sleeper.java`
  - 判据：`acquireAll` 全有或全无；失败释放已拿到的；`heartbeat` 只更新自己的 job；`reclaimExpired` 返回回收列表。
- [x] T3-4 新增 `application/execution/LeaseView.java`
- [x] T3-5 新增定时任务 `infrastructure/ExecutionSchedulerConfig.java`（`@Profile("!test")` 或开关）
  - 判据：心跳 40s / 回收 60s / 超时检查 60s；测试环境不启动。
- [x] T3-6 新增 `api/execution/LeaseController.java`
  - 判据：`GET /api/execution/leases` 只读、`EXECUTION:READ`。
- [x] T3-7 新增 `LeaseConcurrencyTest`、`LeaseServiceTest`
  - 判据：100 线程同 key 成功 1；10 key 总成功 10；A 释放 B 影响 0 行；部分失败全释放。
- [x] T3-8 生成 `docs/evidence/v0.4/lease-concurrency.log`
  - 判据：真实 `mvn -Dtest=LeaseConcurrencyTest` 输出，不手写。
- [x] T3-9 生成 `docs/evidence/v0.4/lease-conflict-0.sql`
  - 判据：SQL 文本 + 本地 WSL 执行命令，首行 `[待本地 WSL执行]`。

**验收**：`LeaseConcurrencyTest` 成功数恰为 1；`LeaseServiceTest` 全绿。

---

## W4 · EXE-05 取消 / 超时 / 幂等 / 重启恢复

**依赖**：W2、W3 ｜ **状态**：未开始 → 待验 ｜ **证据**：`cancel-ps.txt`、`restart-recovery.log`

- [x] T4-1 新增 `application/execution/ProcessRegistry.java`
- [x] T4-2 新增 `application/execution/ProcessTreeKiller.java`
  - 判据：descendants + 自身 destroy → 等 5s → destroyForcibly；返回 `KillOutcome`。
- [x] T4-3 新增 `application/execution/CancelService.java`
  - 判据：非终态才可取消；越权 403；终态 409；杀进程 + 释放租约 + CANCELED + finishedAt + 审计。
- [x] T4-4 新增 `application/execution/RunTimeoutMonitor.java`
  - 判据：`startedAt + timeout` 超时 → 杀进程、释放租约、`FAILED(failure_reason=timeout)`。
- [x] T4-5 新增 `application/execution/RestartRecoveryService.java`
  - 判据：`ApplicationReadyEvent` 扫 active → `FAILED(interrupted)` + 释放租约；返回 `RecoveryReport`。
- [x] T4-6 在 `RunService.create` 落实幂等（见 T1-10），补 `IdempotencyTest`
  - 判据：5 次同 requestId → 1 条 run。
- [x] T4-7 新增 `CancelServiceTest`、`RunTimeoutMonitorTest`、`RestartRecoveryTest`、`ProcessTreeKillerTest`
  - 判据：spec §5.1 对应行；OS 不支持子进程时 `Assumptions` 跳过并记录原因。

**验收**：单测全绿；本机子进程 kill 实测；本地 WSL 重启恢复待人工。

---

## W5 · EXE-04 实时日志（RingBuffer / 限流 / WebSocket / 落盘）

**依赖**：W2 ｜ **状态**：未开始 → 待验 ｜ **证据**：`log-latency.txt`、`frontend-build.log`

- [x] T5-1 `backend/pom.xml` 增 `spring-boot-starter-websocket`
- [x] T5-2 新增 `application/execution/LogRingBuffer.java`
  - 判据：容量 2000；滚动丢最旧；`total/dropped` 可查。
- [x] T5-3 新增 `application/execution/LogBatcher.java`
  - 判据：每批 ≤50；`MAX_PENDING=20000`；超限生成「已省略 N 行」；reconnect ≤200。
- [x] T5-4 新增 `application/execution/RunLogService.java`
  - 判据：按 run 分组；`publish/recent/drain/subscribe/complete` 线程安全。
- [x] T5-5 新增 `api/execution/RunLogWebSocketHandler.java` + `ExecutionWebSocketConfig.java`
  - 判据：`/ws/runs/{runId}?token=&projectId=`；校验令牌；100ms 推送；关闭退订。
- [x] T5-6 在 `PabotOrchestrator` 接 `RunLogService` 与全量落盘 `<artifacts>/<runId>/run.log`
  - 判据：推送受限流、落盘不受限流。
- [x] T5-7 新增 `LogRingBufferTest`、`LogBatcherTest`、`RunLogServiceTest`
  - 判据：spec §5.1 对应行。
- [x] T5-8 前端 `frontend/src/api/execution.ts` + `RunsView.vue` 实时面板
  - 判据：WS 连接/重连 3s/补 200 行/FAIL 过滤/自动滚动；`npm run build` 与 `npm run lint` exit 0。
- [x] T5-9 生成 `docs/evidence/v0.4/log-latency.txt`
  - 判据：首行 `[待本地 WSL 实跑]` + 测量方法与命令；不编造 P95。

**验收**：日志单测全绿；前端 build/lint 双 0；真实延迟留本地 WSL。

---

## W6 · EXE-06 失败分类与重试 ＋ EXE-08 DUT 采集（并行）

**依赖**：W2（EXE-06）、W2（EXE-08） ｜ **状态**：未开始 → 待验 ｜ **证据**：`failure-class-30.md`、`dut-real.md`

### W6A · EXE-06

- [x] T6A-1 新增 `domain/execution/FailureClass.java`
- [x] T6A-2 新增 `application/execution/FailureRuleSet.java`、`FailureClassifier.java`、`RetryPolicy.java`
  - 判据：优先级 `CANCELED > ENV > INFRA > ASSERT`；规则可由 `execution.failure-rules` 覆盖。
- [x] T6A-3 新增 `FailureClassifierTest` + 30 条样本 fixture
  - 判据：分类准确率 100%；assert 不重试；env 重试 1 次；infra/canceled 不重试。
- [x] T6A-4 迁移 `db/migration/V4_3__failure_class.sql` + down（含 `run_attempt`）
- [x] T6A-5 生成 `docs/evidence/v0.4/failure-class-30.md`
  - 判据：30 行样本 → 期望/实际/是否重试；结论 30/30。

### W6B · EXE-08

- [x] T6B-1 新增 `application/execution/DutTarget.java`（`toString` 脱敏）
- [x] T6B-2 新增 `application/execution/SshClient.java` + `infrastructure/SystemSshClient.java`
  - 判据：`-o ConnectTimeout=5`、命令 15s；`-J` 跳板；口令仅 `SSHPASS` 环境变量；argv/日志无明文。
- [x] T6B-3 新增 `application/execution/DutCollectorService.java` + `DutOutcome.java`
  - 判据：三条采集命令；iface 白名单；四类失败分类与建议。
- [x] T6B-4 新增 `api/execution/DutController.java`
  - 判据：`POST /api/execution/dut/collect`，权限 `ASSET:READ`，返回 `DutOutcome`。
- [x] T6B-5 新增 `fixtures/dut-replay/{ip-br-addr,ethtool-s,proc-interrupts}.out` 与 `ReplaySshClient`
  - 判据：样本为真实 DUT 输出形态；测试不联网。
- [x] T6B-6 新增 `DutCollectorServiceTest`、`DutTargetRedactionTest`
  - 判据：解析非空；三类失败分类正确；口令 grep 0 命中。
- [x] T6B-7 生成 `docs/evidence/v0.4/dut-real.md`
  - 判据：首行 `[REPLAY] 非实网`；实网命令与本地 WSL 待办列清；不冒充通过。

**验收**：分类 30/30；回放采集解析成功；口令扫描 0；EXE-08 状态 `待验`。

---

## W7 · EXE-07 4 并发压测（脚本 + 固定 fixture）

**依赖**：W3、W2、W5 ｜ **状态**：未开始 → 待验 ｜ **证据**：`bench-20rounds.txt`、`bench-2process.log`

- [x] T7-1 新增 `fixtures/bench/4suite-2cases/suite{1..4}.robot`
  - 判据：4 suite × 2 用例；每条 `Sleep 6s` + `Log`；`--include` 可过滤。
- [x] T7-2 新增 `scripts/bench-4way.sh`
  - 判据：参数 `--rounds 20`；串行基线 + 4 并发 + 2 并发对照；输出 `speedup` / `conflict` / `overhead` / `p95`；每轮清产物。
- [x] T7-3 新增 `fixtures/bench/README.md`
  - 判据：固定用例形态、加速比口径、独占环境要求。
- [x] T7-4 生成 `docs/evidence/v0.4/bench-20rounds.txt`、`bench-2process.log`
  - 判据：首行 `[待本地 WSL 实跑]`；附脚本命令与预期门槛（≥2.5x / 最差 ≥2.2x / 冲突 0）。

**验收**：脚本 `bash -n` 通过；fixture 可被 RF 解析需本地 WSL 验证。

---

## W8 · 收口

- [x] T8-1 跑 `mvn -B -f backend/pom.xml test`，落 `docs/evidence/v0.4/mvn-test.log`
  - 判据：退出 0；记录 `Tests run: N, Failures: 0`。
- [x] T8-2 跑 `mvn -B -f backend/pom.xml test -Dtest=LeaseConcurrencyTest`，落 `lease-concurrency.log`
- [x] T8-3 跑前端 `npm run build` + `npm run lint`，落对应日志
  - 判据：均 exit 0。
- [x] T8-4 更新 `docs/plan/WBS-STATUS.md`
  - 判据：`#30~37` 改 `待验` 并填证据；汇总 `24/1/31/0 → 16/1/39/0`；最近完成追加一行。
- [x] T8-5 更新 `docs/release/v0.4-execution/spec.md` 的「待批准决策」为已确认
- [x] T8-6 自检：`git status` 只含预期文件；不执行任何 `git commit`

---

## 人工验收（本地 WSL）

- [ ] H-1 `make db-reset` + `make db-migrate` + `make db-audit`：V4_1~V4_3 落库无错
- [ ] H-2 `mvn -B -Pit test`：DB 集成测试全绿
- [ ] H-3 TC-v0.4-301/302 现场演示：≤2 分钟定位到真实原因
- [ ] H-4 取消后 `ps` 无 `robot`/`pabot` 残留；立即重跑拿到同设备
- [ ] H-5 20 轮压测：平均 ≥2.5x、最差 ≥2.2x、冲突 0
- [ ] H-6 4 并发日志 P95 <2s
- [ ] H-7 DUT 实网 1 条（或书面接受 `[REPLAY]`）
- [ ] H-8 人签署后自行把 `已完成` 改入 WBS
