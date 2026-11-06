# HRD-02 残余：补录值进报告渲染/导出 + 真实 HTTP 调用（2026-11-06）

> 目标：关闭 WBS #50 第 7 列残余里可自证的两条——
> ① **「补录值尚未合并进报告渲染/导出」**：此前补录只写 `run_metadata`，报告详情 / 打印页 /
> PDF / DOCX 仍只读解析出的 `metadata_json`，出现「十项补齐了，对外导出依旧 400」的死角；
> ② **「运行中服务的真实 HTTP 调用未跑」**：两类埋点此前只有服务层单测。
> 第三条「`system:deploy` 尚无发布脚本调用方」属产品边界，本批不改实现，只在 §6 记录判定。

## 1. 设计：读时叠加、写时回写、重析不冲

| 层 | 做法 | 位置 |
|---|---|---|
| 读取 | `ReportRepository.find` 在返回前把 `run_metadata` 的补录项叠加到解析 metadata 上，并按十项重算门禁 | `domain/report/MetadataOverlay.java`、`infrastructure/ReportRepositoryJdbc.java` |
| 补录 | PATCH 落库后立刻按叠加结果重算门禁，回写 `run.exportable` / `run.metadata_missing`，让列表页与其它 SQL 消费方口径一致 | `application/execution/RunMetadataService.java`、`ReportRepositoryJdbc.updateGate` |
| 重析 | 解析入库时也用「解析值 + 补录值」算门禁，避免一次重新解析把人已补齐的十项冲回红 | `application/report/ReportIngestService.java` |

三条约束：

- **不回写 `metadata_json`**：解析产物保持不可变，补录值只在 `run_metadata` 一份真源，
  叠加发生在读取时。这样「哪些值是解析的、哪些是人补的」永远可追溯（审计里也是 `before/after`）。
- **空白值不覆盖**：补录传 `""` 表示删项（见 PATCH 语义），传空格视为未填，不能把解析值抹掉。
- **门禁不放宽**：叠加后仍走同一个 `MetadataGate.check`（十项必填、值 ≤ 40 字符），
  超长值即使键齐也依旧 `exportable=false`。

## 2. 证据怎么造

`hrd02b-e2e.sh`（同目录）对运行中的后端 `:8081` 做真实 HTTP 调用，全流程：

1. 用 RF 7.5.1 造一份**只带 4 项 metadata**（`CASE_ID/RUN_ID/QA_PROJECT/OPERATOR`）的真实产物，
   跑出 2 通过 1 失败，落 `var/artifacts/<RUN_ID>/shard-0/`，复制一份到 `artifacts/output.xml`；
2. 登记 `run` 行（3 tests / 2 pass / 1 fail），插一条 2 小时临时会话令牌（末尾撤销）；
3. `POST /api/reports/{runId}/parse` → **exportable=false，缺失恰好 6 项**
   （`DUT/LINK/SW_BUILD/BASELINE/STARTED_AT/CASE_KIND`）；
4. 对外 PDF 导出 → **400**（门禁拦在导出前）；
5. `PATCH /api/execution/runs/{runId}/metadata` 补六项 → 200；
6. **再解析一次** → exportable 仍 true（证明 §1 的「重析不冲」）；
7. `GET /api/reports/{runId}` → **exportable=true、缺失 0、metadata 10 项**，
   补录值盖过解析值（`DUT=WSL-DEV-NODE-02`），解析值保留（`CASE_ID=HRD02-PATCH-001`）；
8. 落库口径：`run.exportable=1`、`run.metadata_missing=NULL`、`metadata_json` **不含** `DUT`
   （未被改写）、`run_metadata` 6 行；
9. 重复 PATCH 同样内容 → 200，但**审计条数不变**（未变化不写审计）；
10. 对外 PDF / DOCX 各导出一次 → 200，下载后**逐字比对**六项补录值，
    并断言带「可对外」、不含「内部版」；
11. `PATCH {"DUT":""}` 删必填项 → 详情 exportable=false、缺失含 `DUT`、
    `run.exportable` 回写 0、对外导出重新 **400**；
12. 审计：两次变更各一条，`after={…}` / `before={…}; after={DUT=（删除）}`；
13. 清理：撤销临时令牌，删除 `run` 及其衍生行（产物留在 `artifacts/`）。

## 3. 读数（本批实跑，`logs/00-run.log`）

**62 项断言全部通过**（日志里另有一个孤立的 `FAIL` 是 DOCX 正文表格里「用例结论」列的取值，不是断言失败）。

关键读数：

| 项 | 值 |
|---|---|
| 解析后 | `exportable=false`，缺失 `DUT/LINK/SW_BUILD/BASELINE/STARTED_AT/CASE_KIND` |
| 补录前对外导出 | **400**（0.033 s） |
| 补录后详情 | `exportable=true`，missing `[]`，metadata **10 项** |
| 重析后 | `exportable` 仍 `true`（补录未被解析覆盖） |
| `run` 表 | `exportable=1`、`metadata_missing=NULL`、`metadata_json` 不含 `DUT` |
| 重复补录 | 审计条数 1 → 1 |
| 对外 PDF | 200，`209,154 B`，1 页，`sha256=bf118b3c…`（响应=文件） |
| 对外 DOCX | 200，`3,724 B`，`sha256=1fbe5e74…` |
| PDF/DOCX 内容 | 六项补录值 + `CASE_ID` + `RUN_ID` + `QA_PROJECT` + `OPERATOR` 逐字命中 |
| 删 `DUT` 后 | 详情 false、缺失 `DUT`、`run.exportable=0`、对外导出 **400** |
| 审计 | 2 条：`after={DUT=…,…}` 与 `before={DUT=…}; after={DUT=（删除）}` |
| `export_record` | 2 条（external/pdf、external/docx，均 `created`） |

## 4. 逐字比对怎么做的

- PDF：`pdf-text.py` 用 pypdf 逐页 `extract_text()`。依赖装在一次性 venv `/tmp/pdfenv`
  （脚本自动创建，不进产品依赖）。Chrome 渲染的 PDF 带 ToUnicode，中英文都能抽。
- DOCX：`unzip -p word/document.xml` + 正则取 `<w:t>` 顺序拼接、`html.unescape` 还原实体
  （按 `<w:p>` 分会把表格里的嵌套 XML 吐出来，见 RPT-05 的踩坑记录）。

## 5. 复现

```bash
cd ~/robot-autotest
# 后端 :8081（dev jar）与前端 :5273 必须在跑；PDF 由无头浏览器渲染，浏览器不可用会 503
bash docs/evidence/v1.0/hrd02b/hrd02b-e2e.sh | tee docs/evidence/v1.0/hrd02b/logs/00-run.log
```

脚本可重复执行（每次新 `RUN_ID`），末尾会删掉本次 `run` 与衍生行，只留产物。

## 6. 第三条残余（`system:deploy` 无调用方）为何不改

`POST /api/system/deploy` 是给「平台自己升级/回滚」留的审计入口，当前发布走 `scripts/rollback.sh`
（本机脚本）+ 手工重启，没有自动化发布器。硬造一个调用方等于把「谁在什么时候把哪个版本推上去」
这件事塞进一个没人用的脚本里，反而让审计记录不可信。因此本批**只保留接口与审计**，
在 WBS 残余里如实写「尚无发布脚本调用方」；等真有发布流水线时再接。

## 7. 门禁读数（`01-*.log` … `08-*.log`）

| 步骤 | 结果 |
|---|---|
| 01 subset-font | PASS（1037 字 / 180,974 B gzip，预算 800 KiB） |
| 02 frontend vitest | **204 passed (204)**，28 个文件 |
| 03 frontend tsc | **0 行输出** |
| 04 frontend build | **built in 9.72s**（入口 `index-sPYz3fCg.js`） |
| 05 backend test | **Tests run: 625, Failures: 0, Errors: 0, Skipped: 0** |
| 06 lint | **[lint] PASS**（品牌 / 色值 / 表格 / px / 图标 / WBS 全通过） |
| 07 release-docs | PASS（7 个版本，FAIL 0 / WARN 0） |
| 08 wbs | **[PASS] WBS 自洽：96 个包 / 待验 83 / 已完成 13** |

新增单测：`MetadataOverlayTest`（8）+ `RunMetadataServiceTest` 增 2 个门禁翻转用例 +
`ReportIngestServiceTest` 增 1 个「重析叠加补录」用例 → 后端从 614 增至 **625**。

## 8. 本批改动清单

| 文件 | 改动 |
|---|---|
| `domain/report/MetadataOverlay.java` | 新建：`merge`（纯合并）+ `apply`（叠加并重算门禁） |
| `application/repository/ReportRepository.java` | 新增 `updateGate(projectId, runId, gate)` |
| `infrastructure/ReportRepositoryJdbc.java` | 注入 `RunMetadataRepository`；`find` 叠加补录；`UPDATE_GATE` 实现 |
| `application/execution/RunMetadataService.java` | PATCH 后按叠加结果重算门禁并回写 |
| `application/report/ReportIngestService.java` | 解析入库按「解析值 + 补录值」算门禁 |
| 测试 | 新增 `MetadataOverlayTest`；`RunMetadataServiceTest` / `ReportIngestServiceTest` 各补用例与假实现 |

未改：`MetadataGate`（门禁口径不动）、`metadata_json` 的写入（保持解析原值）。
