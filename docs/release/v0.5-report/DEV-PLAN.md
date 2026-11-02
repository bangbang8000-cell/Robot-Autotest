# v0.5 报告 · 开发计划

**文档编号** RA-REL-2026-005-DEV ｜ **版本** v0.5 ｜ **门禁** G5

**范围** RPT-01~07 ｜ **对应 PRD** FR-v0.5-01 ~ 35

---

## 1. 任务拆解与依赖

| WBS | 任务 | 参考人日 | 前置 | AI 自主度 | 完成判定（命令级） |
|---|---|---:|---|---|---|
| RPT-01 | output.xml 解析（递归 + metadata 装配） | 12 | G4 产物 | **B** | 4 并发合并产物能取到每个子套件 6 项 metadata |
| RPT-02 | 运行详情（套件树 / 步骤 / 日志着色） | 15 | RPT-01 | A | 1 万行日志页面不卡 |
| RPT-03 | 双列结果并置 | 12 | RPT-01 | **B** | 本轮缺失时接口返回 `null` |
| RPT-07 | metadata 门禁（入库前校验） | 8 | RPT-01 | **B（T3）** | 缺项 run 入库即 `exportable=false` |
| RPT-04 | 趋势与性能基线 | 10 | RPT-03 | A | 换环境趋势断开 |
| RPT-05 | PDF / Word 导出 | 12 | RPT-02 | **B** | PDF 首页十项可读、中文不乱码 |
| RPT-06 | 保留策略与清理 | 8 | RPT-05 | B | dry-run 与实际删除一致 |

### 1.1 顺序

```
RPT-01 解析 ─┬─► RPT-07 门禁（先于展示，因为 exportable 要随解析落库）
             ├─► RPT-02 详情 ─► RPT-05 导出
             └─► RPT-03 对比 ─► RPT-04 趋势
                                  RPT-06 清理（依赖 RPT-05 的导出记录）
```

**RPT-07 必须紧跟 RPT-01**：如果先做页面再补门禁，`exportable` 字段会到处打补丁。

---

## 2. 解析实现（RPT-01）

### 2.1 为什么用 Java 解析 XML 而不是调 Python

| 选项 | 问题 |
|---|---|
| 调 Python `robot.api.ExecutionResult` | 需要 Python 进程与 venv，跨语言调用增加故障点；且它是**全量加载**，大文件吃内存 |
| **Java 流式解析 XML** | ✅ 单进程、内存可控（StAX） |

**但有一个坑必须处理**：RF 7.5 的 `output.xml` 中 metadata 序列化为 `<meta name="..." value="..."/>`，且**只在根套件上有部分内容**，pabot 合并后真值分散在子套件里。

```java
// 递归收集，不能只读根套件
private void collectMeta(XmlSuite suite, Map<String,String> acc) {
    acc.putAll(suite.metadata());          // 子套件覆盖或补充
    suite.suites().forEach(s -> collectMeta(s, acc));
}
```

> **实测教训**：用 `ExecutionResult(...).suite.metadata` 只取根套件时，用目录跑的结果是 `{}`，真值在子套件。这是 v4.1 报告里明确记过的坑，本版必须按递归实现。

### 2.2 表与字段（本版补 v0.1 骨架）

```sql
-- V5_1__report_fields.sql
ALTER TABLE run
  ADD COLUMN dut VARCHAR(40) NULL,
  ADD COLUMN link VARCHAR(40) NULL,
  ADD COLUMN sw_build VARCHAR(40) NULL,
  ADD COLUMN baseline VARCHAR(40) NULL,
  ADD COLUMN operator VARCHAR(40) NULL,
  ADD COLUMN started_at DATETIME(3) NULL,
  ADD COLUMN case_kind VARCHAR(20) NULL,
  ADD COLUMN metadata_missing VARCHAR(200) NULL,      -- 'SW_BUILD,DUT'
  ADD COLUMN exportable TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN pass_count INT NOT NULL DEFAULT 0,
  ADD COLUMN fail_count INT NOT NULL DEFAULT 0,
  ADD COLUMN skip_count INT NOT NULL DEFAULT 0;

ALTER TABLE test_result
  ADD COLUMN case_key VARCHAR(160) NOT NULL,          -- 'suitePath.caseName' 用于跨 run 对比
  ADD COLUMN log_offset BIGINT NULL,                  -- 日志文件内的行偏移（点失败跳转用）
  ADD COLUMN failure_kind VARCHAR(16) NULL;           -- assert/env/infra（与 v0.4 分类对齐）

ALTER TABLE keyword_result
  ADD COLUMN arg_count SMALLINT NOT NULL DEFAULT 0,
  ADD COLUMN log_span VARCHAR(64) NULL;               -- 'start:end' 行区间，日志不重复存
```

**`test_result.case_key` 是双列并置的连接键**。不能用 `test_case.id`：同一用例可能改过编号，用「套件路径 + 用例名」更稳定。

### 2.3 大字段策略

| 数据 | 存哪 | 为什么 |
|---|---|---|
| `output.xml` | `artifacts/<run_id>/output.xml` | 单文件可能数十 MB |
| 日志全文 | 同上（XML 内 `<msg>`） | 不进库 |
| 报告页要展示的日志片段 | **运行时按 `log_offset`/`log_span` 从文件读取** | 库只存偏移 |
| 截图/证据 | `artifacts/<run_id>/evidence/` | 只存路径 + sha256 |

**库记录体积目标**：单 run <1 MB（1,000 用例规模的 `test_result` + `keyword_result` 约 2,000 行）。

---

## 3. metadata 门禁（RPT-07，T3）

```java
private static final List<String> REQUIRED = List.of(
    "CASE_ID","RUN_ID","QA_PROJECT","DUT","LINK","SW_BUILD",
    "BASELINE","OPERATOR","STARTED_AT","CASE_KIND");

public GateResult check(Map<String,String> meta) {
    List<String> missing = REQUIRED.stream()
        .filter(k -> isBlank(meta.get(k)))       // 空白也算缺，不是只有 null
        .toList();
    // 值长度校验（RF 会在每个 test 行重复渲染，>40 字符会撑爆报告）
    List<String> tooLong = REQUIRED.stream()
        .filter(k -> meta.getOrDefault(k,"").length() > 40).toList();
    return new GateResult(missing.isEmpty() && tooLong.isEmpty(), missing, tooLong);
}
```

**四个判定细节**（不写清就会各人各判）：

| 细节 | 规则 | 理由 |
|---|---|---|
| 空字符串 | 视为缺失 | `<meta name="DUT" value=""/>` 是常见「填了但没填」 |
| 全空白 | 视为缺失 | `"   "` 不是有效值 |
| 长度 | ≤40 字符 | RF 在每行重复渲染，超长会破坏报告排版 |
| 大小写 | 键名必须全大写精确匹配 | 键名不统一等于没有标准 |

**校验时点**：解析入库的**同一个事务内**，结果写 `run.exportable` 与 `run.metadata_missing`。**不在导出时校验**——否则库里存着「其实不可对外」的 run，报表统计会失真。

---

## 4. 双列并置（RPT-03）

### 4.1 基线选择规则

```
baseline = 同 project + 同 case_key + 同 env（DUT+LINK）+ 时间早于本轮
           中最近的一次，且 state=finished
```

- 手动切换基线时，`baselineRunId` 作为参数，但要校验同项目同环境。
- **找不到基线不算错**：左列显示「无历史」，右列正常。

### 4.2 接口契约（关键：缺失必须是 null）

```json
GET /api/reports/{runId}/compare?baselineRunId=opt
{
  "baseline": { "runId": "...", "startedAt": "..." },
  "rows": [
    { "caseKey": "rdma/link_up.TC-01",
      "history": { "verdict": "PASS", "durationMs": 6200 } | null,
      "current": { "verdict": "FAIL", "durationMs": 6100 } | null,
      "change": "PASS_TO_FAIL" }
  ],
  "summary": { "newFail": 2, "newPass": 1, "bothFail": 3, "notRun": 1 }
}
```

**三条硬规则**：

1. `current` 为本轮未执行时必须是 `null`，**不得用 `history` 填充**。
2. `change` 由后端算（`PASS_TO_FAIL` / `FAIL_TO_PASS` / `SAME` / `NOT_RUN`），前端不重算。
3. 前端渲染时 `current === null` 显示「无（本轮未执行）」，且**禁止兜底显示 `history` 的值**。

### 4.3 为什么这条要写三遍（PRD / DEV / TEST）

因为它是**最容易被「顺手优化」掉的地方**：开发者看到空值，直觉会「用上次的填上更好看」。这在报告场景里等于造假。所以：

- PRD 写成「禁止做某件事」的需求（FR-v0.5-16）；
- DEV 写成接口契约的硬规则；
- TEST 写成断言 JSON 里是 `null` 的用例。

---

## 5. 导出（RPT-05）

### 5.1 PDF：Chromium 渲染前端打印页

```
后端 → 生成一次性 token → 调 node scripts/pdf-render.mjs
     → Playwright 打开 /report/<runId>/print?token=xxx
     → page.pdf({format:'A4', printBackground:true})
     → 落 artifacts/<run_id>/report.pdf → 记 export_record
```

| 为什么这么做 | 说明 |
|---|---|
| 样式与页面一致 | 不用维护两套排版 |
| 中文天然支持 | Chromium 自带字体回退；wkhtmltopdf 的中文配置是长期坑 |
| 149 有 Node | `~/tools/node` v22.23.4，已存在 |

**依赖准备**（M0 之外的额外动作，写进部署文档）：

```bash
export PLAYWRIGHT_DOWNLOAD_HOST=https://npmmirror.com/mirrors/playwright   # 本网络直连会被 reset
npx playwright install chromium
```

**打印页设计**：独立路由 `/print`，不加载导航与交互组件，只渲染静态内容；分页用 CSS `break-inside: avoid` 保护表格行。

### 5.2 Word：poi-tl 模板

- 模板 `templates/report.docx` 放 `src/main/resources`，占位符 `{{run_id}}`、`{{meta_table}}` 等。
- 用 poi-tl 的表格循环渲染用例列表。
- **Word 版排版从简**：它是「可编辑交付件」，正式版是 PDF（PRD §1.4）。

### 5.3 对外 / 内部两套

| 版本 | 条件 | 首页标识 |
|---|---|---|
| 对外版 | `exportable=true` | 正常 metadata 表 |
| 内部版 | `exportable=false` | 顶部红字「**非对外交付件**：缺 SW_BUILD」 |

对外版按钮在 `exportable=false` 时**禁用**（后端也拒绝，不信前端）。

---

## 6. 保留与清理（RPT-06）

```yaml
retention:
  max_days: 90              # 超过 90 天的 run 产物可清理
  max_project_gb: 50        # 单项目产物上限
  keep_recent_runs: 20      # 最近 20 次永不清理
  protections:
    - exported             # 有 export_record 的不清
    - pinned               # 手动标记「保留」的不清
    - p0_evidence          # 标记为缺陷证据的不清
```

**执行流程**：

```
1. 计算候选集（按 max_days + max_project_gb）
2. 扣除保护集（三类）
3. 生成待删清单（run_id + 占用空间）
4. dry-run：输出清单 + 总空间，不删
5. 实际执行：删文件 → 更新 run_artifact.availability='purged' → 写审计
6. 库记录保留（run/test_result 不删）
```

**关键约束**：**只删文件，不删库记录**。删库记录等于让「某年某月跑过什么」永久消失，而报告页应该还能显示「产物已清理」。

---

## 7. 实施顺序（Wave）

| Wave | 内容 | 中间可验证态 |
|---|---|---|
| **W1** | RPT-01 解析 + RPT-07 门禁 | 一个 run 能解析入库，`exportable` 正确 |
| **W2** | RPT-02 详情（**先丑后美**） | 套件树 + 步骤 + 日志能看，样式不管 |
| **W3** | RPT-02 样式 + 大日志性能 | 1 万行不卡 |
| **W4** | RPT-03 对比 + RPT-04 趋势（并行） | 双列显示正确，缺失为「无」 |
| **W5** | RPT-05 导出（PDF 先，Word 后） | PDF 首页十项可读 |
| **W6** | RPT-06 清理 | dry-run 与实际一致 |

**W2 的「先丑后美」是刻意的**：报告的价值在数据完整，不在好看。先让大师看到「信息全不全」，再谈样式——反过来会返工两遍。

---

## 8. 完成判定

```bash
# 1. 解析（用 v0.4 的 4 并发真实产物）
java -jar report-cli.jar parse --run <RUN_ID>
mysql robot_dev -e "SELECT exportable, metadata_missing, pass_count, fail_count FROM run WHERE id='<RUN_ID>'"
# 期望：exportable=1, metadata_missing=NULL, 计数与 output.xml 一致

# 2. 递归 metadata（关键）
java -jar report-cli.jar meta --run <RUN_ID> | grep -c "SW_BUILD"
# 期望 ≥ 分片数（每个子套件都有）

# 3. 双列并置：本轮缺失必须为 null
curl .../api/reports/<RUN_ID>/compare | jq '[.rows[] | select(.current==null)] | length'
# 期望 > 0（造一条跳过的用例），且这些行 .history 不为 null

# 4. metadata 门禁
# 造一个缺 SW_BUILD 的 run
curl .../api/reports/<ID> | jq '.exportable,.metadataMissing'   # false, ["SW_BUILD"]
curl -X POST .../api/reports/<ID>/export?mode=external          # 400 + 缺项说明

# 5. 导出
curl -X POST .../api/reports/<RUN_ID>/export?mode=external
pdftotext artifacts/<RUN_ID>/report.pdf - | head -40    # 首页含十项键名
# 中文不乱码：pdftotext 输出含中文（非方块或乱码）

# 6. 清理
bash scripts/retention.sh --dry-run > /tmp/dry.txt
bash scripts/retention.sh --apply
diff <(grep -o 'run-[a-z0-9]*' /tmp/dry.txt | sort) <(审计里的实际删除列表 | sort)   # 一致
```

---

## 9. 风险与降级

| 风险 | 信号 | 降级 | 谁批 |
|---|---|---|---|
| 报告样式反复不满意 | 三轮改版仍被否 | 先定「信息完整」为验收线，样式进运营期迭代 | 大师 |
| Playwright 装不上（网络） | chromium 下载失败 | 用系统 chromium（`apt` 或已有 chrome）+ `channel:'chrome'` | — |
| 双列并置被「顺手优化」成回填 | code review 发现前端有 `?? history` | **视为 P0 缺陷**，不改不合并 | silence |
| 大日志渲染卡 | 1 万行滚动 >500 ms | 虚拟滚动（只渲染可视区），不做全量 DOM | — |
| 清理误删 | dry-run 与实删不一致 | 立即停用清理任务，从备份恢复产物；查保护规则 | 大师 |
| 导出用例数不符 | PDF 少页 | 分页 CSS 加 `break-inside: avoid`，导出后校验条数 | — |

---

## 10. 交付物

| 类别 | 内容 |
|---|---|
| 代码 | 解析器（StAX）、门禁校验器、报告 API、对比/趋势查询、打印页、`pdf-render.mjs`、docx 模板、清理任务、前端报告页 |
| 迁移 | `V5_1__report_fields.sql`、`V5_2__retention.sql` |
| 文档 | 报告字段与门禁说明、导出说明（含 Playwright 安装）、`ADR-0010 大字段策略` |
| 证据 | `docs/evidence/v0.5/`：解析对账、递归 metadata 输出、null 断言、门禁拦截、PDF 抽样、清理 dry-run 对比 |
