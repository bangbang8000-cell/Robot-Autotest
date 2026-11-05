# UX-05 第一批 · 表格规范壳 + 表格主题接管（2026-11-05）

包：#75 UX-05（美术 S2 数据与报告视觉）第一批，四块里做完第一块「表格规范」。
复现：`bash docs/evidence/v0.5/ux05-20261105/run-all.sh`（17 条命令，逐条落 `.log`）。

## 一、这一批解决什么

UX-04b 把 12 处原生表格换成 `NDataTable` 之后，「规范」反而是散的：`size="small"`、
`:bordered="false"`、`:scroll-x="1000"`、一段几乎相同的 `#empty` 在 **19 处**各写一遍。
改一次表头样式要动 19 个文件；加一个加载状态要记得 19 个 `v-if`。

本批把这三类重复收进一个壳 `frontend/src/components/DataTable.vue`，并顺手修掉两个
**迁库时静默失效**的缺陷（见第二节）。

## 二、两个静默缺陷（守门与单测都发现不了，只有暗色截图能看见）

| 缺陷 | 成因 | 后果 |
|---|---|---|
| 暗色下表头发白 | 库默认 `thColor = composite(cardColor, tableHeaderColor)`，而 `tableHeaderColor` 是 light 主题里的亮白常量 | 暗色页面里表头被叠成浅灰白，浅色表头文字压上去几乎看不见 |
| 表头不再弱化 | 库默认 `thTextColor = textColor1`，我们把 `textColor1` 映射成了正文色 `--text` | UX-01b 的「表头为灰阶弱化色」判据形同虚设，表头与正文同色 |

修法：`composables/naiveTheme.ts` 增加 `DataTable` 命名空间，把
`thColor → --surface-2`、`thTextColor → --text-muted`、`tdColor → --panel`、
`borderColor → --border` 显式接回 token（token 仍是唯一真源，方向不变）。

## 三、做了什么

1. **`utils/tableWidth.ts` + 8 条单测**：`computeScrollX()` 按列宽推导横滚宽度。
   原来 12 处写死 `:scroll-x="1000"` —— 加一列不加数字那列被压成一行一个字，
   删一列又多出一条无意义横滚。视图从此只声明列宽。
2. **`components/DataTable.vue`（泛型壳）+ 9 条组件测试**：
   - 粘性表头：默认 `max-height: calc(100vh - 340px)`，长列表表体内滚、表头不动
     （naive-ui 在给 `max-height` 时会把表头渲染成独立元素，不需要 CSS sticky）；
   - 骨架行：加载中且**无数据**时按「列数 × 骨架行数」画灰条，复用真实表头（不跳版）；
   - 刷新不闪骨架：加载中但已有数据时保留旧行（翻页时整表消失比慢一点更难受）；
   - 空态默认 `EmptyState`，`#empty` 插槽可覆盖（端口面板那行灰字就靠这个保留）；
   - `#empty` 折叠：19 处重复的 `EmptyState` 声明改成三个 props。
3. **19 处视图迁移**（脚本 `migrate_tables.py`，扫描器而非正则 —— 属性值里有
   `=>` 箭头函数，正则会把 `>` 提前截断并吞掉后面整张表）：
   Admin 4 / Assets 4 / Audit 1 / Cases 1 / Overview 3 / Plans 1 / ReportDetail 2 / Reports 2 / Runs 1。
   净减 116 行（+85 / −201）。
4. **新增守门 `tools/check_tables.py`**：`NDataTable` 只许出现在壳里；`<DataTable>` 实例 ≥ 19；
   壳的三条实现探针（骨架条 / 默认空态 / max-height）必须在；原生 `<table>` 只许三处白名单
   （打印页零库依赖、metadata 定义表、线设计系统展示页）。配 `neg-tables.py` 注入证据。
5. **修 `check_icons.py` 误报**：`\bicon=` 会吃掉 `:icon="emptyIcon"`（`\b` 在 `:` 与 `i` 之间成立），
   把变量名当未定义图标名。加 `(?<!:)` 后顾，并补第 4 条自检用例。

## 四、判据对照（§3.2c TC-S2-01~08）

| ID | 判据 | 证据 |
|---|---|---|
| TC-S2-01 | 19 处数据网格全走壳，0 处裸 `NDataTable` | `check-tables.log`（PASS，逐文件计数） |
| TC-S2-01n | 注入裸 `NDataTable` 必须 FAIL 且指向该文件 | `neg-tables.log` |
| TC-S2-02 | 横滚宽度按列宽推导 | `fe-test.log`（tableWidth 8 条） |
| TC-S2-03 | 骨架条数 = 列 × 行，且不同时渲染空态 | `fe-test.log`（DataTable 9 条） |
| TC-S2-04 | 刷新保留旧行 | 同上 |
| TC-S2-05 | 空态默认 + 插槽覆盖 | 同上 |
| TC-S2-06 | 默认 max-height；`null` 可关 | 同上 |
| TC-S2-07 | 表头/单元格色接 token，且表头 ≠ 正文色 | `fe-test.log`（naiveTheme 4 条） |
| TC-S2-08 | 原生 `<table>` 仅三文件白名单 | `check-tables.log` |

## 五、实测读数

- 前端单测 **127/127**（19 文件；本批 109 → 127）
- `vue-tsc --noEmit` 通过
- `lint.sh` **18 项**全 PASS（新增 `check_tables`）
- 体积：图标 4,207 B / vendor 105,173 B / 懒加载共享分包最大 `DataTable-*.js` 79,524 B（预算 120 KiB）/ 字体 976 字 171,234 B
- 后端 504 tests / 0 failures（本批未改后端，跑一遍排除连带）

## 六、本批刻意没做

- **错误态不进表格**：各页已有 `ErrorLineView`，壳只报「空」。原来的
  `v-if="!loading && !error"` 在 error 时把整块区域吞成空白，反而让人以为表格没渲染出来。
- **不改写 columns**：对齐/列宽仍由列自己声明，壳只做「总宽 = 列宽之和」这件列算不了的事。
- **行高双档（舒适/紧凑）留到 UX-06**：密度切换要配合全局行高变量一起做，单独在壳里加
  一个 `density` prop 会成为第二个真源。

## 七、遗留 / 下一步

UX-05 剩余三块：图表薄封装（y 轴刻度 + tooltip + 空图 + 图例显隐）、品牌
（几何标记 + 字标 + favicon + 登录页品牌区 + 版本号入「关于」）、3 种空态插画。

另：**登录后四态人工截图**仍是 UX-04b / UX-05 共同缺口的唯一一项（本地无测试账号、
Windows 侧访问不到 WSL dev server），需在现场补。
