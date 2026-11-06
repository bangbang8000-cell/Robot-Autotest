# PUBX-01 证据：用例资产版本标识与引用方检测（FR-v2.1-10/11）

## 结论

- **真实 HTTP 验收 13 项全 PASS**（`run.log`，退出码 0）：平台集 projectId=0 管理路径登记版本、
  写钩子自动推进、内容不变幂等、顺序算内容、改名不涨版本、bindCopy 登记对齐点、
  平台升版后引用方列表出现提示、两条负向（404 / 400）。
- **单测 28 条全绿**：`AssetVersionServiceTest` 14 条（hash 规范化 / 幂等 / behind 双条件 /
  bindCopy / references）+ `AuditAspectTest` 14 条（TestSetService 构造签名变更回归）。
- **顺带修复一个 v2.0 存量 bug**：`TestSetRepositoryJdbc.reorder` 在两元素换序时撞
  `uk_test_set_item(test_set_id, seq_no)` 唯一键，真实链路 500（`Duplicate entry '8-1'`）。
  改为三段式：整体平移腾位 → 落新序 → 未点名条目接尾。此 bug 由本包验收暴露，修复后有
  reorder 200 + 版本 +1 的端到端证据（PUBX01-06）。

## 判据对照（DEV-PLAN §4.4）

| 判据 | 证据 |
|---|---|
| 内容未变不出新版本 | PUBX01-04：重复 refresh 后 version 不变、仍 1 行、`updated_at` 不动 |
| 变更后引用方列表出现提示 | PUBX01-10/11：平台升 v2 后 `references` 里副本 `hasNewVersion=true`、`latestAssetVersion=2` |
| 版本可追溯 | `asset_version` 表每集一行：版本号 + 64 位内容 hash + synced 读数 + fork_state；DB 断言在 run.py 内 |

## 命令

```bash
# 1) 迁移（65 张表，flyway 9.3）
bash scripts/db-migrate.sh
# 2) 单测
cd backend && mvn -o -B test -Dtest=AssetVersionServiceTest,AuditAspectTest
# 3) 打包 + 重启
mvn -o -B -q package -DskipTests && bash /tmp/restart-backend.sh
# 4) 真实 HTTP 验收
python3 docs/evidence/v2.1/PUBX-01/run.py
```

## 原始输出

| 文件 | 内容 |
|---|---|
| `run.log` | 13 项断言的完整输出（PASS/FAIL 明细 + 夹具 id），最终 FAIL 0 |
| `../v2.1/PUBX-01/run.py` | 验收脚本本身（夹具种入、断言、清理全部可从零重跑） |

## 设计口径（写死，改动须回改文档）

1. **版本号唯一驱动是内容 hash**：hash 不变则连 `updated_at` 都不动（PUBX01-04）。
2. **顺序算内容**：hash 覆盖 `seq_no`（PUBX01-06，换序 → v+1）。
3. **改名不算内容**：`PUT /case-sets/{id}` 不接钩子（PUBX01-07）。
4. **hash 规范化格式固定**：按 `seq_no` 升序，每行 `seq_no|case_code|title|priority`，
   行间 `\n`。格式一旦发布不得改，否则等于给所有资产发一次假升级。
5. **归属沿用 V7_3 模式**：`owner_scope` + 平台行 `project_id` NULL + 生成列
   `scope_project_id` 做唯一性；`asset_version` 行的 `project_id=0` 表示平台资产自身。
6. **平台集版本登记走真实接口**：`POST /api/test-sets/{id}/asset-version/refresh?projectId=0`
   （admin 可过 `bindProject(0)`），不另开管理端点。
7. **读接口不写库**：GET version 不推进版本，推进只发生在写钩子与显式 refresh。

## 遗留

- 副本内容与平台差异的**逐行对比与同步动作**归 PUBX-02（本包只做版本读数与引用方检测）。
- 平台用例集的建立入口（管理端页面）不在本包：验收里平台集按 V7_3 同款 SQL 落库，
  其版本登记走的是真实 HTTP。
- 版本按「每次写操作」推进：连续两次 addItem 是两代版本（v1→v2），不做批内合并——
  口径简单且与「hash 变了才 +1」一致。
