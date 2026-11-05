"""把真实 pabot 合并产物按套件复制放大到万行级，用于 RPT-01/RPT-02 的解析压力验证。

只放大结构，不改元素形态：套件 id / name / 用例 name 加序号保证唯一。
root 套件尾部的 <doc>/<status>/<meta> 保留（meta 是本次要覆盖的真实格式）。
"""
import re
import sys
from pathlib import Path

src = Path(sys.argv[1])
dst = Path(sys.argv[2])
copies = int(sys.argv[3])

text = src.read_text(encoding="utf-8")
start = text.index('<suite id="s1-s1"')
end_marker = '</suite>\n<suite id="s1-s2"'
end = text.index(end_marker)
block = text[start:end + len('</suite>')]

head = text[:start]
# 根套件自身没有 <doc>：尾部 = 最后一个子套件的 </suite> + 根套件的 meta/status/statistics 等。
tail = text[text.index('\n<meta name=') + 1:]

parts = []
for i in range(1, copies + 1):
    piece = block.replace('id="s1-s1"', f'id="s1-s1-x{i}"')
    piece = piece.replace('name="Suite1"', f'name="Suite1-{i}"')
    piece = re.sub(r'id="s1-s1-(t\d+)"', lambda m: f'id="s1-s1-x{i}-{m.group(1)}"', piece)
    piece = re.sub(r'name="Bench-1-([AB])"', lambda m: f'name="Bench-{i}-{m.group(1)}"', piece)
    parts.append(piece)

out = head + "\n".join(parts) + "\n" + tail
dst.parent.mkdir(parents=True, exist_ok=True)
dst.write_text(out, encoding="utf-8")
print(f"[gen] {dst} lines={out.count(chr(10)) + 1} suites={copies} tests={copies * 2}")
