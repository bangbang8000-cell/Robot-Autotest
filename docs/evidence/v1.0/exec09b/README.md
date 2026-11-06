# EXEC-09 残余收口：租约等待耗时回读（2026-11-06）

## 1. 缺口

EXEC-09 已把「等待资源」做成可见状态，`lease_wait_ms` 也在成功 / 超时 / 异常三条路径落库，
但只是**写**进去了：

| 已有验证 | 覆盖到什么 |
|---|---|
| `RunControllerTest#pendingActivity` | `PREPARING` + 租约在等待时返回 `waiting`，不碰耗时 |
| `RunsView.spec` 等待资源 | 渲染「等待资源」文案，看不出已经等了多久 |
| `RunExecutionServiceTest` 5 条 | 执行期持有 / 释放 / 超时不启动；只断言 `recordLeaseWait` 被调用 |

结果是：报告与界面能回答「在等」，回答不了「等了多久」，`run.lease_wait_ms` 成了只写不读的字段。

## 2. 做法

### 后端

| 位置 | 改动 |
|---|---|
| `RunRepository` | 新增 `default Optional<Long> findLeaseWaitMs(projectId, runId)`（默认空实现，不动内存仓储与既有测试） |
| `RunRepositoryJdbc` | 覆盖实现：`SELECT lease_wait_ms FROM run WHERE project_id = ? AND run_id = ?`，`TenantGuard` 校验；`wasNull()` → `empty`，**NULL 不读成 0 秒** |
| `LeaseWaitRegistry` | 新增 `elapsed(runId)`：仍在登记时返回 `now - since` 毫秒，不注销登记 |
| `RunService` | 新增 `leaseWaitTotalMs(projectId, runId)`（归属 + `execution:read` 校验后回读落库值）与 `record LeaseWait(state, elapsedMs, totalMs)` |
| `RunController` | 新增 `GET /api/execution/runs/{runId}/lease-wait`：等待中 `state=waiting` + 实时 `elapsedMs`（`totalMs=null`）；结束后 `state=settled`，`elapsedMs` 回落到落库总耗时 |

### 前端

| 位置 | 改动 |
|---|---|
| `api/execution.ts` | 新增 `LeaseWait` 类型与 `leaseWait(projectId, runId)` |
| `views/RunsView.vue` | `pending.state === 'waiting'` 时补一次 `leaseWait` 请求，等待条显示「等待资源 · 已等待 N 秒」；切运行 / 结束 / 请求失败时一并清空 |
| `locales/zh-CN.ts`、`en-US.ts` | 新增 `execution.leaseWaitElapsed`（`已等待 {seconds} 秒` / `{seconds}s elapsed`），键集保持 648 相等 |

### 测试

| 用例 | 验证点 |
|---|---|
| `LeaseWaitRegistryTest` | 等待中 `elapsed()` 随秒推进；结束后归零，重复 `end` 不叠加 |
| `RunControllerTest#leaseWait_耗时回读` | 等待中 `waiting/4200/null`；结束后 `settled/6500/6500` |
| `RunsView.spec` 等待资源 | 调 `leaseWait(1,'RUN-WAITING')` 并渲染「已等待 7 秒」（7400ms 取整） |
| `LeaseWaitIT`（真实 MariaDB） | 未回写返回 `empty`；回写 2500 / 0 都能回读（**0 秒 ≠ NULL**）；跨项目查不到 |

## 3. 门禁读数

| 项 | 结果 |
|---|---|
| `tools/subset_font.py` | PASS |
| 前端 `vitest run` | 28 文件 / 202 用例 |
| 前端 `vue-tsc --noEmit` | 0 错误 |
| 前端 `vite build` | `built in 8.59s` |
| 后端 `mvn -o -B test` | 601 / 0 / 0 |
| `scripts/lint.sh` | PASS |
| `check_release_docs.py` | PASS |
| `check_wbs_consistency.py` | PASS（96 包 / 待验 83 / 已完成 13） |
| `LeaseWaitIT` | **3 / 0 / 0**（Flyway v7.6） |

日志见 `01-subset-font.log` … `09-lease-wait-it.log`。

## 4. 仍未验

1. 实时性受轮询间隔限制：界面 5 秒拉一次，秒数按拉取时刻跳变，未做前端本地计时器叠加。
2. 跨进程不生效：`LeaseWaitRegistry` 是进程内登记，后端重启后等待中的 run 只能读库（此时通常为 `null`，界面退回只显示「等待资源」）。
3. `lease_wait_ms` 仍未进入运行详情 / 对外报告，只用于等待态展示；若后续要出「本 run 等待了多久」的统计，需另加读取方。
