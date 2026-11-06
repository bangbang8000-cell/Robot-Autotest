# EXEC-13 残余收口：重试来源自连接的真实 SQL 覆盖（2026-11-06）

## 1. 缺口

EXEC-13 的展示链路已完成：后端 `GET /api/execution/runs/{runId}/retry-origin` → `RunRetryOrigin`
→ 前端日志面板「重试自 {runId} · 上次失败：{reason}」。但验证只到两处：

| 已有验证 | 覆盖到什么 |
|---|---|
| `RunControllerTest`（打桩服务） | 接口透传与 404，不碰 SQL |
| `RunsView.spec`（前端） | `attemptNo > 1` 时的渲染，不碰数据 |

`RunRepositoryJdbc.findRetryOrigin` 的自连接（`run r JOIN run p ON p.project_id = r.project_id
AND p.run_id = r.retry_of_run_id`）没有一条测试真正执行过。它恰好是「跨项目是否串数据」和
「`markAttempt` 写入是否被正确读回」两个风险点，属于 EXEC-13 备注里「本包只验读取与展示」的残留。

## 2. 做法

新增 `RetryOriginIT`（`@Tag("it")`），与 `TenantIsolationIT` 同一套真实 MariaDB 基建
（`DriverManagerDataSource` + Flyway `clean/migrate`，不使用 H2，ADR-0002；库名必须 `*_it`）。

三条用例：

| 用例 | 验证点 |
|---|---|
| 重试来源：自连接带出原运行的失败结论，attempt_no 一并落库 | `markAttempt` 写入 → `findRetryOrigin` 取回 `runId / FAILED / ENV / dut_offline / ping timeout / finishedAt`，且 `findByRunId.attemptNo = 2` |
| 首次尝试没有来源：返回空而不是报错 | `retry_of_run_id IS NULL` → `Optional.empty()` |
| 跨项目引用不拼接：B 的运行指向 A 的编号时查不到 | 真实 SQL 的 `project_id` 双限定生效，`findRetryOrigin(B, runB) = empty` |

生产代码未改动——本批是补验证，不改行为。

## 3. 门禁读数

| 项 | 结果 |
|---|---|
| `tools/subset_font.py` | PASS |
| 前端 `vitest run` | 28 文件 / 202 用例 |
| 前端 `vue-tsc --noEmit` | 0 错误 |
| 前端 `vite build` | `built in 8.66s` |
| 后端 `mvn -o -B test` | 600 / 0 / 0 |
| `scripts/lint.sh` | PASS |
| `check_release_docs.py` | PASS |
| `check_wbs_consistency.py` | PASS（96 包 / 待验 83 / 已完成 13） |
| `RetryOriginIT` | **3 / 0 / 0**（9.45s，Flyway v7.6） |

日志见 `01-subset-font.log` … `09-retry-origin-it.log`。

## 4. 仍未验

1. 端到端真跑「env 失败 → 重试 → 详情带出上次原因」仍需真实环境（DUT 侧失败），留 v1.0 验收。
2. 重试链仍是按行平铺，未做成组/时间线视图（PRD 未要求）。
3. `markAttempt` 的调用方（重试策略）仍由 `RetryPolicyTest` 单测覆盖，本批只验 SQL 读回。
