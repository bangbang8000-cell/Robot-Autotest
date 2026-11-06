# RPT-01 / RPT-05 残余收口：写路径与导出接口走 MockMvc（2026-11-06）

## 1. 缺口

`ReportControllerTest` 8 条只覆盖读路径；写路径与导出接口停在服务层单测：

| 层 | 已有 | 缺什么 |
|---|---|---|
| `ReportIngestServiceTest` 4 条 | 解析 → 门禁 → 入库的编排 | 控制器是否真把显式 `outputXmlPath` 透传；错误码是否按 400/404 返回 |
| `ExportServiceTest` 6 条 | 缺项拒绝 / 内部版放行 / 归属校验 | `mode`/`format` 默认值、缺项 400 的 `data.metadataMissing` 形状、下载响应头 |
| `RunsView` / 报告页 | 消费 `exportable` / `metadataMissing` / 下载地址 | 前端契约没有接口层反证 |

## 2. 做法

### `ReportParseApiTest`（4 条，真实解析器 + 真实编排，仓储用 Mockito）

| 用例 | 验证点 |
|---|---|
| `parse_完整XML入库` | 真实 pabot 合并产物经 `POST /api/reports/{runId}/parse` → 200；`exportable=true`、`metadataMissing=[]`、`metadata.DUT=GPU-01`、`counts = 2/1/1`；Captor 反查入库对象：4 条用例、10 项 metadata |
| `parse_缺项XML不可导出` | 缺项产物 → 200 但 `exportable=false`、`metadataMissing.length=10` |
| `parse_路径不存在` | 400 + 「output.xml 不存在或不可读」，不退化成 500 |
| `parse_运行不存在` | 404 |

### `ReportExportControllerTest`（7 条）

| 用例 | 验证点 |
|---|---|
| `export_默认外部版PDF` | 不传参时按 `external/pdf` 透传；响应字段 `exportId/format/mode/bytes/downloadUrl` |
| `export_内部版Docx` | `internal/docx` 原样透传，控制器不强制外部版 |
| `export_缺项返回400与缺失清单` | 400，`data.metadataMissing[0..1]` 逐项可读，`message` 含拼接清单 |
| `download_返回附件流` | `Content-Type=application/pdf`、`Content-Disposition` 带 `attachment` 与 RFC 5987 `filename*=UTF-8''`（中文名不直塞 `filename=`）、字节一致 |
| `download_不存在返回404` | NOT_FOUND → 404，不退化成 500 |
| `history_映射字段` | 导出历史字段名与前端一致，Instant 按 standalone 默认 epoch 秒断言 |
| `printToken_默认外部版` | 不传 `mode` 按 `external` 发放，返回 `token/printUrl/expiresAt` |

## 3. 门禁读数

| 项 | 结果 |
|---|---|
| `tools/subset_font.py` | PASS |
| 前端 `vitest run` | 28 文件 / 202 用例 |
| 前端 `vue-tsc --noEmit` | 0 错误 |
| 前端 `vite build` | `built in 10.09s` |
| 后端 `mvn -o -B test` | **612 / 0 / 0**（新增 11 条） |
| `scripts/lint.sh` | PASS |
| `check_release_docs.py` | PASS |
| `check_wbs_consistency.py` | PASS（96 包 / 待验 83 / 已完成 13） |

日志见 `01-subset-font.log` … `08-wbs.log`。

## 4. 残余

1. standalone MockMvc 无鉴权上下文：401/403 仍由 `AuthorizationBoundaryTest` 覆盖，本批未重复。
2. 写路径的入库对象是 Mockito 捕获，不是真实 MariaDB 落库；`ReportRepositoryJdbc` 的 SQL 由 `ReportQueryService` / 打印端到端证据间接覆盖。
3. 导出「对外成功分支」仍缺真实样本（该 run 无 metadata），属 RPT-05 既有残余，未在本批解决。
