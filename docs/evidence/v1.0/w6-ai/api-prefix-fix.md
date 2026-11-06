# 缺陷订正：W2–W5 前端 API 双 `/api` 前缀

**发现时间**：2026-11-06（W6 落地时核对 `frontend/src/api/http.ts`）
**影响面**：`testReports.ts`、`kernels.ts`、`groups.ts`、`imports.ts` 共 17 处端点。

## 根因

`http.ts` 的 `request()` / `requestMultipart()` 自身会拼 `BASE_URL`（默认 `/api`）：

```ts
const response = await fetch(`${BASE_URL}${path}`, { ... })
```

仓库既有模块一律写 `/system/build-info`、`/execution/runs` 这类**不带** `/api` 的路径；
W2–W5 新增模块沿用了文档里的完整路径写法 `'/api/test-reports'`，运行时实际请求
`/api/api/test-reports` → 404。构建、`vue-tsc`、vitest、既有静态守门**全都发现不了**：
它们不发起真实 HTTP。

## 修复

- 17 处端点路径去掉 `/api` 前缀；`requestBlob` 原本就带剥离逻辑，一并统一写法。
- `aiPanel.ts`（W6）从一开始就按无前缀写。
- 新增 `tools/check_api_prefix.py`：扫 `frontend/src/api/*.ts`，禁止
  `request<T>('/api/...')`（`requestBlob` 允许，因为它要接收后端返回的完整下载 URL）。
- 接入 `scripts/lint.sh`，`[OK] request() URL 前缀检查通过（扫描 19 个 API 模块）`。

## 元验证

修复前对该守门做反向验证：在任一模块注入 `request<X>('/api/probe')` 时脚本须返回非 0；
修复后对当前仓库实跑返回 0。守门脚本本身是纯静态对账，不依赖浏览器。
