# v2.2 证据包（W2：分布式执行分片）

> 日期 2026-11-07 ｜ 对应规划 `docs/plan/中期开发功能规划-20261107.md` §3 W2
> 结论：**W2.1–W2.6 全部落地**。门禁 `bash scripts/gates.sh` **FAILS=0**（16 个 run 步骤全 PASS，
> 第 17 步结构自检通过）；前端全量 `43 files / 290 tests passed`；后端全量 **959 用例 0 失败 0 错误
> （4 skipped）**；迁移往返 TC-32 在并入 V9_6 后 **PASS（业务表 67）**；`db-audit` 7 项 PASS。
> 原始输出见同目录 `raw/`。

## 逐包结论

| 包 | 实现 | 证据 |
|---|---|---|
| **W2.1 建表** | `db/migration/V9_6__execution_shard.sql` + `db/down/...down.sql`：`execution_shard` 17 业务列 + 审计四列；唯一键 `uk_shard_run_index`、索引 `idx_shard_state/node/run` | `raw/migration-roundtrip.txt`（`TC-v2.1-32: PASS`）、`raw/db-audit.txt`（7 项 OK） |
| **W2.2 租约与 fencing** | `ExecutionShardRepositoryJdbc.claimNext`：候选 `PENDING` / 过期 `LEASED`，CAS `UPDATE ... WHERE state=? AND lease_owner=? AND fencing_token=?`；抢到后 `fencing_token+1`、`lease_until=now+300s`；旧 token 写入被 409 拒绝 | `raw/backend-w2-surefire.txt`（ShardService 8 / ShardController 4 / NodeShardEndpoint 5） |
| **W2.3 分片策略** | `ShardPolicy` 按**套件**为最小单位贪心装箱，节点上限默认 4（`shard.max-nodes`） | `raw/backend-w2-surefire.txt`（ShardPolicyTest 6） |
| **W2.4 分片 API** | `GET /api/execution/runs/{runId}/shards`、`POST .../shards/plan`、`POST .../shards/aggregate`；节点侧 `POST /api/nodes/{id}/shards/claim`、`/complete`（`X-Node-Token` 鉴权，DISABLED → 403） | 同上 |
| **W2.5 分片看板** | `frontend/src/components/ShardBoard.vue`（状态/进度/租约倒计时/重试角标，3s 轮询）+ `src/api/shards.ts` + 中英 i18n；嵌入运行详情 | `raw/frontend-vitest-w2.txt`（ShardBoard 6 + RunsView 16 = 22 项定向） |
| **W2.6 两节点等价** | 两个执行节点各领一片、互不重叠；各自完成后聚合为 `DONE`；执行并集无重复无缺失 | `raw/backend-w2-two-node.txt`（`coverage: executed=8 distinct=8 duplicates=0 missing=0`） |

## 聚合口径（W2 版，W3 起再加 RETRYING）

- `settled(rows)` = 全部分片终态（DONE/FAILED）且无「还能重派」的 LOST。
- 未 settled 时 `aggregateInternal` 只做 `expireLeases`，**不动 run 状态**，避免把还在重试的 run 提前判死。
- settled 后按「有 FAILED → FAILED，否则 DONE」回写 `updateCounts` 与 `transition`。

## 诚实标注：两节点等价是「进程内假 agent」

`ShardTwoNodeEquivalenceTest` 用两个 node-id（11/12）在**同一 JVM** 内共享假仓储模拟领片，
**不是物理双机**。它证明的是分片语义（唯一性、覆盖完整、聚合终态、均衡），
**不证明跨主机网络、节点时钟偏差与并发争抢**。物理双机验证需要第二台可执行节点，
当前环境不具备，已记入 W4/W5 的后续验证项，不作为本波结论。

## 门禁读数

| 项 | 读数 |
|---|---|
| `bash scripts/gates.sh` | `GATES_RESULT FAILS=0`（01–16 全 PASS，17 structure_selfcheck 通过） |
| 前端全量 vitest | 43 files / **290 tests passed**（`raw/frontend-vitest-w2.txt`） |
| 后端全量 | 959 tests / 0 failures / 0 errors / 4 skipped |
| 迁移往返 TC-32 | `PASS（up → down → up + 备份恢复全部符合判据）`，业务表 67 |
| 两节点等价 | `[two-node] coverage=8/8 duplicates=0 missing=0`、`aggregate: DONE`、`balance=[4,4]` |

## 复跑命令

```bash
cd ~/robot-autotest
set -a && . ~/.config/robot-autotest/env && set +a
bash scripts/gates.sh                                   # 期望 GATES_RESULT FAILS=0
cd backend && mvn -o test -Dtest='ShardPolicyTest,ShardServiceTest,ShardTwoNodeEquivalenceTest,ShardControllerTest,NodeShardEndpointTest'
cd ../frontend && npx vitest run src/components/ShardBoard.spec.ts src/views/RunsView.spec.ts
cd .. && bash scripts/v21/migrate-roundtrip.sh          # 期望 TC-v2.1-32: PASS（67 业务表）
```
