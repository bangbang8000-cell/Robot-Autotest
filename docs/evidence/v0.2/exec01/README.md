# EXEC-01 执行人工触发 — 实现与门禁证据

- **日期**：2026-11-05
- **任务包**：WBS #73 / `EXEC-01`（`FR-v0.2-59~63`）
- **权威副本**：WSL `~/robot-autotest`（HEAD `a996a54` 之上，未提交）
- **状态**：`未开始 → 待验`（AI 不改 `已完成`；验收由人做）

## 1. 交付内容

### 1.1 派发器公共化（去计划域私有化）

| 项 | 结果 |
|---|---|
| 接口迁移 | `application.plan.RunDispatcher` → `application.execution.RunDispatcher` |
| 生产实现 | `infrastructure.SerialRunDispatcher`（原 `PlanRunDispatcher`）：单线程 `core=max=1`，`ThreadPoolTaskExecutor` 队列 64 |
| 去重语义 | `inFlight` 以 `runId` 为键做在途去重，同一 run 并发重复提交只执行一次 |
| 租户上下文 | 异步线程用 `TenantContext.runAs(null, "runner", projectId, task)` 包裹 |
| 死代码处置 | 删除无调用方的 `RunQueuePolicy` 与其测试（评估书允许的二选一） |

### 1.2 手工触发（FR-59/60）

`POST /api/execution/quick-run`

- 权限：`EXECUTION:EXECUTE`（`RUN_CREATE` 审计仍由 `RunService` 写入）
- 校验：至少 1 个套件路径；去空行/去重；拒绝绝对路径、盘符、`..` 段；上限 200 条
- 落库：`RunOrigin.manual()` → `trigger_type=manual`、`plan_id=NULL`
- 派发：`RunService.create` 返回（事务已提交）后立即 `dispatcher.submit`，响应不等执行
- 审计：`RUN_TRIGGER`，detail = `source=...; suites=N; operator=<当前用户>; testSet=<id|省略>`
- 幂等：带 `runRequestId` 重放返回原 run；非 `QUEUED` 状态不再派发、不再重复记触发审计

### 1.3 CI 直建端点收编（FR-61）

`POST /api/execution/runs` 保留原请求体与路径，改为：

1. `RunOrigin(CI, planId=null)` 落库；
2. 立即交给同一派发器。

原先「只落库、无消费者、永远 queued」的僵尸 run 不再产生。控制器基路径由
`/api/execution/runs` 调整为 `/api/execution`，原有 4 条路径（列表/详情/日志/取消）逐条保留。

### 1.4 用例集立即执行（FR-63）

`POST /api/execution/test-sets/{testSetId}/run?projectId=...`

- 解析口径：用例集内每个 `case_code` 在脚本工作区（`robot_file`）的
  `*** Test Cases ***` 段中找到同名顶层测试名 → 该文件路径即套件；与报告覆盖
  `case_key = case_code` 的既有约定一致。
- 未绑定：整单拒绝（400），错误信息列出全部未绑定编号与条数，**不静默跳过、不猜路径**。
- 空集合 → 400；集合不存在 → 404。
- 解析成功后复用 `QuickRunService`（审计 `source=test-set`、带 `testSet`）。

### 1.5 前端

`frontend/src/views/RunsView.vue` 工具栏新增「手动执行」，内联表单支持两种来源：

- **按套件路径**：多行文本框，逐行 trim/去空，实时控制提交按钮可用性；
- **按用例集**：下拉选择（`caseSetApi.page`），提示未绑定会整单拒绝；
- 环境 ID / 并发 / 超时 / Include 标签；提交后刷新列表并直接把日志面板接到新 run；
- 中英 i18n 双份；`execution.manual.*` 新增 key。

## 2. 门禁实测

| 门禁 | 结果 | 日志 |
|---|---|---|
| 后端单测 | `Tests run: 488, Failures: 0, Errors: 0` | `backend-test.log` |
| 后端集成（`-Pit`，robot_it） | `Tests run: 28, Failures: 0`，BUILD SUCCESS | `backend-it.log` |
| 前端测试 | `23 files / 153 tests passed` | `frontend-test.log` |
| 前端类型 | `vue-tsc --noEmit` 退出 0 | `frontend-tsc.log` |
| 前端构建 | `npm run build` 退出 0 | `frontend-build.log` |
| 仓库守门 | `bash scripts/lint.sh` → `[lint] PASS` | `lint.log` |

新增测试：

| 文件 | 覆盖 |
|---|---|
| `QuickRunServiceTest`（6） | 合法提交落 manual + 立即派发 + RUN_TRIGGER 审计；空/绝对/`..` 路径 400；runRequestId 幂等；访客 403；终态不派发 |
| `TestSetRunServiceTest`（4） | 全部绑定时按用例顺序解析；混合集合 400 且列编号；空集合 400 / 不存在 404；节区解析（缩进/注释/中英节区） |
| `SerialRunDispatcherTest`（2） | 峰值并发=1；同 runId 在途重复提交只执行一次 |
| `RunControllerTest`（4） | quick-run 400/403 状态映射；参数透传；用例集 404；CI 端点 origin=CI + 立即派发 |
| `RunsView.spec.ts`（+3） | 表单开合与默认值；套件路径逐行解析并提交；空路径禁用提交 |

## 3. 验收对照（TC-EXEC-01~08）

| 用例 | 落点 | 证据 / 说明 |
|---|---|---|
| TC-EXEC-01 合法提交 2s 内可见 | `quick-run` 立即派发；前端提交后 `selectRun` | API 响应不等执行；`RunControllerTest` + 前端 `RunsView.spec` |
| TC-EXEC-02 非法入参 | 空列表/绝对路径/`..` → 400 | `QuickRunServiceTest`、`RunControllerTest` |
| TC-EXEC-03 重复提交幂等 | `runRequestId` + 派发器在途去重 | `QuickRunServiceTest`、`SerialRunDispatcherTest` |
| TC-EXEC-04 无权限拒绝 | `EXECUTION:EXECUTE` | `QuickRunServiceTest`（viewer 403）、`RunControllerTest`（403 映射） |
| TC-EXEC-05 CI 端点不再 queued | `POST /execution/runs` 接派发器 | `RunControllerTest`；真实执行仍由 `RunExecutionService` 承担 |
| TC-EXEC-06 用例集成功 | `TestSetRunService` | `TestSetRunServiceTest` |
| TC-EXEC-07 混合集合并列未绑定 | 400 + 编号列表 | `TestSetRunServiceTest` |
| TC-EXEC-08 真机 pabot 端到端 | 执行器不变，mock launcher 已覆盖编排 | **按 ADR-0013 不在开发期跑真机**，留给验收人在有环境时执行 |

## 4. 口径偏差与风险（验收时必须知道）

1. **套件路径不做磁盘存在性校验**：`execution.workspace-root` 默认指向
   `~/robot-test/infra-monitor`，dev 机上不存在，请求期无法可靠判定。因此非法路径不会被 404，
   而是落库后由执行期（预检/pabot）明确失败，`failure_reason` 可查。计划书 TC-EXEC-02 的
   「套件不存在 → 404」在本次实现中改为「执行期明确失败」；若验收要求请求期 404，
   需先把执行工作区配置化并落盘（另开包）。
2. **用例集绑定是约定解析，不是绑定模型**：`case_code == 脚本测试名`。真正的
   用例↔脚本绑定（勾选用例直跑）仍按计划留给 `EXEC-02`，本包不做 schema 变更。
3. **脚本工作区与执行工作区仍可能存在不一致**（ADR-0005：脚本存 DB，执行器用磁盘）。
   用例集解析出的路径来自 DB 工作区，若磁盘未镜像，执行会在预检/pabot 阶段失败，
   失败原因会明确返回。该一致性属既有架构缺口，不在 EXEC-01 范围。
4. **真实 pabot 端到端未跑**：遵守 ADR-0013 开发期禁跑令；单测用录制式派发器 + mock launcher，
   未对任何真实 DUT/交换机执行台账用例。
