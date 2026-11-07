# B3 · 保留策略清理 dry-run 与实删（G5 第 5 项）

> 对应 `06-收口执行计划-20261106.md` §3 **B3**：`dry-run 清单与实删条数一致；审计留清理记录；误删 0`。
> **本证据复用 A8 的同一次实跑**（2026-11-07 14:42，项目 76 隔离造数），不重复造数：
> 原始 HTTP 响应、前后库快照与产物都在 `docs/evidence/v2.0/a8-report-chain/raw/`。
> 复用理由：保留策略与报告链路是同一条代码路径（`ReportRetentionService`），
> 同一次运行里同时取证，比分两次更不容易「两次条件不一致」。

## 1. 判据与结论

| 判据（06 计划原文） | 结论 | 依据（原始读数） |
|---|---|---|
| dry-run 清单与实删条数一致 | **PASS** | dry-run `runCount=1 / totalBytes=4141 / items=[RUN-20261107144245-580000, expired]`；实删同样 `runCount=1 / totalBytes=4141 / 同一 runId`；dry-run `deletedFiles=0`，实删 `deletedFiles=3` |
| 审计留清理记录 | **PASS** | `audit_log.action='artifact_purge'` 计 **2 行**（本次实删 1 次 + 前一轮取证 1 次） |
| 误删 0 | **PASS** | 项目 29 的 `run_artifact` 行数 **65 → 65**（跨项目零误删）；被导出 PDF 的 run `…144242` 未进候选（`protectedRun` 命中导出记录）；清理只删文件不删记录，项目内 `run` 行数保留 2 |

原始 JSON：`a8-report-chain/raw/a8-result.json` 的 `retention_dry` / `retention_real` /
`runs_kept_in_db` / `p29_artifacts_before` / `p29_artifacts_after` / `audit_artifact_purge_rows`，
对应检查项 `retention_dryrun_equals_real` / `retention_deleted_files` /
`retention_protects_exported_run` / `retention_targets_plain_run` / `retention_keeps_run_rows` /
`no_cross_project_purge` 全部 `true`。

## 2. 取证条件（必须连带说明，否则读数会被误解）

- 为制造候选，取证时临时把 drop-in 的 `REPORT_RETENTION_MAX_DAYS` 设 **0**、`KEEP_RECENT_RUNS` 设 **0**；
  实删后**立即移除**，保留策略回到代码默认（`docs/evidence/v2.0/a8-report-chain/README.md` §6）。
- 因此本证据证明的是**机制正确**（候选筛选 / 保护规则 / 只删文件 / 审计 / 跨项目隔离），
  **不证明**「生产默认阈值下哪天会触发清理」——那需要真实时间跨度，本机无法加速。

## 3. 复现

```bash
cd ~/robot-autotest/docs/evidence/v2.0/a8-report-chain
python3 probe.py     # 约 2 分钟；含保留策略 dry-run + 实删 + 前后 mysqldump
```

前置：后端 `:8081`、`qa-e2e-admin` 口令在 `~/.config/robot-autotest/env`、`mysqldump`/`mysql` 可用。
`probe.py` 会在结束时把 `REPORT_RETENTION_*` 恢复并重启后端（见 A8 README §6）。

## 4. 遗留

- G5 第 5 项的判定权仍在现场日；本证据只补「当场证据」，**不构成门禁通过**。
- 生产默认值下没有候选样本，清理任务是否按日调度执行，需另看 `planning` 调度日志；
  本轮未取该日志，记为遗留（不伪造）。
