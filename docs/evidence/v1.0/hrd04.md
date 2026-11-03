# HRD-04 证据：回滚预案 + 干跑

**日期**：2026-11-03 ｜ **执行环境**：Windows 开发机 + WSL `Ubuntu-24.04`（干跑）
**结论一句话**：`scripts/rollback.sh` 与 `docs/ROLLBACK.md` 已交付，干跑退出码 **0**、未改任何状态；**真实回滚演练未做，RTO 未计时，需大师签字**。

---

## 1. 交付物

| 文件 | 说明 |
|---|---|
| `scripts/rollback.sh` | 三步回滚：备份 → 停服/部署上一版（+可选 down）→ 起服/健康检查/抽样 |
| `docs/ROLLBACK.md` | 三步流程、down 风险表、默认策略理由、演练空表、抽样清单、升级路径 |
| `docs/evidence/v1.0/rollback-dryrun.log` | 干跑真实输出（退出码 0） |

---

## 2. 脚本参数

```bash
bash scripts/rollback.sh --to <版本标签> [--dry-run] [--schema=keep|down] [--yes]
```

| 参数 | 默认 | 说明 |
|---|---|---|
| `--to` | 必填 | 目标版本标签；发布包需在 `releases/<标签>/` |
| `--dry-run` | 关 | 只打印，不改状态，退出 0 |
| `--schema` | **`keep`** | 只回代码；`down` 才执行 `db/down` 倒序迁移 |
| `--schema-to` | 由标签推导 | 显式迁移天花板（如 `6.3`），非标准标签时用 |
| `--yes` | 关 | 真实执行必须显式带；缺省只打印并退出 0 |
| `--app-dir` / `--release-dir` / `--backup-dir` / `--artifacts-dir` | `~/robot-autotest/...` | 路径可配 |
| `--jar` / `--dist` | `~/robot-autotest/app/robot-autotest-backend.jar` / `~/robot-autotest/dist` | 线上包位置 |
| `--src-jar` / `--src-dist` | `releases/<标签>/...` | 上一版包位置 |
| `--service` | `robot-autotest.service` | systemd 用户级 unit |
| `--base-url` | `http://127.0.0.1:${SERVER_PORT:-8081}` | 健康检查基址 |

**安全设计：**

- 默认 `schema=keep`：只回代码，不回 schema（迁移只增不删，旧代码兼容新列）。
- `--schema=down` 需 `--yes`；TTY 输入 `DOWN`，非 TTY 需 `ROLLBACK_DOWN_CONFIRM=1`；缺 `--yes` 直接退出 3。
- 口令只从 `DB_PASSWORD` 环境变量读，脚本中无明文。
- 每步可重入：重复执行会生成新备份目录，部署用 `.new + mv` 原子替换。
- 失败即停，并打印“下一步做什么”（看错误 → 查服务 → 重跑 → 不要手工删库）。

**退出码**：`0` 干跑/成功；`1` 中途失败；`2` 参数/依赖/发布包缺失；`3` down 未确认。

---

## 3. 干跑真实输出

```bash
wsl -d Ubuntu-24.04 -- bash -n scripts/rollback.sh          # 退出码 0
wsl -d Ubuntu-24.04 -- bash scripts/rollback.sh \
  --to v0.6-automation --dry-run > docs/evidence/v1.0/rollback-dryrun.log 2>&1
# 退出码 0
```

> **版本标签说明**：仓库**没有任何 git tag**，`v0.6.3` 不存在；这里使用真实存在的发布标签 `v0.6-automation`（对应 `docs/release/v0.6-automation/`）。仓库当前也没有 `releases/v0.6-automation/` 发布包，干跑因此打出“发布包不存在（真实执行会中止）”的警告，但仍完整打印了命令。

日志正文（节选，完整见 `rollback-dryrun.log`）：

```
[rollback] 目标版本：v0.6-automation
[rollback] 模式：只打印
[rollback] schema：keep（目标迁移天花板 6.3）
[rollback] 服务：robot-autotest.service  健康检查：http://127.0.0.1:8081/api/_ping
[rollback] 警告：发布包不存在：.../releases/v0.6-automation/robot-autotest-backend.jar（干跑继续；真实执行会中止）

[rollback] ① 备份当前状态
  $ mkdir -p .../backups/rollback-20261103_202905
  $ MYSQL_PWD="$DB_PASSWORD" mysqldump -h127.0.0.1 -P3306 -urobot --single-transaction --routines --triggers robot_dev | gzip -9 > .../db.sql.gz
  $ find .../artifacts -type f -printf '%P\t%s\n' > .../artifacts-manifest.tsv
  $ cp .../app/robot-autotest-backend.jar .../current-jar.jar
  $ tar -czf .../current-dist.tar.gz -C ... dist

[rollback] ② 停服 → 部署上一版 → 可选 down 迁移
  $ systemctl --user stop robot-autotest.service
  $ cp .../releases/v0.6-automation/robot-autotest-backend.jar .../app/robot-autotest-backend.jar.new && mv ...new ...jar
  $ rsync -a --delete .../releases/v0.6-automation/dist/ .../dist/

[rollback] ③ 起服 → 健康检查 → 抽样
  $ systemctl --user start robot-autotest.service
  $ curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8081/api/_ping   # 期望 200，最多重试 30 次

[rollback] 抽样验证清单（必须人工完成并记录）：
  [ ] 登录：浏览器打开 http://127.0.0.1:8081 ，用管理员账号登录
  [ ] 看一个历史报告：/reports/<runId> 能打开，metadata 十项齐全
  [ ] 跑一条用例：选轻量用例集执行，状态到 passed/failed（不是卡在 running）
[rollback] 干跑完成（未改任何状态，退出 0）

DRYRUN_EXIT_CODE=0
```

另外单独验证了 `--schema=down` 的倒序逻辑（`--to v0.5-report`，由标签推导迁移天花板 5.5），输出顺序为：

```
V7_1__audit_summary.down.sql
V6_3__ci_trigger.down.sql
V6_2__plan_dependency.down.sql
V6_1__execution_plan.down.sql
```

与 `db/down/` 真实文件名和版本倒序一致；该验证仅打印，未执行。

补充验证安全开关（均为只打印，未改状态）：

| 命令 | 退出码 | 含义 |
|---|---:|---|
| `--schema=down` 且不带 `--yes` | **3** | 明确拒绝破坏性操作，只打印计划 |
| `--schema=keep` 且不带 `--yes` | 0 | 只打印计划，安全默认 |
| `--dry-run` | 0 | 只打印，不改状态 |

---

## 4. 明确未做的项（红线）

| 项 | 状态 | 说明 |
|---|---|---|
| **真实回滚演练** | ❌ **未做** | 本机是 Windows 开发态，无本地 WSL 的 jar/dist 发布包与 systemd unit；**没有执行任何停服/部署/down** |
| **RTO 计时** | ❌ **未计时** | `docs/ROLLBACK.md §7` 演练记录表为空；干跑耗时不能当 RTO |
| **down 迁移实跑** | ❌ 未执行 | 未连接生产库，未删任何表/列；`--schema=down` 仅验证命令生成与顺序 |
| **发布包归档** | ❌ 未建立 | `releases/v0.6-automation/` 在本机不存在，需在本地 WSL 部署时归档最近 3 个版本 |
| **抽样验证** | ❌ 未做 | 登录/看报告/跑用例需在真实回滚后人工完成 |
| **大师签字** | ❌ 待签字 | 演练完成后在 `ROLLBACK.md §7` 填表签字 |

---

## 5. 在本地 WSL 上补齐的步骤

1. 归档当前版本与上一版到 `~/robot-autotest/releases/<标签>/`（jar + dist）；
2. 按 `docs/ROLLBACK.md §2` 做一次**不停生产**的干跑：`--dry-run`，核对路径；
3. 在维护窗口做真实演练（建议先在测试库/测试实例），从第一条命令开始计时，填 §7 表；
4. 抽样 6 项全过才算回滚成功；任一项失败按 `OPS-MANUAL §7` 定位；
5. 合计 RTO ≤ 30min 方可达标；超过则分析瓶颈（DB dump、停服、启动、健康检查）并回改脚本/手册；
6. 演练记录 + RTO 表交大师签字，本文“未做”项同步更新。
