#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""本次回写涉及的 Markdown 表格「列数一致性」校验。

坑：单元格里的转义竖线 `\\|` 不能当分隔符，否则会误报；分隔行 |---|---| 不参与比较。
用法：python3 docs/evidence/v1.0/policy-20261106/check_md_tables.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
FILES = [
    "docs/自动化测试平台中长期工作规划.md",
    "docs/release/v2.0-platform/DEV-PLAN.md",
    "docs/release/README.md",
    "docs/process/版本开发流程.md",
    "docs/plan/WBS-STATUS.md",
    "docs/plan/决策纪要-20261106.md",
    "docs/evidence/v1.0/policy-20261106/README.md",
]
SPLIT = re.compile(r"(?<!\\)\|")


def cells(line):
    s = line.strip()
    if not s.startswith("|"):
        return None
    s = s[1:]
    if s.endswith("|"):
        s = s[:-1]
    return len(SPLIT.split(s))


def is_separator(line):
    return bool(re.fullmatch(r"[\s|:\-]+", line.strip())) and "-" in line


total_tables = 0
total_rows = 0
fails = []

for rel in FILES:
    p = ROOT / rel
    if not p.exists():
        fails.append(f"{rel}: 文件不存在")
        continue
    block = []
    start = 0
    for n, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        if line.strip().startswith("|"):
            if not block:
                start = n
            block.append((n, line))
            continue
        if block:
            total_tables += 1
            widths = [(ln, cells(raw)) for ln, raw in block if not is_separator(raw)]
            total_rows += len(widths)
            want = widths[0][1] if widths else None
            for ln, w in widths:
                if w != want:
                    fails.append(f"{rel}:{ln} 列数 {w} != 块首 {want}")
            block = []
    if block:
        total_tables += 1
        widths = [(ln, cells(raw)) for ln, raw in block if not is_separator(raw)]
        total_rows += len(widths)
        want = widths[0][1] if widths else None
        for ln, w in widths:
            if w != want:
                fails.append(f"{rel}:{ln} 列数 {w} != 块首 {want} (起始行 {start})")

print(f"扫描 {len(FILES)} 份文档 / {total_tables} 个表格块 / {total_rows} 个数据行")
if fails:
    for f in fails:
        print("FAIL  " + f)
    sys.exit(1)
print("[PASS] 表格列数一致性：全部表格块内列数一致（转义竖线已按非分隔符处理）")
