# ORCH-01 证据 · 计划组与批量建边

- **执行时间**：2026-11-06 20:26（GMT+8）
- **链路**：真实 HTTP（`:8001` 统一入口 → `:8081`）→ 真实 MySQL `robot_dev`（V9_1 已应用）
- **结论**：**通过**。8 项断言全 PASS；单测 8 条全绿；`db-audit` 7 项 PASS。

## 1. 命令原文

```bash
cd ~/robot-autotest
# 单测（内存伪仓储，验证链式语义与环检测）
cd backend && mvn -o -B test -Dtest=PlanGroupServiceTest
# 迁移（真实库建表）
cd .. && bash scripts/db-migrate.sh
# 真实 HTTP 验收（需后端跑含本包代码的 jar）
python3 docs/evidence/v2.1/ORCH-01/run.py
```

## 2. 判据逐条核对

| 判据（DEV-PLAN §10.2） | 实测 | 结论 |
|---|---|---|
| 3 计划成组产生 2 条边 | `created=2`，库内 `execution_plan_dependency` 计数 = 2 | 通过 |
| 重复加入幂等 | 二次加同一批成员：`items` 仍为 3，无报错 | 通过 |
| 成环被拒且错误含计划名 | HTTP 400，`组内顺序与既有依赖成环: orch01-chain-a → orch01-chain-b → orch01-chain-a` | 通过 |
| 跨项目 403 | HTTP 403，`计划属于其它项目: 1` | 通过 |
| 迁移可回退 | `DB_NAME=robot_it bash scripts/db-undo.sh` 倒序执行到 `V1__baseline.down.sql`，剩余表仅 `flyway_schema_history`；随后 clean+migrate 恢复 62 表 | 通过 |
| db-audit 7 项 | `业务表数 = 62（62）` … 全部 OK | 通过 |

**额外一条（不在原判据里，但语义容易做错）**：删组后**边必须保留**——边是计划自身配置，删组不应改变已有计划的调度行为。实测删组后组数 0、边数仍 2（`ORCH01-08`）。

## 3. 原始输出

```
# 夹具项目 projectId=29，已登录 qa-e2e-admin
# 计划 id = [2, 3, 4]

PASS  ORCH01-01   建组返回 200                        HTTP 200
PASS  ORCH01-02   加入 3 个成员后 items=3，edgeCount=2   HTTP 200 items=3 edgeCount=2
PASS  ORCH01-03   重复加入不报错、成员数仍为 3                 HTTP 200 items=3
PASS  ORCH01-04   首次应用 created=2 且库内边数=2          HTTP 200 created=2 skipped=0 库内边数=2
PASS  ORCH01-05   二次应用 created=0 skipped=2 且边数不变  HTTP 200 created=0 skipped=2 库内边数=2
PASS  ORCH01-06   成环返回 400 且消息带计划名                HTTP 400 message=组内顺序与既有依赖成环: orch01-chain-a → orch01-chain-b → orch01-chain-a
PASS  ORCH01-07   跨项目计划返回 403                     HTTP 403 message=计划属于其它项目: 1
PASS  ORCH01-08   删组后组不存在但边保留（2 条）                HTTP 200 组数=0 边数=2

合计 8 项，FAIL 0 项
```

单测：

```
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- PlanGroupServiceTest
```

## 4. 设计决定（后续维护要看）

| 决定 | 理由 | 反例（如果做成另一种） |
|---|---|---|
| 组**不建第二套图**，边落 `execution_plan_dependency` | 图能力已有（V6_2 + `PlanDependencyGraph`），再建一套会让调度器与图视图各读一份 | 组内独立存边 → 调度器看不到，计划跑不起来 |
| 应用是**追加**而非覆盖 | 用户手工配的组外依赖不能被一次「应用」抹掉 | 覆盖 → 跨组编排一次就丢依赖 |
| 环检测是**整图**而非只看组内 | 跨组的环最容易漏（组 A 的顺序 + 组 B 的顺序合起来才成环） | 只看组内 → 真环写进库，调度器触发时死循环 |
| 删组**不删边** | 边属于计划，删除编排视角不该改计划行为 | 级联删边 → 删组后计划调度顺序静默变化 |
| `INSERT IGNORE` 做幂等 | 并发下「先查再插」的查不可信 | 先查 → 并发双插撞唯一键报 500 |

## 5. 遗留

| # | 事项 | 影响 | 归属 |
|---|---|---|---|
| 1 | 组内顺序调整后**不会自动重算边**：改顺序要重新点「应用」，旧边不会自动删 | 用户改顺序后若忘记应用，调度仍按旧链 | 已写入 `planGroups.chainHint` 文案；是否需要「自动同步」留待 ORCH-02 评估 |
| 2 | `execution_plan_dependency` 没有组来源标记 | 无法区分「手工边」与「组推出来的边」，删组后无法一键回收 | 影响可控（组删除本就不回收边）；若后续要做「撤销应用」需加 `source_group_id` |
| 3 | `db-audit` 的业务表数期望值已从 60 改为 62 | 这是门禁随 schema 演进而更新，不是放宽 | 已在脚本注释写明 v2.1 的 V9_1 加 2 表 |

## 6. 文件

| 文件 | 内容 |
|---|---|
| `run.py` | 验收脚本（幂等，可反复跑；夹具在 `qa-e2e-fixture` 项目内） |
| `run.log` | 本次运行的完整输出 |
