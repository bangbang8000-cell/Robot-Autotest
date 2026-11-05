#!/usr/bin/env python3
"""UX-05 第一批落库后回写 WBS：#75 未开始 → 进行中（本批只完成四块里的「表格规范」一块）。

顺带把汇总行的「进行中 0」与提示语改对 —— 改了单包行忘了汇总，正是
`tools/check_wbs_consistency.py` 存在的原因（2026-11-05 真发生过一次）。
"""
from __future__ import annotations

import pathlib

DOC = pathlib.Path(__file__).resolve().parents[4] / "docs" / "plan" / "WBS-STATUS.md"

OLD_T75 = "| 75 | **UX-05** | 美术 S2 数据与报告视觉：表格规范 12 处 + 图表封装（y 轴/tooltip/空图）+ 品牌轻量版 + 空态插图 + 打印版式 | B5 | 未开始 | 待补 | 归 v0.5（与 RPT 同期）。前置 `UX-04` |"
NEW_T75 = (
    "| 75 | **UX-05** | 美术 S2 数据与报告视觉：表格规范 19 处 + 图表封装（y 轴/tooltip/空图）+ 品牌轻量版 + 空态插图 + 打印版式 | B5 | 进行中 | 待补 | "
    "归 v0.5（与 RPT 同期）。前置 `UX-04`。**2026-11-05 第一批已落**：表格规范壳（19 处数据网格迁壳、横滚宽度改按列宽推导、骨架行、空态统一）+ 表格主题接管（暗色表头发白、表头不弱化两处静默缺陷）+ `tools/check_tables.py` 守门；"
    "余下三块：图表薄封装 / 品牌（标记+字标+favicon+登录页品牌区+版本号入「关于」）/ 3 种空态插画。证据 `docs/evidence/v0.5/ux05-20261105/` |"
)

OLD_ROW_DOING = "| **进行中** | 0 | **2026-11-05 清零**：`UX-04b` 七批全部落库（12 个业务视图组件化完成），已转 `待验` | 剩余 3 个 `未开始` 包：`EXEC-01`（冻结至 F2 现场，已过）、`UX-05`、`UX-06` |"
NEW_ROW_DOING = "| **进行中** | 1 | **2026-11-05 `UX-05` 第一批落库**（表格规范壳 + 表格主题接管），四块里做完一块，余三块续做 | 剩余 2 个 `未开始` 包：`EXEC-01`（冻结至 F2 现场，已过）、`UX-06` |"


def main() -> int:
    text = DOC.read_text(encoding="utf-8")
    if OLD_T75 not in text:
        print("找不到 #75 原行（可能已改过），请人工核对", flush=True)
        return 1
    text = text.replace(OLD_T75, NEW_T75)
    if OLD_ROW_DOING not in text:
        print("找不到「进行中」汇总行，请人工核对", flush=True)
        return 1
    text = text.replace(OLD_ROW_DOING, NEW_ROW_DOING)
    DOC.write_text(text, encoding="utf-8")
    print("已更新 #75 与「进行中」汇总行")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
