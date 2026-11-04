#!/usr/bin/env python3
"""色值守门：确认组件与页面里没有再写死颜色（UX-01 / FR-v0.2-32）。

为什么需要这个工具
------------------
亮/暗双主题最容易的失败方式不是"忘了做切换"，而是**切换做了但有几个地方不跟着变**。
改造前 `main.css` 有 35 处 hex、`views/` 与 `components/` 有 68 处；这些写死的值
在亮色下就是深色块、黑边框、看不清的告警条。它不报错、功能也正常，但一眼就是没做完。

靠人肉 review 抓不住——新增一个卡片时顺手写 `border: 1px solid #22303f` 太自然了。
所以把它变成可执行检查：**只有 `styles/theme.css` 允许定义色值**。

豁免机制
--------
打印页（`ReportPrintView.vue`）与 `main.css` 里的 `body.print-mode` 必须强制白底黑字：
它们由无头浏览器直接打开导出 PDF，不能继承暗色主题。用成对注释显式标注：

    /* color-check: ignore-start 原因 */
    ...
    /* color-check: ignore-end */

显式标注而不是按文件名硬编码豁免，是为了让"为什么这里可以写死"留在代码里。

用法
----
    python tools/check_colors.py            # 扫 frontend/src
    python tools/check_colors.py --verbose  # 连豁免的也打印

退出码：0 = 干净；1 = 发现未豁免的硬编码色值。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "frontend" / "src"

# 唯一允许定义色值的文件（token 定义处）
ALLOWED_FILES = {SRC / "styles" / "theme.css"}

SKIP_DIRS = {"node_modules", "dist"}

# #rgb / #rrggbb / #rrggbbaa；不匹配 #后面跟非 hex 的情况（如 URL 锚点在这里不会出现）
HEX_RE = re.compile(r"#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})\b")

# 允许用 rgb()/hsl() 的地方也一并禁掉？不——只禁 hex 是我们能可靠判定的；
# rgb() 出现次数少，且 theme.css 里 legit 用到。这里只守 hex（占 99%）。

IGNORE_START = re.compile(r"color-check:\s*ignore-start")
IGNORE_END = re.compile(r"color-check:\s*ignore-end")


def scan_file(path: Path, verbose: bool) -> list[tuple[int, str, str]]:
    """返回 [(行号, 色值, 该行内容)]，已剔除豁免区间。"""
    hits: list[tuple[int, str, str]] = []
    ignoring = False
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return hits

    for lineno, line in enumerate(text.splitlines(), 1):
        if IGNORE_START.search(line):
            ignoring = True
            if verbose:
                print(f"  [豁免开始] {path.relative_to(ROOT)}:{lineno}")
            continue
        if IGNORE_END.search(line):
            ignoring = False
            continue
        if ignoring:
            continue
        for match in HEX_RE.finditer(line):
            hits.append((lineno, match.group(0), line.strip()))
    return hits


def main() -> int:
    parser = argparse.ArgumentParser(description="检查前端是否还有硬编码 hex 色值")
    parser.add_argument("--verbose", action="store_true", help="打印豁免区间与文件清单")
    args = parser.parse_args()

    if not SRC.is_dir():
        print(f"[FAIL] 找不到前端源码目录：{SRC}", file=sys.stderr)
        return 2

    targets = [
        p
        for p in SRC.rglob("*")
        if p.is_file()
        and p.suffix in {".vue", ".css", ".scss", ".ts"}
        and not (set(p.relative_to(SRC).parts) & SKIP_DIRS)
        and p not in ALLOWED_FILES
    ]

    total = 0
    bad_files = 0
    for path in sorted(targets):
        hits = scan_file(path, args.verbose)
        if not hits:
            continue
        bad_files += 1
        total += len(hits)
        print(f"\n{path.relative_to(ROOT)}  （{len(hits)} 处）")
        for lineno, value, line in hits:
            print(f"  {lineno:>4}: {value:<9} {line[:96]}")

    if args.verbose:
        print(f"\n扫描 {len(targets)} 个文件，豁免 {len(ALLOWED_FILES)} 个（theme.css）")

    if total:
        print(
            f"\n[FAIL] {bad_files} 个文件共 {total} 处硬编码色值。\n"
            f"       改用语义 token（见 frontend/src/styles/theme.css），"
            f"确需写死请加 color-check: ignore-start/end 并注明原因。",
            file=sys.stderr,
        )
        return 1

    print(f"[PASS] {len(targets)} 个文件无硬编码色值")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
