#!/usr/bin/env python3
"""守门：frontend/src/api/ 下 request()/request() 的 URL 不得自带 /api 前缀。

http.ts 会自行拼 BASE_URL，重复前缀会静默产生 /api/api/...（浏览器里 404，
单测与构建都发现不了）。requestBlob 允许带 /api（它会自行剥离，因为下载 URL
可能来自后端响应），故这里排除。
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
API_DIR = ROOT / "frontend" / "src" / "api"
PATTERN = re.compile(r"\brequest(?!Blob)\w*\s*(?:<[^>]*>)?\s*\(\s*[`'\x22]/api/")
ALLOW = {"http.ts"}

hits: list[str] = []
for path in sorted(API_DIR.glob("*.ts")):
    if path.name in ALLOW:
        continue
    for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if PATTERN.search(line):
            hits.append(f"{path.relative_to(ROOT)}:{lineno}: {line.strip()}")

if hits:
    print("[FAIL] request() URL 不得带 /api 前缀（http.ts 会拼 BASE_URL）：")
    for item in hits:
        print("  " + item)
    sys.exit(1)
print(f"[OK] request() URL 前缀检查通过（扫描 {len(list(API_DIR.glob('*.ts')))} 个 API 模块）")
