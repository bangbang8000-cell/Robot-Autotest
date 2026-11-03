# HRD-03 证据：小型试点迭代验证 + 运维手册

> 需求变更（2026-11-03）：24 小时连续压测已取消，本文保留早期冒烟记录，不再作为验收依据。HRD-03 验收改为「小型试点项目持续迭代验证 + 运维手册可执行」：每轮记录崩溃/重启/内存趋势/连接数作为优化输入；不设 24h、不设 RSS<20% 硬门槛。

**日期**：2026-11-03 ｜ **执行环境**：Windows 开发机 + WSL `Ubuntu-24.04`
**结论一句话**：脚本与手册已交付并通过语法/短时冒烟；**小型试点迭代验证尚未启动，新人独立验证未做**，需在本地 WSL 上按轮执行并记录。

---

## 1. 交付物

| 文件 | 说明 |
|---|---|
| `scripts/stability-24h.sh` | 稳定性采样脚本（脚本名保留，仅作采样工具；时长与频率按需指定，不要求 24h） |
| `scripts/window-stats.sql` | 近 7 天按日统计 runs/ok/success_rate/exportable_runs（趋势参考） |
| `docs/OPS-MANUAL.md` | 8 节中文运维手册，Windows 开发态与本地 WSL 常驻态分开 |
| `docs/evidence/v1.0/stability-smoke.{txt,csv}` | 3 分钟冒烟真实输出（本文 §3，**仅冒烟**） |
| `docs/evidence/v1.0/window-stats-dev.txt` | SQL 在 WSL `robot_dev` 的真实执行结果（本文 §4） |
| `docs/evidence/v1.0/pilot-iterations.md` | 每轮试点迭代记录（启动后产出，字段见 §6） |

---

## 2. 脚本用法与判定口径

### 2.1 依赖（缺任一项即 exit 2，不伪造数据）

| 依赖 | 用途 | 本地 WSL 上怎么满足 |
|---|---|---|
| `curl` | 健康检查 / 触发负载 | 系统自带 |
| `ps` / `df` | RSS、磁盘 | 系统自带 |
| `mysql` 客户端 | DB 连接数 | `mysql-client`（MySQL 8.0） |
| `jcmd` / `jstat` | JVM heap、GC 次数 | 把 JDK `bin` 放进 PATH（`/usr/lib/jvm/*/bin`） |
| `DB_PASSWORD` | 库口令 | 从 `~/.config/robot-autotest/env` 读取后 export |
| `STABILITY_TOKEN` 或 `--token` | 平台 API | 管理员登录后取 token |
| `--project` + 两个用例集 ID | 负载 | `STABILITY_LIGHT_SET_ID`（轻量）、`STABILITY_HEAVY_SET_ID`（4 并发） |

```bash
# 本地 WSL 采样（示例 30m；时长按轮次按需调整，不设 24h 要求）
export DB_PASSWORD="$(grep -m1 '^DB_PASSWORD=' "$HOME/.config/robot-autotest/env" | cut -d= -f2-)"
export STABILITY_TOKEN='<平台 token>'
export STABILITY_LIGHT_SET_ID='<轻量用例集 ID>'
export STABILITY_HEAVY_SET_ID='<4 并发用例集 ID>'
export PATH="/usr/lib/jvm/*/bin:$PATH"

bash scripts/stability-24h.sh \
  --project <项目ID> --base-url http://127.0.0.1:8081 \
  --duration 30m --interval 60s \
  --out "$HOME/robot-autotest/evidence/stability"
```

| 参数 | 默认 | 说明 |
|---|---|---|
| `--duration` | `24h`（脚本历史默认值，本验收不使用） | 支持 `24h` / `3m` / `60s` / 裸秒；试点轮次按需显式传入 |
| `--interval` | `60s` | 采样周期（实际周期 ≈ 该值 + 采样耗时） |
| `--cooldown` | `5m` | 负载结束后静置再判连接数 |
| `--project` | 无 | 项目 ID，触发负载必填 |
| `--base-url` | `http://127.0.0.1:8081` | 本地 WSL 运行端口默认 8081 |
| `--out` | `artifacts/stability` | 输出目录 |
| `--label` | `24h`（历史默认值） | 文件名后缀：`stability-<label>.{csv,txt}`；建议按轮次命名如 `r1` |
| `--token` / `--light-set` / `--heavy-set` | 环境变量 | 同上 |
| `--process-pattern` | `robot-autotest.*\.jar` | 匹配后端进程；也支持其它正则 |
| `--jcmd` | 自动探测 | 显式指定 jcmd 路径 |
| `--conn-ceiling` | 16 | Hikari 池上限（prod 16 / dev 4） |
| `--monitor-only` | 关 | 只采样不触发负载；**不能作为稳定性结论** |

**摘要里的三个 grep 键（用于趋势记录，不作为硬门槛）：**

- `restarts=`：匹配到的后端 PID 发生变化的次数（systemd 重启/崩溃拉起都会变 PID）；进程缺失的采样另记 `process_missing_samples`。
- `rss_growth=`：`(峰值 RSS - 首样本 RSS) / 首样本 RSS × 100%`。脚本原判定写 `<20%`，本验收**只记录趋势，不把 20% 当硬门槛**。
- `conn_leak=`：最终/峰值 DB 连接数。Hikari 的 `minimumIdle` 默认等于 `maximumPoolSize`，负载结束后连接会稳定停在池上限（prod 16），这不是泄漏。脚本以 `conn_ceiling` 为判据，并同时输出 `conn_baseline` / `conn_peak` / `conn_final` 供人工复核。
- `result=`：脚本保留旧判定逻辑（`PASS` 需 `restarts=0`、`rss_growth<20%`、`conn_leak=无` 等）；**本验收不以其 PASS/FAIL 作为门禁**，只看每轮记录是否齐全、优化项是否闭环。

**负载接口假设（尚未实跑）**：`POST /api/execution/runs`，Header `Authorization: Bearer <token>` + `X-Project-Id`，body `{projectId,testSetId,concurrency,runRequestId}`；轻量每 600s、4 并发每 7200s。该负载路径只做了代码对齐，**没有对活动平台发出过真实触发**（冒烟为 monitor-only），首次本地 WSL 执行时需先确认接口返回 2xx。

### 2.2 语法检查

```bash
wsl -d Ubuntu-24.04 -- bash -n scripts/stability-24h.sh
# 退出码 0
```

---

## 3. 3 分钟冒烟真实输出（仅冒烟，不构成稳定性结论）

命令（WSL，monitor-only，因没有平台 token/用例集 ID；进程取 `mysqld`）：

```bash
DB_PASSWORD=... bash scripts/stability-24h.sh \
  --monitor-only --duration 3m --interval 20s \
  --label smoke --out docs/evidence/v1.0 --process-pattern mysqld
# 退出码 0
```

摘要原文（节选，完整见 `stability-smoke.txt`；首行标题为人工添加，其余数值未改写）：

```
mode=monitor-only
started_at=2026-11-03 20:01:31 CST
ended_at=2026-11-03 20:04:31 CST
elapsed_s=180
samples=9
restarts=0
process_missing_samples=0
rss_first_mb=5.7
rss_peak_mb=19.6
rss_growth=243.9%
conn_baseline=0
conn_peak=0
conn_final=0
conn_ceiling=16
conn_leak=无
http_failures=0
load_triggers_ok=0
load_triggers_fail=0
result=MONITOR-ONLY
```

CSV 共 9 行样本（每条 20s 间隔，实际 20~25s）：

```
2026-11-03 20:01:31,0,573,5.7,NA,NA,NA,0,4,920.2,200,0,0
2026-11-03 20:01:51,20,573,18.2,NA,NA,NA,0,4,920.2,200,0,0
...
2026-11-03 20:04:28,177,573,11.9,NA,NA,NA,0,4,920.2,200,0,0
```

**如实说明：**

1. 冒烟监控的是 WSL 的 `mysqld`（PID 573），不是 JVM；冒烟时未把 JDK 加进 PATH，`heap_*` / `gc_count` 全部为 `NA`，这是脚本的「采不到就标 NA」行为，不是造假。
2. `rss_growth=243.9%` 是 `mysqld` 冷启动后首次触内存（5.7MB → 19.6MB → 回落 10~12MB）造成的，**不能当成稳定性趋势结论**；真实采样应在服务已稳定运行后启动。
3. `conn_baseline=0`：WSL 里没有应用连接 `robot_dev`，脚本的统计口径排除了监控自身连接；`conn_leak=无` 只证明口径可工作，不代表长期无泄漏。
4. `result=MONITOR-ONLY` 明确拒绝把冒烟当通过。
5. **本节数字仅为 3 分钟冒烟，不构成 24h 结论，也不作为 HRD-03 验收依据。**

---

## 4. 运行统计脚本真实执行

`scripts/window-stats.sql` 按 DEV-PLAN §6.1 输出 `d/runs/ok/success_rate/exportable_runs`，按真实 schema 做了两处映射（已在 SQL 注释中写明）：

- `state='finished'` → `run.run_status='passed'`（本库没有 `finished` 状态）；
- `exportable` → `run.exportable`（V5_1 实列，1 = 十项 metadata 齐全且可交付）。

WSL 执行结果（`window-stats-dev.txt` 原文）：

```
$ mysql  Ver 8.0.46-0ubuntu0.24.04.4 for Linux on x86_64 ((Ubuntu))
$ SELECT COUNT(*) FROM run; SELECT COUNT(*) FROM export_record;
0
0

$ mysql -h127.0.0.1 -P3306 -urobot robot_dev < scripts/window-stats.sql

MYSQL_EXIT_CODE=0
```

**读法**：`robot_dev` 的 `run` / `export_record` 均为 0 行，按日查询无数据行，退出码 0。这是空表真实结果，不是 SQL 报错；本地 WSL 运行库有真实运行后重跑即可，统计结果仅作趋势参考。

---

## 5. 明确未做的项（红线）

| 项 | 状态 | 说明 |
|---|---|---|
| **24 小时连续压测** | ⛔ **已取消** | 需求变更（2026-11-03）不再要求；改为小型试点迭代，每轮记录崩溃/重启/内存趋势/连接数 |
| **小型试点迭代验证** | ❌ 未启动 | 需选 1-2 条真实链路组成小型试点项目，按轮运行并按 §6 记录 |
| **真实负载触发路径** | ❌ 未验证 | 冒烟是 monitor-only，`POST /api/execution/runs` 未对活动平台实发 |
| **JVM heap / GC 采集** | ❌ 未验证 | 冒烟时未加 JDK PATH，两列均为 NA；本地 WSL 上需确认 `jcmd`/`jstat` 可用 |
| **新人独立验证** | ❌ **未做** | 需一名未接触过本项目的人，只按 `docs/OPS-MANUAL.md` 完成一次健康检查 + 备份 + 恢复演练 |
| **运维手册本地 WSL 实机核对** | ❌ 未做 | 本地 WSL 尚未部署本产品，unit 名/端口/路径为约定值，首次部署后回填 |
| **RTO/备份耗时** | ❌ 无实测 | 手册 §6 的耗时表留空，禁止估数 |

## 6. 后续步骤（试点迭代）

1. 部署并确认 `SERVER_PORT`、unit 名、`LOG_DIR`，回填 `OPS-MANUAL §1.2`；
2. 选 1-2 条真实链路组成小型试点项目，准备轻量/4 并发两个用例集，取 ID；管理员登录取 token；
3. `bash -n scripts/stability-24h.sh` → 先跑 10 分钟（`--duration 10m --interval 60s`）确认能触发、能采 heap/GC；
4. 按轮持续运行试点项目（时长与频率按需，**不设 24h**）；每轮结束把以下字段写入 `docs/evidence/v1.0/pilot-iterations.md`：

   | 字段 | 来源 |
   |---|---|
   | 本轮运行次数 / 结果 | 平台 run 列表（`dut` 非空、非回放） |
   | 崩溃 / 重启 | 采样摘要 `restarts` / `process_missing_samples` |
   | 内存趋势 | RSS / heap 采样 |
   | 连接数 | `conn_baseline` / `conn_peak` / `conn_final` |
   | 缺陷 / 用户反馈 | 缺陷列表 + 使用方原话 |
   | 本轮优化项 / 下一轮动作 | 迭代评审结论 |

5. 每轮做一次迭代评审，优化项闭环；能力不倒退是唯一硬约束；
6. 让一名新人只按 `OPS-MANUAL.md` 独立走一遍备份/恢复，记录卡点并回改手册。
