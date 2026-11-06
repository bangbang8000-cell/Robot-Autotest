# RPT-01 接口层用例（残余项收口）

日期：2026-11-06　状态：待验（`docs/plan/WBS-STATUS.md` 第 77 行）

## 1. 缺口是什么

RPT-01 的真 pabot 产物解析、metadata 装配、服务层单测都已验过，唯一剩余的是
**API 层没有任何 MockMvc 用例**。控制器是薄壳，但它有三处约定只写在代码里、没有钉住：

1. 每个端点先 `access.bindProject(projectId)` —— 越权必须在进服务层之前被拒；
2. 列表 `size` 上限 100（`Math.min`），前端传 500 不能被真的取出 500 条；
3. 详情视图字段名（`metadata` / `metadataMissing` / `exportable` / `counts`）与前端契约一致。

## 2. 覆盖（`ReportControllerTest`，8 条）

| 用例 | 钉住的约定 |
|---|---|
| 列表：size 夹到 100 且先绑定项目 | `verify(query).page(7,2,100)`；`verify(access).bindProject(7)` |
| 详情：视图字段 | `data.metadata.DUT`、`data.metadataMissing[0]`、`data.exportable`、`data.counts.*` |
| 详情：不存在 → 404 | `NOT_FOUND` 映射为 404 + `codeName` |
| 越权 → 403 | `bindProject` 抛 `FORBIDDEN` 时**不进入**查询服务 |
| 缺 `projectId` → 400 | 请求不成立时既不绑项目也不查服务 |
| 参数类型不对（`page=abc`）→ 400 | 同上 |
| 解析入库：路径透传 | `outputXmlPath` 原样传给 `ReportIngestService` |
| 套件树 / 日志默认分页 | `tree` 与 `logs` 默认 `offset=0 / limit=200` 的原值透传 |

## 3. 顺带修掉的真实缺陷（缺参 500）

写第 5 条用例时先得到的是 **500**，不是 400：

- `GET /api/reports/RUN-1` 漏 `projectId` → `MissingServletRequestParameterException`
- `GET /api/reports?projectId=7&page=abc` → `MethodArgumentTypeMismatchException`

两者都不在 `GlobalExceptionHandler` 的映射表里，落进兜底分支被当成「服务内部错误」。
后果：前端漏传一个参数就会触发 500 告警，真正的服务故障被淹掉。

已加 `handleBadRequestParam`，统一返回 **400 + INVALID_ARGUMENT**，
message 直接点出参数名（`缺少参数：projectId` / `参数类型不正确：page`）。
这是全局修复，对**所有接口**生效，不只报告域；与 HRD-01 修掉的「405 误 500」同类。

## 4. 门禁读数（2026-11-06）

日志目录：`docs/evidence/v1.0/rpt01/`

| 项 | 结果 | 日志 |
|---|---|---|
| 字体子集 | 1035 字 / 179634 B gzip，预算内 | `01-subset-font.log` |
| 前端单测 | 28 文件 / **202** 通过 | `02-frontend-vitest.log` |
| 类型检查 | exit 0 | `03-frontend-tsc.log` |
| 前端构建 | `✓ built in 10.11s` | `04-frontend-build.log` |
| 后端全量 | **598 / 0 / 0**（较上批 590 增 8） | `05-backend-test.log` |
| lint / 发布文档 / WBS | 全部 PASS（96 包 / 待验 83 / 已完成 13） | `06`–`08` |

## 5. 仍未验

1. **写路径未走 MockMvc**：`POST /{runId}/parse` 只验了参数透传，没有用真实 XML 走一遍
   「上传 → 解析 → 入库」；该链路仍靠服务层单测 + 真 pabot 产物（`docs/evidence/v0.5/rpt01-rpt02-real/`）。
2. **导出接口**（`/export`、外部版导出）未补 MockMvc：缺项拦截的 409 语义仍只有服务层用例。
3. **无鉴权上下文**：`standaloneSetup` 不装拦截器，`tenant` / `operator` 不是真实登录态；
   真实登录下的 401/403 由 `AuthorizationBoundaryTest` 覆盖。
