# EXEC-14 证据：日志级别过滤（WBS #95 / FR-v0.4-20）

- 日期：2026-11-05
- 范围：日志面板的分级过滤——从单档「只看失败」改为 ERROR / WARN / INFO 三档独立勾选
- 状态：**待验**
- 约束：只改前端，未连接真实 DUT（ADR-0013）

## 1. 结论先行

`FR-COVERAGE` 里 FR-20 的最后一条缺口是「过滤只有『只看失败』一档，不能按 INFO/WARN/DEBUG 任选」。
本包把这一档换成三个可独立勾选的级别，默认全开。

**没做 DEBUG 档**：后端 `RunLogService` 只产出 `ERROR` / `WARN` / `INFO` 三档（`FAIL` 已被归到 `ERROR`），
没有数据源的档位做出来就是个永远空的勾选框。PRD 里写的 DEBUG 档留给 v0.5 日志补口。

## 2. 改动

| 文件 | 改动 |
|---|---|
| `frontend/src/views/RunsView.vue` | `onlyFail` → `enabledLevels`（默认 `['error','warn','info']`）；`lineLevel()` 归一化后端级别（未知按 `info`）；三档 `NCheckbox` 独立勾选；空态按「是否被过滤」分两句 |
| `frontend/src/locales/zh-CN.ts` | 删 `onlyFail`；加 `levelError` / `levelWarn` / `levelInfo` / `logFilteredEmpty` |
| `frontend/src/locales/en-US.ts` | 同上（`Errors` / `Warnings` / `Info` / `No lines match the current level filter`） |
| `frontend/src/views/RunsView.spec.ts` | mock 日志加一条 `warn` 行；新增 1 条视图用例 |

## 3. 三个设计取舍

1. **三档全关允许，但空态要说实话**：全关时面板显示「当前级别过滤下没有日志」，不是「暂无日志」——
   后者会让用户以为这份 run 真没日志。
2. **未知级别按 INFO 放行**：真出现没见过的 `level`，宁可多显示一行，也不要把行藏起来（藏了就是"日志丢了"）。
3. **着色不动**：`level-warn` / `level-error` 的 CSS 原样保留，`info` 走默认色；本包只解决过滤。

## 4. 用例

`RunsView.spec.ts` → `日志按级别过滤：取消「信息」后 INFO 行隐藏，ERROR / WARN 保留（FR-v0.4-20）`：

1. 打开 RUN-AAA 日志面板，三行（`started` / `FAIL boom` / `slow step`）都在；
2. 取消「信息」→ `started` 消失，`FAIL boom` 与 `slow step` 仍在；
3. 再把「错误」「警告」也取消 → `FAIL boom` 消失，且出现「当前级别过滤下没有日志」。

## 5. 门禁（日志见同目录）

| 门禁 | 命令 | 结果 | 日志 |
|---|---|---|---|
| 后端单测 | `cd backend && mvn -B test` | **539** 通过（未改后端） | `backend-test.log` |
| 后端集成 | `DB_NAME=robot_it mvn -B -Pit test` | **28** 通过 | `backend-it.log` |
| 前端单测 | `npx vitest run` | **164** 通过（EXEC-13 为 163，+1） | `frontend-test.log` |
| 类型检查 | `npx vue-tsc --noEmit` | exit 0 | `frontend-tsc.log` |
| 前端构建 | `npx vite build` | exit 0 | `frontend-build.log` |
| 规范 | `bash scripts/lint.sh` | PASS | `lint.log` |
| WBS 自洽 | `python3 tools/check_wbs_consistency.py` | PASS `95 个包 / 1 / 0 / 81 / 0 / 13` | `wbs.log` |
| 发布文档机检 | `python3 tools/check_release_docs.py` | FAIL 0 / WARN 0 | `release-docs.log` |
| 工作区 | `git status --short` | 仅工作区改动，**无提交** | `git-status.log` |

## 6. 已知缺口

1. **没有 DEBUG 档**：不是漏做，是后端不产出。要做需在执行器侧把 RF 的 DEBUG 级消息也采集进 `RunLogService`，
   属 v0.5 日志补口范围。
2. **过滤只在已加载的行上生效**：面板只用 `recentLog(limit=200)` + WebSocket 增量，不覆盖完整日志；
   完整日志请走「下载完整日志」（FR-21 / EXEC-06）。
3. **过滤状态不持久化**：刷新页面回到「三档全开」，没有写 URL / localStorage。
