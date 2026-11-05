# EXEC-11 证据：设备范围（WBS #92）

- 日期：2026-11-05
- 范围：收口 FR-v0.4-01 的「设备范围落库与回溯」部分（手动执行可声明、运行记录可查、日志面板可见）
- 状态：**待验**
- 约束：开发期未连接真实 DUT、未触发真实 pabot（ADR-0013）

## 1. 改动

| 项 | 之前 | 现在 |
|---|---|---|
| 运行表 | `run` 无设备范围字段 | `run.device_scope VARCHAR(512) NULL`（`V7_4__run_device_scope.sql`，down 同名 `DROP COLUMN`） |
| 创建入口 | `deviceKeys` 只传不落库 | `RunService.deviceScope(cmd)` 去重保序 → 逗号拼接 → 落库；空/全空白 → `NULL`（不自造键） |
| 长度保护 | 无 | 拼接后 > 512 字符抛 `INVALID_ARGUMENT`，不建 run、不派发 |
| 领域模型 | `RunRecord` 无该字段 | 末尾追加 `String deviceScope`；`RunRepositoryJdbc` SELECT/INSERT/MAPPER 全链路 |
| 状态流转 | — | `transition` / `updateCounts` 回写时沿用 `current.deviceScope()`，不丢值 |
| 前端类型 | `ExecutionRun` 无该字段 | `deviceScope: string \| null`；`QuickRunPayload` / `TestSetRunPayload` 增 `deviceKeys?: string[]` |
| 手动执行表单 | 无 | 「设备范围」`NSelect multiple filterable tag clearable`：设备名下拉（`deviceApi.page` 取前 100）+ 可直接输入端口键 |
| 运行详情 | 无 | 日志面板头部显示「设备范围：xxx」，未声明时明确写「全部设备（不互斥）」 |

改动文件：

- `db/migration/V7_4__run_device_scope.sql`、`db/down/V7_4__run_device_scope.down.sql`
- `backend/src/main/java/com/qa/autotest/domain/execution/RunRecord.java`
- `backend/src/main/java/com/qa/autotest/infrastructure/RunRepositoryJdbc.java`
- `backend/src/main/java/com/qa/autotest/application/execution/RunService.java`
- 构造点补参（14 处）：`RunControllerTest`、`PlanTestSupport`、`CiTestSupport`、`ExportServiceTest`、`ReportIngestServiceTest`、`CaseRunServiceTest`、`TestSetRunServiceTest`、`InMemoryRunRepository`、`ExecutionTestSupport`、`QuickRunServiceTest`、`RunLogFileServiceTest`、`RunExecutionServiceTest`
- `backend/src/test/java/com/qa/autotest/application/execution/QuickRunServiceTest.java`（+3 条）
- `backend/src/test/java/com/qa/autotest/migration/MigrationFileTest.java`（+1 条）
- `frontend/src/api/execution.ts`、`src/views/RunsView.vue`、`src/locales/zh-CN.ts`、`src/locales/en-US.ts`
- `frontend/src/views/RunsView.spec.ts`（+2 条）

**迁移版本号说明**：`V4_4` 不可用——开发库已执行 `V4_1~V4_3` 与 `V5/V6/V7` 系列，补插低位版本会触发 Flyway out-of-order 校验失败。故取 `V7_4`，语义仍归 v0.4 EXEC-11。

## 2. 用例

后端（`QuickRunServiceTest`）：

1. `设备范围落库并可回溯` —— `[" DUT-01/mlx5_0 ", "DUT-02/mlx5_1", "DUT-01/mlx5_0"]` → `DUT-01/mlx5_0,DUT-02/mlx5_1`（去重保序 + trim），按 runId 回查一致
2. `设备范围缺省为空` —— 不传 `deviceKeys` → `deviceScope == null`
3. `设备范围超长被拒` —— 三个 200 字符键 → `INVALID_ARGUMENT`，`insertCount == 0`，派发器无提交

迁移（`MigrationFileTest`）：

4. `设备范围字段` —— V7_4 与 down 均存在，含 `` `device_scope` `` / `VARCHAR(512)` / `DROP COLUMN `device_scope``

前端（`RunsView.spec.ts`）：

5. `手动执行表单可声明设备范围，并随 quick-run 提交（FR-v0.4-01）`
6. `日志面板显示运行的设备范围，未声明时明确写「全部设备」`

## 3. 门禁（日志见同目录）

| 门禁 | 命令 | 结果 | 日志 |
|---|---|---|---|
| 后端单测 | `cd backend && mvn -B test` | **533** 通过（EXEC-10 为 529，+4） | `backend-test.log` |
| 后端集成 | `DB_NAME=robot_it mvn -B -Pit test` | **28** 通过（`SchemaIT` 7/7，`V7_4` 在空库迁移通过） | `backend-it.log` |
| 前端单测 | `npx vitest run` | **162** 通过（160 → +2） | `frontend-test.log` |
| 类型检查 | `npx vue-tsc --noEmit` | exit 0（无输出） | `frontend-tsc.log` |
| 前端构建 | `npx vite build` | exit 0 | `frontend-build.log` |
| 规范 | `bash scripts/lint.sh` | PASS（表格规范 20 处 / 品牌检查均 PASS） | `lint.log` |
| WBS 自洽 | `python3 tools/check_wbs_consistency.py` | PASS `92 个包 / 未开始 1 / 进行中 0 / 待验 78 / 待现场 0 / 已完成 13` | `wbs.log` |
| 发布文档机检 | `python3 tools/check_release_docs.py` | FAIL 0 / WARN 0 | `release-docs.log` |
| 工作区 | `git status --short` | 仅工作区改动，**无提交** | `git-status.log` |

## 4. 已知缺口（未在本包做）

1. ~~**并发度三档选择**~~ → **2026-11-06 已补，见第 5 节**
2. **运行列表「设备范围」列**：只在日志面板展示，列表页未加列（列宽紧张，需评估截断与悬浮全文）。
3. **执行期设备互斥**：`device_scope` 目前只做「记录 + 展示」，未参与调度判重。若要做「同一设备不允许并行」，需要与 `ProjectRunDispatcher` 的排队键联动，属独立包。

## 5. 2026-11-06 补记：并发度改 1/2/4 三档

PRD FR-v0.4-01 要求并发度为 1 / 2 / 4 三档，原实现是 1~`MAX_PARALLEL` 的数字输入，易填出无意义值。

| 位置 | 改动 |
|---|---|
| `frontend/src/views/RunsView.vue` | 手动执行表单 `NInputNumber` → `NRadioGroup` + 三个 `NRadioButton`（1 / 2 / 4），默认 1；加提示「实际分片数取 min(并发度, 套件数)，并受可用端口数限制」 |
| `frontend/src/views/CasesView.vue` | 「执行所选」卡片同样改三档，并在组前加文字标签 |
| `frontend/src/locales/{zh-CN,en-US}.ts` | 新增 `execution.manual.concurrencyHint` |
| `frontend/src/views/RunsView.spec.ts` | 新增「并发度固定 1/2/4 三档，选 4 后按 4 提交（EXEC-11）」：断言渲染恰好三档且提交 `concurrency: 4` |

后端不变：`RunService` 已有 `1..MAX_PARALLEL` 校验，三档不会越界；分片数仍由执行期 `min(并发度, 套件数)` 决定。

门禁（2026-11-06）：前端 **198** 用例通过（197 + 1，28 个测试文件）；`npm run build` exit 0（构建 8.96s）；
字体子集 **1028 字 / 178610 B gzip**（预算 800 KiB，新增提示文案带来的 16 B）；`bash scripts/lint.sh` PASS；
`check_release_docs.py` FAIL 0 / WARN 0；WBS 自洽 PASS（96 包 / 待验 83 / 已完成 13）。
日志见 `frontend-test-20261106.log`、`frontend-build-20261106.log`、`font-20261106.log`、`lint-20261106.log`、
`release-docs-20261106.log`、`wbs-20261106.log`（同目录，可复跑）。
