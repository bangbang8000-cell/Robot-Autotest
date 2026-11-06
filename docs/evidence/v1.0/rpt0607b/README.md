# RPT-06 / RPT-07 残余收口：导出期再判门禁 + 清理的库侧同事务（2026-11-06）

两条都是 WBS 第 43/44 行第 7 列点名的残余，本批逐条闭合，并给出可在本地 WSL 复跑的证据。

## 1. 缺口

| 包 | 原残余 | 实际危害 |
|---|---|---|
| RPT-07 | 「门禁只覆盖解析入库路径」 | `ExportService` 导出时读的是 `run.exportable` **这一列**。列由入库时写入，重析、手工改库、历史数据迁移都可能让它与 metadata 脱节 —— 列一旦是 1，外部版就放行，十项 metadata 缺一半也能导出去 |
| RPT-06 | 「审计写入与删文件非同事务」 | `markPurged` 与 `audit.insert` 各自单独执行，中途异常会留下「产物行已标 purged 但没有审计」的中间态，事后查不到是谁清的、清了多少 |

## 2. 做法

### 2.1 RPT-07：导出期按 metadata 重算，不信库列

`application/report/ExportService.java` 把原来的

```java
if (mode == ExportMode.EXTERNAL && !report.exportable()) { ... }
```

改成按当前 metadata 现算：

```java
// 导出期二次拒绝（RPT-07）：按当前 metadata 重算门禁，不依赖 run.exportable 列的旧结论，
// 保证「入库时算过」与「导出时再算一次」是同一条口径，库列被改错也放不过去。
GateResult gate = MetadataGate.check(report.metadata());
if (mode == ExportMode.EXTERNAL && !gate.exportable()) {
    List<String> missing = gate.missing();
    ...
}
```

`MetadataGate` 是入库用的同一个类，**口径完全一致**（十项必填、null/空串/全空白算缺失、>40 字符算超长、
键名大写精确匹配、缺失清单按十项顺序稳定），不存在两套规则漂移。内部版（`mode=internal`）照旧不受门禁限制。

新增 `ExportServiceTest#externalRejectsWhenStaleColumnSaysExportable`：把 `report(true, List.of(), ...)`
（列说可导出）喂给服务，断言仍抛 `ExportRejectedException`、`metadataMissing=[DUT, LINK]`、
不写 `export_record`、不落文件。

### 2.2 RPT-06：文件先删、库侧后写，且两处写入同事务

`application/report/RetentionService.java` 新增 `TransactionTemplate`（Spring 装配走
`PlatformTransactionManager`；6 参构造器保留给计划/单测，`tx == null` 时不分事务）：

```java
private void markAndAudit(long projectId, RetentionPlanner.Item item,
                          List<Long> purgedIds, String operator) {
    Runnable write = () -> {
        retention.markPurged(projectId, item.runDbId(), purgedIds);
        audit.insert(projectId, TenantContext.userId(), AUDIT_ACTION, "run", item.runId(),
                detail(item.runId(), item.bytes(), operator));
    };
    if (tx == null) { write.run(); return; }
    tx.executeWithoutResult(status -> write.run());
}
```

顺序理由写在方法 javadoc 里：文件系统不参与数据库事务，所以只能「先删文件、后写库」。
两者之间若事务失败，结果是**文件已删、产物行仍是 `ok`**；下一次清理时 `delete` 返回 false，
但该行照样被置 purged 并补写审计 —— 这就是自愈路径，不会永久卡在中间态。
反过来若先写库再删文件，失败会留下「已标 purged 但文件还在」，既占空间又查不出矛盾。

新建 `RetentionServiceTest`（此前 `RetentionService` 一条单测都没有），5 条：

| 用例 | 验证点 |
|---|---|
| `dryRunTouchesNothing` | dryRun 不删文件、不改 availability、不写审计，`txManager.begins == 0`（连事务都不开） |
| `executeDeletesFilesAndWritesAuditInOneTransaction` | 删 2 个文件、`{101,102}` 置 purged、1 条 `artifact_purge` 审计、**commits 1 / rollbacks 0** |
| `auditFailureRollsBackPurge` | 审计写失败 → 抛异常、**rollbacks 1 / commits 0**，证明两处写入不会一成一败 |
| `missingFileIsHealedOnNextRun` | 文件本就不存在 → `deletedFiles=0`，但行仍置 purged 并补写审计（自愈） |
| `nonAdminRejected` | 非管理员 `FORBIDDEN`，不碰文件、不进规划 |

## 3. 证据怎么造

脚本 [`rpt0607b-e2e.sh`](rpt0607b-e2e.sh)，在本地 WSL 打真后端（`:8081`）+ 真 MySQL（`robot_dev`）：
**61 项断言，0 失败**，完整输出见 [logs/00-run.log](logs/00-run.log)。

### 3.1 RPT-07 段（第 1–3 节）

用 `rf-stack` 里的 robot 真跑一个只有 4 项 metadata 的套件（[suite/partial_metadata.robot](suite/partial_metadata.robot)），
解析入库 → 门禁为红 → 再走真实 HTTP 导出：

| 步骤 | 读数 |
|---|---|
| 解析入库 | `exportable=false`，缺失 6 项，顺序 `DUT/LINK/SW_BUILD/BASELINE/STARTED_AT/CASE_KIND` |
| 入库即拒 | 外部版 `400`，`data.metadataMissing` 同上 6 项 |
| 内部版 | `200`（不受门禁限制，写 `export_record` `internal` 一条） |
| **手工 `update run set exportable=1, metadata_missing=null`** | 库列已被改成可导出 |
| **列说谎时再导** | **仍 `400`**，拒绝项仍来自 metadata；`export_record` 中 `external` 计数 `0` |
| 补录六项 | `200`，`exportable=1`，外部导出 `200`，下载 `200` / 3619 B，DOCX 是有效 zip 且含 `word/document.xml` |

第 4 步是本批的核心证据：**列与 metadata 打架时，导出期信 metadata**。

### 3.2 RPT-06 段（第 4–8 节）

在专用项目（`code=RPT0607B`，避免碰真项目）里造 27 次运行：

- **19 个「最近」空运行** —— `keepRecentRuns=20` 会把最新 20 次全部保护起来，必须先把这 20 个名额占掉，
  老运行才会进入候选（19 个空运行 + R07 自己正好 20）；
- 3 个候选（200/201/202 天前，产物 150+40 / 120+30 / 60+10 MB = **410 MB**）；
- 1 个 150 天前的干扰运行（无产物，不进候选）；
- 3 类受保护样本：`pinned=1`（90 MB）、`note=p0_evidence`（80 MB）、有 `export_record`（70 MB）。

真实大文件用 `dd` 落在 `/tmp`，`run_artifact.path` 指向真实路径，删除走生产实现
`ArtifactFileStoreImpl`（只删登记过的单文件，删空后顺手移除空目录，**不做目录递归**）。

| 步骤 | 读数 |
|---|---|
| 项目登记占用 / run 行数 | 650 MB（681574400）/ 27 |
| dry-run | `runCount=3`、`totalBytes=410 MB`、`projectBytes=650 MB`、`deletedFiles=0`、`purged=0`、审计 0、文件仍在 |
| 候选原因 | 三条均为 `expired` |
| 执行 | `runCount=3`、`deletedFiles=6`、`totalBytes=410 MB` |
| 文件 | 6 个全删，三个目录随空一并移除 |
| 库 | `purged=6` 且 `sum(size_bytes)=410 MB`（占用量保留可查）；`run` 27 行、`test_result` 10 行**前后不变**；`BIG-001` 的三条 PASS 仍可查 |
| 剩余占用 | `251658240` = 240 MB（= 90 + 80 + 70，只剩受保护的三份） |
| 三类保护 | `pinned` / `p0` / 有导出记录：`availability` 仍 `ok`，文件仍在 |
| 审计 | 3 条 `artifact_purge`，`detail` 分别为 `{"runId":"RPT06-BIG-001","bytes":199229440,...}` / `157286400` / `73400320` |
| 幂等 | 再跑一次：`runCount=0`、`deletedFiles=0`、审计仍 3 条（不重复写） |

## 4. 门禁读数（八步全绿）

| 项 | 结果 |
|---|---|
| `tools/subset_font.py` | PASS（1037 字 / 180974 B gzip，预算 819200 B） |
| 前端 `vitest run` | 28 文件 / **204 用例**（本批无前端改动） |
| 前端 `vue-tsc --noEmit` | 0 错误 |
| 前端 `vite build` | `built in 9.79s` |
| 后端 `mvn -o -B test` | **631 / 0 / 0**（本批 +6：`RetentionServiceTest` 5 + `ExportServiceTest` 1） |
| `scripts/lint.sh` | PASS |
| `check_release_docs.py` | PASS（7 版本 / FAIL 0 / WARN 0） |
| `check_wbs_consistency.py` | PASS（96 包 / 已完成 13 / 待验 83） |

门禁日志见 [logs/01-subset_font.log](logs/01-subset_font.log) … [logs/08-wbs_consistency.log](logs/08-wbs_consistency.log)。

## 5. 残余与产品边界

1. **定时清理未在真实 cron 上验证**：`report.retention.enabled` 默认 `false`，本批走的是手工触发
   `POST /api/reports/retention/run`。`enabled=true` 后由 `RetentionScheduler` 按 `0 3 * * *` 拉起，
   这条链路需要一台常开的实例跑过夜才算验证。
2. **事务回滚路径只在单测里制造过**：`auditFailureRollsBackPurge` 用假件让审计抛异常，
   没有在真库里制造「purged 已写、审计失败」的回滚现场（要做需要临时插桩）。
   文件先删后写库导致的「文件没了但行仍 ok」已由 `missingFileIsHealedOnNextRun` 覆盖。
3. **配额触发（`maxProjectGb`）本批未跑真实数据**：脚本里的候选三条都是 `expired`；
   `quota` 分支由 `RetentionPlannerTest#GB 上限触发` 单测覆盖，未在真库上按 GB 上限压一遍。
4. **导出期重算只逐格式验了 DOCX**：`export` 的四种格式共用同一道门禁判断，但只有 DOCX 走了真实下载与 zip 校验。
5. 清理只作用于 `run_artifact` 登记过的文件；未登记在现场目录里的文件永远不会被带走（这是刻意的安全边界，
   不是缺口）。

## 6. 改动清单

| 文件 | 改动 |
|---|---|
| `backend/.../application/report/ExportService.java` | 外部导出改按 `MetadataGate.check(report.metadata())` 现算，不再读 `run.exportable` 列 |
| `backend/.../application/report/RetentionService.java` | 新增 `TransactionTemplate`；`execute()` 改为先删文件、`markPurged + audit.insert` 同事务；补自愈说明 |
| `backend/src/test/.../report/RetentionServiceTest.java` | **新建**，5 条（dry-run / 同事务 / 回滚 / 自愈 / 权限） |
| `backend/src/test/.../report/ExportServiceTest.java` | 新增 1 条：库列说谎时导出期仍拒绝 |
| `docs/evidence/v1.0/rpt0607b/` | 本目录：脚本 + 套件 + 日志 + 导出样本 |
