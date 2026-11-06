# NODX-01 证据：节点注册与心跳（FR-v2.1-13/14）

## 结论

- **真实 HTTP 验收 10 项全 PASS**（`run.log`，退出码 0）：注册只存哈希、明文不入
  16MB 后端日志、心跳自认证、错令牌 401 防枚举、Monitor 真实把缺心跳节点打成
  OFFLINE、停用节点拒绝心跳。
- **后端单测 10 条全绿**：`ExecutionNodeServiceTest` 7 + `NodeHeartbeatMonitorTest` 3。
- **前端组件 4 条全绿**（`NodesView.spec.ts`）：四态原样显示（NEW 不被前端推断成
  OFFLINE）、明文令牌只出现一次且不写 localStorage、停用/启用分支正确。

## 判据对照（DEV-PLAN §4.5）

| 判据 | 证据 |
|---|---|
| 3 次缺心跳转 OFFLINE | NODX01-07：`last_heartbeat_at` 回拨 10 分钟（≈20 个周期）→ @Scheduled Monitor 真实扫描 → 库与列表同时 OFFLINE；单测锁死阈值公式 = now − 3×周期 |
| 令牌不入日志、不入库明文 | NODX01-02（库内 hash = SHA-256(明文)）+ NODX01-03（16MB 日志全文无明文，抽样落 `out/log-sample.txt`） |
| 节点列表显示内核能力与占用 | NODX01-06：rf/pytest 能力 + maxConcurrency + lastHeartbeatAt；并发占用（runningRuns）在 NODX-02 接上 run.node_id 后显示 |

## 命令

```bash
bash scripts/db-migrate.sh                # 65 → 66 张表
cd backend && mvn -o -B test -Dtest=ExecutionNodeServiceTest,NodeHeartbeatMonitorTest
mvn -o -B -q package -DskipTests && bash /tmp/restart-backend.sh
cd frontend && npx vitest run src/views/NodesView.spec.ts
python3 docs/evidence/v2.1/NODX-01/run.py
```

## 原始输出

| 文件 | 内容 |
|---|---|
| `run.log` | 10 项断言完整输出，最终 FAIL 0 |
| `out/log-sample.txt` | 后端日志中含「node」的行抽样（供人工复核无令牌） |

## 设计口径（写死，改动须回改文档）

1. **令牌只存哈希**：SHA-256（64 hex）。令牌是 32 字节随机数（SecureRandom），高熵
   所以不适用 bcrypt（那是口令防爆破的慢哈希场景）。明文只在注册响应出现一次。
2. **心跳自认证**：`X-Node-Token` 头，服务端比哈希。`WebConfig` 把
   `/api/nodes/*/heartbeat` 加进 AuthInterceptor 白名单（agent 无用户会话）；
   注册/列表/停启用仍走管理员会话。
3. **错令牌与不存在的节点统一 401、文案「节点令牌无效」**——防枚举。
4. **状态四态**：NEW（注册了还没 agent 心跳过）/ ONLINE / OFFLINE（连续 3 周期缺席，
   Monitor 置位）/ DISABLED（手动停用，心跳 403，不能「复活」；启用后回 NEW）。
5. **Monitor 只把 ONLINE 置 OFFLINE**：NEW 是「还没部署」，和「挂了」是两回事，
   界面必须能区分（NODX-04 四态之一）。
6. **阈值公式**：`now − heartbeat-interval-seconds × offline-after-misses`
   （默认 30s × 3 = 90s），两个参数均可配置；Monitor 每 15s 扫一次。
7. **节点是平台级资源**：`execution_node` 无 project_id，有意不走 TenantGuard
   （豁免写在接口与迁移注释里）；`run.node_id` 列在本迁移顺带加好（NODX-02 用）。

## 遗留

- 指定节点执行与离线拒绝归 NODX-02；节点页的离线时长 / 最近任务 / 四态截图归 NODX-04。
- 心跳接口暂未做限流（内网自用，令牌错无限重试的收益为零）；若对外开放需补滑动窗口。
