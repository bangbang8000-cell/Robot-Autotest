# TPLX-03 · 四格式字段一致性校验

**判据（DEV-PLAN §10.7）**：一致 → 退出码 0；**人为从 xlsx 删一列 → 必须非 0 并指出缺哪列**。
**结论**：**通过**。正向 4 格式各 21 字段一致（exit 0）；xlsx 删掉 `passRate` 后 exit 1 且输出 `缺 passRate`；顺序被换也判红；文件缺失走 exit 2。

---

## 1. 做了什么

| 文件 | 说明 |
| --- | --- |
| `tools/check_report_consistency.py` | 新增。四种格式各一个解析器，比对字段**集合与顺序**；退出码 0/1/2 三态 |
| `gates.sh`（第 14 步 `report_consistency`） | 接入门禁 |
| `ReportFourFormatDumpTest`（后端单测） | 用**当前导出器**现生成四份文件到 `backend/target/evidence/four-formats/` |

### 为什么样本要「每次现生成」

如果门禁比对的是仓库里存的四份历史文件，它守住的只是**检查器**：导出器某条分支漏写一个字段，
样本还是老样子，门禁照样绿。所以第 14 步比对的是第 05 步（`mvn test`）里
`ReportFourFormatDumpTest` **刚刚**产出的文件——导出器改坏了，同一次门禁就会红。

### 各格式的机读锚点

`ReportExportService` 的约定是「每一行都以字段 key 起头」，解析方式各格式不同：

| 格式 | 取法 |
| --- | --- |
| HTML | `<th title="标题">fieldKey</th>` 的文本 |
| DOCX | `word/document.xml` 段落文本，取第一个 ` \| ` 之前 |
| XLSX | A 列（跳过首行表头），兼容共享字符串与内联字符串 |
| PDF | 内容流 `(...) Tj` 的字符串，取第一个 ` \| ` 之前（中文降级成 `?`，key 是 ASCII 不受影响） |

工具**只用标准库**：门禁里跑的东西不该多一个 pip 依赖，所以 XLSX 自己解
`sharedStrings.xml` + `sheet1.xml`，不依赖 openpyxl。

### 退出码三态

| 码 | 含义 |
| --- | --- |
| 0 | 判过了，一致 |
| 1 | 判过了，**不一致** |
| 2 | 这次没法判（文件缺失 / 格式不认识 / 解不开） |

分开的理由很实际：混成一个码，CI 里就分不清「导出器坏了」和「样本没生成」。

---

## 2. 证据清单

| 文件 | 说明 |
| --- | --- |
| `run.log` | 正/负向 6 项断言原始输出（`TPLX3-01..06`，FAIL 0） |
| `run.py` | 验收脚本（调检查器 + 用 openpyxl 造坏文件） |
| `positive.log` | 四格式一致的原始输出 |
| `negative-xlsx-missing.log` | xlsx 删掉 `passRate` 后的输出（exit 1） |
| `negative-xlsx-order.log` | 只换顺序后的输出（exit 1） |
| `negative-missing-file.log` | 文件不存在（exit 2） |
| `samples/report.{html,docx,xlsx,pdf}` | 四份原样样本 |
| `samples/report-missing-passRate.xlsx` | 故意删一列 |
| `samples/report-reordered.xlsx` | 故意换两行顺序 |

### 关键读数

```
PASS  TPLX3-01  四格式字段一致 → 退出码 0                exit=0
PASS  TPLX3-03  xlsx 缺一列 → 退出码非 0              exit=1
PASS  TPLX3-04  且点名缺哪个字段、只报缺不误报多            [FAIL] XLSX: 共 20 个字段，缺 passRate
PASS  TPLX3-05  字段集合相同但顺序不同 → 退出码非 0 且判定为顺序问题  exit=1
PASS  TPLX3-06  文件不存在 → 退出码 2（不与不一致的 1 混淆）   exit=2
合计 6 项，FAIL 0 项
```

正向（`positive.log`）末行：`结论：4 种格式字段集合与顺序完全一致，每种 21 个字段`。

**「只判退出码」是不够的**：`negative-xlsx-missing.log` 里既要有 exit 1，也要有具体字段名。
这条是本仓的老教训——RF `--dryrun` 对语法错误同样返回 0，只看退出码等于没校验。

---

## 3. 复现

```bash
cd ~/robot-autotest
# 1) 用当前导出器现生成四份文件
cd backend && mvn -o -B test -Dtest=ReportFourFormatDumpTest && cd ..
# 2) 直接跑检查器
python3 tools/check_report_consistency.py \
  backend/target/evidence/four-formats/report.{html,docx,xlsx,pdf}
# 3) 正/负向完整自检
python3 docs/evidence/v2.1/TPLX-03/run.py
# 4) 门禁第 14 步
bash scripts/gates.sh
```

---

## 4. 遗留

- **PDF 文本层只有 ASCII**：中文标题在 PDF 里是 `?`。一致性校验比的是字段 key，所以不受影响；
  但如果将来要比「标题文案」四格式一致，PDF 这条会首先红。要根治得给 PDF 嵌中文字体（体积从 1.5 KB 涨到几百 KB）。
- **只比对字段集合与顺序，不比「值」**。值层面的差异（比如 xlsx 把 `80.0` 写成 `80`）属于格式化差异，
  强行统一会逼着各格式放弃自己的表达方式，不划算。
- **HTML 的解析假设「一行一个 `<th>`」**：将来若改成表头一行多列（宽表），解析规则要同步改。
