#!/usr/bin/env python3
"""WBS 进度表自洽守门（2026-11-05 新增）。

为什么需要这个工具
------------------
`docs/plan/WBS-STATUS.md` 是进度**真源**，但它的数字靠手改：加一个包要同时动
「汇总表」「一句话进度」「分组表」「单包行」四处。漏改任何一处，文档就自相矛盾，
而读的人通常只看汇总那一行 —— 于是"看起来 13 已完成"，实际行里只有 5 个。

这不是假想：2026-11-05 的 F2 裁决把 8 个 `待现场` 包授权代改为 `已完成`，
汇总行、分组表、修订记录三处都写了，**唯独 8 个单包行的状态格没动**；
汇总显示「待现场 0 / 已完成 13」，逐行数出来是「待现场 8 / 已完成 5」。
人眼对着几百行表格是发现不了的，所以必须机检。

检查四件事
----------
  1. 单包行的状态值必须是五态之一；
  2. 汇总行的五个数字 = 逐行数出来的真实分布；
  3. 「当前进度：N / M」的 N/M 与逐行结果一致；
  4. 单包行的列数必须是 7（转义竖线 `\\|` 不算分隔符）。

用法
----
    python3 tools/check_wbs_consistency.py
    python3 tools/check_wbs_consistency.py --fix-hint   # 只打印该怎么改，不写文件

退出码：0 = 自洽；1 = 有矛盾；2 = 文档读不到 / 结构识别失败（别当通过）。
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
DOC = ROOT / "docs" / "plan" / "WBS-STATUS.md"
STATES = ["未开始", "进行中", "待验", "待现场", "已完成"]
EXPECTED_COLUMNS = 7  # 序号 / WBS / 任务 / 批次 / 状态 / 证据 / 阻塞
ROW_RE = re.compile(r"^\| (\d+) \| ")
SPLIT_RE = re.compile(r"(?<!\\)\|")


def split_cells(line: str) -> list[str]:
    parts = SPLIT_RE.split(line.strip())
    return [p.strip() for p in parts[1:-1]]


def main() -> int:
    parser = argparse.ArgumentParser(description="WBS 进度表自洽守门")
    parser.add_argument("--fix-hint", action="store_true", help="只打印修改建议")
    args = parser.parse_args()

    if not DOC.exists():
        print(f"[FAIL] 读不到 {DOC}", file=sys.stderr)
        return 2
    text = DOC.read_text(encoding="utf-8")
    lines = text.splitlines()

    problems: list[str] = []
    per_state = {state: 0 for state in STATES}
    total = 0

    for no, line in enumerate(lines, 1):
        if not ROW_RE.match(line):
            continue
        total += 1
        cells = split_cells(line)
        if len(cells) != EXPECTED_COLUMNS:
            problems.append(f"L{no} 单包行列数 {len(cells)} ≠ {EXPECTED_COLUMNS}：{cells[0][:16]}")
            continue
        state = cells[4]
        if state not in per_state:
            problems.append(f"L{no} 状态无法识别：{cells[0]} → {state!r}")
            continue
        per_state[state] += 1

    if total == 0:
        print("[FAIL] 一条单包行都没解析到，结构可能变了", file=sys.stderr)
        return 2

    m = re.search(r"^\| (\d+) 个任务包 \|(.+)\|$", text, re.M)
    if not m:
        problems.append("找不到「N 个任务包」汇总行")
    else:
        declared_total = int(m.group(1))
        nums = [int(x) for x in re.findall(r"\*\*(\d+)\*\*", m.group(2))]
        if len(nums) != len(STATES):
            problems.append(f"汇总行应有 {len(STATES)} 个数字，实际 {len(nums)} 个")
        else:
            for state, num in zip(STATES, nums):
                if num != per_state[state]:
                    problems.append(f"汇总「{state}」= {num}，逐行实际 {per_state[state]}")
            if sum(nums) != declared_total:
                problems.append(f"汇总相加 = {sum(nums)}，声明总数 = {declared_total}")
        if declared_total != total:
            problems.append(f"声明总数 = {declared_total}，逐行实际 {total} 条")

    m = re.search(r"当前进度：(\d+) / (\d+)（[^）]*）", text)
    if not m:
        problems.append("找不到「当前进度」行")
    else:
        done, all_pkgs = int(m.group(1)), int(m.group(2))
        if done != per_state["已完成"]:
            problems.append(f"「当前进度」已完成 = {done}，逐行实际 {per_state['已完成']}")
        if all_pkgs != total:
            problems.append(f"「当前进度」总数 = {all_pkgs}，逐行实际 {total}")

    if problems:
        for item in problems:
            print(f"[FAIL] {item}")
        if args.fix_hint:
            real = " | ".join(f"{s} {per_state[s]}" for s in STATES)
            print(f"       逐行真实分布：{real}（共 {total}）")
        print(f"\n[FAIL] 共 {len(problems)} 处不一致", file=sys.stderr)
        return 1

    print(
        f"[PASS] WBS 自洽：{total} 个包 / "
        + " / ".join(f"{s} {per_state[s]}" for s in STATES)
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
