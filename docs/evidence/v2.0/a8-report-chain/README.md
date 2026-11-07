# A8 · 报告真实链路抽样（冻结 / 删源 / 聚合 / 三格式 / 保留策略）

> 对应 06 计划 §3 **A8**：`报告真实链路抽样（冻结→删源 run→再渲染 hash 校验；3-run 聚合；三格式各 1 份；保留策略 dry-run 与实删）`，
> 并承接 A6 未完成的 **TC-v2.0-67 PDF `downloadUrl`** 取证。
> 覆盖 TEST-PLAN：**TC-03 / TC-04 / TC-05 / TC-08 / TC-67**。
> 执行时间：2026-11-07 14:42–14:44（CST）。全部读数来自真实 HTTP + 真库 + 真实文件，脚本与原始产物在 `raw/`。

## 1. 判据与结论

| 计划判据 | 结论 | 依据 |
|---|---|---|
| 冻结 → 删源 run → 再渲染，hash 校验 | **PASS** | 冻结后渲染 589 B / sha256 `cff41a60…ec2e`；删掉源 run（库 5 表 + 产物目录）后再次渲染 **逐字节相同**；`test_report.snapshot_hash` 未变；源 run 明细接口 404 |
| 3-run 聚合 | **PARTIAL** | 结果概览 digest 能聚合 3 个 run（4 PASS / 1 FAIL / 1 SKIP，失败分布 `A8 FAIL 01 ×1` 与明细一致）；但**「测试报告」侧不支持多 run**：`POST /api/test-reports` 用 `runIds` 数组 → `400 runId 不能为空` |
| 三格式各 1 份 | **PARTIAL** | HTML/DOCX/PDF 均 200 且魔数正确、页眉含 version+frozenAt、无敏感串；**PDF 中文标题变成 `????`**（fallback 简易生成器只内嵌 Helvetica Type1，无 CJK） |
| 保留策略 dry-run 与实删 | **PASS** | dry-run 与实删清单完全一致（1 个 run / 4141 B / `expired`）；实删 3 个文件；run/test_result 行保留；已导出的 run 被保护；项目 29 产物 65→65 零误删；审计 2 条 |
| TC-67 PDF `downloadUrl` 指向 8001 | **PASS** | `exportId=28`，`downloadUrl=/api/exports/28/download?projectId=76`，经 `http://127.0.0.1:8001` 下载 **200 / 199,465 B**，sha256 与 `export_record` 一致 |

`raw/a8-result.json` → `checks`：27 项里 23 项 true，4 项 false，**4 项 false 全部是产品侧缺口/口径偏差**（见 §7），不是脚本失败。

## 2. 方法与对象

- 隔离项目：`projectId=76`（`code=a8-report-20261107144236`），不污染既有项目 29。
- 3 个套件均带 §4.1 的 10 项 metadata，`STARTED_AT` 取运行前 60 s（UTC），避免「finishedAt < startedAt」的假异常。
- 3 个真实 run（串行，`concurrency=1`，`timeoutSeconds=150`）：

| # | 套件 | RUN_ID | 终态 | 明细（库内一致） |
|---|---|---|---|---|
| 1 | `suites/a8/pass2.robot` | `RUN-20261107144240-580000` | `finished` | PASS 2 |
| 2 | `suites/a8/skip1.robot` | `RUN-20261107144242-580000` | `finished` | PASS 1 / SKIP 1 |
| 3 | `suites/a8/fail1.robot` | `RUN-20261107144245-580000` | `failed` | PASS 1 / FAIL 1 |

- 授权口径执行到位：取证前后各一次 `mysqldump` → `raw/db-before.sql.gz`（152,798 B，sha256 `3d13205e…`）、
  `raw/db-after.sql.gz`（154,769 B，sha256 `94d9f951…`）。所有写入只落在本次新建的 project 76 与其 3 个 run。

## 3. 冻结 → 删源 → 再渲染

```
POST /api/test-reports            {"projectId":76,"runId":"RUN-…144240-…","title":"A8 冻结报告 …"}  → reportId 10
POST /api/test-reports/10/freeze                                                                     → state FROZEN, version 2
GET  /api/test-reports/10?external=true                                                              → 589 B, sha256 cff41a60…ec2e
-- 删源：keyword_result / test_result / run_artifact / run_attempt / run_metadata / run 六表按 runId 精确删除，
        产物目录整体移出到 raw/deleted-run-artifacts/（留证，不真删）
GET  /api/reports/RUN-…144240-…?projectId=76                                                        → 404
GET  /api/test-reports/10?external=true                                                              → 589 B, sha256 cff41a60…ec2e（逐字节相同）
```

`test_report.snapshot_hash` 删除前后都是 `e882e9aab843cc1ac83a12ee57ca544ea0ea7987bfd59f08208404c591cf5312`，
`frozen_at = 2026-11-07 14:42:48.156`。渲染原文见 `raw/render-before.json` / `raw/render-after.json`。

> 注：TC-03 还要求「修改接口返回 409」。v2.0 的 `TestReportController` 在冻结态只暴露
> `revoke` / `archive` 两个状态迁移接口，**没有内容修改接口**（快照只在 freeze 时由服务端生成），
> 因此「冻结后改内容被拒」在这一版没有可调用的入口，本项按「快照不可变（hash 不随源数据删除而变）」法代替并记录。

## 4. 3-run 聚合

`GET /api/projects/76/report-digest?days=30` 返回 3 条 `recentReports`，逐 run 计数与明细一致：

| RUN_ID | passRateBp（实测） | 代码口径 `pass/(pass+fail+skip)` | PRD 口径 `pass/(pass+fail)` |
|---|---|---|---|
| `…144240` | **10000** | 10000 ✅ | 10000 ✅ |
| `…144242`（含 1 skip） | **5000** | 5000 ✅ | 10000 ❌ |
| `…144245`（含 1 fail） | **5000** | 5000 ✅ | 5000 ✅ |

聚合 4 PASS / 1 FAIL / 1 SKIP → 实测 **66.67%**（代码口径），PRD 口径应为 **80.00%**。
失败分布 `topFailedCases = [{caseKey: "run-…144245.A8 FAIL 01", failures: 1}]`，与 `test_result` 逐条一致。

**两个口径缺口**：

1. **多 run 聚合报告未实现**：`CreateRequest(projectId, runId, title)` 只收单个 `runId`；
   用 `runIds=[3 个]` 发起 → `400 INVALID_ARGUMENT: runId 不能为空`。PRD FR-v2.0-03 写的是「选 1..N 个 run」。
2. **通过率把 skip 计入分母**：与 PRD FR-v2.0-04「跳过不计入分母，单列」冲突（`ReportDigest` 的 javadoc 自述
   「与 OVW-01 一致：skip 计入分母」，即代码与 PRD 之间本身就不一致）。

## 5. 三格式导出（冻结报告）

| 格式 | 状态 | 字节 | Content-Type | 魔数 | sha256 前 16 |
|---|---|---|---|---|---|
| HTML | 200 | 1,558 | `text/html;charset=UTF-8` | `<` | `87c9ccb4108fb570` |
| DOCX | 200 | 2,678 | `...wordprocessingml.document` | `PK` | `c3427370dd5af997` |
| PDF | 200 | **1,535** | `application/pdf` | `%PDF` | `76d72b9f73aa4ded` |

内容一致性（`export_content`）：

| 检查 | HTML | DOCX | PDF |
|---|---|---|---|
| 含 version + frozenAt | ✅ | ✅ | ✅ |
| 中文标题保留 | ✅（`A8 冻结报告`） | ✅ | ❌ **`A8 ???? 20261107142938`** |
| 无 IP / 路径 / 库名 / 账号 | ✅ | ✅ | ✅ |

**PDF 中文丢失的成因**（原始字节可自证）：`raw/export-pdf` 只有 1.5 KB、`/BaseFont /Helvetica`（Type1，无 CJK），
正文用 `(...) Tj` 直接写字面量；而 run 级导出（§6）走 Chrome 打印，产物 199 KB，中文正常。
即**同一产品里两条 PDF 通道质量不一致**，测试报告侧用的是 fallback 简易生成器。

## 6. TC-67：run 级 PDF `downloadUrl`

```
POST /api/reports/RUN-…144242-…/export?projectId=76&format=pdf&mode=external
→ {"exportId":28,"format":"pdf","mode":"external","bytes":199465,
   "sha256":"1a19e3fb88476dac5c39a9905621383a41c3dc760c22c1204f0577546496219c",
   "downloadUrl":"/api/exports/28/download?projectId=76"}

GET http://127.0.0.1:8001/api/exports/28/download?projectId=76
→ 200, 199,465 B, sha256 与 export_record.id=28 完全一致
```

打印基址来自后端进程环境 `REPORT_PRINT_BASE_URL=http://127.0.0.1:8001`
（`REPORT_PDF_CHROME=/home/evergreen/robot-autotest/scripts/chrome-wsl-bridge.sh`）。
> 取证动作：新增 drop-in `~/.config/systemd/user/robot-autotest-backend.service.d/30-a8-exercise.conf`，
> 只保留 `REPORT_PRINT_BASE_URL=8001`（默认 drop-in 10 里是 5273 的 Vite 开发前台，指向 5273 不符合 TC-67 口径）。
> 取证期间曾临时加 `REPORT_RETENTION_MAX_DAYS=0` / `KEEP_RECENT_RUNS=0` 制造候选，**已移除**，保留策略回到代码默认；
> 重启后 `active`、`/api/health` 200（`raw/a8-env.txt`）。

## 7. 保留策略：dry-run 与实删

阈值（取证时临时）：`max_days=0`、`keep_recent_runs=0`；实删后立即恢复。

| 项 | dry-run | 实删 |
|---|---|---|
| 候选数 | 1 | 1 |
| 候选明细 | `RUN-…144245`，4,141 B，`expired` | 同左 |
| `deletedFiles` | 0 | **3** |
| 项目总占用 | 7,380 B | 7,380 B（清理前口径） |

- **清单一致**：dry-run 与实删的 runId 序列完全相同。
- **保护规则生效**：已导出 PDF 的 `…144242` 未进候选（`protectedRun` 命中导出记录）；
  被冻结的 `…144240` 在删源步骤里已无产物，自然不入候选。
- **只删文件不删记录**：清理后该项目 `run` 行数 2（另一个已在删源步骤移除）。
- **零误删**：项目 29 的 `run_artifact` 行数 65 → 65（`no_cross_project_purge=true`）。
- **审计**：`audit_log.action='artifact_purge'` 计 2 行（本次实删 1 次 + 上一轮取证 1 次）。

## 8. 复现

```bash
cd ~/robot-autotest/docs/evidence/v2.0/a8-report-chain
python3 probe.py            # 约 2 分钟：备份库 → 建项目/套件 → 3 个 run → 冻结/删源 → 三格式 → 保留策略 → 再备份库
```

依赖：后端 `:8081`（入口 `:8001`）、`qa-e2e-admin` 口令在 `~/.config/robot-autotest/env`、`mysqldump`/`mysql` 可用、
`REPORT_PDF_CHROME` 指向 `scripts/chrome-wsl-bridge.sh`（Windows Chrome 无头打印）。

## 9. 证据清单

```
probe.py                       全链路探针（真实 HTTP + 真库 + 文件解析）
raw/a8-result.json             27 项 checks + 全部读数
raw/db-before.sql.gz           取证前 dev 库快照（152,798 B）
raw/db-after.sql.gz            取证后 dev 库快照（154,769 B）
raw/render-before.json         删源前冻结报告渲染原文（589 B）
raw/render-after.json          删源后渲染原文（逐字节相同）
raw/export-pdf / -docx / -html 三格式产物（PDF 1,535 B，含 ???? 证据）
raw/deleted-run-artifacts/     删源 run 的产物目录（留证，未真删）
raw/a8-env.txt                 后端进程与端口/健康检查快照
```

## 10. 结论

链路本身立得住：**冻结快照与源数据解耦**（删源后逐字节一致）、**保留策略可预览、只删文件、有保护、有审计**、
**导出 URL 走统一入口且摘要可核**。四个缺口都是产品侧口径问题，已登记待 v2.1：
① PDF（测试报告通道）无 CJK；② 有用例失败时 run 记 `failed` 且文案 `分片执行失败: ok`（分片其实成功）；
③ 通过率 skip 入分母，与 PRD 不一致；④ 测试报告不支持多 run 聚合。
