# EXEC-13 证据：运行详情显示「第 N 次尝试」（WBS #94 / FR-v0.4-29）

- 日期：2026-11-05
- 范围：重试可追溯的前端展示——运行详情（日志面板）显示「第 N 次尝试」
- 状态：**待验**
- 约束：只改前端，未连接真实 DUT（ADR-0013）

## 1. 结论先行

`attemptNo` 早就贯通全链路：`RunRepository.markAttempt` 写库 → `RunRecord` → `RunService` 构造 `RunView`
→ 前端 `ExecutionRun.attemptNo`。缺的只是**展示**。所以本包不动后端，只在日志面板加一条标记。

标记**只在 `attemptNo > 1` 时渲染**：首次尝试不显示「第 1 次尝试」——否则每一次正常运行的详情页
都会被同一句话占满，反而看不出哪条是重试。

## 2. 改动

| 文件 | 改动 |
|---|---|
| `frontend/src/views/RunsView.vue` | 日志面板头部（设备范围之后）新增 `<span v-if="selected.attemptNo > 1">` 显示 `execution.attemptLine` |
| `frontend/src/locales/zh-CN.ts` | `attemptLine: '第 {n} 次尝试'` |
| `frontend/src/locales/en-US.ts` | `attemptLine: 'Attempt {n}'` |
| `frontend/src/views/RunsView.spec.ts` | mock 补 `attemptNo`（RUN-AAA = 2，RUN-BBB = 1）；新增 1 条视图用例 |

「第几次 / 原因 / 结果」三件事的落点：第几次 = 本次新增的标记；原因 = 列表既有「失败原因」列
（`failureReason`）；结果 = 列表「状态」列（`StatusTag`）。

## 3. 用例

`RunsView.spec.ts` → `日志面板显示「第 N 次尝试」，首次尝试不渲染该标记（FR-v0.4-29）`：

1. 打开 RUN-AAA（`attemptNo = 2`）日志面板，断言文案含 `第 2 次尝试`；
2. 切到 RUN-BBB（`attemptNo = 1`），断言文案**不含** `第 1 次尝试`。

## 4. 门禁（日志见同目录）

| 门禁 | 命令 | 结果 | 日志 |
|---|---|---|---|
| 后端单测 | `cd backend && mvn -B test` | **539** 通过（未改后端，应为基线） | `backend-test.log` |
| 后端集成 | `DB_NAME=robot_it mvn -B -Pit test` | **28** 通过 | `backend-it.log` |
| 前端单测 | `npx vitest run` | **163** 通过（EXEC-12 为 162，+1） | `frontend-test.log` |
| 类型检查 | `npx vue-tsc --noEmit` | exit 0 | `frontend-tsc.log` |
| 前端构建 | `npx vite build` | exit 0 | `frontend-build.log` |
| 规范 | `bash scripts/lint.sh` | PASS | `lint.log` |
| WBS 自洽 | `python3 tools/check_wbs_consistency.py` | PASS `94 个包 / 1 / 0 / 80 / 0 / 13` | `wbs.log` |
| 发布文档机检 | `python3 tools/check_release_docs.py` | FAIL 0 / WARN 0 | `release-docs.log` |
| 工作区 | `git status --short` | 仅工作区改动，**无提交** | `git-status.log` |

> 注：`lint.sh` 的字体子集检查会扫源码里的汉字，第一次跑因测试注释里写了「噪声」判 FAIL
> （`噪` 不在中文子集内）。改掉措辞后复跑 PASS，未重新生成字体子集。

## 5. 已知缺口

1. **看不到「上一次失败原因」**：`retry_of_run_id` 已入库，但 `RunRecord` / `RunView` 没这个字段，
   前端也无从读取。要补需在 `RunRecord` 增字段并改 `new RunRecord(...)` **23 处**（分布在 14 个文件，
   多数是测试支撑），属独立一包。
2. **重试链未成组展示**：列表里每次重试是独立一行，用户需自己按 `attemptNo` 对照；
   PRD 未要求成组/时间线视图。
3. **`attemptNo` 的写入路径未在本包验证**：`markAttempt` 已有 `RetryPolicyTest` 覆盖，
   本包只验展示；端到端（真跑一次 env 失败 → 自动重试 → 详情显示第 2 次）留到 v1.0 验收。
