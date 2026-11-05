#!/usr/bin/env python3
"""把 UX-05 第一批的修订记录插进设计计划（v1.10），插在 v1.9 之前（该表倒序）。

单独写成文件而不是 heredoc：修订记录里全是反引号与 `$(...)` 形态的片段，
经 Windows 命令行层时会被先展开一次（本项目踩过：`thColor = composite(...)` 被当命令执行）。
"""
from __future__ import annotations

import pathlib

DOC = pathlib.Path(__file__).resolve().parents[4] / "docs" / "design" / "美术体系-开发与测试计划-20261104.md"

ROW = (
    "| 2026-11-05 | v1.10 | **UX-05 第一批：表格规范壳 + 表格主题接管（前端 109 → 127）**，"
    "新增 §3.2c 与 TC-S2-01~08、固定套路 8~10 条，并把「12 处表格」校准为 **19 处数据网格**。"
    "三条写进文档的判断："
    "① **壳不是「再封一层」，是收掉 19 份会各自走偏的规范** —— `size` / `bordered` / `scroll-x` / `#empty` "
    "原本在 19 处各写一遍，改一次表头样式要动 19 个文件；"
    "② **横滚宽度不该是魔法数** —— 原来 12 处写死了 `scroll-x = 1000`，加一列不加数字那列会被压成一行一个字、"
    "删一列又多出一条无意义横滚；改成按列宽推导（`computeScrollX`），视图只声明列宽；"
    "③ **迁库时静默失效的两条判据被找回** —— 库的 `thColor` 由 `cardColor` 与 `tableHeaderColor` 叠加而来，"
    "而 `tableHeaderColor` 是 light 主题里的亮白常量，暗色下表头被叠成一片浅灰白；`thTextColor` 又接 `textColor1`"
    "（正文色），使 UX-01b「表头灰阶弱化」形同虚设。两者都只在暗色截图上看得见，守门与单测全绿。"
    "连带两个守门修复：`check_icons.py` 的图标属性正则误吃 `:icon=\"变量\"`（加 `(?<!:)` 后顾 + 自检第 4 条）；"
    "新增 `tools/check_tables.py`（19 处数据网格全走壳 / 0 处裸 `NDataTable` / 原生 `table` 三文件白名单）"
    "并配 `neg-tables.py` 注入证据。"
    "**错误态仍不进表格**：各页已有 `ErrorLineView`，壳只报「空」，不再用 `!loading && !error` 吞掉空态"
    "（原写法在 error 时整块区域空白，反而让人以为表格没加载出来）。 |"
)


def main() -> int:
    lines = DOC.read_text(encoding="utf-8").split("\n")
    if any("| v1.10 |" in line for line in lines):
        print("已存在 v1.10，跳过")
        return 0
    idx = next(i for i, line in enumerate(lines) if line.startswith("| 2026-11-05 | v1.9 |"))
    lines.insert(idx, ROW)
    DOC.write_text("\n".join(lines), encoding="utf-8")
    print(f"已在第 {idx + 1} 行插入 v1.10")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
