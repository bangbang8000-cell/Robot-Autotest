# PUBX-02 证据：差异对比与同步/分叉（FR-v2.1-12）

## 结论

- **真实 HTTP 验收 15 项全 PASS**（`run.log`，退出码 0）：diff 三类方向正确、
  分叉只置状态不动内容、同步后 hash 逐字节对齐、审计两条都落 `audit_log`。
- **后端单测 23 条全绿**：`AssetUpgradeServiceTest` 9 条 + `AssetVersionServiceTest` 14 条。
- **前端组件 8 条全绿**（`CaseSetsView.spec.ts`）：版本读数渲染、diff 行渲染、
  分叉动作走确认框且不动条目、非副本集合显示占位。

## 判据对照（DEV-PLAN §4.4 / §10.9）

| 判据 | 证据 |
|---|---|
| 三类差异分类正确 | PUBX02-04：MODIFIED=TC-001（标题）/ ADDED=TC-003 / REMOVED=TC-002，方向不反 |
| 同步后版本对齐 | PUBX02-08/10/11：fork_state=SYNCED、synced=平台 v2、副本 hash = 平台 hash、diff 清空 |
| 分叉后副本不动且列表可辨 | PUBX02-05/06：fork_state=DIVERGED（库同读数）、条目逐字节不变、版本号不变 |

## 命令

```bash
cd backend && mvn -o -B test -Dtest=AssetUpgradeServiceTest,AssetVersionServiceTest
mvn -o -B -q package -DskipTests && bash /tmp/restart-backend.sh
cd frontend && npx vitest run src/views/CaseSetsView.spec.ts
python3 docs/evidence/v2.1/PUBX-02/run.py
```

## 原始输出

| 文件 | 内容 |
|---|---|
| `run.log` | 15 项断言完整输出，最终 FAIL 0 |
| `out/audit.log` | `audit_log` 里 ASSET_DIVERGE / ASSET_SYNC 的原始行（action / resource_id / detail） |

## 设计口径（写死，改动须回改文档）

1. **diff 方向以平台为准**：ADDED = 平台新增（副本缺）、REMOVED = 平台已删（副本多余）。
   方向反了用户会反向同步，把平台改动冲掉。
2. **按 case_code 匹配行**，顺序差异归 MODIFIED（detail 写两边 seq）——三类口径是产品定的。
3. **同步是真对齐**：缺的用例按 case_code 导入本项目；复用的行把 title/priority 对齐平台值
   （hash 覆盖这两个字段，不对齐就永远差一版）；条目按平台顺序整体重建（item_source=
   PLATFORM_SYNC）；然后 synced 读数推到平台当前版本、fork_state 回 SYNCED。
4. **分叉是声明不是修改**：只置 fork_state=DIVERGED，synced 读数与内容原样保留——
   用户随时可以再点同步回对齐（PUBX02-05→08 的路径连贯可走）。
5. **审计**：ASSET_SYNC（detail 带 toVersion/imported/updated/rows）与 ASSET_DIVERGE
   （detail 带 keptVersion）各一条，`out/audit.log` 可直接取证。
6. **前端落点**：`CaseSetsView.vue` 成员面板内嵌「平台资产版本」区
   （`data-testid="asset-panel"` / `asset-fork` / `asset-has-new` / `asset-diff` /
   `diff-row` / `asset-sync` / `asset-diverge`）；文案全走语言包（`caseSets.asset*`）。

## 踩过的坑（已修，防复发）

- **夹具同행陷阱**：首轮验收让平台集与副本引用**同一批 test_case 行**，改标题两边一起变，
  MODIFIED 永远不出现。修正：平台用例放独立「属主项目」，与副本项目的用例行分开——
  这才是「副本按 case_code 对齐」的真实语义。
- **replaceItems 的 INSERT 被租户守卫拦成 403**：INSERT 语句没带 project_id 字面量。
  改 `INSERT ... SELECT ... FROM test_set WHERE project_id = ? AND id = ?`，
  既满足守卫又在落行前再验一次集合归属。
- **复用行不对齐字段导致 hash 永远差一版**：补 `updateCaseFields`，
  同步时把复用行的 title/priority 对齐平台值。

## 遗留

- 同步是**全量覆盖式**（条目整体重建）：副本上的本地改动会被平台内容覆盖——
  这正是「分叉」按钮存在的意义；细粒度（按行挑选）合并不在本版范围。
- 平台用例集的管理入口（建平台集、维护平台用例）仍是 SQL/脚本层面，页面归后续版本。
