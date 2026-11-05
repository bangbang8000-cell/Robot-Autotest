# UX-04b 第七批：登录 / 404 / 编辑器收尾 —— 业务视图迁移完成

日期：2026-11-05 ｜ WBS：`#80`（`UX-04b` 视图组件化迁移）｜ 复现：`bash docs/evidence/v0.3/ux04b6-20261105/run-all.sh`

## 1. 这批做了什么

| 页面 / 组件 | 迁移内容 | 附带修正 |
|---|---|---|
| `LoginView` | 两个原生 `<input>` → `FormField` + `NInput`；提交按钮 → `NButton attr-type="submit"` | 删掉 `.login-card .field` / `.field-label` / `.login-card .input` 三条死样式 |
| `NotFoundView` | `<RouterLink class="btn">` → `RouterLink custom` + `NButton` | 路由跳转仍归 RouterLink，视觉与三态归按钮 |
| `OverviewView` | 工具栏最后一枚原生 `<button class="btn">` → `NButton` | 第四批漏网的一枚 |
| `EditorView` | 5 个 `.editor-host__btn` → `NButton`（保存 `primary`+`loading`、版本开关 `small`、版本行 `tiny`、diff 关闭 `small`） | 删掉 4 条三态全无、且用 `rem` 而非项目 token 的样式 |

新增 `LoginView.spec.ts` 5 条。视图+组件回归 **51 → 56**，前端全量 **104 → 109**。
至此**业务视图组件化迁移完成**：12 个业务视图全部走库组件 + 自建组件；`ReportPrintView` 按 §2.2 步骤 5
的约定保持零库依赖；`StyleguideView` 本身即设计系统展示页。

## 2. 测试抓到的两个真回归（都是"看起来正常"的那类）

1. **`autocomplete` 静默丢失。**
   `NInput` 不会把顶层 `autocomplete` 透传到内部 `<input>`（要写进 `:input-props`）。
   迁移后密码管理器再也认不出这是用户名/密码框，而页面**完全正常**、提交也能用 ——
   只有自动化断言 `inputs[0].getAttribute('autocomplete') === 'username'` 才发现。
   这类"属性掉在组件边界"的问题，靠人眼验收永远验收不出来。

2. **提交按钮的 `type` 语义被库占用。**
   `NButton` 的 `type` 是**语义色档**（`primary` / `default` / `tertiary`），
   HTML 的提交行为要写 `attr-type="submit"`。写错了的表现是：点按钮能提交、**回车没反应**。
   已加断言 `form button[type=submit]`，并把这个坑留在了 `LoginView.vue` 的注释里。

## 3. 一个取舍：`NotFoundView` 为什么还要 RouterLink

直觉做法是把 `NButton` 直接当链接（`tag="a"` + `href`），或把 `class="btn"` 挂在 `<a>` 上。
两者都会退化成"长得像按钮的链接"：前者丢掉 RouterLink 的 `prefetch` 与导航事务语义，
后者拿不到按钮的 `disabled` / `loading` 状态位。当前写法是 `custom` + `navigate`：
**导航仍由 RouterLink 负责，视觉与三态由按钮负责**，两边都不越界。

## 4. 终态实测

| 项 | 结果 |
|---|---|
| 前端全量 | **109 / 109**（17 文件） |
| 视图 + 组件级回归 | **56 / 56**（9 文件：8 视图 spec + `SuiteTree.spec.ts`） |
| 后端 | **504 / 0 / 0**（68 suite） |
| `vue-tsc --noEmit` | 通过 |
| `lint.sh` | **PASS**（0 FAIL；含 `eslint`） |
| 图标 chunk | 4,207 B ≤ 15 KiB |
| vendor chunk | 105,148 B ≤ 200 KiB |
| 懒加载共享分包 | 102,531 B ≤ 120 KiB |
| 字体子集 | 967 字 / 169,974 B gzip ≤ 800 KiB |
| 13 条命令 | **rc 全 0** |

## 5. lint 中断的一次（如实记录）

第一次跑证据时 `lint.sh` rc=1，原因是我给 `LoginView.spec.ts` 的 mock 写成
`vi.fn(async (_username: string, _password: string) => undefined)` —— ESLint 的
`@typescript-eslint/no-unused-vars` **连 `_` 前缀也拦**。改成泛型签名
`vi.fn<(username: string, password: string) => Promise<void>>()`：参数只存在于类型里，
既是"未使用变量"零命中，又保住了 `toHaveBeenCalledWith` 的类型检查。

## 6. 收尾时发现并修掉一处文档自相矛盾（顺带新增一条守门）

把 `UX-04b` 从 `进行中` 转 `待验` 时，按惯例先核对 WBS 的数字，发现：

| | 汇总行说 | 逐行数出来 |
|---|---|---|
| 待现场 | **0** | **8** |
| 已完成 | **13** | **5** |

原因是 2026-11-05 的 F2 裁决（8 个 `待现场` 包授权代改 `已完成`）只更新了**汇总行、
分组表、修订记录三处**，**8 个单包行的状态格没动**。读文档的人通常只看汇总那一行，
于是"看起来 13 已完成"是假的。

本批按裁决逐行改正（`EDT-08 / EXE-07 / EXE-08 / PLN-03 / PLN-04 / HRD-03 / HRD-04 / DOC-03`），
并在每行收尾列追加「授权代改」出处说明（原文的待现场口径保留，便于追溯）。
一次性脚本留在本目录：`apply-f2-decision.py`（改状态格，要求每行**恰好**一个 `| 待现场 |`）
与 `annotate-f2-rows.py`（追加说明）。当前自洽结果：
**82 包 / 未开始 3 / 进行中 0 / 待验 66 / 待现场 0 / 已完成 13**。

**防复发**：新增 `tools/check_wbs_consistency.py` 并接线进 `lint.sh`，机检四件事 ——
状态值合法、汇总五数 = 逐行分布、「当前进度 N/M」与逐行一致、单包行列数 = 7。
这类"汇总骗人"的问题，人眼对着几百行表格是发现不了的。

## 7. 未完成

- 登录后界面的人工四态截图仍记待现场（本地拿不到测试账号，Windows 侧访问不到 WSL 的 dev server）。
  这是 `UX-04b` 转 `待验` 后**转 `已完成` 的唯一缺口**，不是代码项。
- `ReportPrintView` 不动（约定）。
