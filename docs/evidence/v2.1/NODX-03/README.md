# NODX-03 证据：节点 agent 分布式执行与崩溃自愈

## 判据（DEV-PLAN §10.12）

同参数命令 **diff 为空**；产物完整回传；节点崩溃不留悬挂 run。

## 结论：14 项全 PASS（`run.log`，2026-11-07 01:45）

| # | 检查 | 结果 |
|---|------|------|
| 01 | 注册节点并登记 agentBaseUrl（HTTP 200，id=26） | PASS |
| 02 | V9_5 迁移：agent_base_url 真实落库（http://127.0.0.1:8082） | PASS |
| 03 | agent 心跳保活 → 节点 ONLINE（同 jar node profile，:8083） | PASS |
| 04 | 指定节点发起 run（HTTP 200） | PASS |
| 05 | 节点真实执行 → run FINISHED | PASS |
| 06 | 回传后主控解析入库：test_result 行存在 | PASS |
| 07 | **同参数命令 diff 为空**（kernel_cmd_diff.py，8 参数归一化一致） | PASS |
| 08 | **产物完整回传**：output.xml 落主控产物目录（size=1460） | PASS |
| 09 | 崩溃注入准备：第二节点 ONLINE | PASS |
| PROBE | :8083 agent HTTP 层可服务（注入前探测） | PASS |
| 10 | run 到执行态（RUNNING）后 killpg 注入崩溃 | PASS |
| HEARTBEAT | kill 后心跳停止（last_heartbeat_at 不再更新） | PASS |
| 11 | **崩溃不留悬挂 run**：监视器判 FAILED，reason=`node_agent_lost: 节点心跳连续 3 个周期缺席，agent 已判离线，本次运行终止（不回落本机）` | PASS |
| 12 | 崩溃节点被监视器置 OFFLINE | PASS |

## 链路

真实 HTTP（urllib）+ 真实节点进程（Popen 同 jar `--spring.profiles.active=node` :8083）
+ 真实 RF 执行（pabot argv 由节点侧 KRN 生成）+ mysql 直查取证
（`~/.config/robot-autotest/env` 凭据）。

1. 主控按 nodeId 投递 `NodeTask` JSON（HMAC-SHA256 签名，key=sha256(token)）到节点 agent。
2. 节点用与主控相同参数（`findForRunner` + KRN referenceCommand）build pabot argv 并执行。
3. 执行完节点把 output.xml / node-cmd-agent.json / 产物（base64）POST 回主控
   `/api/agent/result`（X-Node-Token 头，主控侧核验令牌哈希 + run.node_id 归属）。
4. 主控 `NodeResultService` 落盘 + `parseAndIngestForAgent` 解析入库。

## 崩溃自愈判据

- 节点 agent 进程被 `killpg` 杀死后心跳停止（HEARTBEAT：last_heartbeat_at 值不再变化）。
- `NodeHeartbeatMonitor`（主控侧）发现 stale ONLINE → 先 `failRunningRunsByNodes`
  把该节点在跑 run 判 FAILED（reason=`node_agent_lost`），再 markStaleOffline 置 OFFLINE。
- 不排队、不重投、**不回落本机执行**。

## 单元测试

`NodeAgentRunnerTest` 等 24 条节点相关单测全绿（验签正负、执行委托 nodeId 置空防
递归、回传完整、异常回传 node_agent_execute_failed）。

## 踩坑记录（已修复并复验）

1. **主控 HttpClient 复用 stale keep-alive 连接**：节点进程重启后主控侧连接池里的旧
   连接已失效，投递报 `header parser received no bytes`。修复：NodeChannel 网络失败
   重试时新建 HttpClient（`retryHttpClient()`）；run.py 投递前探测 agent HTTP 就绪
   （PROBE），`node_dispatch_failed` 且节点 ONLINE 时自动重发一次。
2. **主控与节点双重 PREPARING 迁移撞状态机**：`execute` 开头加幂等预查
   （state != PREPARING 才迁移）。
3. **EXEC-05 口径**：robot_file 是脚本真源，materialize 只落库内脚本；端到端 seed
   直插 robot_file，写磁盘 workspace-root 不参与。
4. **RF / pabot 命令等价**：节点侧不自己拼命令，复用主控 KRN 同参数 build，
   `kernel_cmd_diff.py` 归一化绝对路径后逐参数 diff 为空。

## 复跑方式

```bash
set -a; . ~/.config/robot-autotest/env; set +a
cd ~/robot-autotest && python3 docs/evidence/v2.1/NODX-03/run.py
```
