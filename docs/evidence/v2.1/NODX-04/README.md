# NODX-04 证据：节点页四态 / 离线时长 / 最近任务 / agentBaseUrl

## 判据（DEV-PLAN §10.13，引用 TEST-PLAN TC-v2.1-25/27）

四态齐全且前端不推断；离线时长正确；最近任务展示；agent 接入地址随注册表单可填；
离线口径（**拒绝发起，不排队**）写入 DEV-PLAN §5.3 并被 TEST-PLAN 引用。

## 结论

### HTTP 层：8 项全 PASS（`run.log`，2026-11-07）

| # | 检查 | 结果 |
|---|------|------|
| 01 | GET /api/nodes 每行带 agentBaseUrl / lastRun 新字段 | PASS |
| 02 | 最近任务读数真实：lastRun.runId + 合法状态 + 时间（rows_with_lastRun=3） | PASS |
| 03 | 注册节点带 agentBaseUrl（HTTP 200） | PASS |
| 04 | agent_base_url 真实落库（mysql 核对 http://127.0.0.1:8099） | PASS |
| 05 | NEW 节点 lastHeartbeatAt=null（前端只显占位 —，不伪造「离线」） | PASS |
| 06 | NEW 节点发起 → 409 当场拒绝，文案含「NEW」「不回落本机执行」 | PASS |
| 07 | 不排队不重投：拒绝发生在落库前（run rows=0） | PASS |
| 08 | disable 返回体保持新字段（字段级回归） | PASS |

离线口径三态拒绝与崩溃判失败由既有证据覆盖引用：NODX-02 run.log（NEW/OFFLINE/
DISABLED 三类拒绝）、NODX-03 run.log（node_agent_lost 不留悬挂 run）。

### 浏览器层：4 张截图 + 4 项检查全 PASS（`capture.log`、`shots/`、`capture-result.json`）

| 检查 | 结果 | 证据 |
|------|------|------|
| S1 1440 列表：状态列/最近任务列/心跳相对时长真实渲染（rows=7，lastRun0=`RUN-… · 失败`，hb0=`1 小时前`） | PASS | shots/nodes-list-1440.png |
| S2 注册表单：agent 接入地址输入 + 提示「节点上 agent 的 HTTP 入口；留空表示暂不参与分布式执行」 | PASS | shots/nodes-register-form.png |
| S3 令牌一次性弹窗：明文 64 hex 只显示一次 | PASS | shots/nodes-token-once.png |
| S4 1280 宽无横向滚动（scrollWidth=1280 = clientWidth，TC-v2.1-31） | PASS | shots/nodes-list-1280.png |

### 视图单测

`NodesView.spec.ts` 7 条全绿：四态原样显示（NEW 不被前端推断成 OFFLINE）、令牌
不入 localStorage、停用/启用分支、agentBaseUrl 随注册提交、离线时长相对时间
（无心跳只显占位）、最近任务 runId + 本地化状态（`enum.runStatus.*`）。

## 实现（对应 DEV-PLAN §10.13「改/做」）

- 后端：`ExecutionNodeRepository(+Jdbc).latestRunsByNode()`（平台级 SQL，与
  runningRunsByNode 同口径豁免注释）；`ExecutionNodeService.NodeView` 加
  `agentBaseUrl` 与 `lastRun`（LastRunView）；后端 882 条测试全绿。
- 前端：`NodesView.vue` 注册表单加 agentBaseUrl 输入（可空）、四态着色
  （is-online/is-offline/is-disabled，只着色不推断）、心跳列相对时长
  （刚刚/x 分钟前/x 小时前/x 天前，title 留绝对时间）、最近任务列
  （runId · enum.runStatus 状态）；双语言包词条；`api/nodes.ts` 类型同步。
- 文档：DEV-PLAN §5.3 补「离线口径」（非 ONLINE 一律当场拒绝——不排队、不重投、
  不回落本机执行），TEST-PLAN TC-v2.1-25/27 判据同步引用。

## 踩坑记录（已修复并复验）

1. **截图拍的是旧 bundle**：:8001 服务的是构建产物，改完 NodesView 不 `npm run build`
   截图取证全是旧行为（新列/新输入不存在）——先 build 再拍。
2. **NInputNumber 也渲染 `<input>`**：spec 里按 `.form-card input` 序号取输入框会拿错，
   按 placeholder 定位才稳。
3. **截图脚本重名注册 409**：令牌弹窗演示节点用时间戳后缀命名，脚本可重复执行。
4. **i18n 词条段**：运行状态词条在 `enum.runStatus`（非顶层 runStatus），组件里
   `te()` 探测 + 回退原始 state。

## 复跑方式

```bash
set -a; . ~/.config/robot-autotest/env; set +a
cd ~/robot-autotest && python3 docs/evidence/v2.1/NODX-04/run.py
cd ~/robot-autotest/frontend && npm run build
node docs/evidence/v2.1/NODX-04/capture.mjs
```
