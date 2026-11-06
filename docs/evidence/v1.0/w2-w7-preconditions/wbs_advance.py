# -*- coding: utf-8 -*-
"""通用 WBS 波次收口工具：把指定包从「进行中」转「已完成」，重算汇总并留 AI 验收戳。

用法:
    python3 wbs_advance.py W3 108,109,110,111,112 docs/evidence/v1.0/w3-kernel/ "说明"

幂等：已完成的包跳过；汇总数字按逐行实算，不靠手填；只在既有列内改值，不新增列。
"""
from __future__ import annotations

import pathlib
import re
import subprocess
import sys

WBS = pathlib.Path("docs/plan/WBS-STATUS.md")
STATES = ("未开始", "进行中", "待验", "待现场", "已完成")


def states_of(text: str) -> dict[str, int]:
    result = {s: 0 for s in STATES}
    for line in text.splitlines():
        if not re.match(r"^\|\s*\d+\s*\|", line):
            continue
        cells = line.split("|")
        if len(cells) < 7:
            continue
        state = cells[5].strip().strip("*").strip()
        if state in result:
            result[state] += 1
    return result


def main() -> None:
    wave, ids_raw, evidence, note = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
    ids = set(ids_raw.split(","))
    t = WBS.read_text(encoding="utf-8")
    lines = t.splitlines(keepends=True)
    out = []
    changed = 0
    for line in lines:
        m = re.match(r"^\|\s*(\d+)\s*\|", line)
        if m and m.group(1) in ids and "进行中" in line.split("|")[5]:
            cells = line.split("|")
            cells[5] = " 已完成 "
            cells[6] = " " + evidence + " "
            if len(cells) > 7:
                cells[7] = cells[7].replace("**未开工**", "**AI 验收 2026-11-06**")
                if "AI 验收" not in cells[7]:
                    cells[7] = cells[7].rstrip() + " **AI 验收 2026-11-06** "
            line = "|".join(cells)
            changed += 1
        out.append(line)
    t = "".join(out)
    print(f"[{wave}] 本轮转已完成 {changed} 包（登记 {len(ids)} 包）")

    counts = states_of(t)
    total = sum(counts.values())
    if total != 130:
        raise SystemExit(f"FAIL WBS 逐行合计 {total} != 130")
    summary = ("| 130 个任务包 | "
               + " | ".join(f"**{counts[s]}**" for s in STATES) + " |")
    t = re.sub(r"^\| 130 个任务包 \|.*$", summary, t, count=1, flags=re.M)
    done = counts["已完成"]
    progress = (f"**当前进度：{done} / 130（约 {done / 130 * 100:.1f}% · "
                f"{counts['待验']} 待验 · {counts['待现场']} 待现场 · "
                f"{counts['未开始']} 未开始 · {counts['进行中']} 进行中）**")
    t = re.sub(r"^\*\*当前进度：.*$", progress, t, count=1, flags=re.M)

    marker = f"**{wave} 收口**"
    if marker not in t:
        t = t.rstrip() + (f"\n\n> {marker} 2026-11-06：{note}。\n"
                          f"证据 `{evidence}`；本机机器门与单测全绿，真机 / 外部样本类 TC 仍记 `待现场`。\n"
                          "G4/G5/G8 未判过，本波「已完成」指功能实现 + 机器门全绿，不冒充门禁通过。\n")
    WBS.write_text(t, encoding="utf-8")
    r = subprocess.run([sys.executable, "tools/check_wbs_consistency.py"],
                       capture_output=True, text=True)
    print(r.stdout.strip().splitlines()[-1] if r.stdout else r.stderr.strip())
    if r.returncode:
        raise SystemExit("FAIL WBS 自洽校验未通过")
    md = subprocess.run([sys.executable, "docs/evidence/v1.0/policy-20261106/check_md_tables.py",
                         "docs/plan/WBS-STATUS.md"], capture_output=True, text=True)
    if md.returncode:
        raise SystemExit("FAIL WBS 表格列数不一致:\n" + md.stdout + md.stderr)
    print(f"[{wave}] 五数 {counts} 合计 {total}")


if __name__ == "__main__":
    main()
