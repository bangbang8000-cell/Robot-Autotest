# v0.4 PRD 34 条 FR 覆盖复核（2026-11-05）

> 复核对象：`docs/release/v0.4-execution/PRD.md` §3.1~§3.7 的 **FR-v0.4-01 ~ 34**。
> 方法：只读代码盘点 + 既有测试清单核对，不跑真实 DUT（ADR-0013）。
> 结论分四档：**覆盖**（实现 + 有测试）/ **部分**（实现不全或接线缺失）/ **缺失**（无实现）/ **待现场**（实现齐但验收标准需要真机）。

## 1. 结论速览

| 档位 | 条数 | FR |
|---|---|---|
| 覆盖 | 20 | 02 / 06 / 11 / 12 / 13 / 14 / 15 / 16 / 17 / 18 / 19 / 21 / 22 / 25 / 26 / 27 / 28 / 30 / 31 / 32 |
| 部分 | 12 | 01 / 03 / 04 / 05 / 07 / 08 / 09 / 10 / 20 / 23 / 24 / 29（见下表逐条说明） |
| 缺失 | 0 | — |
| 待现场 | 2 | 33 / 34 |

> 说明：33 的失败分类代码与单测齐全，但三条真实失败场景（连不通 / 认证失败 / 命令不存在）需真机复现；
> 34 的端到端实网跑通按 ADR-0013 属验收人现场事项。两者都不是「没做」。

## 2. 逐条对照

| FR | 需求要点 | 实现位置 | 测试证据 | 结论 | 缺口 / 说明 |
|---|---|---|---|---|---|
| 01 | 创建运行：用例集 + 环境 + 并发度 1/2/4 + 设备范围 | `RunService.create`、`frontend/src/views/RunsView.vue`、`CasesView.vue` | `QuickRunServiceTest`、`CaseRunServiceTest` | **部分** | 并发度是数字输入（1~`MAX_PARALLEL`），不是 1/2/4 三档选择；**无「设备范围」字段**（全仓无 `deviceScope`） |
| 02 | 状态机合法边 + 非法跃迁抛错 | `RunStateMachine`、`RunService.transition` | `RunStateMachineTest` | 覆盖 | — |
| 03 | 同项目按优先级 + 创建时间；跨项目不互阻 | `SerialRunDispatcher`（全局单线程）、`RunRepositoryJdbc` 列表 SQL | — | **部分** | 派发全局串行（`max=1`），跨项目互阻；入队顺序按 `enqueued_at`，未按 `priority` |
| 04 | 按 suite 切分，分片数 = min(并发度, suite 数)，每片申请租约 | `RunExecutionService`（`processes = min(...)`）、`LeaseService.acquireAll` | `RunExecutionServiceTest`、`LeaseServiceTest` | **部分** | 分片数正确，但**租约未接入执行链路**：全仓 `acquireAll` 只被单测调用，`RunExecutionService` 里仍是「W2 最小闭环」注释 |
| 05 | `MAX_PARALLEL` 可配，达上限排队不拒绝 | `ExecutionProperties.maxParallel`、`RunService` 校验 | `RunServiceTest` | **部分** | 只用于校验 `concurrency` 上限；派发侧不并发，配置不改变实际吞吐 |
| 06 | 租约粒度 = 设备 + 端口，同 key 唯一 | `LeaseService`、`resource_lease` 主键 | `LeaseServiceTest`、`LeaseConcurrencyTest` | 覆盖 | 组件级成立；因 04 未接线，运行期不产生租约 |
| 07 | 申请→持有→续约→释放/超时回收 | `LeaseService`（heartbeat / reclaim） | `LeaseServiceTest` | 覆盖 | 组件级成立，同上未接线 |
| 08 | 设备被占可等待，超时给明确原因 | `LeaseService`（`leaseMaxWaitSeconds`） | `LeaseServiceTest` | **部分** | 等待语义在服务层有，**运行界面无「等待 DUT-01/mlx5_0」状态**；运行状态机上也没有 WAITING |
| 09 | 租约可视化（谁占哪个端口、剩余时间） | EXEC-07：`AssetsView.vue` 租约页签 + `GET /leases` | `AssetsView.spec` | **部分** | UI/接口已齐，但 04 未接线 → 列表在真实使用中恒为空 |
| 10 | 强制释放（管理员、二次确认、审计） | EXEC-07：`LeaseAdminService`、`POST /leases/force-release` | `LeaseAdminServiceTest`、`LeaseControllerTest`、`AssetsView.spec` | **部分** | 逻辑与审计齐；同理，无生效租约时始终 404，实跑验不到 |
| 11 | 按分片调 pabot，`--processes` = 分片数 | `PabotCommandBuilder` | `PabotCommandBuilderTest` | 覆盖 | — |
| 12 | PATH 前置 venv/bin，找不到 robot 明确报错 | `PabotCommandBuilder`（env PATH）、`ExecutionPrecheck` | `PabotCommandBuilderTest`、`ExecutionPrecheckTest` | 覆盖 | — |
| 13 | 双日志：stdout/stderr + `pabot_manager.log` | `PabotOrchestrator` | `RunExecutionServiceTest` | 覆盖 | — |
| 14 | 退出码不可信：校验 output.xml 存在/非空/可解析/用例数>0 | `ArtifactVerifier`、`OutputXmlProbe` | `ArtifactVerifierTest` | 覆盖 | — |
| 15 | 端口数 ≥ 并发进程数，不足预检拒绝 | `ExecutionPrecheck` | `ExecutionPrecheckTest` | 覆盖 | — |
| 16 | `artifacts/<run_id>/` + 分片目录 + 日志 | `RunExecutionService.registerArtifacts` | `RunExecutionServiceTest` | 覆盖 | — |
| 17 | WebSocket 按 `run_id` 分频道 | `RunLogWebSocketHandler` | `RunLogServiceTest` | 覆盖 | — |
| 18 | 断线重连补最近 N 行 | `RunLogService`（补 200 行） | `RunLogServiceTest` | 覆盖 | — |
| 19 | 日志限流（合并/采样） | `LogBatcher` | `LogBatcherTest`、`LogDeliveryLatencyTest` | 覆盖 | — |
| 20 | 分级着色 + 可过滤 | `RunsView.vue`（`level-*` 着色 + ERROR/WARN/INFO 三档勾选） | `RunsView.spec`（级别过滤用例） | 覆盖 | 三档可独立勾选、默认全开、未知级别按 INFO 放行；**仍差** DEBUG 档——后端不产出 DEBUG（PRD 里的 DEBUG 档留给 v0.5 日志补口） |
| 21 | 落盘 + 完整日志可下载 | EXEC-06：`RunLogFileService`、`/log/download` | `RunLogFileServiceTest`、`RunControllerTest` | 覆盖 | — |
| 22 | 取消杀进程组，SIGTERM 后 5 s SIGKILL | `ProcessTreeKiller`、`CancelService` | `ProcessTreeKillerTest`、`CancelServiceTest` | 覆盖 | — |
| 23 | 取消后立即释放租约 | `CancelService`（`leases.releaseRun`） | `CancelServiceTest` | **部分** | 调用已接上，但租约从未申请 → 实际无可释放 |
| 24 | 单 run 总超时 + 单用例超时，超时按取消并标原因 | `RunTimeoutMonitor`、`ExecutionProperties.runTimeoutSeconds`、`TestTimeoutInjector` | `RunTimeoutMonitorTest`、`TestTimeoutInjectorTest` | **覆盖** | 总超时 + 单用例超时（套件级 `Test Timeout` 注入，原因 `test_timeout`，归类 CANCELED） |
| 25 | 同 `run_request_id` 只创建一次 | `RunService`、`RunRepository` 唯一约束 | `IdempotencyTest` | 覆盖 | — |
| 26 | 重启后 running → failed(interrupted) 并释放租约 | `RestartRecoveryService`（`ApplicationReadyEvent`） | `RestartRecoveryTest` | 覆盖 | — |
| 27 | 失败分类 assert / env / infra / canceled | `FailureClassifier`、`FailureRuleSet` | `FailureClassifierTest` | 覆盖 | — |
| 28 | 只有 env 可重试，最多 N 次；assert 不重试 | `RetryPolicy` | `RetryPolicyTest` | 覆盖 | — |
| 29 | 重试可追溯（第几次、原因、结果） | `RunRepository.markAttempt`、`RunRecord.attemptNo`、`RunsView.vue`（`execution.attemptLine`） | `RetryPolicyTest`、`RunsView.spec` | 覆盖 | 运行详情在 `attemptNo > 1` 时显示「第 N 次尝试」；**仍差**「上一次失败原因」——`retry_of_run_id` 已入库但未对外暴露，需在 `RunRecord` / `RunView` 增字段 |
| 30 | 分类规则可配（关键词 + 正则） | `ExecutionProperties.failureRules`（`FailureRuleConfig`） | `FailureClassifierTest` | 覆盖 | — |
| 31 | SSH 采集器经跳板取实网数据 | `DutCollectorService`、`SystemSshClient` | `DutCollectorServiceTest`、`ReplaySshClient` | 覆盖 | 命令为 `ip -br addr` / `ethtool -S <iface>` / `/proc/interrupts`（与 PRD 的 `ethtool -S` 一致） |
| 32 | 凭据从平台凭据库注入，不落盘不进日志 | `DutController`（`credentials.resolveSecret`）、`DutTarget.toString` | `DutTargetRedactionTest` | 覆盖 | 请求体仍允许直接传 `password`（有 credentialId 时优先走凭据库） |
| 33 | 采集失败分类（连不通 / 认证失败 / 命令不存在） | `DutCollectorService`（四类 kind + advice） | `DutCollectorServiceTest` | **待现场** | 代码与单测齐，真实三类故障需真机复现 |
| 34 | 一条真实链路端到端跑通 | 全链路 | — | **待现场** | ADR-0013 开发期禁跑真实 DUT，属验收人现场事项 |

## 3. 最值得补的缺口（按优先级）

1. **P0 · 租约接入执行链路（FR-04 / 07 / 08 / 09 / 10 / 23 的总闸）**
   现在 `LeaseService` 是一套「能跑但没人用」的组件：`RunExecutionService` 仍按「无租约服务」的最小闭环执行，
   导致租约页恒空、强制释放恒 404、取消释放无对象。要接的是：执行前按分片 `acquireAll` → 执行中续约 →
   终态/取消/超时统一 `releaseRun`。这是 v0.4「正确性地基」的核心，建议列为下一包 **EXEC-09**。
2. **P0 · 队列语义（FR-03 / 05）**：`SerialRunDispatcher` 全局单线程，`MAX_PARALLEL` 不生效；
   入队顺序未按 `priority`。要么把派发改为「按项目/端口池」并发，要么在文档里明确降级为「串行队列」并把
   `MAX_PARALLEL` 从「并发上限」改称「单 run 内分片上限」，避免对外承诺与实现不符。
3. **P1 · 设备范围（FR-01）**：请求模型缺 `deviceScope`，创建运行只能跑全量套件。
4. **P1 · 单用例超时（FR-24）**：只有总超时，长单测会把整 run 拖到上限。
5. **P1 · 重试可见性（FR-29）**：`attemptNo` 已入库，运行详情缺「第 N 次尝试 / 上次失败原因」展示。
6. **P1 · 日志过滤档位（FR-20）**：只有「只看失败」，补按级别筛选。
7. **P2 · 等待态可视化（FR-08）**：状态机与界面都没有 WAITING，设备被占时仍表现为「排队中」。

## 4. EXEC-09 收口（2026-11-05 17:20）

> 本节为复核后的进展追加，不改动上方复核快照。

| FR | 复核档位 | EXEC-09 后 | 说明 |
|---|---|---|---|
| 04 | 部分 | **覆盖** | `RunExecutionService` 进入 `RUNNING` 前 `acquireAll`；状态机前置条件 `leaseHeld` 生效 |
| 07 | 覆盖 | 覆盖 | 续约沿用 40s 全局心跳；成功 / 失败 / 异常 / 取消 / 超时 / 重启统一 `releaseRun` |
| 09 | 部分 | **覆盖** | 执行期确有租约行 → 资产页租约页签、`GET /leases` 不再恒空 |
| 10 | 部分 | **覆盖** | 存在生效租约 → 强制释放可命中（403/404/400 口径不变） |
| 23 | 部分 | **覆盖** | 取消时 `releaseRun` 有对象可释放 |
| 08 | 部分 | 部分 | 后端拒绝原因已给（`lease_timeout` + 资源键清单）；**等待态 WAITING UI 仍缺** |

剩余 P0 仍是队列语义（FR-03 / 05），P1 为 FR-01 / 24 / 29 / 20。

## 5. 复核范围与限制

- 只读代码与测试，未启动服务、未跑真实 DUT；「覆盖」表示「实现 + 有测试」，不等价于验收通过。
- 前端判断依据为视图代码与既有 spec；未做浏览器实测。
- 本复核不改动任何实现，仅产出清单，供后续拆包。


## 6. EXEC-10 收口（2026-11-05 17:55）

| FR | 复核档位 | EXEC-10 后 | 说明 |
|---|---|---|---|
| 03 | 部分 | **覆盖** | 派发队列按项目分道；同项目 `priority` 降序、再按提交先后；跨项目分线程池互不阻塞 |
| 05 | 部分 | **覆盖** | 单项目并发 = `MAX_PARALLEL`（默认 4），队列无界，达上限只排队不拒绝 |

剩余 P0 已清空；P1 为 FR-01（deviceScope 落库与执行期使用）、FR-24（单用例超时）、FR-29（重试次数展示）、
FR-20（日志过滤档位）。

## 7. EXEC-11 收口（2026-11-05 18:00）

| FR | 复核档位 | EXEC-11 后 | 说明 |
|---|---|---|---|
| 01 | 部分 | **覆盖** | `run.device_scope`（`V7_4`）落库并按 runId 回溯；手动执行可声明设备范围（设备名下拉 + 手填端口键）；日志面板展示。**仍差**：PRD 的并发度 1/2/4 三档选择未做（现为 1~`MAX_PARALLEL` 数字输入），这一条已在 WBS #92 阻塞列登记 |

剩余 P1：FR-29（重试次数展示）、FR-20（日志过滤档位）。

## 8. EXEC-12 收口（2026-11-05 19:30）

| FR | 复核档位 | EXEC-12 后 | 说明 |
|---|---|---|---|
| 24 | 部分 | **覆盖** | 总超时 `RunTimeoutMonitor` 保留；单用例超时由 `TestTimeoutInjector` 在 run 副本注入套件级 `Test Timeout`（RF 7.5 无 CLI 开关），命中后 run 原因 `test_timeout` → `FailureClassifier` 归 CANCELED，符合「超时按取消处理并标记原因」 |

剩余 P1：FR-29（重试次数展示）、FR-20（日志过滤档位）。

## 9. EXEC-13 收口（2026-11-05 17:15）

| FR | 复核档位 | EXEC-13 后 | 说明 |
|---|---|---|---|
| 29 | 部分 | **覆盖** | `attemptNo` 后端全链路已通，本包补上展示：日志面板 `attemptNo > 1` 时显示「第 N 次尝试」（首次尝试不渲染），与列表既有「失败原因 / 状态」列合起来可回答「第几次 / 原因 / 结果」。**仍差**：`retry_of_run_id` 未对外暴露，「上一次失败原因」看不到 |

剩余 P1：FR-20（日志过滤档位）。

## 10. EXEC-14 收口（2026-11-05 17:25）

| FR | 复核档位 | EXEC-14 后 | 说明 |
|---|---|---|---|
| 20 | 部分 | **覆盖** | 单档「只看失败」换成 ERROR / WARN / INFO 三个可独立勾选的档位（默认全开）；着色沿用既有 `level-warn` / `level-error`；三档全关时显式提示「当前级别过滤下没有日志」，不冒充「暂无日志」。**仍差**：DEBUG 档无数据来源 |

剩余 P1：**无**。v0.4 范围内 P0 / P1 已全部收口，PRD §3「执行闭环」的缺口清单清空。
