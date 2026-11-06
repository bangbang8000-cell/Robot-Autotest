# RPT-05 残余收口：对外（external）导出成功分支 + PDF/DOCX 文案逐字比对

> 批次：RPT-05 残余（WBS #42，`docs/plan/WBS-STATUS.md` 第 81 行第 7 列）
> 日期：2026-11-06 · 环境：WSL Ubuntu-24.04 权威副本 `~/robot-autotest`
> 结论：**原三条残余中两条已关闭**（对外成功分支有正样本；PDF 文案已逐字比对）；
> 第三条（print token 存内存）判定为**单实例部署下的设计选择**并在此写明边界，不改实现。

## 1. 收口前的三条残余

| # | 残余原文 | 本次处置 |
|---|---|---|
| 1 | 仅内部模式有正样本（该 run 无 metadata，对外成功分支缺样本） | ✅ 造 10 项 metadata 齐全的真实 RF 产物，对外导出 PDF/DOCX 全链路跑通 |
| 2 | token 存内存不跨实例 | ⚠️ 保留，写明理由与失效表现（见 §6） |
| 3 | 无 PDF 文本抽取工具，文案未逐字比对 | ✅ pypdf 抽取 PDF 文本、`word/document.xml` 抽取 DOCX 文本，10 项 metadata 逐字断言 |

## 2. 做法

一条命令：

```bash
cd ~/robot-autotest && bash docs/evidence/v1.0/rpt05b/rpt05b-e2e.sh
```

脚本内链路（每一步都落日志，见 `logs/`）：

1. **造真实产物**：`suite/print_metadata.robot` 用 RF 7.5.1 真跑一遍（2 通过 1 故意失败），
   `*** Settings ***` 里写满 10 项 `Metadata`（键名大写、值 ≤ 40 字符，`STARTED_AT` 为 UTC `Z`）。
   产物落 `var/artifacts/<RUN_ID>/shard-0/`，与执行链路的目录布局一致。
2. **登记 run**：`run` 表插一行（`artifacts_dir` 指向上面的真实目录），不伪造 XML 结构。
3. **解析入库**：`POST /api/reports/{runId}/parse?projectId=1`，**不传显式路径**，
   走 `artifacts_dir/shard-0/output.xml` 回退分支（贴近平台真实调用方式）。
4. **打印页取数**：`POST /api/reports/{runId}/print-token?mode=external` 拿一次性 token，
   再经 Vite（5273）代理 `GET /api/print/{runId}`，确认 `mode=external / internal=false`。
5. **导出**：`POST /api/reports/{runId}/export?mode=external&format=pdf|docx` → 下载 → 校验。
6. **逐字比对**：`pdf-text.py`（pypdf）抽 PDF 文本；`unzip -p ... word/document.xml` 抽 DOCX 文本。
7. **落库/审计**：`export_record`、`audit_log(report_export)`、`run.exportable/metadata_missing`。
8. **反向对照**：旧的无 metadata 样本 `RUN-20261104155814-952426` 仍必须 400。

## 3. 结果：46 项断言全通过

`logs/00-run.log` 末尾 `== 全部检查通过 ==`，`PASS` 计数 46，`FAIL` 0。

### 3.1 解析入库（`logs/02-parse.json`、`logs/03-parse-assert.txt`）

```
exportable=True missing=[] keys=10 counts={'pass': 2, 'fail': 1, 'skip': 0}
```

10 项逐一：`CASE_ID / RUN_ID / QA_PROJECT / DUT / LINK / SW_BUILD / BASELINE / OPERATOR / STARTED_AT / CASE_KIND`，
`metadataMissing` 为空数组。

### 3.2 打印页取数（`logs/05-print-data-summary.txt`）

```
mode=external internal=False exportable=True missing=0 metadata=10
```

即：对外模式的打印页**不带内部版标识**、门禁判定为可对外、metadata 页有 10 行。

### 3.3 PDF（`artifacts/report_external.pdf`，213,723 B，`pages=1`）

- `exportId=9`，`sha256=020709c1…`，**响应声明值 = 下载文件实际值 = `export_record` 落库值**，三方一致。
- 文本抽取（`logs/08-pdf-text.txt`）后逐字命中 10 项 metadata 值，另有：
  - 页眉 `… | 可对外`（对外标识存在）
  - **不含** `内部版`（内部版标识已排除）
- 关键片段：

```
自动化测试运行报告
RUN_ID ： RUN-RPT05EXT-20261106-101647 | 可对外
十项  metadata
CASE_ID RPT05-EXT-001
DUT WSL-DEV-NODE-01
LINK wsl2-lo -> backend:8081
...
运行结果 PASS 2 FAIL 1 SKIP 0
故意失败用例 FAIL 2 故意失败：验证失败用例渲染 : 1 != 2
```

> 说明：该 run 的套件根名是 `Print Metadata`（由 `.robot` 文件名派生），`FAIL` 为脚本里故意失败的
> 第三条用例——目的是验证**对外报告在有用例失败时同样可导出且失败消息完整**，不是缺陷。

### 3.4 DOCX（`artifacts/report_external.docx`，3,719 B）

- `exportId=10`，`sha256=a1e3d465…`；zip 魔数 `PK` 校验通过。
- `word/document.xml` 的 `<w:t>` 文本（`logs/11-docx-text.txt`）逐字命中 10 项，
  含 `一、十项 metadata` 标题、失败用例名 `故意失败用例`、页脚 `可对外交付`，**不含** `内部版`。

### 3.5 落库与审计（`logs/13`、`logs/14`、`logs/15`）

```
7  RUN-RPT05EXT-…  external pdf  created 212729  c1dec7b26321
8  RUN-RPT05EXT-…  external docx created   3716  9205373b9abc
661 report_export report RUN-RPT05EXT-… {"runId":…,"format":"pdf","mode":"external","sha256…
662 report_export report RUN-RPT05EXT-… {"runId":…,"format":"docx","mode":"external","sha25…
```

`run.exportable=1`、`metadata_missing=NULL`、`pass_count=2 / fail_count=1`。

### 3.6 反向对照（`logs/12-control-old-run.json`）

旧样本对外导出仍为 **400 / 40001**，`metadataMissing` 恰好是十项全缺：

```
外部版导出被拒绝：缺少 metadata 项 CASE_ID, RUN_ID, QA_PROJECT, DUT, LINK, SW_BUILD, BASELINE, OPERATOR, STARTED_AT, CASE_KIND
```

说明本次正样本之所以能导出，是因为 metadata 真的补齐了，而不是门禁被放宽。

## 4. 逐字比对工具的落点

- `pdf-text.py`：pypdf 逐页 `extract_text()`，输出 `pages=N` + 每页文本。
- 依赖装在**一次性 venv** `/tmp/pdfenv`（脚本里自动创建；不污染 `~/rf-stack/.venv`，
  也不给后端加运行时依赖）。Chrome 生成的 PDF 带 ToUnicode，中文与 ASCII 均可抽取。
- DOCX 侧不引入库：`unzip -p` + 正则取 `<w:t>`，`html.unescape` 还原实体。

## 5. 复现注意

- 后端 `:8081`（dev jar）、前端 Vite `:5273` 必须先起来；PDF 由无头浏览器渲染，
  浏览器探测失败会返回 **503 而不是伪造文件**（本脚本对此不降级，直接记 FAIL）。
- 临时会话令牌脚本自己插 `api_token` 并**在末尾撤销**；run 与导出产物保留，便于复核。
- 脚本可重复执行（每次新 `RUN_ID`）。清理历史演练数据见 `/tmp/rpt05b-clean.sh`（不入库）。

## 6. 第三条残余（token 存内存）为何不改为落库

`PrintTokenService` 用进程内 `Map` 保存一次性打印令牌（10 分钟、用后即焚）。
改为跨实例共享需要引入表 + 清理任务，而当前部署形态是**单实例后端**：

- 失效面只有一种：**服务在「已发 print-token」与「取打印页/导出」之间重启** → 该次导出 400/503，重试即可；
- 落库方案会让每个打印请求多一次写库 + 需要过期清理，收益在单实例下为零。

因此保留现状，并把边界写清：**扩到多实例前必须改为共享存储**，否则导出会随机失败。

## 7. 文件清单

```
docs/evidence/v1.0/rpt05b/
├── README.md                  ← 本文
├── rpt05b-e2e.sh              ← 一条命令复现（46 项断言）
├── pdf-text.py                ← pypdf 文本抽取
├── suite/print_metadata.robot ← 真实 RF 套件（10 项 metadata）
├── artifacts/                 ← output.xml / report_external.pdf / report_external.docx
└── logs/                      ← 00-run.log 全量 + 01..15 分步日志
```

## 8. 门禁读数

见 `logs/01-subset-font.log` … `logs/08-wbs.log`（与同目录 `0X-*.log` 并列）。
本批次**无产品代码改动**（仅新增证据与文档），门禁用于证明工作区未破坏既有基线：

| 步骤 | 结果 |
|---|---|
| 01 subset-font | PASS（exit 0） |
| 02 frontend vitest | **204 passed (204)** |
| 03 frontend tsc | **0 行输出**（无 error） |
| 04 frontend build | **built in 10.68s** |
| 05 backend test | **Tests run: 614, Failures: 0, Errors: 0, Skipped: 0** |
| 06 lint | **[lint] PASS**（含品牌 / 色值 / 表格 / px 守门） |
| 07 release-docs | PASS |
| 08 wbs | **[PASS] WBS 自洽：96 个包 / 待验 83 / 已完成 13** |
