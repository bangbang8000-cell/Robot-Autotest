import pathlib

p = pathlib.Path(__file__).resolve().parents[3] / "plan" / "WBS-STATUS.md"
s = p.read_text()

DESC = (
    "美术 S2 数据与报告视觉：表格规范 **19** 处数据网格迁壳 + 图表薄封装 `TrendChart` "
    "+ 品牌轻量版（`AppLogo`/`favicon`/`AboutDialog`/登录页品牌区）+ 空态插画 `EmptyIllustration` "
    "+ 打印版式（RPT-05，零库依赖保留）"
)

NOTE = (
    "归 v0.5（与 RPT 同期）。前置 `UX-04`（已转 `待验`）。"
    "**2026-11-05 第一批实际把四块全做了**（原计划只批一块）："
    "① 表格规范壳——19 处数据网格迁 `DataTable`（粘性表头/行高双档/对齐/列宽按列宽推导/骨架行/三态空态统一）"
    "+ `naiveTheme` 接管表头主题（修掉暗色表头发白、表头不弱化两处迁库静默缺陷）；"
    "② 图表薄封装 `TrendChart`（y 轴刻度带单位/tooltip/空图/偏离图例显隐）；"
    "③ 品牌轻量版——`AppLogo` 几何标记+字标、`favicon.svg`、版本号移入 `AboutDialog`、登录页品牌区、`index.html` 标题去版本号；"
    "④ 3 种空态插画 `EmptyIllustration`（no-data/no-result/error 语义分流，错误态不再被误读成「暂无数据」）。"
    "守门新增 `tools/check_tables.py`（19 处走壳/0 处裸 `NDataTable`/原生 `<table>` 仅白名单 3 文件）+ 自检负向、"
    "`tools/check_brand.py`（favicon/侧栏+登录页标记/版本号只在关于）+ 自检。"
    "**余下一块**：打印版式（RPT-05）——`ReportPrintView` 按约定保持零库组件依赖，打印视觉打磨并入本包收尾或留待 `UX-06` 暗色微调顺带，未单列新包。"
    "设计计划 v1.10 已记三条写进文档的判断。"
)

EVIDENCE = (
    "`frontend/src/components/DataTable.vue`、`TrendChart.vue`、`AppLogo.vue`、`AboutDialog.vue`、"
    "`EmptyIllustration.vue`、`public/favicon.svg`、`tools/check_tables.py`、`tools/check_brand.py`；证据 `docs/evidence/v0.5/ux05-20261105/`"
)

repls = [
    (
        "| 82 个任务包 | **3** | **0** | **66** | **0** | **13** |",
        "| 82 个任务包 | **2** | **0** | **67** | **0** | **13** |",
    ),
    (
        "66 待验 · 0 待现场 · 3 未开始 · 0 进行中",
        "67 待验 · 0 待现场 · 2 未开始 · 0 进行中",
    ),
    (
        "| **进行中** | 0 | **2026-11-05 清零**：`UX-04b` 七批全部落库（12 个业务视图组件化完成），已转 `待验` | 剩余 3 个 `未开始` 包：`EXEC-01`（冻结至 F2 现场，已过）、`UX-05`、`UX-06` |",
        "| **进行中** | 0 | **2026-11-05 清零**：`UX-04b` 七批全部落库（12 个业务视图组件化完成），已转 `待验` | 剩余 2 个 `未开始` 包：`EXEC-01`（冻结至 F2 现场，已过）、`UX-06`；`UX-05` 已转 `待验` |",
    ),
    (
        "美术 S2 数据与报告视觉：表格规范 12 处 + 图表封装（y 轴/tooltip/空图）+ 品牌轻量版 + 空态插图 + 打印版式",
        DESC,
    ),
    (
        "| B5 | 未开始 | 待补 |",
        f"| B5 | 待验 | {EVIDENCE} |",
    ),
    (
        "归 v0.5（与 RPT 同期）。前置 `UX-04`",
        NOTE,
    ),
]

for a, b in repls:
    assert a in s, f"MISS: {a[:40]}"
    s = s.replace(a, b, 1)

p.write_text(s)
print("WBS updated OK")
