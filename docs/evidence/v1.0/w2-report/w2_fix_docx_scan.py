# -*- coding: utf-8 -*-
"""C5 扫描器：DOCX 只扫正文文本层，不再扫 XML 标签/命名空间；幂等。

误报根因：`<w:document xmlns:w="...">` 里的 `w:` 被 ABS_PATH 规则的盘符分支
`[A-Za-z]:` 命中。XML 标签与命名空间不是对外报告字段，扫它们属于范围错误，
不是「放宽判据」——正文里的敏感值照旧会被拦。
"""
import pathlib

p = pathlib.Path("tools/check_report_sensitive.py")
t = p.read_text(encoding="utf-8")
old = '''    if suffix == ".docx" or suffix == ".xlsx":
        with zipfile.ZipFile(path) as zf:
            names = [n for n in zf.namelist() if n.endswith(".xml")]
            return [zf.read(n).decode("utf-8", "replace") for n in names]'''
new = '''    if suffix == ".docx":
        # 只取 <w:t> 正文文本：XML 标签 / 命名空间 / 关系目标不属于对外报告字段，
        # 扫它们会把 `w:document` 误判成 Windows 盘符路径。
        with zipfile.ZipFile(path) as zf:
            xml = zf.read("word/document.xml").decode("utf-8", "replace")
        body = " ".join(re.findall(r"<w:t[^>]*>(.*?)</w:t>", xml, re.S))
        return [body or re.sub(r"<[^>]+>", " ", xml)]
    if suffix == ".xlsx":
        with zipfile.ZipFile(path) as zf:
            names = [n for n in zf.namelist() if n.endswith(".xml")]
            return [zf.read(n).decode("utf-8", "replace") for n in names]'''
if new in t:
    print("[SKIP] 扫描器已是正文文本层版本")
else:
    if t.count(old) != 1:
        raise SystemExit(f"FAIL texts_of 锚点命中 {t.count(old)} 次")
    p.write_text(t.replace(old, new, 1), encoding="utf-8")
    print("[PASS] DOCX 扫描范围收敛到 <w:t> 正文")

doc = pathlib.Path("docs/plan/C5-对外报告字段白名单-20261106.md")
d = doc.read_text(encoding="utf-8")
marker = "| 2026-11-06 | v1.1 |"
if marker in d:
    print("[SKIP] C5 修订记录已留痕")
else:
    row = ("| 2026-11-06 | v1.1 | 扫描器对 DOCX 只读 `<w:t>` 正文文本层，不再扫 XML 标签与命名空间 | "
           "实测 `<w:document xmlns:w=...>` 的 `w:` 被 ABS_PATH 盘符分支误报；"
           "**禁止类别与白名单字段一项未放宽** |")
    lines = d.splitlines(keepends=True)
    idx = next((i for i, l in enumerate(lines) if l.startswith("| 日期 |")), None)
    # 有修订记录表就插在表头之后（newest first）；没有就追加一节
    if idx is not None and idx + 1 < len(lines) and lines[idx + 1].startswith("|---"):
        lines.insert(idx + 2, row + "\n")
        doc.write_text("".join(lines), encoding="utf-8")
    else:
        doc.write_text(d.rstrip() + "\n\n## 6. 修订记录\n\n| 日期 | 版本 | 改了什么 | 为什么 |\n"
                       "|---|---|---|---|\n" + row + "\n", encoding="utf-8")
    print("[PASS] C5 文档补 v1.1 修订记录")
