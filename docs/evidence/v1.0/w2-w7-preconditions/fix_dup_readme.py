# -*- coding: utf-8 -*-
"""清掉 release README 里重复追加的 v2.26 段及其残留正文；幂等。"""
import pathlib

p = pathlib.Path("docs/release/README.md")
t = p.read_text(encoding="utf-8")
marker = "*v2.26（2026-11-06 16:50）"
n = t.count(marker)
if n == 0:
    raise SystemExit("FAIL 未找到 v2.26 段")
if n > 1:
    head, first, *_ = t.split(marker)
    block = marker + first
    if not block.rstrip().endswith("*"):
        raise SystemExit("FAIL v2.26 段结构异常，未自动改动")
    t = head + block.rstrip() + "\n"
    print(f"[PASS] 清理重复 v2.26 段：{n} -> 1")
else:
    print("[SKIP] v2.26 段唯一")

# marker 段之后的任何内容都是第二轮追加的残留正文，应整体丢弃
lines = t.splitlines(keepends=True)
idx = next(i for i, l in enumerate(lines) if marker in l)
end = next(i for i in range(idx, len(lines)) if lines[i].rstrip().endswith("*"))
if end + 1 < len(lines):
    t = "".join(lines[:end + 1])
    p.write_text(t, encoding="utf-8")
    print(f"[PASS] 删除段后残留 {len(lines) - end - 1} 行")
else:
    print("[SKIP] 段后无残留")
p.write_text(t, encoding="utf-8")
print("[PASS] 当前出现次数", p.read_text(encoding="utf-8").count(marker))
