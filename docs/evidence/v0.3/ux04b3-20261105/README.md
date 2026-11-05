# UX-04b 第四批：资产 / 计划 / 管理 / 概览四页迁库组件

日期：2026-11-05 ｜ WBS：`#80`（`UX-04b` 视图组件化迁移）｜ 复现：`bash docs/evidence/v0.3/ux04b3-20261105/run-all.sh`

## 1. 这批做了什么

四页从原生 `table`/`button`/`select`/`input` 迁到 `naive-ui` + 自建库组件，并各补一份视图级回归。

| 页面 | 迁移内容 | 新增视图测试 |
|---|---|---:|
| `AssetsView` | 三张表 → `NDataTable`；页签 → `NTabs`；端口面板改「表格下方卡片」；凭据搜索改前端过滤；换设备清空端口 | 8 |
| `PlansView` | 表格 → `NDataTable`；表单 → `FormField` + `NInput`/`NInputNumber`/`NSelect`/`NCheckbox`；删窄屏卡片版 | 7 |
| `AdminView` | 四张表 → `NDataTable`；成员角色内联 `NSelect`；停用文案统一 | 8 |
| `OverviewView` | 三张表 → `NDataTable`；状态列 `badge` → `StatusTag` | 4 |

视图级测试总数 **8 → 35**（6 个 spec），前端全量 **61 → 88**。

## 2. 五个判断，都写了理由

1. **勾选列仍不用 `NDataTable` 内置的 `type: 'selection'`。**
   内置列只认 `checked-row-keys`，`data` 一变就把不在当前页的键丢掉，与 `useSelection` 的跨页保留
   （FR-v0.2-35）**静默冲突**。四页统一自渲染 `NCheckbox`，状态仍由 `useSelection` 持有。

2. **资产页的端口面板从「行内展开」改成「表格下方卡片」。**
   行内展开要往单元格里塞一张子表和一张表单，宽度和焦点管理都没有余量；而「点一行 → 下方出详情」
   是本项目已统一的交互语言（执行页日志、报告页明细）。同一套心智比省一次滚动更值。

3. **凭据页签的搜索改成本地过滤。**
   `GET /credentials` 没有关键词参数（按项目全量返回）。原实现在凭据页签输入搜索词是**静默无效**的：
   有输入框、有回车、列表不动，用户会以为系统坏了。改为前端按 `name`/`credType`/`username` 过滤，
   且关键词变化不再打后端。

4. **计划页删掉窄屏卡片版。**
   原来 `@media (max-width: 760px)` 下把表格藏起来、另渲染一套卡片，两份 markup 纯复制。
   `NDataTable` 的 `scroll-x` 已能在窄屏横滚，双份渲染只会让以后每次迁移都要改两处。

5. **概览页状态列从「只有颜色」换成 `StatusTag`。**
   原来是 `class="badge verdict-pass"` 之类按 class 上色，**没有图标**。六预设里 crimson/gold
   与 fail/warn 同色相，色觉障碍下等于没有状态信息。换成图标 + 文字双编码，与执行页/报告页口径一致。

## 3. 顺手修的两个真缺陷

- **换链路端点设备时不清已选端口**：`changeLinkDevice` 原来只重载端口列表、不动 `aPortId`。
  从设备 A 换成设备 B 后，`aPortId` 仍指向 A 的端口 → 提交出一条「A 端设备是 B、A 端端口是 A 的端口」
  的错链，而下拉框还显示着上一次的端口名，看不出已经错位。现在换设备即清空对应端口并重载。
- **删除当前展开的设备后面板指向已不存在的对象**：`removeDevice` 未复位 `portsDeviceId`，
  端口卡片会继续挂着已被删除设备的名字。现在一并清空。

## 4. 两个新踩的坑（后续写测试直接用）

1. **naive-ui 2.45.3 的 `NTabPane` 从 `children.tab` 取标题**，只给 `:tab` prop 会渲染出
   **三个空白页签且没有任何报错**（`n-tabs-tab__label` 里是空的）。必须写
   `<template #tab>{{ label }}</template>`。
2. **`h(RouterLink, ...)` 在未装 router 的测试环境会崩在 vue-router 内部**
   （`Cannot read properties of undefined (reading 'resolve')`）。因为它是**模块导入的组件**，
   `app.component('RouterLink', stub)` 换不掉它 —— 必须在模块层 `vi.mock('vue-router')`。

## 5. 终态实测

| 项 | 结果 |
|---|---|
| 前端全量 | **88 / 88**（14 文件） |
| 视图级回归 | **35 / 35**（6 文件：资产 8 / 计划 7 / 管理 8 / 概览 4 / 运行 4 / 用例 4） |
| 后端 | **504 / 0 / 0**（68 suite） |
| `lint.sh` | **PASS**（含 14 组 × 20 项 = 280 项对比度、i18n、token、交互态、组件 import、体积） |
| 图标 chunk | 4,208 B ≤ 15 KiB |
| vendor chunk | 105,054 B ≤ 200 KiB |
| 懒加载共享分包 | 100,668 B ≤ 120 KiB（最大 `EmptyState-*.js` 91,872 B） |
| 字体子集 | 953 字 / 167,838 B gzip ≤ 800 KiB |
| 12 条命令 | **rc 全 0** |

## 6. 未完成

- `ReportDetailView`（元数据表 / 导出历史 / 对比表 + 10 个原生按钮）仍为原生元素。
  计划：按钮与下拉迁库组件，**元数据键值表保持原生**（两列定义表，不是数据网格，
  塞进 `NDataTable` 会凭空多一行表头并丢掉 `th` 语义）。
- `ReportPrintView` 按 §2.2 步骤 5 的约定**保持零库组件依赖**。
- 登录后界面的人工四态截图仍记待现场（本地拿不到测试账号，Windows 侧访问不到 WSL 的 dev server）。
