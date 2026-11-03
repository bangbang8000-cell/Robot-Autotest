# RPT-02 / RPT-03 交付证据（v0.5 报告域）

> 日期：2026-11-03
> 范围：运行报告详情页（套件树 + 步骤 + 按需日志）与双列并置对比。
> 口径依据：`docs/release/v0.5-report/DEV-PLAN.md` §4（基线规则与接口契约）。

---

## 1. 改动清单

### 1.1 数据库

| 文件 | 内容 |
|---|---|
| `db/migration/V5_3__report_log.sql` | `test_result` 增加 `log_span`；`keyword_result` 增加 `log_offset`。日志正文仍只存 `output.xml`，库只存序号区间（DEV-PLAN §2.3 大字段策略）。 |
| `db/down/V5_3__report_log.down.sql` | 逐列 `DROP COLUMN IF EXISTS`，与既有 down 脚本风格一致。 |

偏移口径：把 `output.xml` 中所有 `<msg>` 按文档顺序从 0 编号；`log_offset` 为首条序号，
`log_span` 为 `start:end`（左闭右开）。解析与读取两端使用同一个计数规则。

### 1.2 后端（`backend/src/main/java/com/qa/autotest/`）

| 文件 | 说明 |
|---|---|
| `domain/report/CaseNode.java` | 追加 `logOffset` / `logSpan`，保留 RPT-01 五参构造。 |
| `domain/report/KeywordNode.java` | 同上。 |
| `domain/report/LogSpan.java` | `start:end` 解析，非法/空区间返回 `EMPTY`。 |
| `domain/report/RunSnapshot.java` | 运行只读快照（projectId / runId / state / dut / link / startedAt）。 |
| `domain/report/CaseVerdict.java` | 单次运行的用例结论。 |
| `domain/report/CaseLogRef.java` | 用例日志定位。 |
| `domain/report/RunCaseRow.java` | 套件树扁平用例行。 |
| `domain/report/KeywordRow.java` | 套件树扁平关键字行。 |
| `application/report/OutputXmlParser.java` | 解析时记录 `msg` 全局序号，写入 case/keyword 的 offset/span。 |
| `infrastructure/ReportRepositoryJdbc.java` | 入库时写 `log_offset` / `log_span`。 |
| `application/repository/ReportReadRepository.java` | 报告域只读仓储接口（与 RPT-01 写入仓储分离，避免改已有测试伪造）。 |
| `infrastructure/ReportReadRepositoryJdbc.java` | join `run` 做租户校验的只读查询。 |
| `application/report/ReportArtifactLocator.java` | output.xml 定位端口。 |
| `application/report/ReportFileLocator.java` | 产物定位实现（run_artifact → artifacts_dir/shard-0/output.xml）。 |
| `application/report/LogXmlReader.java` | StAX 流式读取，按序号区间命中，最多保留 limit 行。 |
| `application/report/ReportLogService.java` | 日志分页：默认 200、上限 1000、`nextOffset=null` 表示到底。 |
| `application/report/ReportTreeService.java` | 由 `test_result` / `keyword_result` 重建套件树与步骤树。 |
| `application/report/CompareService.java` | 双列并置对比，基线选择与 change/summary 计算。 |
| `api/report/ReportController.java` | 新增 tree / logs / compare 三个 GET 端点；保留 3 参构造兼容 RPT-01 单测。 |
| `test/.../CompareServiceTest.java` | 11 条契约测试（见 §3）。 |
| `test/.../ReportLogServiceTest.java` | 6 条分页/上限/边界测试。 |
| `test/.../ReportLogReaderTest.java` | 5 条偏移口径与分段读取测试。 |

### 1.3 接口清单（均需 `projectId`，内部 `access.bindProject` + `EXECUTION:READ`）

| 方法 | 路径 | 参数 | 返回 |
|---|---|---|---|
| GET | `/api/reports/{runId}/tree` | `projectId` | 套件树：套件名/状态、用例 `caseKey/name/verdict/durationMs/failureMsg`、关键字步骤树 |
| GET | `/api/reports/{runId}/logs` | `projectId`、`testKey`、`offset=0`、`limit=200`（上限 1000） | `{lines:[{seq,level,keyword,message}], total, nextOffset}` |
| GET | `/api/reports/{runId}/compare` | `projectId`、可选 `baselineRunId` | `{baseline, rows:[{caseKey,history,current,change}], summary}` |

### 1.4 前端

| 文件 | 说明 |
|---|---|
| `frontend/src/api/reports.ts` | 报告域类型与接口封装（页面禁止直接 fetch）。compare 类型复用同一文件。 |
| `frontend/src/views/ReportsView.vue` | 列表页：runId / 状态 / PASS·FAIL·SKIP / 开始时间 / 可对外或缺失键 / 分页。 |
| `frontend/src/views/ReportDetailView.vue` | 详情页：十项 metadata（缺失标红显示「缺失」）、可折叠套件树、步骤树、日志按需 200 行 + 加载更多、对比开关。 |
| `frontend/src/components/report/SuiteTree.vue` | 递归套件树；根默认展开，子级点开；用例按结论着色。 |
| `frontend/src/components/report/KeywordSteps.vue` | 递归关键字步骤树。 |
| `frontend/src/router/index.ts` | 新增 `/reports/:runId` → `report-detail`，仍归属「报告」一级菜单。 |
| `frontend/src/locales/zh-CN.ts` / `en-US.ts` | 新增 `reports.*` 中英文键，页面无硬编码文案。 |
| `frontend/src/styles/main.css` | 追加 `.badge` / `.verdict-*` 公共样式。 |

---

## 2. RPT-03 契约示例 JSON

### 2.1 基线缺失（关键：两侧均不得兜底）

```json
{
  "code": 0,
  "codeName": "SUCCESS",
  "message": "ok",
  "data": {
    "baseline": null,
    "rows": [
      {
        "caseKey": "Pabot Merged/Shard-1.带宽达标",
        "history": null,
        "current": { "verdict": "PASS", "durationMs": 1250 },
        "change": "SAME"
      },
      {
        "caseKey": "Pabot Merged/Shard-1.环境自检",
        "history": null,
        "current": null,
        "change": "NOT_RUN"
      }
    ],
    "summary": { "newFail": 0, "newPass": 0, "bothFail": 0, "notRun": 1 }
  }
}
```

`baseline: null` 表示没有可用基线；`history` 全部为 null（因为没有基线，而不是因为用例没跑）。
前端左列显示「无（基线未执行）」，不拿本轮值填充。

### 2.2 基线存在且本轮未执行某用例

```json
{
  "baseline": { "runId": "RUN-20261030_120000", "startedAt": "2026-10-30T04:00:00Z" },
  "rows": [
    {
      "caseKey": "Pabot Merged/Shard-1.带宽达标",
      "history": { "verdict": "PASS", "durationMs": 1180 },
      "current": { "verdict": "FAIL", "durationMs": 1300 },
      "change": "PASS_TO_FAIL"
    },
    {
      "caseKey": "Pabot Merged/Shard-1.外部依赖跳过",
      "history": { "verdict": "PASS", "durationMs": 900 },
      "current": null,
      "change": "NOT_RUN"
    }
  ],
  "summary": { "newFail": 1, "newPass": 0, "bothFail": 0, "notRun": 1 }
}
```

右列对第二行显示「无（本轮未执行）」，禁止回填 `history` 的 PASS。

### 2.3 基线选择规则（后端实现）

- 自动：同 `projectId` + 同 `DUT` + 同 `LINK` + `started_at` 早于本轮 + `run_status='finished'`，按 `started_at DESC` 取第一条，并排除本轮。
- 手动：`baselineRunId` 必须存在且属于当前项目、DUT/LINK 与本轮一致；否则 400（`INVALID_ARGUMENT`）并给中文原因，**不静默改用自动基线**。
- `change`：`PASS_TO_FAIL` / `FAIL_TO_PASS` / `SAME` / `NOT_RUN`，由后端计算。
- `summary`：`newFail` / `newPass` / `bothFail` / `notRun`。

---

## 3. 测试真实结果

命令（cwd=`backend`）：

```powershell
$env:JAVA_HOME='C:\Users\everg\.jdk\temurin-25\jdk-25.0.2'
& 'C:\Users\everg\.maven\maven-3.9.16\bin\mvn.cmd' -B test
```

结果：**Tests run: 226, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**（总耗时 22.5 s）。
完整输出：`docs/evidence/v0.5/mvn-test-rpt02-rpt03.log`。

本包新增测试：

| 测试类 | 条数 | 结果 |
|---|---|---|
| `CompareServiceTest` | 11 | 全绿 |
| `ReportLogServiceTest` | 6 | 全绿 |
| `ReportLogReaderTest` | 5 | 全绿 |
| `LayerRulesTest`（ArchUnit） | 6 | 全绿 |

`CompareServiceTest` 覆盖：自动基线取最近、找不到基线不报错、手动基线跨项目拒绝、跨环境拒绝、
本轮未执行 `current=null`（关键）、历史未执行 `history=null`、PASS→FAIL、FAIL→PASS、
SAME/NOT_RUN、summary 四类计数、手动基线被采用。

> 说明：日志中存在 JaCoCo 在 JDK 25 下的 instrument 提示堆栈，属既有环境噪声；测试本身 0 失败。

## 4. 前端构建与 lint

| 命令 | 退出码 | 证据 |
|---|---|---|
| `npm run build` | **0** | `docs/evidence/v0.5/frontend-rpt02-rpt03-build.log` |
| `npm run lint` | **0** | `docs/evidence/v0.5/frontend-rpt02-rpt03-lint.log` |

项目无前端测试基建，未引入 vitest；以 build + lint 全绿为门槛。

> 说明：build 日志里的 `NativeCommandError` 是 PowerShell 把 Vite 的 stderr 分块体积告警
> 记成错误流所致，不是构建失败；同文件末尾为 `✓ built in 14.00s`，进程退出码 0。

---

## 5. 未覆盖项 / 已知风险

1. **真实本地 WSL 大文件未验**：分页与偏移读取用 RPT-01 手写 fixture 验证（4 条消息，覆盖跨关键字/FAIL 级别）；
   尚未在本地 WSL 的 1 万行级真实 `output.xml` 上压测，`LogXmlReader` 仍会从文件头流式扫描到目标区间
   （内存有界，时间随文件增大）。
2. **真 pabot 合并产物未验**：fixture 为手写样例，未覆盖 pabot 多 shard 的真实结构。
3. **未跑 DB 集成**：当前 8081 的 jar 未重启，新增接口与 V5_3 迁移未在 `robot_dev` 实际执行；
   本次以单元测试（伪造仓储）与构建/lint 为证据。
4. **手动基线未在前端暴露**：后端支持 `baselineRunId` 并校验同项目同环境，前端只做自动基线开关，
   手动选择基线未做 UI。
5. **日志正文不入库**：这是设计选择（DEV-PLAN §2.3），因此报告页必须能定位 `output.xml`；
   产物被清理后日志接口返回 404，列表/详情仍可读元数据。
