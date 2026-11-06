# TPLX-02 · 按模板冻结 + xlsx 导出

**判据（DEV-PLAN §10.6）**：改模板不影响已冻结报告；xlsx 可被 Excel/WPS 打开；数值列是数字不是文本。
**结论**：**通过**。真实 HTTP 22 项全 PASS；**真机 Excel 16.0 打开并读出 `passRate = 80`（`clrType=Double`）**；改模板前后对外视图 `diff` 为空。

---

## 1. 做了什么

| 文件 | 改动 |
| --- | --- |
| `ReportSnapshotService.java` | 快照新增 `templateId` / `templateVersion` / `template{id,version,name,fields[]}` 三个节点；`build()` 加带模板重载 |
| `ReportViewRenderer.java` | **渲染只读快照内模板**：字段顺序、取舍、标题全部来自 `snapshot_json`；白名单在渲染层再过滤一次；必填字段兜底补齐；新增 `fieldTitles()` / `templateRef()` |
| `ReportExportService.java` | 新增 **xlsx 分支**（`XSSFWorkbook`，三列 key/title/值，数值走 `setCellValue(double)`）；`export()` 增加标题入参；HTML/PDF/DOCX 行格式统一成「key 为机读列」；新增 `summaryView()` 供 run 级导出复用 |
| `TestReportService.java` | 建/冻结时取 `findDefault(projectId)`（项目级 → 平台级回落）；导出把模板标题传给导出器；新增 `template(id)` |
| `TestReportController.java` | 新增 `GET /api/test-reports/{id}/template` |
| `ExportFormat.java` / `ExportService.java` | `format` 增加 `xlsx`，run 级导出走同一套 xlsx 渲染 |

### 两条口径写死在代码里

1. **模板只从快照读**，渲染时绝不回查 `report_template` 表。若查「当前模板」，改一次模板就把所有历史已冻结报告的字段集合改掉，等于冻结没发生。
2. **必填字段兜底**：模板是旧版本或白名单后来收紧时，按白名单顺序把 `disclaimer/watermark` 等追加回末尾——合规字段不是可选装饰。

---

## 2. 本次抓到的两个真 bug（单测都没抓到）

| # | bug | 表现 | 根因 |
| --- | --- | --- | --- |
| 1 | `freeze` 不推进 `version` 列 | 冻结后接口返回 `version=1`，而快照里写的是 `version:2` | `TestReportRepositoryJdbc.freeze` 只 UPDATE `snapshot_*`，漏了 `version = ?`；单测不碰 JDBC 所以全绿 |
| 2 | `markDefault` 顺手清掉平台级默认 | A 项目设自己的默认模板后，B 项目回落不到平台 DEFAULT | `UPDATE ... WHERE project_id IN (?, 0)` 把 `project_id=0` 的全局回落值一起清了 |

两条都是 **「单测绿、真实链路红」**：伪实现在内存里跑，跨项目的全局状态和 JDBC 列更新根本不在它的视野里。

---

## 3. 证据清单

| 文件 | 说明 |
| --- | --- |
| `run.log` | 真实 HTTP 22 项断言原始输出（`TPLX2-01..22`，FAIL 0） |
| `run.py` | 验收脚本（标准库 urllib + mysql CLI 夹具，可从零重跑） |
| `out/report-frozen.xlsx` | 冻结时导出（8 字段一份），Excel 实测样本 |
| `out/report-frozen-after-edit.xlsx` | 改模板后再导出，字段与上一份逐项相同 |
| `out/report-frozen.html` / `.pdf` / `.docx` | 另外三种格式抽样 |
| `out/view-before-edit.json` / `out/view-after-edit.json` | 改模板前后的对外视图 |
| `out/view-diff.txt` | 两者 `diff`：**0 行**（改模板对已冻结报告零影响） |
| `out/excel-open.log` | **本机 Excel 16.0** 只读打开并逐行读回的原始输出 |

### 关键读数

```
PASS  TPLX2-05  冻结 200 且 state=FROZEN、version=2                     HTTP 200 version=2
PASS  TPLX2-11  数值列是数字不是文本（passRate=80.0 type=n）              passRate={'title': '通过率', 'value': 80.0, 'type': 'n'}
PASS  TPLX2-17  改模板后已冻结报告字段集合不变（仍 8 项、顺序不变）
PASS  TPLX2-21  设项目默认不会清掉平台级 DEFAULT（全局回落值仍为 1）        平台级 is_default=1
PASS  TPLX2-22  无项目模板时回落平台级 DEFAULT（21 字段，顺序=白名单）      模板id=1 字段=21
合计 22 项，FAIL 0 项
```

Excel 侧读数（`out/excel-open.log`）：

```
EXCEL_VERSION=16.0
SHEET1_NAME=report
HEADER=FIELD_KEY|TITLE|VALUE
ROW 9: key=passRate title=通过率 value=80 clrType=Double
OPEN_OK=1
```

`title=通过率` 说明模板的自定义标题确实进了产物；`clrType=Double` 说明数值列没有被拼成文本。

---

## 4. 复现

```bash
# 1) 打包并重启（含本包代码）
cd ~/robot-autotest/backend && mvn -o -B package -DskipTests
systemctl --user restart robot-autotest-backend.service

# 2) 真实 HTTP 验收（退出码 0 = 全过）
cd ~/robot-autotest && python3 docs/evidence/v2.1/TPLX-02/run.py

# 3) 改模板前后渲染 diff（必须为空）
diff docs/evidence/v2.1/TPLX-02/out/view-before-edit.json \
     docs/evidence/v2.1/TPLX-02/out/view-after-edit.json

# 4) 用真机 Excel 打开（Windows 侧 PowerShell，只读、不显示窗口）
#    见本 README §5
```

---

## 5. Excel 打开这一条是怎么验的

WSL 里没有 Excel/WPS/LibreOffice，`openpyxl` 只能证明「OOXML 结构合法」，证不了「Excel 真能打开」。
本机 Windows 侧装有 Microsoft Office（`C:\Program Files\Microsoft Office\root\Office16\EXCEL.EXE`），
所以用 COM 只读打开、逐行读回单元格类型、再 `Close` + `Quit`：

```powershell
$excel = New-Object -ComObject Excel.Application
$excel.Visible = $false; $excel.DisplayAlerts = $false
$wb = $excel.Workbooks.Open('<xlsx 绝对路径>', 0, $true)   # 0=不链接, $true=只读
# ... 读 Cells.Item(r,c).Value2 与 .GetType().Name ...
$wb.Close($false); $excel.Quit()
```

跑完确认 `tasklist` 无 `EXCEL.EXE` 残留。

---

## 6. 遗留

- **xlsx 只有「行式」一种布局**（key / 标题 / 值）。宽表（一行数据、字段当列头）对 21 字段的报告更好看，但要处理列宽自适应与多语言表头，本版不做。
- **run 级 xlsx（`ExportService`）只给汇总计数 + metadata**，不含模板概念；模板化导出只有 `/api/test-reports/{id}/export` 一条链路。两条链路的 xlsx 布局相同（同一个渲染函数），但字段来源不同，对外说明时要区分。
- **前端还没有测试报告导出的入口**（`testReportsApi.exportBlob` 至今无 UI 调用点）：本包只做后端能力，界面归后续版本。
