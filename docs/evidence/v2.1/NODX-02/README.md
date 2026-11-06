# NODX-02 · 指定节点执行与离线拒绝

- **判据（DEV-PLAN §10.11）**：离线节点发起被拒且原因明确（不回落本机）；未指定走本机；并发独立计数。
- **证据形态**：接口测试（正/负）+ 派发层单测。

## 1. 命令

```bash
# 真实 HTTP 验收（正/负 12 项）
set -a; . ~/.config/robot-autotest/env; set +a
python3 docs/evidence/v2.1/NODX-02/run.py

# 派发层单测（正/负 + 并发独立 + NodeChannel 出站纪律）
cd backend && mvn -o -B test -Dtest="RunDispatchNodeTest,NodeChannelTest"
```

## 2. 原始输出

- `run.log`：真实 HTTP 12 项 **PASS 12 / FAIL 0**。
- `out-unit.txt`：`RunDispatchNodeTest` 6 条 + `NodeChannelTest` 4 条全绿。
- `out/nodes-view.json`：`GET /api/nodes` 原始响应（每行含 `runningRuns` 占用读数）。

## 3. 结论（逐条对判据）

| 判据 | 证据 | 结果 |
|---|---|---|
| 离线节点发起被拒且原因明确、不回落本机 | NODX02-06/08/09：NEW → 409「尚未心跳上线（NEW）；不回落本机执行」；OFFLINE → 409「节点离线（OFFLINE，最后心跳 …）；不回落本机执行」；DISABLED → 409「节点已停用（DISABLED），先由管理员启用」；NODX02-07：拒绝发生在落库前（`run_request_id` 无 run 行） | PASS |
| 未指定走本机 | NODX02-11：不带 `nodeId` 发起 200，`run.node_id IS NULL`；本机执行路径未改（`RunDispatcher.submit` 行为与 v2.0 等价，`ProjectRunDispatcher` 只是内部道键从裸 id 换成 `p<id>` 字符串） | PASS |
| 并发独立计数 | `RunDispatchNodeTest.并发独立计数`：本机道占满（max-parallel=1 + 阻塞任务）时节点道任务照常并行执行；节点道（并发 1）占满时本机任务不受影响。节点道池大小 = `execution_node.max_concurrency` | PASS |
| `nodeId` 参数 + `run.node_id` 落库 | NODX02-01/02：`POST /api/execution/runs` 带 `nodeId` → 200，库内 `run.node_id` = 指定节点；NODX02-03：`RUN_CREATE` 审计含 `node=<id>` | PASS |
| 节点执行体不落本机 pabot | NODX02-04：指定节点的 run 由 `RunExecutionService.executeOnNode` 处理——判 FAILED（`node_channel_unavailable`），文案明确「不回落本机执行」。**NODX-03 把该主体换成 NodeChannel 真实投递 + 产物回传** | PASS |
| 节点不存在 | NODX02-10：404「执行节点不存在: 99999」 | PASS |
| 节点页占用读数（§10.10 修订推迟项） | NODX02-12：`GET /api/nodes` 每行带 `runningRuns`（按 `run.node_id` 聚合 `run_status='running'`） | PASS |
| NodeChannel 出站纪律（§5.3） | `NodeChannelTest`：统一超时 10s / 网络失败重试 1 次 / HTTP 拒绝不重试 / 失败即 UNREACHABLE；本机路径不经过该类 | PASS |

## 4. 踩过的坑（给下个包留）

1. **Spring 构造选择**：`RunService` 三个构造里 `@Autowired` 若留在 7 参便捷构造上，
   生产装配会拿到 `NO_NODES` 桩——症状是「刚注册的节点发起时报不存在」。
   新构造加好后 `@Autowired` 必须挪到全参主构造；HTTP 验收第一轮抓到（单测直接手写构造，抓不到）。
2. **验收脚本路径**：执行域接口前缀是 `/api/execution`（`RunController` 类级映射），
   不是 `/api/runs`；404 文案「路径不存在：api/runs」来自 `GlobalExceptionHandler`。
3. **`failureReason` 只存原因码**（如 `node_channel_unavailable`），人类可读文案在
   `failureMessage`；断言要分别取字段。
4. **失败迁移清理要三件套**（NODX-01 遗留教训，本包复用）：列 + 表 + history 一起回滚。

## 5. 实现清单

- `RunDispatcher.submitToNode`（default 抛不支持）+ `ProjectRunDispatcher` 节点道
  （道键 `n<nodeId>`，池大小 = 节点并发上限；本机道键 `p<projectId>` 语义不变）。
- `RunService.CreateRunCommand` 加 `Long nodeId`（旧 12 参构造委托，8 处既有调用点零改动）；
  `insertRun` 落库前校验四态（非 ONLINE 一律 CONFLICT + 原因）、落库后 `assignNode`。
- `RunRepository.assignNode`（default 抛不支持）+ `RunRepositoryJdbc`（`UPDATE run SET node_id`
  带 `project_id` 租户条件，过 `TenantGuard`）。
- `QuickRunService.dispatch` 节点分支（查节点并发上限 → `submitToNode`）。
- `RunExecutionService.executeOnNode`：NODX-02 占位（判 FAILED 不落本机），NODX-03 实装。
- `NodeChannel`（application/node/）：出站统一超时 10s、重试 1 次、失败即 UNREACHABLE。
- `ExecutionNodeRepository.runningRunsByNode` + Jdbc 聚合（平台级统计，豁免注释在方法上）；
  `NodeView.runningRuns` → 前端 `NodesView` 「在跑任务」列 + 双语言包。
- 单测：`RunDispatchNodeTest`（execution 包，6 条）、`NodeChannelTest`（4 条）；
  既有 `ExecutionNodeServiceTest` / `NodeHeartbeatMonitorTest` 的伪仓储补 `runningRunsByNode`。
