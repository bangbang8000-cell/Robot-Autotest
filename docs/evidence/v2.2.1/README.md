# v2.2.1 证据包（W3：故障转移）

> 日期 2026-11-07 ｜ 对应规划 `docs/plan/中期开发功能规划-20261107.md` §3 W3
> 结论：**W3 全部落地**。门禁 `bash scripts/gates.sh` **FAILS=0**（01–16 全 PASS，17 结构自检通过）；
> 前端全量 `43 files / 290 tests passed`；后端全量 **959 用例 0 失败 0 错误（4 skipped）**，
> 其中 W3 定向 `ShardFailoverTest 4 / ShardFailoverServiceTest 3 / ShardServiceTest 8` 全绿。
> 原始输出见同目录 `raw/`。

## 机制与落点

| 机制 | 行为 | 代码 |
|---|---|---|
| **节点失联判定** | 既有 `NodeHeartbeatMonitor`：心跳连续 3 个周期未达 → 节点 `OFFLINE` | `application/node/NodeHeartbeatMonitor.java`（W1 起已有） |
| **离线节点租约回收** | `@Scheduled` 每 15s（首个 25s）扫描 `OFFLINE` 节点，其 `LEASED` 分片判 `LOST` 并聚合受影响 run | `application/execution/ShardFailoverService.sweepOfflineNodes()` + `ShardService.releaseLeasesOfNodes()` |
| **LOST 重派** | `claimNext` 候选扩为 `PENDING` / 过期 `LEASED` / `LOST` 且 `attempt < maxAttempts`（默认 3）；抢到时 `attempt+1`、`fencing_token+1` | `ExecutionShardRepositoryJdbc.CLAIMABLE`、参数 `shard.max-attempts` |
| **重试态聚合** | 存在可重派 LOST 时聚合状态为 **`RETRYING`**，不动 run；耗尽尝试次数后 LOST 视为终态 → run `FAILED` | `ShardService.settled()/retryPending()/aggregateState()` |
| **主控重启恢复** | `ApplicationReadyEvent` 时把上一进程全部 `LEASED` 判 `LOST`（无遗留则零写入，重复调用幂等） | `ShardFailoverService.recoverOnRestart()` → `ShardService.releaseAllLeasesOnRestart()` |
| **与 NODX 判死协同** | 心跳判死 run 的 SQL 加 `NOT EXISTS`：仍有 `PENDING/LEASED/LOST` 分片的 run **不判死**，交给重派 | `ExecutionNodeRepositoryJdbc.failRunningRunsByNodes` |
| **前端呈现** | 聚合状态新增 `RETRYING` → 文案「等待重派」/「Awaiting re-dispatch」，warn 色，继续轮询 | `ShardBoard.vue`、`zh-CN.ts`、`en-US.ts` |

## 4 类故障注入断言（`ShardFailoverTest`，全部在假仓储上重放真实服务逻辑）

| # | 故障 | 断言 |
|---|---|---|
| 1 | 节点失联 → 分片判 LOST → 重派 | 重派后 `attempt=2`、`fencing_token=2`；旧 token 写入 409；两节点共执行 4 条**无重复无丢失**；聚合 `DONE` 且 `updateCounts(RUN,4,4,0,...)` + `transition(FINISHED)` |
| 2 | 租约超时（节点未失联） | 到期后重派，只多一次尝试，不重复执行 |
| 3 | 主控重启回收 | 第一次 `affected=1`（run 变 `RETRYING`）、第二次 `affected=0`（幂等） |
| 4 | 健康节点不被误抢 | OFFLINE 扫描 `affected=0`，其分片仍 `LEASED` |

另：`ShardFailoverServiceTest` 3 项验证只对 `OFFLINE` 节点下发回收、全健康时零调用、启动事件正确委派。

## 验证过程中发现并修掉的两个真实缺陷

1. **全局回收 SQL 触发租户守卫，直接导致应用启动失败**（门禁 05 红：
   `RunLogWebSocketE2ETest` context 加载失败）。根因：跨项目 sweep 的 `UPDATE` 未含 `project_id`，
   `TenantGuard.requireProjectScope` 抛 `IllegalStateException`。
   修复：两处回收 SQL 的 `WHERE` 补 `AND project_id > 0`（与既有跨项目查询口径一致，
   守卫口径是「SQL 文本必须出现 project_id」），行为不变。
2. **启动恢复异常会拖垮启动**：`ApplicationReadyEvent` 监听器抛异常会让整个 Spring 上下文失败。
   修复：`recoverOnRestart()` 捕获 `RuntimeException` 并记 `ERROR`，服务继续启动
   （租约到期后仍会被自然回收），恢复失败不再等于服务不可用。

这两个问题只有「真的让应用启动一次」才会暴露 —— 定向单测全绿时仍然存在，已作为门禁价值样例记录。

## 门禁读数

| 项 | 读数 |
|---|---|
| `bash scripts/gates.sh` | `GATES_RESULT FAILS=0`（`raw/gates-w3.txt`） |
| 后端全量 | 959 tests / 0 failures / 0 errors / 4 skipped（`raw/backend-w3-full.txt`，完整日志 `raw/backend-w3-full-log.txt`） |
| W3 定向 surefire | ShardFailover 4 / ShardFailoverService 3 / ShardService 8 / ShardPolicy 6 / TwoNode 3 / ShardController 4 / NodeShardEndpoint 5（`raw/backend-w3-surefire.txt`） |
| 前端全量 vitest | 43 files / 290 tests passed（`raw/frontend-vitest-w3.txt`） |

## 复跑命令

```bash
cd ~/robot-autotest
set -a && . ~/.config/robot-autotest/env && set +a
bash scripts/gates.sh                                   # 期望 GATES_RESULT FAILS=0
cd backend && mvn -o test -Dtest='ShardFailoverTest,ShardFailoverServiceTest,ShardServiceTest,ShardTwoNodeEquivalenceTest'
cd ../frontend && npx vitest run src/components/ShardBoard.spec.ts src/views/RunsView.spec.ts
```
