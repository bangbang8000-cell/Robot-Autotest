#!/usr/bin/env python3
"""C5 · EXTERNAL 导出件敏感字段扫描器。

用法:
    python3 tools/check_report_sensitive.py <file> [<file> ...]
    python3 tools/check_report_sensitive.py --self-check

命中即退出 1；只打印类别与行号，不打印命中内容本身（避免二次泄漏）。
判据本体见 docs/plan/C5-对外报告字段白名单-20261106.md。
"""
from __future__ import annotations

import pathlib
import re
import sys
import zipfile

RULES: list[tuple[str, re.Pattern[str]]] = [
    ("IP", re.compile(r"\b(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)\b")),
    ("COMMAND", re.compile(r"(?m)(?:^|[^\w])(?:robot|pabot|pytest|iperf3|ssh|mysql|curl|bash|sh)\s+-{1,2}[A-Za-z]")),
    ("CREDENTIAL", re.compile(r"(?i)\b(?:password|passwd|pwd|api[_-]?key|secret|token|authorization)\b\s*[:=]|Bearer\s+[A-Za-z0-9._-]{8,}")),
    ("ABS_PATH", re.compile(r"(?:^|[^\w])(?:/home/|/root/|/mnt/|/var/|[A-Za-z]:)[^\s<]{2,}")),
    ("USER", re.compile(r"(?i)\b(?:user|username|login|account)\s*[:=]\s*[A-Za-z0-9._@-]{2,}")),
    ("RAW_OUTPUT", re.compile(r"Traceback \(most recent call last\)|^\[ OUT \]|^\[ ERR \]")),
]

TEXT_SUFFIXES = {".html", ".htm", ".txt", ".md", ".json", ".xml", ".pdf", ".log"}


def texts_of(path: pathlib.Path) -> list[str]:
    suffix = path.suffix.lower()
    if suffix == ".docx":
        # 只取 <w:t> 正文文本：XML 标签 / 命名空间 / 关系目标不属于对外报告字段，
        # 扫它们会把 `w:document` 误判成 Windows 盘符路径。
        with zipfile.ZipFile(path) as zf:
            xml = zf.read("word/document.xml").decode("utf-8", "replace")
        body = " ".join(re.findall(r"<w:t[^>]*>(.*?)</w:t>", xml, re.S))
        return [body or re.sub(r"<[^>]+>", " ", xml)]
    if suffix == ".xlsx":
        with zipfile.ZipFile(path) as zf:
            names = [n for n in zf.namelist() if n.endswith(".xml")]
            return [zf.read(n).decode("utf-8", "replace") for n in names]
    if suffix in TEXT_SUFFIXES:
        return [path.read_text(encoding="utf-8", errors="replace")]
    raise SystemExit(f"SKIP 不支持的文件类型: {path}")


def scan(path: pathlib.Path) -> list[tuple[str, int]]:
    hits: list[tuple[str, int]] = []
    for blob in texts_of(path):
        for number, line in enumerate(blob.splitlines(), 1):
            for category, pattern in RULES:
                if pattern.search(line):
                    hits.append((category, number))
    return hits


def self_check() -> int:
    good = "结论 PASS 12 条，通过率 92.3%，版本 v2.0，水印 INTERNAL-ONLY。"
    bad = "ssh -o root@10.202.54.106 password=abc123 /home/evergreen/run.sh"
    g = [c for c, _ in RULES if re.search(dict(RULES)[c], good)]
    b = {c for c, _ in RULES if re.search(dict(RULES)[c], bad)}
    if g:
        print("FAIL 自校验：白样本被误判", g)
        return 1
    if not {"IP", "COMMAND", "CREDENTIAL", "ABS_PATH"} <= b:
        print("FAIL 自校验：黑样本漏判", sorted(b))
        return 1
    print("PASS 自校验：白样本 0 命中；黑样本命中 IP/COMMAND/CREDENTIAL/ABS_PATH")
    return 0


def main(argv: list[str]) -> int:
    if not argv or argv[0] == "--self-check":
        return self_check()
    failed = 0
    for raw in argv:
        path = pathlib.Path(raw)
        if not path.exists():
            print(f"FAIL 文件不存在: {raw}")
            failed += 1
            continue
        hits = scan(path)
        if hits:
            print(f"FAIL {raw}: 命中 {len(hits)} 处 -> "
                  + ", ".join(f"{c}@L{n}" for c, n in hits[:12]))
            failed += 1
        else:
            print(f"PASS {raw}: 0 命中")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
