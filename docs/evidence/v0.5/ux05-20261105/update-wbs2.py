#!/usr/bin/env python3
"""把 WBS 的两处汇总数字（分布表 + 「当前进度」行）同步到逐行状态：2 未开始 / 1 进行中。"""
from __future__ import annotations

import pathlib

DOC = pathlib.Path(__file__).resolve().parents[4] / "docs" / "plan" / "WBS-STATUS.md"

OLD_TABLE = "| 82 个任务包 | **3** | **0** | **66** | **0** | **13** |"
NEW_TABLE = "| 82 个任务包 | **2** | **1** | **66** | **0** | **13** |"

OLD_LINE = "**当前进度：13 / 82（约 15.9% 已完成 · 66 待验 · 0 待现场 · 3 未开始 · 0 进行中）**"
NEW_LINE = "**当前进度：13 / 82（约 15.9% 已完成 · 66 待验 · 0 待现场 · 2 未开始 · 1 进行中）**"


def main() -> int:
    text = DOC.read_text(encoding="utf-8")
    for old, new, label in ((OLD_TABLE, NEW_TABLE, "分布表"), (OLD_LINE, NEW_LINE, "进度行")):
        if old not in text:
            print(f"找不到{label}，请人工核对", flush=True)
            return 1
        text = text.replace(old, new)
    DOC.write_text(text, encoding="utf-8")
    print("已同步分布表与进度行")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
