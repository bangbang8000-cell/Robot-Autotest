#!/usr/bin/env python3
"""A2 判据：--collect-only -q 的 nodeid 集合 vs 真实运行 pytest-report.json 的 nodeid 集合，逐条对齐。

用法:
    python3 compare.py raw/collect-only.log raw/pytest-report.json
退出码 0 = 两个集合完全一致；1 = 有差异。
"""
from __future__ import annotations

import json
import sys


def collect_ids(path: str) -> list[str]:
    ids = []
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.strip()
            if "::" not in line:
                continue
            # nodeid 本身可能含空格（参数化 id），不能按空格切；统计行不含 ::
            ids.append(line)
    return ids


def report_ids(path: str) -> list[str]:
    with open(path, encoding="utf-8") as fh:
        payload = json.load(fh)
    return [t.get("nodeid", "") for t in payload.get("tests", [])]


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    collected = collect_ids(sys.argv[1])
    executed = report_ids(sys.argv[2])
    only_collect = sorted(set(collected) - set(executed))
    only_exec = sorted(set(executed) - set(collected))
    print(f"collect-only nodeid 数 : {len(collected)}")
    print(f"json-report nodeid 数  : {len(executed)}")
    print(f"仅 collect 有         : {len(only_collect)}")
    print(f"仅 执行报告有         : {len(only_exec)}")
    for node in only_collect[:20]:
        print("  - collect only:", node)
    for node in only_exec[:20]:
        print("  - report only :", node)
    if not only_collect and not only_exec:
        print("RESULT: IDENTICAL")
        return 0
    print("RESULT: DIFF")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
