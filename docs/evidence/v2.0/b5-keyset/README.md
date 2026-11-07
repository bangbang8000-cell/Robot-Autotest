# B5 · 百万行分页（G5 输入项）

> 对应 `06-收口执行计划-20261106.md` §3 **B5**：`audit_log` 覆盖索引 + keyset、
> `test_result.case_key` 索引评估，**迁移脚本 + down 脚本 + 百万行造数实测**。
> 取证日期 2026-11-07；MySQL 8.0.46（`robot_dev`）、Flyway 10.1、真实后端 :8081、真实 HTTP。
> **结论：判据全项 PASS。但「加 (project_id, occurred_at, id) 覆盖索引」这一条被实测否定 ——
> InnoDB 二级索引已隐含主键，旧索引的 key_len=24 就包含 id；真正缺的是全局查询的 (occurred_at, id)。**

## 1. 判据与结论

| 判据 | 结论 | 依据（`raw/`） |
|---|---|---|
| 1 份迁移脚本 | **PASS** | `db/migration/V10_1__audit_keyset.sql`；Flyway 记录 `version=10.1 success=1 execution_time=3378ms`（`down-up-cleanup.txt`） |
| 1 份 down 脚本 | **PASS** | `db/down/V10_1__audit_keyset.down.sql` 回放后两索引均 `(none)`，再回放 up 3,755 ms 恢复（`down-up-cleanup.txt`） |
| 百万行造数实测 `LIMIT/OFFSET` 劣化 | **PASS** | 同一条 SQL 在 100 万行上：offset 0 → **38 ms**，200k → **364 ms**，500k → **889 ms**，900k → **3,479 ms**（`audit-timing-before.txt`） |
| keyset 深翻不再随页号变慢 | **PASS** | SQL：offset 500k 的 **889 ms → keyset 44 ms**；HTTP：**1,263 ms → 190 ms**（项目内）、**1,576 ms → 130 ms**（全局）（`audit-timing-after.txt`、`http-timing.txt`） |
| `(project_id, occurred_at, id)` 三列覆盖索引 | **按实测调整** | 现有 `idx_audit_log_project_time(project_id, occurred_at)` 的 `key_len=24` = project_id 9 + occurred_at 7 + **id 8**，id 已在索引里；EXPLAIN 显示 keyset 已走它（`audit-timing-after.txt`）。再建三列索引是等价索引，只占空间 → **不加**，改为补全局缺的 `idx_audit_time_id(occurred_at, id)` |
| `test_result.case_key` 索引评估 | **PASS（结论：要加）** | 克隆表 100 万行：无索引 **544 ms**（`type=ALL` + temporary + filesort）→ 加 `(case_key, run_id)` 后 **46 ms**（`ref`，rows=200）（`test-result-casekey.txt`） |
| keyset 结果正确 | **PASS 6/6** | 页大小正确、首行 = offset 下一页第 2 行（游标行不重复）、两页无重叠、occurred_at 降序、id 降序（`http-correctness.txt`） |
| 单测 | **PASS** | `AuditQueryTest` 8 个用例 0 失败，新增 `cursorKeyset`（游标解析 + 非法游标 400）（`AuditQueryTest.txt`） |

## 2. 改了什么

| 文件 | 变更 |
|---|---|
| `db/migration/V10_1__audit_keyset.sql` | 加 `audit_log.idx_audit_time_id(occurred_at, id)`、`test_result.idx_test_result_case_key(case_key, run_id)`；文件头写明为什么不加三列索引 |
| `db/down/V10_1__audit_keyset.down.sql` | 反向 `DROP INDEX` 两条（不删数据、不改列） |
| `application/iam/AuditQuery.java` | 新增 `cursorAt/cursorId` 两个分量 + 6 参兼容构造器；`keyset()` / `normalized()` |
| `infrastructure/AuditRepositoryJdbc.java` | `page()` 分两支：带游标走 keyset（`LIMIT ?`，无 OFFSET），否则走页码模式；`COUNT(*)` 两支共用 |
| `api/iam/AuditController.java` | `GET /api/audit` 新增可选 `cursor=<epochMillis>_<id>`；非法游标 400（`INVALID_ARGUMENT`） |
| `test/.../AuditQueryTest.java` | 新增 `cursorKeyset`（解析进查询 + 400） |

向后兼容：不传 `cursor` 时 SQL 与旧版逐字相同；`page` 参数在 keyset 模式下只回显。

## 3. 两个必须记住的坑

### G-B5-1（P1，语义等价但性能差 30 倍）行构造器写法不走 range

```sql
-- 写法 A：实测 1,319 ms（type=ref/ALL，扫 49 万行）
AND (occurred_at, id) < ('2026-01-06 18:53:20.000', 1019143)
-- 写法 B：实测 44 ms（type=range，Backward index scan）
AND (occurred_at < ? OR (occurred_at = ? AND id < ?))
```
MySQL 8.0.46 对 `(a,b) < (?,?)` 这种行构造器在**本项目索引形态下没有做 range 优化**，
两种写法结果集完全一致。代码里用 B，并在注释里写了原因，防止后来人「简化」回 A。

### G-B5-2（P2）前端未接 cursor

审计页仍用 `page=X` 页码模式；后端已具备 keyset 能力，但 UI 还没把「下一页」换成游标。
影响：管理员翻到第 2.5 万页仍需 1.2 s（不再无限恶化，但没有享受 44 ms）。属后续迭代项。

### G-B5-3（P3）OFFSET 深翻非线性

同一张表 offset 900k 的耗时（3,479 ms）是 500k（889 ms）的近 4 倍，
说明该查询在深翻时还叠加了回表随机 IO，不能按「线性行数」估算。这也是必须上 keyset 的实证理由。

## 4. 复现

```bash
# 1) 造数（写 100 万行 audit_log，project_id=-1 作隔离标记；克隆 test_result_b5 100 万行）
bash docs/evidence/v2.0/b5-keyset/seed.sh
# 2) 迁移前后 SQL 计时（含 EXPLAIN）
bash docs/evidence/v2.0/b5-keyset/measure.sh before
bash scripts/db-migrate.sh
bash docs/evidence/v2.0/b5-keyset/measure.sh after
# 3) HTTP 实测（真实后端 + 真账号）
bash docs/evidence/v2.0/b5-keyset/http.sh
# 4) down/up 回放 + 清理造数
bash docs/evidence/v2.0/b5-keyset/down-up.sh
```

上述四个脚本由本轮实际执行的 `/tmp/b5-*.sh` 整理而来；造数用 `project_id=-1 AND action='B5-SEED'`
做标记，清理按 10 万一批删除，实测 10 批删完、`audit_log` 回到 1,566 行（`down-up-cleanup.txt` 末两行）。

## 5. 文件表

| 文件 | 内容 |
|---|---|
| `raw/audit-timing-before.txt` | 迁移前 SQL 计时 + 4 条 EXPLAIN（含全局 `type=ALL` + filesort） |
| `raw/audit-timing-after.txt` | 迁移后 SQL 计时 + EXPLAIN（keyset 走 range / Backward index scan） |
| `raw/test-result-casekey.txt` | case_key 有无索引两轮计时 + EXPLAIN |
| `raw/http-timing.txt` | 5 个真实 HTTP 请求的最小耗时（含登录态、`COUNT(*)`） |
| `raw/http-correctness.txt` | 6 条分页正确性断言 |
| `raw/http-p-*.json` / `http-g-*.json` | 原始响应体（各 20 行） |
| `raw/down-up-cleanup.txt` | Flyway 记录、down/up 回放、造数清理 |
| `raw/AuditQueryTest.txt` / `mvn-AuditQueryTest.log` | 单测结果 |
| `raw/V10_1__audit_keyset*.sql` | 迁移与回退脚本快照（与库内一致） |

## 6. 遗留

- G-B5-2 前端接入 cursor（P2）；G-B5-1 已在代码注释固化，不需要再动。
- 索引体积未能取证：`robot` 账号读 `mysql.innodb_index_stats` 被拒（`ERROR 1142`），
  `information_schema.tables.index_length` 也没有在 100 万行时截存。若现场日需要报磁盘增量，
  需在造数状态下重跑一次 `seed.sh` 后查 `SHOW TABLE STATUS`。
- 本项只证明「API 与索引可用」，不改变 G5 的判定权。
