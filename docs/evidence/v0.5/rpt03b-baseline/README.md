# RPT-03 补口证据：手动基线选择 UI（WBS #40 / v0.5）

- 日期：2026-11-05
- 范围：详情页「本轮 vs 基线」的手动基线选择（候选接口 + 下拉）
- 状态：**待验**（不新增 WBS 包号，仍记在 #40 / RPT-03 名下）
- 约束：开发期禁跑真实 DUT（ADR-0013）；本包只做本机可验部分

## 1. 结论先行

RPT-03 的阻塞列原本写着「手动基线选择未做前端 UI」：后端 `GET /api/reports/{runId}/compare?baselineRunId=`
早已支持手动指定基线（跨项目 / 跨环境 400），但页面上没有入口，用户只能一直看自动基线。

本补口补上两块：

1. **候选接口** `GET /api/reports/{runId}/baseline-candidates?projectId=`：返回同项目、同 `dut` / 同 `link`、
   `finished`、排除本轮、按开始时间倒序的最近 20 条；
2. **详情页下拉**：默认项「自动（最近一次）」，选中候选后带 `baselineRunId` 重新拉对比。

## 2. 改动

| 文件 | 改动 |
|---|---|
| `backend/.../application/repository/ReportReadRepository.java` | 新增 `listBaselineCandidates(projectId, dut, link, excludeRunId, limit)` |
| `backend/.../infrastructure/ReportReadRepositoryJdbc.java` | 复用 `RUN_SNAPSHOT` 常量实现候选 SQL（`COALESCE(dut,'')` / `COALESCE(link,'')` 对齐 NULL 口径） |
| `backend/.../application/report/CompareService.java` | 新增 `BaselineCandidate(runId, startedAt)` 记录 + `candidates(projectId, runId)` + `MAX_BASELINE_CANDIDATES = 20` |
| `backend/.../api/report/ReportController.java` | 新增 `GET /{runId}/baseline-candidates` |
| `frontend/src/api/reports.ts` | 新增 `CompareBaseline` 类型与 `baselineCandidates(projectId, runId)` |
| `frontend/src/views/ReportDetailView.vue` | 基线下拉（`NSelect`）+ `selectedBaseline` 状态 + 候选拉取失败只清空候选不判红 |
| `frontend/src/locales/{zh-CN,en-US}.ts` | `baselineLabel` / `baselineAuto` / `baselineOption` |

## 3. 三个设计取舍

1. **候选口径与 `resolveBaseline` 校验对齐**：同项目 + 同 DUT/LINK + `finished` + 排除本轮。
   这样「下拉里能选中的一定不会 400」，不会让用户点出一个报错。
2. **只暴露 `runId + startedAt`**：候选不加计数 join，避免为了一个下拉框把对比查询的成本搬进列表接口；
   开始时间为 NULL 的运行照样列出，前端按「未知时间」渲染。
3. **上限 20 条**：下拉只需要近期几条，不把项目全部历史拉进页面；被截断不影响「自动基线」这条主路径。

## 4. 用例

- `CompareServiceTest`（13 条，本轮 +2）：
  - `手动基线候选：同项目同环境的 finished 按开始时间倒序，排除本轮与异环境`——种 CURRENT / OLD / NEAR / OTHER-DUT / OTHER-LINK / FOREIGN，断言 `[NEAR, OLD]`；
  - `手动基线候选：运行不存在时 404`。
- `CompareBaselineIT`（新建，1 条真库用例）：连 `robot_it`，种子含 `started_at` 为 NULL 的运行，断言 `[NEAR, OLD, NULL]`、
  NULL 排最后、`limit=2` 截断、跨项目 `FORBIDDEN`；跑完打印
  `[RPT-03] 手动基线候选=[IT-CMP-NEAR-…, IT-CMP-OLD-…, IT-CMP-NULL-…] limit2=2 跨项目=403`。
- `ReportDetailView.spec.ts`（165 中 1 条）：候选由接口给出，选中后 `compare(…, 'RUN-OLD')`，再选「自动」回到 `undefined`。

## 5. 门禁（日志见同目录）

| 门禁 | 命令 | 结果 | 日志 |
|---|---|---|---|
| 后端单测 | `cd backend && mvn -B test` | **541** 通过（539 → +2） | `backend-test.log` |
| 后端集成 | `DB_NAME=robot_it mvn -B -Pit test` | **29** 通过（28 → +1，含 `CompareBaselineIT`） | `backend-it.log` |
| 前端单测 | `npx vitest run` | **165** 通过 / 23 文件（164 → +1） | `frontend-test.log` |
| 类型检查 | `npx vue-tsc --noEmit` | exit 0 | `frontend-tsc.log` |
| 前端构建 | `npx vite build` | exit 0 | `frontend-build.log` |
| 规范 | `bash scripts/lint.sh` | PASS | `lint.log` |
| WBS 自洽 | `python3 tools/check_wbs_consistency.py` | PASS `95 个包 / 1 / 0 / 81 / 0 / 13` | `wbs.log` |
| 发布文档机检 | `python3 tools/check_release_docs.py` | FAIL 0 / WARN 0 | `release-docs.log` |
| 工作区 | `git status --short` | 仅工作区改动，**无提交** | `git-status.log` |

## 6. 已知缺口

1. **真实环境对比未跑**：候选与对比都只在本机合成数据上验证，真实 DUT 上的多轮运行（真实耗时、真实空基线）未验证。
2. **候选不含「换了 DUT 的同用例」**：同项目但不同 DUT 的运行不会出现在候选里，这是有意的（对比语义要求同环境）；
   若将来要「跨设备对比」，得另开需求，不能在候选口径里放宽。
3. **下拉不记忆选择**：刷新页面回到「自动（最近一次）」，未写 URL / localStorage。
