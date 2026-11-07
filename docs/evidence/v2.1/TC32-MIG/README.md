# TC-v2.1-32 证据：V9_1–V9_4 迁移往返（up → down → up）+ 备份恢复

## 结论

- **61 项断言全 OK、FAIL 0**（`32-up-down-up.log`，退出码 0）：V9_4 → V9_3 → V9_2 → V9_1
  逐个 down 后，对应表/列/索引确实消失；V8_x 数据行数（`test_report=2`、`project=1`）全程未变；
  重新 up 回 V9_4 成功，表清单与首次 up **逐行一致**（66 张）。
- **备份恢复后行数一致**：`32-backup-after-up.sql`（103,288 字节）DROP+CREATE+导入后，
  project/test_report/plan_group 行数与备份时相同，表清单一致。
- **恢复后 `db-audit` 7 项全 PASS**（业务表 66、审计四列、append-only 历史表 created_at、
  单列 id 主键、缺主键、时间列 datetime(3)、utf8mb4）。

## 业务表数变化（实测）

| 阶段 | flyway max | 业务表 |
|---|---|---|
| 首次 up | 9.4 | 66 |
| down V9_4（execution_node） | 9.3 | 65 |
| down V9_3（asset_version 等） | 9.2 | 64 |
| down V9_2（report_template ×2） | 9.1 | 62 |
| down V9_1（plan_group ×2） | 8.6 | 60 |
| 再次 up | 9.4 | 66 |

## 判据对照（TEST-PLAN §4.7 / §5.4 TC-v2.1-32）

| 判据 | 证据 |
|---|---|
| down 后表/列消失 | V9_4：`execution_node` 表、`run.node_id` 列、`idx_run_node` 索引均 0；V9_3：`asset_version` 表、`test_set.owner_scope/source_asset_id/scope_project_id`、`uk_test_set_scope_code`、`idx_test_set_source` 均 0，`project_id` 恢复 NOT NULL、`uk_test_set_project_code` 恢复；V9_2：`report_template`、`report_template_field` 均 0；V9_1：`plan_group`、`plan_group_item` 均 0 |
| 不影响 V8_x 数据 | 每一步（含 down 全部完成后）都断言 `test_report=2`、`project=1`，实测不变 |
| 重新 up 成功 | `mvn flyway:migrate -Dflyway.target=9.4` 退出码 0；history max=9.4；5 个对象全部恢复；表清单 diff 为空 |
| 备份恢复后行数一致 | 恢复后 project=1 / test_report=2 / plan_group=1 / 表 66，与备份时一致 |

## 命令

```bash
cd ~/robot-autotest
bash scripts/v21/migrate-roundtrip.sh       # 退出码 0 = 全 PASS
# 需要保留 scratch 库排查时：
KEEP_DB=1 bash scripts/v21/migrate-roundtrip.sh
```

## 设计口径（写死，改脚本前先读）

1. **只在 scratch 库上演练**：默认 `TARGET_DB=robot_it_local`（robot 用户已授权、演练前为空），
   脚本拒绝 `robot_it` / `robot_dev` / `mysql` / `sys`，并要求库名含 `_it`。演练结束默认
   `DROP DATABASE`，不碰真实业务库。
2. **首次 up 从空库真跑 V1..V9_4**，不依赖既有库的历史状态；`-Dflyway.target=9.4` 明确停在
   V9_4（V9_5 不在本 TC 范围）。
3. **手写 down 不维护 `flyway_schema_history`**，脚本在每次 down 后显式删除该版本记录，
   否则重放 up 会报 already applied。这是 `db-undo.sh`（一次性倒序回退全部版本）之外
   必须逐版本执行的原因。
4. **备份用 `mariadb-dump --single-transaction --skip-comments`**（回落 `mysqldump`）；
   恢复是 DROP+CREATE+导入同一库，比对行数与表清单，再跑 `db-audit`。
5. down V9_1 会连同 `plan_group` 数据一起丢弃（表被 DROP，属预期）；再次 up 后该表为空，
   所以「重放后 plan_group 行数」不作为断言，只断言表恢复。

## 原始输出

| 文件 | 内容 |
|---|---|
| `32-up-down-up.log` | 全流程 8 个阶段 + 61 项 OK/FAIL，末行「TC-v2.1-32: PASS」 |
| `32-tables-after-up.txt` | 首次 up 后 66 张表清单（基线） |
| `32-tables-after-reup.txt` | 再次 up 后表清单 |
| `32-tables-diff.log` | 基线与重放表清单的 diff（空 = 一致） |
| `32-tables-after-restore.txt` | 备份恢复后表清单 |
| `32-backup-after-up.sql` | up 后状态逻辑备份（103,288 字节） |

## 已知限制

- down 脚本是**手写回退**，不是 Flyway 官方 undo（该特性需 Teams 版）；本 TC 验证的是
  回退脚本本身可用与数据不丢。
- 备份恢复是**逻辑备份**（dump/restore），不覆盖 binlog/PITR 时间点恢复。
- MySQL 客户端会把 `--password` 的 Warning 打到 stderr，日志里已过滤该行（不是错误）。
