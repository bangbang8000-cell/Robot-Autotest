# v0.5 报告 · 测试计划

**文档编号** RA-REL-2026-005-TEST ｜ **版本** v0.5 ｜ **门禁** G5

**被测范围** 解析、报告详情、双列并置、趋势、导出、保留清理、metadata 门禁 ｜ **对应 PRD** FR-v0.5-01 ~ 35

---

## 1. 测试目标

### 1.1 这一版要证明的事

| # | 要证明 | 反例（不证会怎样） |
|---|---|---|
| 1 | **报告里的数字与产物一致** | 报告说 12 条通过，实际 output.xml 是 13 条 |
| 2 | **metadata 缺一项就不能对外** | 客户拿到报告问「跑在哪个版本上」答不出来 |
| 3 | **历史不冒充当轮** | 绿色的勾是上次的，本轮其实没跑 |
| 4 | **清理不误删** | 删掉了正在被客户引用的证据 |

### 1.2 一句话判据

> **报告可以被质疑，但不能被推翻。** 每条结论都要能点回到 `output.xml` 的对应节点。

### 1.3 不测什么

- 报告模板定制（不做）
- 公开分享链接（不做）
- Word 精美排版（只测结构与可编辑性）

---

## 2. 分层分配

| 层 | 内容 | 条数 | 归属 |
|---|---|---|---|
| L1 单元 | 门禁校验器（10 项 × 边界）、case_key 生成、change 计算 | ~40 | AI |
| L2 集成 | 解析对账、递归 metadata、compare 接口、清理候选集 | ~35 | AI |
| L4 端到端 | 核心场景 1~7 | 1 长链 | AI |
| L5 性能 | 1 万行日志、首屏、导出耗时 | 3 | AI |
| 数据一致性 | 报告 vs output.xml 双向对账 | 5 | AI，人抽 1 次 |

---

## 3. 解析与对账（RPT-01）

| ID | 用例 | 期望 |
|---|---|---|
| TC-v0.5-101 | 单分片产物解析 | 用例数、通过/失败/跳过数与 XML 统计一致 |
| TC-v0.5-102 | **4 并发合并产物解析** | 4 个子套件的用例全部解析到，无遗漏 |
| TC-v0.5-103 | **递归 metadata** | 每个子套件都能取到 6 项基础 metadata（不是只有根套件） |
| TC-v0.5-104 | 步骤树入库 | `keyword_result` 层级与 XML 一致，`parent_id` 无孤儿 |
| TC-v0.5-105 | 大字段不入库 | `keyword_result` 无大 TEXT；单 run 库记录 <1 MB |
| TC-v0.5-106 | 重复解析幂等 | 解析两次，`test_result` 条数不变，`updated_at` 更新 |
| TC-v0.5-107 | 截断产物容错 | 把 output.xml 截掉后半段 → 明确报「产物损坏，行 N」，不产生半份报告 |
| TC-v0.5-108 | 空产物 | 只有统计节点的 XML → 判「无用例」，不生成报告 |
| TC-v0.5-109 | 双向对账 | 报告页每条用例都能在 XML 找到对应节点（抽 20 条） |
| TC-v0.5-110 | **真 pabot 产物解析**（2026-11-05 已跑） | `pabot --processes 2` 真产物 8/8 用例解析一致，5 项 metadata 全部有值 |
| TC-v0.5-111 | **万行级解析耗时**（2026-11-05 已跑） | 14.4k 行 122 ms、108k 行 / 5.9 MB 273 ms，无 `StackOverflowError` |

**对账方法**（不接受「数字看着对」）：

```bash
# 从 output.xml 独立统计（用 RF 自己的 Python API，与 Java 实现互为裁判）
python -c "
from robot.api import ExecutionResult
r = ExecutionResult('artifacts/<RUN_ID>/output.xml')
print('total', r.statistics.total)
print('pass', r.statistics.passed, 'fail', r.statistics.failed)
"
# 与库里 run.pass_count / fail_count 比对，必须完全一致
```

---

## 4. metadata 门禁（RPT-07，本版最硬的一条）

### 4.1 十项逐一缺失（矩阵测试，10 条）

| ID | 缺失项 | 期望 |
|---|---|---|
| TC-v0.5-201 | `CASE_ID` | `exportable=false`，`metadata_missing` 含 `CASE_ID` |
| TC-v0.5-202 | `RUN_ID` | 同上 |
| TC-v0.5-203 | `QA_PROJECT` | 同上 |
| TC-v0.5-204 | `DUT` | 同上 |
| TC-v0.5-205 | `LINK` | 同上 |
| TC-v0.5-206 | `SW_BUILD` | 同上 |
| TC-v0.5-207 | `BASELINE` | 同上 |
| TC-v0.5-208 | `OPERATOR` | 同上 |
| TC-v0.5-209 | `STARTED_AT` | 同上 |
| TC-v0.5-210 | `CASE_KIND` | 同上 |
| TC-v0.5-211 | 两项同时缺（`DUT` + `SW_BUILD`） | `metadata_missing` 同时列出两项 |
| TC-v0.5-212 | 十项齐全 | `exportable=true`，`metadata_missing` 为空 |

> **10 条而不是 1 条**：只测「缺 SW_BUILD」证明不了另外 9 项真的在校验列表里。这类清单型校验必须逐项过。

### 4.2 边界判定

| ID | 用例 | 期望 |
|---|---|---|
| TC-v0.5-213 | `value=""` 空字符串 | 视为缺失（`exportable=false`） |
| TC-v0.5-214 | `value="   "` 全空白 | 视为缺失 |
| TC-v0.5-215 | 值长度 41 字符 | 判「超长」，`exportable=false`，说明超长项 |
| TC-v0.5-216 | 值长度 40 字符 | 通过（边界内） |
| TC-v0.5-217 | 键名小写 `dut` | 视为缺失（键名必须全大写） |
| TC-v0.5-218 | 十项中有一项重复定义 | 取第一个非空值，不报错 |

### 4.3 校验时点

| ID | 用例 | 期望 |
|---|---|---|
| TC-v0.5-219 | 入库即校验 | 缺项 run 入库后 `exportable` 立即为 false（不等导出） |
| TC-v0.5-220 | 齐全率统计 | 报告一个项目的齐全率，与逐条核对一致 |
| TC-v0.5-221 | 补录留痕 | 通过编辑接口补 `SW_BUILD` → `exportable` 变 true，审计有一条 |

---

## 5. 双列并置（最容易做坏）

| ID | 用例 | 期望 |
|---|---|---|
| TC-v0.5-301 | 有历史 + 本轮通过 | 左列历史、右列本轮，两列标题带各自 run_id |
| TC-v0.5-302 | 无历史 | 左列显示「无历史」，右列正常 |
| TC-v0.5-303 | **本轮未执行** | 右列显示「无（本轮未执行）」，**接口 JSON 中 `current` 为 `null`** |
| TC-v0.5-304 | **禁止回填**（核心） | 断言接口响应里 `current==null` 的行数 >0，且这些行 `history!=null`；前端 DOM 里该行右列文本不含历史结论 |
| TC-v0.5-305 | 变化统计 | `PASS_TO_FAIL`/`FAIL_TO_PASS`/`SAME`/`NOT_RUN` 四类计数与逐行统计一致 |
| TC-v0.5-306 | 手动切换基线 | 换基线后列标题与数据同步变化；候选下拉首项是「自动（最近一次）」，候选中不出现异项目 / 异环境 run（`GET /api/reports/{runId}/baseline-candidates`）；选回「自动」时请求不带 `baselineRunId` |
| TC-v0.5-307 | 跨环境不对比 | 基线 run 的环境不同 → 拒绝并提示（或显示「环境不同不可比」）；手动基线路径下候选已过滤，后端仍二次校验返回 400 |
| TC-v0.5-308 | case_key 稳定性 | 同一用例两次运行 key 相同；改用例名后 key 变化（视为新用例） |

**TC-v0.5-304 的验证细节**（这是本版最重要的一条断言）：

```javascript
// Playwright：抓接口响应 + 检查 DOM
const json = await res.json();
const nullCurrent = json.rows.filter(r => r.current === null);
expect(nullCurrent.length).toBeGreaterThan(0);
for (const row of nullCurrent) {
  const cell = page.locator(`[data-case-key="${row.caseKey}"] [data-col="current"]`);
  await expect(cell).toHaveText(/无|未执行/);
  await expect(cell).not.toHaveText(/PASS|FAIL|通过|失败/);   // 不得出现历史结论
}
```

---

## 6. 导出

| ID | 用例 | 期望 |
|---|---|---|
| TC-v0.5-401 | PDF 首页十项 | `pdftotext` 提取，10 个键名全部出现 |
| TC-v0.5-402 | PDF 中文不乱码 | 提取文本含正确中文（非方块、非 `???`） |
| TC-v0.5-403 | 用例数一致 | PDF 内用例行数 = 报告页用例数 = XML 用例数 |
| TC-v0.5-404 | 分页不切断表格 | 表格行不被从中间切开（人工看 3 页样本） |
| TC-v0.5-405 | 对外版禁用 | `exportable=false` 时「对外版」按钮禁用，接口返回 400 |
| TC-v0.5-406 | 内部版标识 | 导出「内部版」，首页顶部有「非对外交付件」红字 |
| TC-v0.5-407 | Word 结构 | 打开无格式错乱；metadata 表可编辑；用例表完整 |
| TC-v0.5-408 | 导出记录 | `export_record` 有记录（谁/何时/哪个 run/哪种版本） |
| TC-v0.5-409 | 导出耗时 | 100 用例 ≤60 s |
| TC-v0.5-410 | 并发导出 | 3 个导出同时进行不互相覆盖文件 |
| TC-v0.5-411 | 打印链接一次性 | `GET /api/print/{runId}?token=` 成功后复用同一 token 返回 400「打印链接无效或已过期」 |
| TC-v0.5-412 | **打印页经前端可达** | 用 `printUrl` 走前端 dev server 的 `/api` 代理取打印数据 200，`mode` / `internal` / `metadataMissing` 与 run 行一致 |
| TC-v0.5-413 | **PDF 与导出记录一致** | 下载响应的 `Content-Type` / 字节数 / sha256 与 `POST /export` 响应、`export_record.sha256` 三处一致 |
| TC-v0.5-414 | 导出历史可查 | `GET /api/reports/{runId}/exports` 返回 `fileName` / `status=created` / `requestedBy` |
| TC-v0.5-415 | **内部标识进了 PDF** | 内容流解压后存在 `#c00000` 颜色算子（`.7529 0 0 rg/RG`），且 `Tj`/`TJ` 文本算子 > 0（非空白页） |

**2026-11-05 实测（内部模式，`RUN-20261104155814-952426`）**：411~415 全绿——token 复用 400；经
`:5273` 代理取数 200（`internal=true`、`exportable=false`、缺失 10 项）；导出 200 / 7.47 s /
235,253 B / `%PDF-1.4` / 2 页，sha256 三处一致；`export_record` 落库 `created`；`.7529 0 0`
出现 12 次、`Tj/TJ` 346 次；对外模式 400 回显 10 项 `metadataMissing`。证据
`docs/evidence/v0.5/rpt05-print/`。

**未覆盖**：401 / 402 / 403 依赖 `pdftotext`（本机无 poppler、无 pypdf），本轮用「页数 + 文本算子数 +
sha256」替代，文案未逐字比对；404 / 407 / 409 / 410 未做（需人工看稿、Word 打开、100 用例样本、并发样本）；
406 只验到「红色算子存在」，未做人工目视。

---

## 7. 保留与清理

### 7.1 造数（必须先有数据才能测清理）

| 数据 | 设定 |
|---|---|
| 老 run A | `started_at` 100 天前，有产物 |
| 老 run B | 80 天前，有产物，**有导出记录** |
| 老 run C | 70 天前，有产物，**标记 pinned** |
| 新 run D | 5 天前，有产物 |
| 超大 run E | 占 5 GB（模拟，用 `fallocate`），使单项目超 50 GB 触发容量清理 |

### 7.2 用例

| ID | 用例 | 期望 |
|---|---|---|
| TC-v0.5-501 | 按天数清理 | A 进候选；B 因有导出记录被排除；C 因 pinned 被排除 |
| TC-v0.5-502 | 按容量清理 | 超 50 GB 时从最老的开始删，删到达标即停 |
| TC-v0.5-503 | 最近 N 次保护 | 最近 20 次即使超期也不删 |
| TC-v0.5-504 | **dry-run 与实删一致** | 两个清单的 run_id 集合完全相同（diff 为空） |
| TC-v0.5-505 | 只删文件不删库 | 清理后 `run`/`test_result` 仍在；`run_artifact.availability=purged` |
| TC-v0.5-506 | 报告页提示 | 被清理的 run 打开报告 → 显示「产物已清理」，metadata 与统计仍可见 |
| TC-v0.5-507 | 清理审计 | 审计记录含 run 列表与释放空间 |
| TC-v0.5-508 | 清理不碰正在跑的 | `running` 状态的 run 永不进候选 |

---

## 8. 性能

| ID | 用例 | 期望 |
|---|---|---|
| TC-v0.5-601 | 1 万行日志渲染 | 滚动与搜索响应 ≤200 ms |
| TC-v0.5-602 | 报告首屏 | P95 ≤2 s |
| TC-v0.5-603 | 套件树展开 | 500 节点全展开 ≤1 s |

---

## 9. 环境与数据

| 项 | 内容 |
|---|---|
| 真实产物 | 来自 v0.4 的 4 并发运行（`artifacts/<RUN_ID>/`） |
| 齐全样本 | 十项 metadata 齐全的 run（Roce 套件 13 条） |
| 缺项样本 | 手工造 10 个各缺一项的 run（改 XML 的 meta 节点） |
| 跳过样本 | 一条本轮 `--include` 排除的用例，用于 TC-v0.5-303/304 |
| 清理样本 | §7.1 的 5 个 run + `fallocate` 造的空间 |

---

## 10. 退出标准（= G5 检查单）

- [ ] 解析 9 条（TC-v0.5-101~109）全绿，**双向对账差异 0**
- [ ] 门禁矩阵 10 项逐一缺失（201~210）全绿 + 边界 6 条 + 时点 3 条
- [ ] 双列并置 8 条（301~308）全绿，其中 **304 的 null 断言必须过**
- [ ] 导出 10 条（401~410）全绿，PDF 十项可读、中文正常、用例数一致
- [ ] 清理 8 条（501~508）全绿，**dry-run 与实删 diff 为空**
- [ ] 性能 3 条达标
- [ ] 真实链路一次运行能直接导出对外 PDF（大师亲看一份）
- [ ] 无 P0/P1 未关闭缺陷

**任一条不达标不进入 v0.6**：v0.6 的计划任务产生的 run 也要走报告链路，报告不准则自动化只会批量生产错数据。

---

## 11. 证据清单

存 `docs/evidence/v0.5/`：

| 文件 | 内容 |
|---|---|
| `parse-reconcile.md` | Java 解析 vs Python RF API 的双向统计比对 |
| `recursive-meta.txt` | 4 分片各自 metadata 输出 |
| `gate-matrix-10.md` | 十项逐一缺失的结果表 |
| `gate-boundary.md` | 空串/空白/超长/小写 的判定 |
| `compare-null.json` | `current: null` 的接口片段 + 前端渲染截图 |
| `export-sample.pdf` | 对外版 PDF 样本（首页十项） |
| `export-count.md` | PDF 行数 = 页面数 = XML 数 |
| `retention-dryrun.diff` | dry-run 与实删清单的 diff（空） |
| `perf-v05.txt` | 三项性能实测 |

---

## 12. 风险

| 风险 | 影响 | 应对 |
|---|---|---|
| 只测「缺 SW_BUILD」一项 | 另外 9 项校验可能根本没实现 | 矩阵 10 条写进退出标准 |
| 双列回填被当成「体验优化」 | 历史冒充当轮，报告失去可信度 | 304 断言 DOM 里不出现历史结论；code review 见 `??` 即拒 |
| 对账只用库内数字自比 | 解析错了两边一起错 | 用 Python RF API 做独立裁判 |
| PDF 只检查「生成了」 | 缺页、乱码、少用例 | 提取文本核对十项 + 用例数 + 人工看 3 页 |
| 清理测试不造保护数据 | 保护规则形同虚设 | §7.1 五类数据必须先造 |
| 大日志用虚拟滚动后搜索失效 | 搜索只搜可视区 | 搜索走后端全文，不依赖 DOM |
