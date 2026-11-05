# UX-04b 第五批：报告详情页迁库组件

日期：2026-11-05 ｜ WBS：`#80`（`UX-04b` 视图组件化迁移）｜ 复现：`bash docs/evidence/v0.3/ux04b4-20261105/run-all.sh`

## 1. 这批做了什么

`ReportDetailView`（860 行，全站最后一张"全原生"的页）迁到 `naive-ui` + 自建库组件，
并补一份视图级回归。这是 `#80` 里原生控件最密的一页：10 个 `<button>`、2 个下拉、1 个开关、2 张表。

| 位置 | 迁移前 | 迁移后 |
|---|---|---|
| 返回列表 / 下载 / 历史下载（3） | `<button class="link-btn">` | `NButton text type="primary"` |
| 四个导出按钮 | `<button class="btn / btn-ghost">` | `NButton`（外部两枚 `type="primary"`，内部两枚默认档） |
| 趋势用例选择 | `<select class="input">` | `NSelect`（`{label,value}` 选项） |
| 日 / 周分段 | 两个 `<button>` + `.active` 换边框色 | `NRadioGroup` + `NRadioButton` |
| 对比开关 | `<input type="checkbox">` + 手搓 `checked` | `NCheckbox`（`@update:checked` 直接给布尔） |
| 导出历史表 | 原生 `<table>` | `NDataTable`（5 列） |
| 对比表 | 原生 `<table>` + 窄屏媒体查询堆叠 | `NDataTable`（4 列，自带横滚） |
| 对比汇总 4 个计数 | 纯色文字 `change-*` | `StatusTag`（图标 + 文字） |
| 对比「变化」列 | `<span class="badge">` 按 class 上色 | `StatusTag`（色档收敛到 `changeTone`） |
| 对比两侧结论 | 文字 + `verdict-*` 上色 | `StatusTag` + 耗时（`verdictTone`） |
| **元数据键值表** | 原生 `<table>` | **刻意不动**（见 §2 第 1 条） |

视图级回归 **35 → 43**（7 个 spec，本页 8 条），前端全量 **88 → 96**。

## 2. 七个判断，都写了理由

1. **元数据键值表刻意保持原生 `<table>`，并加了一条测试防止被"顺手"改掉。**
   它是 `th`（键）+ `td`（值）的**两列定义表**，不是数据网格：没有"列"这个概念，
   没有排序/分页/筛选需求。塞进 `NDataTable` 会凭空多出一行表头（`键 / 值`），
   并丢掉 `th` 的语义（读屏时不再读成"定义项"）。测试里直接断言
   `table.meta-table` 存在、`tbody tr` 与 `th` 各 10 个，就是把这个决定钉在回归里。

2. **「两轮都是 FAIL」单独给一条 `BOTH_FAIL` 文案。**
   后端 `CompareService.changeOf` 只判断"变没变"，两轮都 FAIL 走 `SAME`，
   于是界面显示「无变化」——而用户读到的是"通过且没变"。对比表存在的意义正是让人看出
   哪些在持续坏，这条文案不只是措辞问题。后端未改（那是运行语义，属 RPT-04 的领域），
   只在展示层按 `history.FAIL && current.FAIL` 覆盖成「持续失败 / Still failing」，
   色档给 warn（不是 fail）——它本轮没有**新**坏，要与刚回归的那条区分开。

3. **导出历史的「内部版」从红字改成 warn 徽标。**
   原来 `text-fail` 的红色读起来像"这次导出失败了"，而它表达的是**适用范围**
   （内部版不可对外）。真正的失败走 `ErrorLineView`，两者不该同色。
   换 `StatusTag` 后还顺带有了图标。

4. **外部导出的禁用提示收敛成一个 `externalHint` computed。**
   原来同一串 `t('...missingHint', {keys})` 在四个按钮的 `:title` 和下方说明行里
   各写一遍（共 5 处），改文案时漏一处就出现两套说法。

5. **日 / 周从"两个按钮 + `.active`"改成 `NRadioGroup`。**
   它本质是单选（互斥、永远有一个选中）。原来的 `.active` 只换边框色：键盘用户看不出
   当前选中项，读屏也拿不到 `aria-checked`。这属于 UX-01b 那类"功能对、语义缺"的问题。

6. **对比汇总的四个计数也换成 `StatusTag`。**
   又是"只有颜色"：`持续失败/新增失败/修复/未执行` 四段文字分别靠 warn/fail/ok/muted
   区分，六预设里 crimson/gold 与 fail/warn 同色相时基本读不出差别。换成图标 + 文字后
   与表内"变化"列、其它页状态列同一口径。

7. **删掉三组已无消费者的样式，而不是留着"以防万一"。**
   `.segmented` / `.compare-toggle` / `.change-*` / 对比表的窄屏堆叠媒体查询
   （`@media (max-width: 760px)` 把表头和单元格全改成 `display:block`）全部移除。
   最后一条能删是因为 `NDataTable` 自带 `scroll-x`：同一份数据不再需要"宽屏一张表 +
   窄屏一套 block 布局"两份渲染。`check_states.py` 与 `check_tokens.py` 复跑均 PASS，
   确认没有把守门项一起删掉。

## 3. 三个新踩的坑（后续写组件直接用）

1. **`NSelect` / `NRadioGroup` 不能 `v-model` 到窄类型 ref。**
   两者的 `value` 是 `string | number | null`，`v-model:value` 到 `ref<'day'|'week'>`
   会被 `vue-tsc` 判成不兼容赋值。改用 `:value` + `@update:value`，在**一个 setter 里**
   收敛类型（`value === 'week' ? 'week' : 'day'`），别在模板里撒 `as`。
2. **`NDataTable` 的列宽是数字（`width: 120`），不带 `px`**，所以不会触发
   `tools/check_tokens.py` 的 px 白名单；但**模板内联** `style="width: 220px"` 会被扫到。
   本次把宽度写在 scoped class 里，既过守门也避免同一数字散落多处。
3. **`StatusTag` 带了 `data-tone`**，测试可以直接断言色档
   （`tag.dataset.tone === 'warn'`），比比对 class 名稳——改名不会让测试假通过。

## 4. 一处连带成本

视图注释里新增了 6 个汉字，`tools/subset_font.py --check` 立刻 FAIL
（`刨/挡/撒/杂/猜/较` 不在子集里）。已重新生成：**953 → 959 字，167,838 → 168,738 B gzip**。
这条守门很值得留着——它把"改注释导致字体回退到系统字体"这种肉眼难发现的问题变成红灯。

## 5. 终态实测

| 项 | 结果 |
|---|---|
| 前端全量 | **96 / 96**（15 文件） |
| 视图级回归 | **43 / 43**（7 文件：资产 8 / 计划 7 / 管理 8 / 概览 4 / 报告详情 8 / 运行 4 / 用例 4） |
| 后端 | **504 / 0 / 0**（68 suite） |
| `lint.sh` | **PASS**（对比度、i18n、token、交互态、组件 import、体积、文档门禁） |
| 图标 chunk | 4,208 B ≤ 15 KiB |
| vendor chunk | 105,080 B ≤ 200 KiB |
| 懒加载共享分包 | 100,855 B ≤ 120 KiB（最大 `DataTable-*.js` 91,630 B） |
| 字体子集 | 959 字 / 168,738 B gzip ≤ 800 KiB |
| 12 条命令 | **rc 全 0** |

## 6. 未完成

- `SuiteTree` / `KeywordSteps` 仍是 `badge verdict-*` + 原生 `.tree-toggle` 按钮。
  `.tree-toggle` **没有 hover / focus-visible**（不在 `check_states.py` 的扫描范围，
  因为它在 scoped style 里），键盘用户看不出焦点。下一批处理：套件/用例行 → `StatusTag` +
  `NButton text`；关键字步骤行**保持紧凑徽标**（步骤是逐行密集渲染的次要信息，
  每行一个图标反而变噪）。
- `ReportPrintView` 按 §2.2 步骤 5 的约定**保持零库组件依赖**，不动。
- 登录后界面的人工四态截图仍记待现场（本地拿不到测试账号，Windows 侧访问不到 WSL 的 dev server）。
