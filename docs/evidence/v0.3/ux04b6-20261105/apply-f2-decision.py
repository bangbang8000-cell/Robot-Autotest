"""把 F2 裁决（2026-11-05，大师授权代改）落到 8 个单包行的状态列。

背景：裁决已在 WBS 的汇总行、分组表、修订记录里写了「8 个待现场 → 已完成」，
但 8 个单包行的状态格没跟着改，文档自相矛盾（汇总说 0 待现场，行里还有 8 个）。
本脚本只改状态格，不动任何正文；每行必须**恰好**有一个 `| 待现场 |` 才动，
否则直接报错退出（避免误改到正文里提到"待现场"的描述）。
"""

from __future__ import annotations

import pathlib
import sys

DOC = pathlib.Path("docs/plan/WBS-STATUS.md")
TARGETS = {
    "EDT-08",
    "EXE-07",
    "EXE-08",
    "PLN-03",
    "PLN-04",
    "HRD-03",
    "HRD-04",
    "DOC-03",
}
OLD = "| 待现场 |"
NEW = "| 已完成 |"


def main() -> int:
    lines = DOC.read_text(encoding="utf-8").splitlines(keepends=True)
    changed: list[str] = []
    errors: list[str] = []

    for index, line in enumerate(lines):
        if not line.startswith("| "):
            continue
        cells = line.split("|")
        if len(cells) < 4:
            continue
        ident = cells[2].strip()
        if ident not in TARGETS:
            continue
        hits = line.count(OLD)
        if hits != 1:
            errors.append(f"L{index + 1} {ident}: `| 待现场 |` 出现 {hits} 次，拒绝修改")
            continue
        lines[index] = line.replace(OLD, NEW, 1)
        changed.append(ident)

    if errors:
        for err in errors:
            print(f"[FAIL] {err}")
        return 1

    missing = TARGETS - set(changed)
    if missing:
        print(f"[FAIL] 以下包没有找到待现场行：{sorted(missing)}")
        return 1

    DOC.write_text("".join(lines), encoding="utf-8")
    print(f"[OK] 已把 {len(changed)} 个包改为 已完成：{' / '.join(changed)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
