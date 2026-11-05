"""给 F2 授权代改的 8 个包追加状态说明。

这 8 行的正文原来写着「待人工签署 / 待现场验证，完成后再由人改 `已完成`」。
按大师 2026-11-05 的 F2 裁决，它们已授权代改为 `已完成`；正文若不跟着说明，
读者会看到「状态=已完成，正文=待签」的自相矛盾。本脚本只在最后一格末尾追加一句
出处说明，不改原文任何判断（原文保留，便于追溯）。
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
MARK = "**2026-11-05 按大师「F2 验收通过」裁决授权代改**"
NOTE = (
    MARK
    + "：原文的待现场口径保留以便追溯；如实际未满足，请直接改回 `待现场` 并注明，"
    "本行状态不代表 AI 自判。"
)


def main() -> int:
    lines = DOC.read_text(encoding="utf-8").splitlines(keepends=True)
    done: list[str] = []
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
        if MARK in line:
            done.append(f"{ident}(已标注)")
            continue
        body = line.rstrip("\n")
        if not body.endswith(" |"):
            errors.append(f"L{index + 1} {ident}: 行尾不是 ' |'，拒绝修改")
            continue
        lines[index] = body[:-2] + " " + NOTE + " |\n"
        done.append(ident)

    if errors:
        for err in errors:
            print(f"[FAIL] {err}")
        return 1

    missing = TARGETS - {d.split("(")[0] for d in done}
    if missing:
        print(f"[FAIL] 未找到：{sorted(missing)}")
        return 1

    DOC.write_text("".join(lines), encoding="utf-8")
    print(f"[OK] 已追加授权说明：{' / '.join(done)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
