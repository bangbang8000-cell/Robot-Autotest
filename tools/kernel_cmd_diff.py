#!/usr/bin/env python3
"""kernel_cmd_diff：主控参照命令 vs 节点实际命令，同参数 diff 必须为空（NODX-03）。

输入两份 JSON（由主控/节点分别落盘）：
    {"runId": "RUN-...", "argv": ["/path/pabot", "--outputdir", "/path/shard-0", ...]}

归一化规则（消掉环境差异、只留结构差异）：
  - 绝对路径统一替换为其 basename 前的目录树为 <path>（保留文件名）；
  - --outputdir 的值保留 shard-N 相对结构；
  - 空串剔除。

用法：
    python3 tools/kernel_cmd_diff.py master.json agent.json
退出码 0 = diff 为空；1 = 有差异（打印第一处差异）。
"""
from __future__ import annotations

import json
import pathlib
import re
import sys


def normalize(argv: list[str]) -> list[str]:
    out = []
    for item in argv:
        if not item:
            continue
        # 绝对路径（含盘符与 / 开头）→ 保留最后一段文件名，前缀归一为 <path>
        if item.startswith("/") or re.match(r"^[A-Za-z]:[\\/]", item):
            out.append("<path>/" + item.replace("\\", "/").rsplit("/", 1)[-1])
        else:
            out.append(item)
    return out


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    master = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
    agent = json.loads(pathlib.Path(sys.argv[2]).read_text(encoding="utf-8"))
    left, right = normalize(master["argv"]), normalize(agent["argv"])

    if left == right:
        print(f"[PASS] 命令 diff 为空（{len(left)} 个参数，归一化后逐项一致）")
        return 0

    print(f"[FAIL] 命令有差异（master {len(left)} 项 / agent {len(right)} 项）")
    for i, (a, b) in enumerate(zip(left, right)):
        if a != b:
            print(f"  第 {i} 个参数: master={a!r} agent={b!r}")
            break
    if len(left) != len(right):
        longer = left if len(left) > len(right) else right
        print(f"  多出的参数: {longer[min(len(left), len(right)):]}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
