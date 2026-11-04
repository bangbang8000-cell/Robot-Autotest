# UX-04c / UX-04d / UX-04b 第二批 证据（2026-11-05）

**复现**：在 WSL 权威副本根目录执行 `bash docs/evidence/v0.3/ux04c-20261105/run-all.sh`，
12 条命令的 stdout 落在本目录，脚本打印每条的**真实 rc**。

> 为什么必须是脚本文件：`wsl.exe -d Ubuntu-24.04 bash -lc '...'` 这种内联写法里，
> `$?` 与 `$变量` 会先被 Windows 命令行层展开一次，rc 恒等于 0，证据会变成假绿。
> 本项目踩过这个坑（见 `docs/evidence/v0.3/ux04-s1-20261104/run-all.sh` 顶部注释）。

## 一、run-all.sh 实测结果

| # | 命令 | rc | 说明 |
|---:|---|---:|---|
| 1 | `npm run build` | 0 | 产物刷新（体积守门读 dist） |
| 2 | `npm run test` | 0 | 前端 **47/47**（7 个文件） |
| 3 | `vitest run src/config/navigation.spec.ts` | 0 | 菜单 **12/12** |
| 4 | `bash scripts/lint.sh` | 0 | secret-scan / eslint / mvn compile / 12 条静态守门全过 |
| 5 | `tools/gen_accent_tokens.py` | 0 | 12 组候选派生全部达标 |
| 6 | `tools/check_contrast.py` | 0 | **14 组 × 20 项 = 280 项** |
| 7 | `tools/check_component_imports.py` | 0 | 28 个 `.vue` 全部有 import |
| 7b | 同上，负向自检（删一个 import） | **1（期望 1）** | 证明这条守门不是摆设 |
| 8 | `tools/check_bundle_budget.py` | 0 | 图标 4,207 B / vendor 104,957 B / 懒加载分包 96,990 B |
| 9 | `tools/subset_font.py --check` | 0 | **928 字**全在子集，gzip 164,113 B |
| 10 | `mvn -B -q test` | 0 | 后端 **504 / 0 fail / 0 error**（68 个 suite） |
| 11 | `git diff --stat` | 0 | 见 `diff-stat.log` |

> 后端测试数由 `target/surefire-reports/*.xml` 逐 suite 汇总得出（`-q` 会隐藏汇总行，
> 不能让「没看到数字」等于「全绿」）。

## 二、三件事分别解决了什么

### 1. 两级菜单（UX-04c）

- 结构抽成唯一真源 `frontend/src/config/navigation.ts`：`NavGroup` / `NavLeaf` +
  `isNodeActive` / `activeGroupKey` / `visibleNav` / `filterNav`。
- **选中判定不再用 `router-link-active`**：资产三页共用一个 `/assets`，靠 `?tab=` 区分，
  浏览器默认规则会把三个子项同时点亮。`AssetsView` 因此补了页签 ↔ URL 双向同步
  （深链、前进后退、菜单点进来都落在正确的页签上）。
- 选中态是**三重编码**：左侧 3px 强调色竖条 + `accent-soft` 底 + `accent` 文字。
  只换底色时，暗色下有几套预设与 hover 几乎分不出来。
- 展开态落 `localStorage`（默认全展开，只记手动折叠），进入某组子页时自动展开。
- 权限：`管理` 组**不设 `adminOnly`** —— 该页除用户管理外还有「项目成员」，PM 与
  项目所有者也要用，整组按管理员隐藏属功能回归。只挡审计子项。
- 守门：`navigation.spec.ts` 12 条，含「每个叶子路径必须能被路由表解析」（防菜单指向 404）、
  「不允许单子项折叠组」、「`/reportsX` 不得误点亮 `/reports`」。

### 2. 土豪金调优（UX-04d）

先把约束算清楚，再改色：

- 亮色金的硬约束是 `accent/soft ≥ 4.5`。`#8a6300` 时该值为 4.73，余量仅 0.23 ——
  **「把主色调亮」这条路本来就走不通**（`#966800` 在 soft 上只有 4.31）。
- 病根其实是机械派生的 tint：暗金 × 10% + 白 = `#f3efe6`，一块几乎无彩的米灰，
  选中行看着像灰色。改成手调金色 tint `#fdf3d6`（仍是金）后，`accent/soft` 反升到 **4.91**，
  于是主色才得以提亮到 `#8f6500`。
- 结果（三项判据，阈值 4.5）：

| | 主色 | text/accent | accent/panel | accent/soft |
|---|---|---:|---:|---:|
| 亮色（改前） | `#8a6300` | 5.43 | 5.43 | 4.73 |
| **亮色（改后）** | **`#8f6500`** | **5.21** | **5.21** | **4.71** |
| 暗色（改前） | `#f5c542` | 11.55 | 9.45 | 7.86 |
| **暗色（改后）** | **`#ffd24a`** | **13.00** | **10.63** | **8.78** |

- `SOFT_OVERRIDE` 是 `gen_accent_tokens.py` 里唯一的例外，只对 gold 生效，
  **不豁免任何判据**（改错直接 FAIL）；色板展示色 `--preset-gold` 同步为 `#ffd24a`。

### 3. 视图迁移第二批（UX-04b）

- `AuditView`：7 列表格 → `NDataTable`（`scroll-x` 保留宽表横滚）、`NSelect`×2 +
  `NDatePicker`×2（`value-format="yyyy-MM-dd"`，与后端参数格式一致）、`NButton`、
  `NPagination`、空态走表格 `#empty` 槽。
- `ReportsView`：报告主表与清理清单双表 → `NDataTable`；状态列改 `StatusTag`
  （图标 + 文字双编码），色档统一由新增的 `runStatusTone()` 提供。
- **顺带修真缺陷**：上一批三处 `EmptyState` **都没 import**。`<script setup>` 下未注册的
  PascalCase 标签不报错、只渲染空白 —— 三个页面「无数据」时一片空，而构建、单测、
  `lint` 全绿。已补 import，并新增 `tools/check_component_imports.py` 把它变成守门项
  （上线 10 分钟内又抓出 `StyleguideView` 一处同款）。
- 体积变化：`NDataTable` 被 Vite 拆成独立懒加载分包（gzip 91,592 B）。原 vendor 判据
  只认含 `naive-ui` 字样的 chunk，这 92 KiB **完全在预算之外**。已补判据
  「非入口 / 非路由视图的共享分包合计 ≤ 120 KiB」，当前 96,990 B。

## 三、本轮未做 / 留待下批

- `UX-04b` 未完成：**用例 / 运行 / 计划 / 概览**四页仍为原生 `table` / `button` / `input`；
  打印页（RPT-05）按约定保持零库组件依赖。
- 菜单的**四态截图与 1440/1920 实际观感**仍需人看：本地拿不到可用测试口令，
  进入不了登录后的侧边栏（Windows 侧也访问不到 WSL 内的 dev server）。
  已在 `/dev/styleguide` 加了「导航（两级菜单）」静态预览段（用与侧边栏完全相同的
  class，四态同屏），人可自行打开核对；真机四态截图仍记为待现场项。
