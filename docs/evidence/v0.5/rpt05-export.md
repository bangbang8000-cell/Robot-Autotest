# RPT-05 报告导出（PDF + Word）交付证据

- 范围：报告导出 PDF / Word、metadata 首页、内外部版、导出记录、下载与审计（FR-v0.5-21~23）
- 日期：2026-11-03
- 结论：后端全量 **266** 条测试通过（Failures 0 / Errors 0 / Skipped 0），新增 15 条；前端 `npm run build`、`npm run lint` 均退出 0；本机 Chrome 无头打印**真实生成 PDF 一次**（46,443 字节，退出码 0）。
- 重要声明：**本机运行中的 8081 仍是旧 jar，未重启**。因此「打印页经后端 → 5173 → Chrome」的端到端未实跑；接口 JSON 示例为按 DTO 结构手写的结构示例，不是本机实跑响应。PDF 冒烟验证的是本机 Chrome 打印链路本身。

## 1. 改动清单

### 1.1 后端主代码

| 文件 | 说明 |
|---|---|
| `backend/src/main/java/com/qa/autotest/domain/report/ExportFormat.java` | pdf/docx 格式、扩展名、Content-Type、wire 名解析 |
| `backend/src/main/java/com/qa/autotest/domain/report/ExportMode.java` | external/internal，internal 带红色标识语义 |
| `backend/src/main/java/com/qa/autotest/domain/report/ExportRecord.java` | `export_record` 领域记录 |
| `backend/src/main/java/com/qa/autotest/application/repository/ExportRecordRepository.java` | 导出记录仓储端口（查询一律带 project_id） |
| `backend/src/main/java/com/qa/autotest/application/report/ExportProperties.java` | `report.export.*`：打印页基址、Chrome 路径、90s 超时 |
| `backend/src/main/java/com/qa/autotest/application/report/ExportDocument.java` | Word 与打印页共用的同源文档模型 |
| `backend/src/main/java/com/qa/autotest/application/report/ExportRejectedException.java` | 外部版缺项拒绝，携带 `metadataMissing` |
| `backend/src/main/java/com/qa/autotest/application/report/DocxExporter.java` | POI `XWPFDocument` 直接生成 docx（无新依赖） |
| `backend/src/main/java/com/qa/autotest/application/report/PdfRenderer.java` | 浏览器探测、命令拼装、90s 超时、`%PDF-` 校验，失败 503 |
| `backend/src/main/java/com/qa/autotest/application/report/PdfExportService.java` | 签发一次性打印 token → 调无头浏览器打印 |
| `backend/src/main/java/com/qa/autotest/application/report/PrintTokenService.java` | 一次性 token，10 分钟，内存表 |
| `backend/src/main/java/com/qa/autotest/application/report/PrintReportService.java` | 打印 token 发放 / 打印页取数 |
| `backend/src/main/java/com/qa/autotest/application/report/PrintReportView.java` | 打印页 DTO（metadata + 套件树 + 计数 + internal 标记） |
| `backend/src/main/java/com/qa/autotest/application/report/ExportService.java` | 门禁、落盘、sha256/字节数、export_record、审计、下载归属校验 |
| `backend/src/main/java/com/qa/autotest/application/report/ReportTreeService.java` | 新增 `treeForPrint`：不做登录态权限判定，仍按 project_id 查询（供 token 打印/导出复用） |
| `backend/src/main/java/com/qa/autotest/infrastructure/ExportRecordRepositoryJdbc.java` | 导出记录 JDBC 实现 + TenantGuard |
| `backend/src/main/java/com/qa/autotest/api/report/ReportExportController.java` | 导出、导出历史、打印 token 三个接口 |
| `backend/src/main/java/com/qa/autotest/api/report/ExportDownloadController.java` | 流式下载，`Content-Disposition: attachment` |
| `backend/src/main/java/com/qa/autotest/api/print/PrintDataController.java` | `/api/print/{runId}?token=`（登录拦截白名单） |
| `backend/src/main/java/com/qa/autotest/api/WebConfig.java` | 排除 `/api/print/**`（token 即授权） |
| `backend/src/main/java/com/qa/autotest/api/GlobalExceptionHandler.java` | 缺项 400 携带 `metadataMissing`；503 映射 |
| `backend/src/main/java/com/qa/autotest/common/ErrorCode.java` | 新增 `DEPENDENCY_UNAVAILABLE(50300)` |
| `backend/src/main/resources/application.yml` | `report.export.print-base-url / Chrome / timeout` |
| `db/migration/V5_5__export_columns.sql` | `export_record` 增 `export_mode / file_name / sha256 / size_bytes` |
| `db/down/V5_5__export_columns.down.sql` | 回滚四列 |

### 1.2 前端

| 文件 | 说明 |
|---|---|
| `frontend/src/api/reports.ts` | 导出结果 / 历史 / 打印数据 / 一次性 token 类型与 API |
| `frontend/src/api/http.ts` | 新增 `requestBlob`：带 Bearer 的二进制下载 |
| `frontend/src/views/ReportDetailView.vue` | 导出 PDF / Word、内部版二次确认、缺项提示、下载链接、导出历史 |
| `frontend/src/views/ReportPrintView.vue` | `/reports/:runId/print` 打印页：无导航、白底 A4、红色内部标识、失败详情、token 失效中文提示 |
| `frontend/src/router/index.ts` | 打印页路由 `meta: { public: true, print: true }`（优先于详情路由） |
| `frontend/src/App.vue` | `meta.print` 时只渲染 RouterView，不渲染侧边栏/顶栏 |
| `frontend/src/styles/main.css` | `body.print-mode` 白底 + 导出工具条样式 |
| `frontend/src/locales/zh-CN.ts` / `en-US.ts` | 导出与打印页中英文案 |

### 1.3 测试

| 文件 | 条数 | 覆盖 |
|---|---|---|
| `backend/src/test/java/com/qa/autotest/application/report/DocxExporterTest.java` | 5 | 十项 metadata、缺失标红、内部页眉红字、500 字截断、合法 zip + RUN_ID |
| `backend/src/test/java/com/qa/autotest/application/report/ExportServiceTest.java` | 6 | 外部缺项 400+缺失键、外部齐全、内部缺项成功、export_record 字段、跨项目下载拒绝、同项目下载 |
| `backend/src/test/java/com/qa/autotest/application/report/PdfRendererTest.java` | 4 | 浏览器缺失 503、命令拼装、超时杀进程、假脚本成功产 PDF |

## 2. 真实 PDF 冒烟（本机 Chrome 无头）

临时 HTML 与 PDF 均在 `%TEMP%\rpt05-pdf-smoke\`，**未写入仓库**。HTML 含中文标题与四行 metadata 表格（RUN_ID / DUT / LINK / SW_BUILD）。

实际命令（PowerShell 经 `Start-Process -Wait` 等待 Chrome 退出）：

```text
C:\Program Files\Google\Chrome\Application\chrome.exe
  --headless=new
  --disable-gpu
  --no-pdf-header-footer
  --virtual-time-budget=15000
  --user-data-dir=%TEMP%\rpt05-pdf-smoke\profile
  --print-to-pdf=%TEMP%\rpt05-pdf-smoke\smoke.pdf
  file:///%TEMP%/rpt05-pdf-smoke/smoke.html
```

| 项 | 实测值 |
|---|---|
| 退出码 | **0** |
| PDF 路径 | `%TEMP%\rpt05-pdf-smoke\smoke.pdf`（仓库外） |
| PDF 字节数 | **46,443 B（约 45.4 KB，> 20 KB 门槛）** |
| 文件头（前 8 字节） | `25-50-44-46-2D-31-2E-34` = `%PDF-1.4` |
| HTML 字节数 | 807 B |
| 复验 | 冒烟后再次读取：存在、46,443 B、`%PDF-1.4` |

> 说明：首次用 `& chrome.exe ...` 直接调用时 PowerShell 对 GUI 子系统程序立即返回、拿不到退出码且未等待，改为 `Start-Process -Wait -PassThru` 后得到上述结果。后端 `PdfRenderer` 走 Java `ProcessBuilder.waitFor`，不受此 PowerShell 行为影响。

### 2.1 打印页无 token 时的中文提示（真实浏览器渲染）

用 5173 上正在运行的 Vite dev 页面 `http://127.0.0.1:5173/reports/RPT05-NO/print`（不带 token），Chrome 无头 `--dump-dom`：

```text
EXIT=0
DOM length=10,317
命中「打印链接无效或已过期」= YES
命中 class="print-error" = YES
实际 DOM 片段：
<div id="app"><div class="print-page"><div class="print-toolbar">…打印 / 另存为 PDF…</div>
<div class="print-error"><h1>自动化测试运行报告</h1>
<p>打印链接无效或已过期，请重新发起导出</p></div></div></div>
```

同一 DOM 中 `<body class="print-mode">` 生效，且**没有** `app-shell` / 侧边栏节点（`app-shell` 只出现在 Vite 内联 CSS 文本里）。说明打印页无导航、非白屏，token 缺失走中文提示。

## 3. Word 结构说明

`DocxExporter` 用 `org.apache.poi:poi-ooxml:5.2.5`（pom 已有）直接写 `XWPFDocument`：

1. **页眉（仅内部版）**：`word/header1.xml`，红字 `FF0000`、加粗、14pt，文案固定为
   `内部版 · 含缺失项，不可对外`。
2. **首页**：标题「自动化测试运行报告」+ RUN_ID/导出时间副标题。
3. **十项 metadata 表格**：2 列（键名 / 值），严格按 `MetadataGate.REQUIRED` 顺序。
   值为空或落在 `metadataMissing` → 写 **「缺失」** 且该 run 标红；**不留空串**。
4. **计数行**：PASS / FAIL / SKIP + 「可对外交付」或「存在缺失项，不可对外交付」。
5. **分页**：首页与正文之间插入 `BreakType.PAGE`。
6. **正文套件树**：每个套件按层级缩进，标题 `▸ 名称 [状态]`；套件内用例表 4 列
   （用例 / 结论 / 耗时(ms) / 失败消息）；FAIL 行结论与消息标红。
7. **失败消息截断**：超过 500 字符取前 500，追加
   `（已截断，原长度 N 字符）`；未超长原样输出。
8. **页脚**：`RUN_ID：... | 导出时间：...`（Asia/Shanghai，`yyyy-MM-dd HH:mm:ss`）。
9. 中文字体同时写 `ascii/hAnsi` 与 `eastAsia = Microsoft YaHei`，避免 Word 用西文字体渲染中文。

## 4. 接口返回示例（结构示例，未在本机 8081 实跑）

> 8081 运行的是旧 jar，未重启；以下 JSON 按后端 DTO / `ApiResponse` 结构手写。

### 4.1 导出成功

`POST /api/reports/RUN-003/export?projectId=7&mode=external&format=docx`

```json
{
  "code": 0,
  "codeName": "OK",
  "message": "OK",
  "data": {
    "exportId": 101,
    "format": "docx",
    "mode": "external",
    "bytes": 18432,
    "sha256": "9f2c...e41a",
    "downloadUrl": "/api/exports/101/download?projectId=7"
  }
}
```

### 4.2 外部版缺项（400）

`POST /api/reports/RUN-004/export?projectId=7&mode=external&format=pdf`

```json
{
  "code": 40001,
  "codeName": "INVALID_ARGUMENT",
  "message": "外部版导出被拒绝：缺少 metadata 项 DUT, LINK",
  "data": {
    "metadataMissing": ["DUT", "LINK"]
  }
}
```

### 4.3 打印 token

`POST /api/reports/RUN-003/print-token?projectId=7&mode=internal`

```json
{
  "code": 0,
  "codeName": "OK",
  "message": "OK",
  "data": {
    "token": "8f3c...（64 位十六进制）",
    "expiresAt": "2026-11-03T09:10:00Z",
    "printUrl": "http://127.0.0.1:5173/reports/RUN-003/print?token=8f3c...",
    "mode": "internal"
  }
}
```

### 4.4 打印页取数

`GET /api/print/RUN-003?token=8f3c...`

```json
{
  "code": 0,
  "codeName": "OK",
  "message": "OK",
  "data": {
    "runId": "RUN-003",
    "mode": "internal",
    "internal": true,
    "exportable": false,
    "metadataMissing": ["DUT"],
    "metadata": { "RUN_ID": "RUN-003", "CASE_ID": "PERF-IPERF-200G-001", "DUT": "", "...": "" },
    "passCount": 1,
    "failCount": 1,
    "skipCount": 0,
    "startedAt": "2026-11-03T01:58:00Z",
    "finishedAt": "2026-11-03T02:00:00Z",
    "tree": {
      "name": "RUN-003",
      "status": "FAIL",
      "cases": [],
      "suites": [
        {
          "name": "a.robot",
          "status": "FAIL",
          "cases": [
            { "caseKey": "suites/a.robot.case-1", "name": "用例-中文校验", "verdict": "FAIL", "durationMs": 1234, "failureMsg": "断言失败：期望 200 实际 500" }
          ],
          "suites": []
        }
      ]
    }
  }
}
```

token 无效/过期时：

```json
{ "code": 40001, "codeName": "INVALID_ARGUMENT", "message": "打印链接无效或已过期，请重新发起导出", "data": null }
```

### 4.5 下载

`GET /api/exports/101/download?projectId=7`

```text
HTTP 200
Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document
Content-Disposition: attachment; filename="report_RUN-003_external.docx"; filename*=UTF-8''report_RUN-003_external.docx
Content-Length: 18432
```

跨项目请求（`projectId` 与记录不符）返回 404 `导出记录不存在或不属于该项目`。

## 5. 测试与构建真实计数

命令（cwd=`backend`，JDK 25）：

```powershell
$env:JAVA_HOME='C:\Users\everg\.jdk\temurin-25\jdk-25.0.2'
& 'C:\Users\everg\.maven\maven-3.9.16\bin\mvn.cmd' -B test
```

| 项 | 实测 |
|---|---|
| 全量测试 | **Tests run: 266, Failures: 0, Errors: 0, Skipped: 0** |
| `DocxExporterTest` | 5 / 0 / 0 |
| `ExportServiceTest` | 6 / 0 / 0 |
| `PdfRendererTest` | 4 / 0 / 0 |
| ArchUnit `LayerRulesTest` | 6 / 0 / 0（BUILD SUCCESS） |
| 退出码 | **0** |
| 日志 | `docs/evidence/v0.5/mvn-test-rpt05.log` |

前端（cwd=`frontend`）：

| 命令 | 退出码 | 日志 |
|---|---|---|
| `npm run build` | **0** | `docs/evidence/v0.5/frontend-rpt05-build.log`（`built in 14.57s`） |
| `npm run lint` | **0** | `docs/evidence/v0.5/frontend-rpt05-lint.log`（无 eslint 输出） |

> 日志中的 JaCoCo `instrument` 警告是本机 JDK 25 与 JaCoCo 0.8.12 的已知兼容噪音（多次出现堆栈快照），**不影响测试结果**：最终汇总仍为 266 通过、BUILD SUCCESS。

数据库迁移（dev 库，执行后 8081 旧进程仍可运行，新列向后兼容）：

```text
flyway:10.10.0:migrate
Database: jdbc:mariadb://127.0.0.1/robot_dev (MySQL 8.0)
Current version of schema `robot_dev`: 4.3
Migrating 5.1 -> 5.3 -> 5.4 -> 5.5
Successfully applied 4 migrations, now at version v5.5 (execution time 00:01.486s)
BUILD SUCCESS
```

## 6. 未覆盖项与风险

1. **打印页后端→5173→Chrome 的端到端未跑**：8081 仍是旧 jar，按要求未重启；新接口需新 jar 启动后才可访问。已验证的是「Chrome 无头打印链路」（第 2 节）与「打印页组件/接口代码/单测」。重启后需补验：`POST export?format=pdf` 是否真实产生 PDF、内部版红色 banner 是否进入 PDF、token 是否一次性。
2. **`ExportRecordRepositoryJdbc` 未经真实 MySQL 集成测试**：已执行 Flyway V5_5 并通过 DDL 校验，单测用伪造仓储验证编排逻辑；插入/查询 SQL 的真实执行待新 jar 上线后随导出接口一并验证。
3. **PDF 依赖前端 5173 可达**：后端所在机器必须能访问 `report.export.print-base-url`；生产部署若前端独立域名/端口，需要改该配置。
4. **Chrome 版本相关**：`--virtual-time-budget=15000` 用于等待 Vue 异步取数；页面极慢或网络超时时，可能打印出「加载中」骨架。当前是本地 5173，风险低；上生产前应加页面就绪信号（如 `window.__PRINT_READY__`）再打印。
5. **token 在内存**：多实例部署时 token 不跨实例共享，需粘性或改 Redis；单机自用当前可接受。
6. **导出并发**：同一 run 并发导出同一模式会写同名文件（`CREATE/TRUNCATE_EXISTING`），以最后一次为准；记录表会各留一条。未加文件锁，属已知取舍。
7. **PDF 内嵌中文**：Chrome 打印依赖系统字体（冒烟已验证中文正常）；若迁移到无中文字体的 Linux 容器，需要安装 `Noto Sans CJK`，否则会出现方框。
