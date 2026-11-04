#!/usr/bin/env python3
"""UX-04 步骤 7：styleguide 展示的 token 清单 ↔ theme.css 定义对账。

判据原文是「token 全量（从 theme.css 读取）+ 与 theme.css 值一致性脚本比对」。
styleguide 页不另抄一份色值，只读 `getComputedStyle`；变量**名**取自对接层
`SEMANTIC_CSS_VARS`。所以这里要守的是：清单里每个名字都必须在 theme.css 有定义
（否则页面显示空白却看不出错），且清单不能短到漏掉整类 token。

用法
----
    python3 tools/check_styleguide_tokens.py
    python3 tools/check_styleguide_tokens.py --self-test   # 注入悬空变量名必须被拦

退出码：0 = 一致；1 = 有悬空名/清单过短；2 = 扫描本身失败。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
THEME_CSS = ROOT / "frontend" / "src" / "styles" / "theme.css"
ADAPTER = ROOT / "frontend" / "src" / "composables" / "naiveTheme.ts"
STYLEGUIDE = ROOT / "frontend" / "src" / "views" / "StyleguideView.vue"

DEF_RE = re.compile(r"(--[\w-]+)\s*:")
TOKEN_BLOCK_RE = re.compile(r"const TOKEN_NAMES = \{(.*?)\} as const", re.S)
TOKEN_NAME_RE = re.compile(r"'(--[\w-]+)'")
MIN_TOKENS = 18  # 对接层当前 19 个语义名；低于 18 视为漏了一类


def collect() -> tuple[set[str], list[str]]:
    theme_text = THEME_CSS.read_text(encoding="utf-8")
    defined = set(DEF_RE.findall(theme_text))
    adapter_text = ADAPTER.read_text(encoding="utf-8")
    block = TOKEN_BLOCK_RE.search(adapter_text)
    if not block:
        raise RuntimeError("naiveTheme.ts 里找不到 TOKEN_NAMES 块（结构改了？）")
    listed = TOKEN_NAME_RE.findall(block.group(1))
    return defined, listed


def judge(defined: set[str], listed: list[str]) -> list[str]:
    problems: list[str] = []
    if len(listed) < MIN_TOKENS:
        problems.append(f"清单只有 {len(listed)} 个语义名（下限 {MIN_TOKENS}）")
    if len(set(listed)) != len(listed):
        problems.append("清单里有重复变量名")
    dangling = sorted(set(listed) - defined)
    if dangling:
        problems.append("theme.css 未定义：" + ", ".join(dangling))
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description="styleguide token 清单与 theme.css 对账")
    parser.add_argument("--self-test", action="store_true", help="注入悬空变量名，验证守门能拦")
    args = parser.parse_args()

    try:
        defined, listed = collect()
    except (OSError, RuntimeError) as exc:
        print(f"[FAIL] 扫描失败：{exc}", file=sys.stderr)
        return 2

    if len(defined) < 40:
        print(f"[FAIL] theme.css 只解析出 {len(defined)} 个定义，疑似解析失效", file=sys.stderr)
        return 2

    if args.self_test:
        injected = judge(defined, [*listed, "--__styleguide_missing__"])
        if not injected:
            print("[FAIL] 自检：注入悬空变量名却没有被拦", file=sys.stderr)
            return 1
        short = judge(defined, listed[:3])
        if not short:
            print("[FAIL] 自检：清单被截到 3 项却没有被拦", file=sys.stderr)
            return 1
        print("[PASS] 自检：悬空变量名与短清单都被拦截")

    problems = judge(defined, listed)
    if problems:
        for item in problems:
            print(f"[FAIL] {item}", file=sys.stderr)
        return 1

    if not STYLEGUIDE.is_file():
        print(f"[FAIL] 找不到 {STYLEGUIDE}", file=sys.stderr)
        return 2
    sg_text = STYLEGUIDE.read_text(encoding="utf-8")
    if "SEMANTIC_CSS_VARS" not in sg_text or "getComputedStyle" not in sg_text:
        print("[FAIL] styleguide 没有从对接层清单 + 计算样式读值（疑似另抄了一份色值）", file=sys.stderr)
        return 1
    if "font-family:" in sg_text and "var(--font-mono)" not in sg_text:
        print("[FAIL] styleguide 等宽展示没用 --font-mono token", file=sys.stderr)
        return 1

    print(f"[PASS] styleguide 清单 {len(listed)} 个语义名，全部在 theme.css（{len(defined)} 个定义）中有定义")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
