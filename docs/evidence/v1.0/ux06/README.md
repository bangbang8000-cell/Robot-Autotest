# UX-06 多语言 / 密度 / 暗色收口 · 走查证据（2026-11-05）

> 权威副本：WSL Ubuntu-24.04 `~/robot-autotest`（Windows 侧 `\\wsl$\Ubuntu-24.04\home\evergreen\robot-autotest\`）
> 走查对象：Vite dev server `http://localhost:5273` + 本地后端 `:8081`；浏览器 Windows Chrome headless（CDP `127.0.0.1:9222`）
> 判定口径：`docs/design/美术体系-开发与测试计划-20261104.md` UX-06 三判据 —— ① 每页 en-US 1440 + 1920 截图无换行撑破 / 横向滚动；② 密度切换后行高 44 ↔ 36 全局生效；③ 暗色微调
> 状态：**待验**（AI 不自行置「已完成」）

## 一、结论先行

| 判据 | 结果 | 证据 |
|---|---|---|
| en-US × 1440 / 1920 | **26 页全部** `lang=en-US`、`overflowPx=0`（`docScrollWidth == docClientWidth`） | `report.json`、`1440/`、`1920/` |
| 密度 44 ↔ 36 | 两档在 `/assets` 与 `/runs` 实测：`--table-row-h` **44px → 36px**，首行实际高度 **44 → 36**，单元格 padding 归零 | `density.json`、`density-measure.log` |
| 暗色微调 | 13 页 WCAG 探针修后 **`offenders=0` / `overflowPx=0`** | `dark/report.json`、`dark/1440/` |

截图共 **40 张**：亮色 1440 × 13、亮色 1920 × 13、密度对照 1（`1440/00-assets-compact.png`）、暗色 1440 × 13。
本轮除判据外，走查还暴露并修掉了 **5 个真实缺陷**（见第六节），其中 2 个是整页白屏 / 整页原始键名级别。

## 二、走查口径

- 语言：登录页内 `fetch('/api/auth/login')` 拿 token → 写 `localStorage`（`auth_token` / `current_project_id=1` / `locale=en-US`），后续每页都读 `document.documentElement.lang` 断言。
- 宽度：`Emulation.setDeviceMetricsOverride` 固定 **1440×900** 与 **1920×1080**，`captureBeyondViewport` 整页截图。
- 溢出判定不看截图肉眼看「有没有被切」：直接读 `documentElement.scrollWidth - clientWidth`，**>0 即判失败**，并把超宽元素写进 `offenders`。
- 暗色判定见第五节。测试账号 `qa-ui`，口令在仓库外 `~/.config/robot-autotest/qa-ui-pwd`（不落库、不进证据）。
- 打印页先 `POST /api/reports/{RUN_ID}/print-token?projectId=1&mode=internal` 取一次性票据，再把绝对 URL 归一成相对路径——
  票据 URL 的主机是 `127.0.0.1`，与入口 `localhost` 不同源，不归一就**不会共享 localStorage**，页面会退回中文并丢掉主题。
- 复现：`node scripts/walkthrough-light.mjs`、`node scripts/walkthrough-dark.mjs`、`node scripts/density-measure.mjs`（均需先起 9222 CDP 与 5273 dev server）。

## 三、覆盖清单（13 路由 × 2 宽度）

| # | 路由 | 名称 | 断言的 en-US 标题 |
|---|---|---|---|
| 01 | `/login` | login | AIDC AutoTest |
| 02 | `/overview` | overview | Overview |
| 03 | `/assets` | assets | Assets |
| 04 | `/cases` | cases | Test Cases |
| 05 | `/runs` | runs | Runs |
| 06 | `/plans` | plans | Execution Plans |
| 07 | `/reports` | reports | Reports |
| 08 | `/reports/RUN-20261104155814-952426` | report-detail | RUN-20261104155814-952426 |
| 09 | 打印票据 URL | report-print | Automated Test Run Report |
| 10 | `/admin` | admin | Admin |
| 11 | `/admin/audit` | audit | Audit Log |
| 12 | `/editor/1` | editor | （编辑器整页，无 h1，按 i18n 键渲染判） |
| 13 | `/viewer/1` | viewer | （只读查看器，同上） |

`report.json` 里每页都留了 `overflowPx`、`lang`、`title`、`h1`、`rows`、`firstRowH`、`rowVar`、`density` 七项读数，可逐页核对。

## 四、密度实测（`density.json`）

| 档位 | 路由 | `data-density` | `--table-row-h` | 数据行数 | 首行实测高 | 单元格 padding-top |
|---|---|---|---|---|---|---|
| comfortable | `/assets` | comfortable | 44px | 3 | 44 | 0px |
| comfortable | `/runs` | comfortable | 44px | 1 | 44 | 0px |
| compact | `/assets` | compact | 36px | 3 | 36 | 0px |
| compact | `/runs` | compact | 36px | 1 | 36 | 0px |

要点：

- 密度只改一个 CSS 变量 `--table-row-h`，行高由 `DataTable.vue` 的 `:deep(.n-data-table-tr)` 统一消费，
  所以**所有走壳的数据网格同时变**，不需要逐页改样式。
- 行高能精确落在 44 / 36，前提是单元格上下 padding 归零——naive-ui 默认给 `td` 上下 padding，
  不归零时 36px 会被撑成 42px 左右，看着「设了没生效」。
- 首屏由 `frontend/index.html` 内联脚本读 `localStorage.density` 先打 `data-density`，避免先亮后跳；
  服务端偏好（`user_preference`）登录后再覆盖一次，跨设备同步。

## 五、暗色专项

**探针做法**（`scripts/walkthrough-dark.mjs`）：遍历所有**直接含文本节点**的可见元素，用 `getComputedStyle` 取前景色，
向上逐层合成半透明背景直到拿到不透明底，按 WCAG 公式算对比度；正文阈值 4.5:1，大字（≥24px 或 ≥18.66px 加粗）3:1。
按「前景色 + 合成背景 + 阈值」去重，只报前 25 条——比人工看截图稳，也不吃「深色看着还行」的错觉。

首轮结果与修后对照：

| 页面 | 首轮违规（去重后） | 形态 | 修后 |
|---|---|---|---|
| login / overview / assets / cases / runs / plans / reports / admin / audit / editor / viewer | 0 | — | 0 |
| report-detail | 3 类 | 主按钮白字压浅蓝底 **2.57:1**；单选按钮组白底压浅蓝字 **2.57:1**；禁用主按钮同款 | 0 |
| report-print | 1 类 | `.muted` 用主题弱化色（暗色 `#8b9bb0`）压固定白纸 **2.83:1** | 0 |

**为什么是 2.57 而不是「差一点」**：naive-ui 的主按钮文字色默认取 `baseColor`（`#fff`），
暗色下 `--accent` 是 `#3fa7ff`，白字压上去只有 2.57:1。亮色主题下 `--accent` 更深，所以问题只在暗色暴露。
单选按钮组同理：`Radio.buttonColor / buttonColorActive` 库默认是纯白，白底 + 浅蓝字。

**修法（只改有证据的项，不重设色板）**：`frontend/src/composables/naiveTheme.ts` 在既有 token 派生层补
`Button.textColorPrimary / Hover / Pressed / Focus / **Disabled**`（全部取 `token.accentContrast`）与
`Radio` 的 `buttonColor / buttonColorActive / buttonTextColor* / buttonBorderColor*`。
`**Disabled**` 是第二轮才补上的：首轮只改 `textColorPrimary` 后按钮仍是 2.57:1，探针发现元素带
`n-button--disabled`，库里禁用态单独一个键 `textColorDisabledPrimary = baseColor(#fff)`（报告页「Export PDF」未就绪时正是这个状态）。

打印页是**固定白纸**，弱化色不能跟着平台主题漂：`ReportPrintView.vue` 在 color-check 白名单块内把
`.print-page .muted` 固定为 `#666`（白底 5.74:1）。

## 六、本轮修掉的真缺陷

| # | 现象 | 根因 | 修法 | 回归 |
|---|---|---|---|---|
| 1 | `/admin/audit` **整页白屏**，console 只有 `Unhandled error during execution of setup function at <DatePicker>` | 两个 `NDatePicker` 绑 `v-model:formatted-value`，初值 `''`，naive-ui 在 setup 阶段把空串交给 date-fns → `RangeError: Invalid time value` | 初值改 `null`，请求参数 `filter.from ?? undefined` | `AuditView.spec.ts`（2 条，含挂载不抛错 + 查询参数不带空串） |
| 2 | `/editor/1` 直接显示 `editor.titleEditor`、`editor.missingRoute` 等原始键名 | `locales/index.ts` 顶层展开是**浅合并**，编辑器包的 `editor` 段整段覆盖平台同名段 | 对 `editor` 段做字段级合并（平台键 + 编辑器键并集） | `locales/index.spec.ts`（6 条，双向断言两边的 `editor` 键都还在） |
| 3 | 暗色主按钮 2.57:1 | 库默认文字色 `#fff` | `Button.textColorPrimary…` 取 `accentContrast` | `naiveTheme.spec.ts` |
| 4 | 暗色主按钮**禁用态**仍 2.57:1 | 禁用态另有独立键 `textColorDisabledPrimary` | 同上补 `textColorDisabledPrimary` | `naiveTheme.spec.ts` 断言 `on-accent` |
| 5 | 暗色单选按钮组白底浅蓝字 2.57:1 | `Radio.buttonColor(Active)` 默认纯白 | 接回 `panel / surface2 / accent / border` token | `naiveTheme.spec.ts`（Button / Radio 值必须都在 token 集内） |

另有两处属本次走查链路本身、已在前序修掉并回归的：`<html lang>` 恒为 `zh-CN`（`setI18nLocale` 现在同步
`documentElement.lang`）、打印页跨源丢 localStorage（票据 URL 归一为相对路径）。

## 七、门禁读数

| 检查 | 结果 | 日志 |
|---|---|---|
| 前端单测 `npx vitest run` | **26 文件 / 181 用例全过** | `fe-vitest.log` |
| 前端类型 `npx vue-tsc --noEmit` | **0 error** | `fe-tsc.log` |
| 后端单测 `mvn -B test` | **542 / 542** | `backend-mvn-unit.log` |
| 后端集成 `artifacts/run_it.sh`（`DB_NAME=robot_it`，`-Pit`） | **30 / 30**（`UserPreferenceIT` 12 条，含密度偏好往返 + 非法值被拒） | `backend-mvn-it.log` |
| 全量门禁 `bash scripts/lint.sh` | **EXIT=0 / `[lint] PASS`** | `lint-sh.log` |

`lint.sh` 内与本包相关的几条读数：对比度 14 组 280 项全达标、字体子集 **1019 字 / 177506 B gzip**、
token 引用 0 悬空（px 字面量 424 处全在白名单）、`check_tables.py` 20 处数据网格全部走壳、
`check_wbs_consistency.py` 汇总数字与逐行状态自洽。

> 字体子集注意：`subset_font.py` 扫描的是源码里的 CJK（**含注释**）。本轮新增中文注释引入了
> 「巡幕感抄美诉赌赢」8 个未收录字，`--check` 直接红——必须 `python3 tools/subset_font.py` 重新生成，
> 不能只改预算。

## 八、证据文件

```
ux06/
├── README.md                 本文件
├── report.json               亮色 26 页读数（overflowPx / lang / h1 / rows / firstRowH / rowVar / density）
├── density.json              两档密度 × 2 路由实测
├── density-measure.log       密度实测 stdout
├── 1440/  01-13*.png         亮色 1440 逐页截图 + 00-assets-compact.png（紧凑档对照）
├── 1920/  14-26*.png         亮色 1920 逐页截图
├── dark/report.json          暗色 13 页读数（theme / offenders / overflowPx）
├── dark/1440/*.png           暗色 13 页截图
├── fe-vitest.log  fe-tsc.log
├── backend-mvn-unit.log  backend-mvn-it.log
├── lint-sh.log
└── scripts/
    ├── walkthrough-light.mjs  亮色走查 + 溢出探针
    ├── walkthrough-dark.mjs   暗色走查 + WCAG 对比度探针
    ├── density-measure.mjs    两档密度实测
    ├── summary.py             读数摘要
    └── ux06-inspect3.mjs / ux06-audit-err2.mjs / ux06-i18n-probe.mjs / ux06-btn-probe.mjs   定位 1 / 2 / 3-5 号缺陷的探针
```

## 九、边界（如实写明）

- 暗色只走 **1440** 一档；1920 的暗色未逐页截图，判据要求的是「暗色微调」，未扩到双宽。
- 溢出判定是 DOM 级（`scrollWidth - clientWidth`），不包含「文字被 `ellipsis` 截断」这类**布局内**的换行撑破；
  表格里长文本的截断按设计是允许的，故不判失败。
- 密度只有 comfortable / compact 两档，与美术计划一致，没有第三档需求。
- 全程 **无真实 DUT**（ADR-0013 冻结），所有页面数据来自本地 `robot_dev` 库与既有 run 产物。
- 本包状态按规矩置 **待验**，是否「已完成」由大师现场定夺。
