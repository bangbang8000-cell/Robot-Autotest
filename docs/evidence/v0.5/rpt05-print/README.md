# RPT-05 打印链路端到端验证证据（2026-11-05）

- 范围：FR-v0.5-21~23 的「打印链路」这一段——导出接口 → 一次性打印 token → 前端打印路由 → 无头浏览器渲染 → PDF 落盘 → 下载接口 → 导出记录落库。
- 用例：`RUN-20261104155814-952426`（项目 `demo` / `projectId=1`，2 用例全 PASS，10 项 metadata 全缺 → 天然是「内部版可导出、外部版被拒」的样本）。
- 结论：**全链路跑通，10 个检查点全部符合预期**；PDF 2 页 / 235,253 B / `%PDF-1.4`，sha256 在「导出响应、下载响应体、`export_record` 表」三处一致；内部版红字标识（`#c00000`）在 PDF 内容流中出现 12 次；一次性 token 复用被 400 拒绝；对外模式被 10 项 metadata 门禁 400 拒绝。**本包已消除 2026-11-03 遗留的唯一阻塞项（8081 旧 jar）**。
- 既有冒烟证据（Chrome 打印链路本身 + Word/单测）：见同目录上层 [../rpt05-export.md](../rpt05-export.md)，本文只补充**后端到浏览器的真实全链路**。

## 1. 链路与实测

```text
robot-autotest-backend (:8081)
  └─ POST /api/reports/{runId}/print-token?mode=internal
       └─ 生成一次性 token（10 分钟）→ printUrl = {REPORT_PRINT_BASE_URL}/reports/{runId}/print?token=…
  └─ POST /api/reports/{runId}/export?mode=internal&format=pdf
       └─ PdfRenderer 启动浏览器 → 打开 printUrl
            └─ Vite dev server (:5273) 返回打印页
                 └─ GET /api/print/{runId}?token=（经 Vite /api 代理回 8081，一次性消费）
            └─ --print-to-pdf=<绝对路径> 生成 PDF
       └─ 计算 sha256 / bytes → 写 export_record → 返回 exportId
  └─ GET /api/exports/{exportId}/download?projectId=（归属校验 + Content-Disposition）
```

| # | 检查点 | 请求 | 实测 |
|---|---|---|---|
| 01 | 鉴权自检 | `GET :8081/api/projects` | **200** / 0.030s / 339B，2 个项目 |
| 02 | 打印 token 发放 | `POST .../print-token?projectId=1&mode=internal` | **200** / 349B，`expiresAt` 为签发后 10 分钟，`printUrl=http://127.0.0.1:5273/reports/RUN-…/print?token=…` |
| 03 | 前端→后端取打印数据（**经 5273 代理**，验证打印页那一段真实可达） | `GET :5273/api/print/{runId}?token=` | **200** / 1072B；`mode=internal`、`internal=true`、`exportable=false`、`metadataMissing` 10 项、`passCount=2`、套件树 4 节点 |
| 04 | 一次性 token 复用 | 同上，第二次 | **400** `{"code":40001,"message":"打印链接无效或已过期，请重新发起导出"}` |
| 05 | 内部模式导出 PDF（走无头浏览器） | `POST .../export?projectId=1&mode=internal&format=pdf` | **200** / **7.47s**（含浏览器启动+渲染）/ `exportId=2` / 235,253B / sha256 `bf4cba03…` |
| 06 | 对外模式导出（metadata 门禁） | 同上 `mode=external` | **400**，`metadataMissing` 列出 `CASE_ID…CASE_KIND` 全部 10 项 |
| 07 | 下载 + 内容核验 | `GET :8081/api/exports/2/download?projectId=1` | **200** / `Content-Type: application/pdf` / `Content-Length: 235253` / 文件头 `%PDF-1.4` / sha256 与 05 一致 |
| 08 | 导出历史 | `GET .../exports?projectId=1` | **200**，`fileName=report_RUN-…_internal.pdf`、`status=created`、`requestedBy=1` |
| 09 | 落库核对 | `select … from export_record` | `2 \| RUN-… \| internal \| pdf \| created \| 235253 \| bf4cba032319 \| report_…_internal.pdf` |
| 10 | 清理 | `update api_token set revoked_at` | 临时会话令牌已撤销（只写入 sha256，口令未落盘） |

原始请求/响应逐条留档在 `artifacts/`（`01..09` 各含 `.hdr` + `.body`），执行日志 `rpt05-e2e.log`，执行体 `rpt05-e2e.sh`。

> 证据里的打印 token 是**一次性且 10 分钟过期**的（03 已消费、04 复用即 400），落档时早已失效；会话令牌从未写入任何产物文件，只在进程内存里传给 `curl`，结束即 `revoked_at`。

## 2. PDF 结构核验（`pdf-inspect.py` / `pdf-inspect.txt`）

本机无 poppler / pypdf，因而改用**结构核验**而不是文本抽取：

| 项 | 实测 |
|---|---|
| 字节数 | 235,253 |
| 文件头 | `%PDF-1.4` |
| sha256 | `bf4cba032319e859a2bb76e02ebcb638f4677509f5511e793455a6d4eaffe558` |
| `/Type /Page` | **2 页** |
| 内容流 | 95 段（可 Flate 解压） |
| 文本绘制算子 `Tj/TJ` | **346 次**（有真实排版内容，非空白页） |
| `#c00000` 颜色算子 | **12 次**（`.7529 0 0 rg/RG`）→ **内部版红字/红框确实进了 PDF** |
| 图片算子 | 0（纯文字版式，符合预期） |

> 说明：`frontend/src/views/ReportPrintView.vue` 的 `.print-internal-banner` 是 `color:#c00000; border:2px solid #c00000`，与 PDF 里出现的 `.7529 0 0` 精确对应，可反向证明这一页渲染的正是**带内部标识**的打印页。

## 3. 环境前提：两处 WSL 开发机专有适配（不属产品逻辑）

产品代码按 Linux/生产假设写，在 WSL 开发机上直接跑不通，因此**只改运行环境、不改代码**：

1. **打印页基址**：`ExportProperties.printBaseUrl` 默认 `http://127.0.0.1:5173`，而本机 Vite dev server 实际监听 **5273**。
2. **浏览器只有 Windows Chrome**：WSL 内无 `google-chrome`/`chromium`（`linux-chrome=none`），且 `PdfRenderer` 把 `--print-to-pdf=<Linux 绝对路径>` 直接交给浏览器，Windows Chrome 会把 `/tmp/x.pdf` 解读成 `C:\tmp\x.pdf`，导致后端判定「浏览器退出但未产出文件」。

处置：

| 项 | 文件（本目录内留档） | 内容 |
|---|---|---|
| systemd drop-in | `10-report-export.conf` | `REPORT_PRINT_BASE_URL=http://127.0.0.1:5273`、`REPORT_PDF_CHROME=<桥接脚本>` |
| Chrome 桥接器 | `chrome-wsl-bridge.sh`（仓库 `scripts/`） | 用 `wslpath -w` 把 `--print-to-pdf` 路径翻成 UNC 再调 Windows Chrome；退出码 0 但文件缺失时显式报错 |

环境快照（服务状态、端口、Chrome 版本、迁移版本、run 行）见 `env.txt`：`robot-autotest-backend.service` active、`8081`(java) / `5273`(node) 监听、Google Chrome 159.0.8130.0 dev、库版本 `7.4`、`run.exportable=0` 且 `metadata_missing` 为 10 项。

## 4. 可复现性

同一脚本连跑两次，结果结构一致、字节数不同（235,216 → 235,253 B，sha256 各自稳定）：

- 差量来自 PDF 元数据/内容流里的**渲染时间戳**，属预期；
- 两次都是 2 页、`%PDF-1.4`、`export_record` 各留一条 `created` 记录，说明导出幂等性只到「不报错、记录完整」，**不做内容去重**（与 `rpt05-export.md` §6.6「同 run 并发导出以最后一次为准」一致）。

## 5. 已知缺口（不阻塞本包转 `待验`）

1. **只在内部模式拿到正样本**：当前 run 无任何 metadata，无法构造「对外模式成功」的样本；对外模式本轮验证的是**拒绝分支**（含 `metadataMissing` 全量回显）。
2. **开发机适配不能当生产结论**：`10-report-export.conf` 与 `chrome-wsl-bridge.sh` 只服务本机验证；生产需 Linux Chrome/Chromium + 正确的 `report.export.print-base-url`。
3. **PDF 内容只做结构核验**：无文本抽取工具，未逐字比对页面文案（页数、文本算子数、红色算子数、sha256 三处一致已足以证明「渲染成功且是内部版」）。
4. **token 存内存**：多实例部署不共享（单机自用可接受，见 `rpt05-export.md` §6.5）。
5. **`--virtual-time-budget` 的固有风险**：页面极慢时可能打印到「加载中」骨架，生产前建议加页面就绪信号（`rpt05-export.md` §6.4）。

## 6. 复跑方法

```bash
# 前提：backend :8081 与前端 dev server :5273 均在跑
bash docs/evidence/v0.5/rpt05-print/rpt05-e2e.sh    # 产物落 /tmp/rpt05-e2e/
python3 docs/evidence/v0.5/rpt05-print/pdf-inspect.py   # 需与脚本同目录的 07-download.pdf
```

脚本自带清理：结束时把注入的临时会话令牌 `revoked_at` 置位（`api_token` 只存 SHA-256，不留原文）。
