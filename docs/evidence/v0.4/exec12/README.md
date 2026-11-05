# EXEC-12 证据：单用例超时（WBS #93 / FR-v0.4-24）

- 日期：2026-11-05
- 范围：单 run 总超时之外的「单用例超时」，超时后 run 原因可区分（`test_timeout`），按取消归类
- 状态：**待验**
- 约束：开发期未连接真实 DUT（ADR-0013），注入与识别均以本地套件 + 构造产物验证

## 1. 结论先行

Robot Framework **没有全局单用例超时命令行开关**（7.5.1 实测 `robot --help` 无任何 timeout 选项）。
唯一可用的机制是套件级 `*** Settings ***` 的 `Test Timeout`。因此本包的做法是：

1. 执行前把 `Test Timeout    <N>s` 注入**本 run 的工作区副本**（不碰数据库脚本与用户原脚本）；
2. run 结束后从 `output.xml` 识别 RF 的超时文案，把 run 原因记 `test_timeout`。

## 2. 改动

| 项 | 之前 | 现在 |
|---|---|---|
| 配置 | 只有 `runTimeoutSeconds`（run 总超时） | 新增 `execution.test-timeout-seconds`，默认 `1800`，`<=0` 关闭 |
| 注入 | 无 | `TestTimeoutInjector.inject(workDir, suitePaths, seconds)`：`.robot` 套件按需注入，目录递归；已有 `Test Timeout` 的套件保持用户值 |
| 注入时机 | — | `RunExecutionService` 在 `workspaces.materialize` **成功后**才注入；回落到真实 workspace-root 时跳过（那是用户原脚本） |
| 产物识别 | 只校验结构与分片 | `OutputXmlProbe.timedOutTests` 数 `msg` 里的 `Test timeout ... exceeded`；`ArtifactVerifier.testTimedOut` 对外暴露 |
| run 原因 | 总超时 `timeout` | 新增 `test_timeout`（`FailureClassifier` 命中 `timeout` → CANCELED，符合 PRD「超时按取消处理」），并写一行 `[system]` 日志 |
| 成功判定 | `exitCode == 0 && 产物 ok && 分片 ok` | 追加 `&& !testTimedOut` |

改动文件：

- `backend/src/main/java/com/qa/autotest/application/execution/TestTimeoutInjector.java`（新建）
- `.../ExecutionProperties.java`、`.../RunExecutionService.java`、`.../OutputXmlProbe.java`、`.../ArtifactVerifier.java`、`.../PabotOrchestrator.java`
- `backend/src/test/java/com/qa/autotest/application/execution/TestTimeoutInjectorTest.java`（新建，5 条）
- `.../ArtifactVerifierTest.java`（+1 条）

## 3. 用例

`TestTimeoutInjectorTest`：

1. `已有_Settings_段时紧随其后注入` —— 注入行落在 `*** Settings ***` 下一行
2. `没有_Settings_段时新建在文件头` —— 自动补 `*** Settings ***` + `Test Timeout`
3. `用户已写_Test_Timeout_时不覆盖` —— 原 `120s` 保留，返回改写数 0
4. `目录递归只改_robot_文件` —— 嵌套 `.robot` 被改，`notes.txt` 不动
5. `秒数非正时不注入` —— `0` 时静默跳过

`ArtifactVerifierTest`：

6. `识别单用例超时的失败消息` —— 构造 `test > status > msg` 含 `Test timeout 30 seconds exceeded.` 的产物，识别为 1；正常产物 0；文件不存在 false（不反向误判）

## 4. 门禁（日志见同目录）

| 门禁 | 命令 | 结果 | 日志 |
|---|---|---|---|
| 后端单测 | `cd backend && mvn -B test` | **539** 通过（EXEC-11 为 533，+6） | `backend-test.log` |
| 后端集成 | `DB_NAME=robot_it mvn -B -Pit test` | **28** 通过 | `backend-it.log` |
| 前端单测 | `npx vitest run` | **162** 通过（未改动前端） | `frontend-test.log` |
| 类型检查 | `npx vue-tsc --noEmit` | exit 0 | `frontend-tsc.log` |
| 前端构建 | `npx vite build` | exit 0 | `frontend-build.log` |
| 规范 | `bash scripts/lint.sh` | PASS | `lint.log` |
| WBS 自洽 | `python3 tools/check_wbs_consistency.py` | PASS `93 个包 / 1 / 0 / 79 / 0 / 13` | `wbs.log` |
| 发布文档机检 | `python3 tools/check_release_docs.py` | FAIL 0 / WARN 0 | `release-docs.log` |
| 工作区 | `git status --short` | 仅工作区改动，**无提交** | `git-status.log` |

## 5. 已知缺口

1. **`.resource` 资源文件不注入**：RF 的 `Test Timeout` 只对套件生效，资源文件里没有该设置；若某用例把长步骤封装在 resource 关键字里，超时仍按整个 test 计时，行为正确。
2. **按用例单独配置超时未做**：PRD 未要求逐用例配置，当前支持全局默认 + 每次触发覆盖（见第 6 节）。
3. **未做真实长跑验证**：按 ADR-0013 开发期不连 DUT，实机验证留到 v1.0 验收。

## 6. 2026-11-06 补记：run 级覆盖 + UI 可调

原先「UI 不可调」的缺口已补齐：本次不做逐用例配置，但允许**每次触发单独指定单用例超时**，
不改全局配置、不影响其他运行。

| 层 | 之前 | 现在 |
|---|---|---|
| 命令模型 | `CreateRunCommand` 无该字段 | 末尾新增 `int testTimeoutSeconds`（0 = 用全局默认） |
| 归一与上限 | — | `RunService.normalizeTestTimeout(Integer)`：`null`/`<=0` → 0；`> 86400` 抛 `INVALID_ARGUMENT` |
| 手工触发 | `QuickRunCommand` 无该字段 | 新增 `Integer testTimeoutSeconds`，`QuickRunService` 归一后透传 |
| 用例集 / 用例批量 | 同上 | `TestSetRunCommand`、`CaseRunCommand` 同步加字段并透传 |
| 执行 | 固定 `props.getTestTimeoutSeconds()` | `cmd.testTimeoutSeconds() > 0 ? cmd : 全局默认`，注入逻辑不变 |
| CI / 计划调度 | — | 新增请求字段；计划调度固定 0（沿用全局默认） |
| 前端 | 只有「超时（秒）」 | 运行记录页手动执行表单、用例页「执行所选」各加「单用例超时（秒，0=默认）」输入（`0..86400`） |

改动文件：

- `backend/.../execution/RunService.java`、`QuickRunService.java`、`TestSetRunService.java`、`CaseRunService.java`、`RunExecutionService.java`
- `backend/.../api/execution/RunController.java`、`backend/.../plan/PlanScheduleService.java`
- `backend/.../execution/QuickRunServiceTest.java`（+2 条）、`RunExecutionServiceTest.java`（+1 条）
- `frontend/src/api/execution.ts`、`views/RunsView.vue`、`views/CasesView.vue`、`locales/zh-CN.ts`、`locales/en-US.ts`
- `frontend/src/views/RunsView.spec.ts`、`views/CasesView.spec.ts`（断言新增字段）

新增用例：

1. `单用例超时归一与上限` —— 120 秒触发成功并派发；86401 秒在落库前抛 `INVALID_ARGUMENT`（消息含 `86400`）
2. `单用例超时归一规则` —— `null`/`0`/`-5` → 0，`120`/`86400` 原样
3. `单用例超时可被本次运行覆盖_未指定时沿用全局默认` —— 覆盖值 90 注入后工作区副本含 `Test Timeout    90s`，全局配置仍 1800；传 0 时副本含 `Test Timeout    1800s`

边界（保持）：`testTimeoutSeconds` 只随本次运行生效，**不落 `execution_job` 表**，运行记录重放不会还原该参数；
`.resource` 仍不注入；逐用例单独配置仍不做。

## 7. 门禁（2026-11-06 复跑）

| 门禁 | 命令 | 结果 |
|---|---|---|
| 后端全量单测 | `cd backend && mvn -o test` | **555** 通过（原 552 + 3） |
| 前端全量单测 | `cd frontend && npm run test` | **197** 通过（28 文件） |
| 前端构建 | `cd frontend && npm run build` | exit 0 |
| 字体子集 | `python3 tools/subset_font.py` | PASS 1028 字 / 178594 B gzip |
| 规范 / WBS / 发布文档 | `bash scripts/lint.sh` 等 | PASS / PASS / PASS，日志见本目录 `*-20261106.log` |

日志文件：`backend-test-20261106.log`、`frontend-test-20261106.log`、`frontend-build-20261106.log`、
`font-20261106.log`、`lint-20261106.log`、`release-docs-20261106.log`、`wbs-20261106.log`、`git-status-20261106.log`。

