# 前端编码约定（CONVENTIONS-frontend）

**文档编号** RA-CONV-2026-002 ｜ **适用** `frontend/` ｜ **版本** v0.2

> 本文只写"代码怎么写"。IA（菜单与路由）见 `docs/release/v0.2-ledger/DOC-03-IA冻结纪要.md`，
> 技术选型理由见 `docs/adr/`。

## 1. 目录职责

```
src/
├── api/          # 只做 HTTP：路径、参数、类型；不放业务判断
├── components/   # 跨页面复用组件（如 ProjectPicker）
├── locales/      # zh-CN / en-US，键结构必须一致
├── router/       # 路由表 + 全局守卫
├── stores/       # Pinia：跨页面状态（登录态、语言、当前项目）
├── styles/       # 全局样式变量与基础类
└── views/        # 页面级组件，一个一级菜单一个文件
```

- `api/*.ts` 不 import `stores` / `views`；`views` 不直接 `fetch`，一律走 `api/`。
- 页面私有逻辑留在 `views`；出现第二个使用方再抽到 `components/`（不要提前抽象）。
- 不新增 UI 框架依赖；样式用 `styles/main.css` 的变量与基础类。

## 2. TypeScript

- 开启严格模式；不用 `any`，确实未知用 `unknown` 再收窄。
- 接口类型放对应 `api` 模块并导出，页面复用；不要在两处各写一份。
- 后端 `record` 的字段名即前端类型字段名，不另造映射层。
- 可选字段用 `?` 或 `| null`，与后端可空语义一致。

## 3. 请求层

- 统一 `api/http.ts` 的 `request<T>` / `requestMultipart<T>` / `toQuery`；不直接用 `fetch`。
- 查询参数用 `toQuery`，空值（`undefined` / `null` / `''`）不发送，避免后端把空串当筛选条件。
- 令牌由 `setTokenProvider` 注入，页面不手工拼 `Authorization`。
- 错误统一抛 `ApiError`（含 `code`、`codeName`、`message`）；页面显示 `codeName：message`。
- 上传（Excel / 文件）必须用 `requestMultipart`，不要手工设 `Content-Type`。

## 4. 状态管理

| 状态 | 位置 | 规则 |
|---|---|---|
| 令牌 / 当前用户 / 当前项目 | `stores/auth.ts` | 令牌与项目 id 落 `localStorage`；用户信息以 `/auth/me` 为准 |
| 语言 | `stores/locale.ts` | 切换即写 `localStorage` 并同步 `<html lang>` |
| 页面内筛选、分页、表单 | 页面 `ref` / `reactive` | 不上 Pinia |

- **项目上下文是接口路径参数**：页面用 `auth.projectId` 拼 `/projects/{id}/...`，不要把项目 id 放请求头当权威。
- 登录态变化（登录 / 登出 / 401）必须走 store action，不在页面里直接改 `localStorage`。

## 5. 路由与鉴权

- 路由表只放 6 个一级菜单 + `/login` + 404；新增一级菜单需先改 IA 纪要（变更要 ADR）。
- 全局 `beforeEach`：未登录跳 `/login?redirect=...`；已登录访问 `/login` 回 `/overview`。
- 前端隐藏按钮**只是体验**，不是安全边界；权限以服务端 403 为准。
- 路由懒加载（`() => import(...)`），首屏不打包全部页面。

## 6. 组件

- 单文件组件顺序：`<script setup lang="ts">` → `<template>`；不写 Options API。
- props / emits 必须显式类型；组件不读全局 store 除非它本身就是"全局控件"（如 `ProjectPicker`）。
- 列表页统一结构：页头（标题 + 项目选择）→ 筛选工具栏 → 错误 / 提示 → 表格 → 分页。
- 破坏性操作（删除）先 `window.confirm`；提交中禁用按钮防重复。

## 7. i18n

- 所有面向用户的文案走 `t('...')`，禁止硬编码中文或英文。
- `zh-CN.ts` 与 `en-US.ts` **键结构必须完全一致**；新增键两边同时加。
- 键按页面分组：`app` / `menu` / `common` / `login` / `project` / `assets` / `cases` / `admin` / `notFound`。
- 带参数用命名插值 `{count}`，不用字符串拼接。

## 8. 样式

- 颜色、圆角、字号优先用 `:root` 变量（`--bg` / `--panel` / `--text` / `--muted` / `--accent` / `--ok` / `--fail`）。
- 基础类：`.page` / `.card` / `.table` / `.input` / `.btn` / `.btn-ghost` / `.toolbar` / `.pager`；页面自定义类加前缀避免撞名。
- 不写内联 `style` 做主题；布局用 flex / grid。
- 暗色是当前唯一主题，新增颜色须保证对比度可读。

## 9. 安全与数据

- 不在前端持久化任何明文口令 / 密文；凭据表单提交后立即清空输入。
- 不在控制台打印令牌、请求头或响应中的敏感字段。
- 展示后端返回的 `configured`，不尝试"顺便显示密码"。
- 导出 / 下载类功能后续版本接入，本版不新增。

## 10. 自检

```bash
npm run build   # vue-tsc --noEmit + vite build
npm run lint    # eslint
```

- 两者必须 exit 0 才能开 PR；日志落 `docs/evidence/<版本>/`。
- 后端契约变更时，同步改 `api/*.ts` 类型与页面字段，不留"字段名不一致"的兼容层。
