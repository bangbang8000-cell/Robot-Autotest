# A6 · 入口 / 部署回滚 / WebSocket 实测证据

> 对应 06 计划 §3 **A6**：`WS 101 握手实测` + `deploy → health → rollback → health` 一次演练。
> 对应 TEST-PLAN **TC-v2.0-66**（8001 调 `/api/health`、`/healthz`、建 WS）与 **TC-v2.0-67**（部署→健康→回滚→再检查）。
> 授权口径（2026-11-06 拍板第 3 条）：**dev 库演练，前后强制 `mysqldump`，只回本次写入**。
> 执行时间：2026-11-07 13:14–13:16（CST）。执行环境：WSL2 Ubuntu-24.04 `~/robot-autotest`，统一入口 `:8001`，后端 `:8081`。

## 1. 判据与结论

| 判据（06 计划原文） | 结论 | 依据 |
|---|---|---|
| WS 握手有 **101** 记录 | **PASS** | 5 次探测全部 `101 Switching Protocols`，见 §3 |
| 回滚后 `/healthz` 与 `/api/health` 均 **200** | **PASS** | `raw/a6-result.json` → `rollback.healthz=200`、`api_health.database=UP` |
| **RTO 计时入表** | **PASS** | 见 §2；闭环 70.2 秒 |
| 演练后只回本次写入 | **PASS（本次写入为 0 条业务数据）** | 67 表行数逐一相同；内容差异仅 2 行心跳时间戳，见 §4 |

总判定 `PASS=true`（9/9 子项全绿）：`raw/a6-result.json` 的 `verdict` 字段。

## 2. RTO 计时表（真实 wall clock）

| 阶段 | 秒 | 说明 |
|---|---|---|
| deploy：生成构建信息 → 构建 jar/dist → 部署 → 自检 | **62.0** | `scripts/deploy.sh` 全程，含 `mvn -DskipTests package` 与 `vite build` |
| 部署后外部健康复核 | 0.0 | deploy.sh 第 5 步已自检到 200，退出即健康（不是跳过复核） |
| rollback：备份当前 → 停服 → 部署上一版 → 起服 → `/api/_ping` 200 | **8.2** | `scripts/rollback.sh --to a6-baseline --yes` |
| 回滚后外部健康复核 | 0.0 | rollback.sh 第 ③ 步自检已通过 |
| **闭环合计（deploy→rollback→health）** | **70.2** | 取 `rto_table.loop_total_s` |

> RTO 分母说明：这是**本机 dev 环境**（WSL2 / 单机 / MySQL 本地）的数字，用于回归期基线，
> 不能直接搬到 149 生产机；上生产复测时按同一脚本重跑即可对比。

## 3. WebSocket 101 握手

探测脚本 `ws_probe.py`：手写 RFC 6455 握手（`stdlib socket`，不引第三方 ws 库），只读状态行与响应头。

| 探测 | 目标 | 状态行 | 结果 |
|---|---|---|---|
| `raw/ws-entry-post.json` | 统一入口 `:8001`（经反代 upgrade） | `HTTP/1.1 101 Switching Protocols` | 101 |
| `raw/ws-backend-post.json` | 后端直连 `:8081`（对照，绕过反代） | 同 | 101 |
| `raw/ws-entry-realrun.json` | 入口 + 真实 runId `RUN-20261107121600-e80000` | 同 | 101，2 秒内无帧（该 run 的日志通道不在内存，符合 `RunLogService` 只在订阅会话存续期保活的设计） |
| `raw/ws-entry-pre.json` / `ws-backend-pre.json` | 演练**前**同一探测 | 同 | 101（部署前后一致） |

复现：

```bash
cd ~/robot-autotest/docs/evidence/v2.0/a6-entry
python3 ws_probe.py --port 8001      # 入口
python3 ws_probe.py --port 8081      # 直连后端
```

## 4. 数据面审计（前后强制 mysqldump）

| 项 | 值 |
|---|---|
| 前 dump | `raw/db-before.sql.gz`（sha256 与字节数见 `a6-result.json` → `db_before`） |
| 后 dump | `raw/db-after.sql.gz` |
| 表数 | **67** 张业务表，**行数逐一相同**（`db_row_count_diff = {}`） |
| 内容差异 | **2 行、5 列**，全部是心跳时间戳：`api_token#430.last_used_at/updated_at`（本次取证自身登录会话，`created_at=13:13:02`）、`resource_lease#303.last_heartbeat_at/expires_at/updated_at`（既有在跑 run `RUN-20261107014329-910000` 的租约心跳，约 60 秒一跳） |
| 业务值变更 | **0**（`verdict.no_business_value_changed=true`） |
| 插入/删除 | **0 行** |

> **为什么不撒谎说「sha256 完全相同」**：`mysqldump` 页脚带「Dump completed on …」时间戳，
> 两份 dump 的数字摘要必然不同。因此判据拆成两层：行数一致（`drill.py`）+ 逐表逐列定位（`dbdiff.py`）。
> 表级/列级明细：`raw/db-diff.json`；变化行的库内实况：`raw/db-changed-rows.txt`。

## 5. 回滚是否真的回到上一版（三重物证）

| 物证 | 部署后 | 回滚后 |
|---|---|---|
| `/api/system/build-info` | `commit=7a6a604`、`buildTime=2026-11-07T13:14:xxZ`（本次构建） | `commit=61c49dd`、`buildTime=2026-11-06T11:01:08Z`（基线） |
| jar sha256 | 新产物 | **与演练前逐字节相同**（`artifact_restored=true`） |
| `frontend/dist` 目录内容摘要 | 新产物 | **与演练前相同** |
| 页面 `/about` | — | 构建提交 `61c49dd`、构建时间 `2026-11-06T11:01:08Z` → `shots/about-after-rollback.png` |

配套服务端日志：`raw/deploy.log`（5 步全程，含 commit `7a6a604`）、`raw/rollback.log`
（备份目录 `~/robot-autotest/backups/rollback-20261107_131406`，`/api/_ping → 200`）。

## 6. 证据清单

```
docs/evidence/v2.0/a6-entry/
├── README.md                     ← 本文
├── ws_probe.py                   ← WS 101 探测（stdlib，可复现）
├── drill.py                      ← 演练编排：dump → deploy → 复核 → rollback → 复核 → 审计
├── dbdiff.py                     ← 两份 dump 的逐表逐列差异定位
├── about_shot.mjs                ← /about 页面 CDP 截图 + 读取 buildTime
├── raw/
│   ├── a6-result.json            ← 总结果（verdict / RTO / db before-after / build-info）
│   ├── deploy.log  rollback.log  ← 两条真实命令的完整输出
│   ├── db-before.sql.gz  db-after.sql.gz
│   ├── db-diff.json  db-changed-rows.txt
│   ├── ws-entry-pre.json  ws-backend-pre.json
│   ├── ws-entry-post.json  ws-backend-post.json  ws-entry-realrun.json
│   └── about-after-rollback.json
└── shots/
    └── about-after-rollback.png
```

复现整条演练（约 70 秒，会真实构建与重启服务）：

```bash
cd ~/robot-autotest/docs/evidence/v2.0/a6-entry
python3 drill.py --dry-run   # 先空跑，确认取数与路径
python3 drill.py            # 真实演练；结束后再跑 about_shot.mjs 截图
```

## 7. 偏差与限制（不藏）

1. **部署后的 jar 与基线 jar 不是同一 commit**：基线与回滚目标 `61c49dd`，而当前 HEAD 是 `7a6a604`。
   说明演练前线上跑的就是「昨天构建的旧包」，本次 deploy 才把它升到 HEAD —— 这恰好让回滚可被观测，
   但也意味着 **回滚后线上又回到旧 commit**，与 `git log` 不一致属预期。
2. **`deploy_health_verify_s = 0.0`**：不是漏测，是 deploy/rollback 脚本内部已做健康轮询，
   脚本退出时服务已 200，外部复核只做确认。
3. **TC-67 的「PDF 导出 URL 指向 8001」不在本项覆盖**：导出链路的 `downloadUrl` 由
   `ExportService` 生成（后端返回相对路径 `/api/exports/{id}/download`），需真实导出一次才能取证，
   归到 **A8 报告链路抽样**，本项只判「部署/回滚 + 双健康端点」。
4. **在跑 run 的租约心跳被重启打断**：`resource_lease#303` 的心跳在被停服期间停顿约 80 秒后继续，
   未见过期抢占（`lease_state=held`）；生产演练时应避开在跑任务窗口，本次记录以备复核。

## 8. 踩到的坑

1. **`mysql` 客户端不存在**：本机只有 `mariadb`，且口令必须走 `MYSQL_PWD`（不能进 argv，否则 `ps` 可见）。
   `drill.py` 已按 `mariadb → mysql` 顺序探测。
2. **PowerShell 会吞 `--noproxy '*'` 与 `\|`**：命令里带引号/竖线一律写进脚本文件再执行，
   否则 `curl --noproxy *` 会被 PowerShell 当通配符展开。
3. **自己的判据 bug（已修）**：`dbdiff.py` 最初把列名写在 `json.dump` **之后**，
   导致 `db-diff.json` 里没有 `column` 字段，`drill.py` 判出 `no_business_value_changed=false` 的假阴性。
   修法：列名在落盘前填充。修订记录见 §9。

## 9. 修订记录

| 时间 | 变更 | 原因 |
|---|---|---|
| 2026-11-07 13:16 | 首版 | 连续跑了 3 次同一演练：第 1 次判据名 `zero_db_writes` 过强（只比行数），审计内容后改为 `row_counts_identical` + `no_business_value_changed`；第 2 次因 `dbdiff.py` 未把列名写进 JSON 而假阴性；第 3 次 9/9 PASS，本文数据取自第 3 次 |
