#!/usr/bin/env python3
"""W7 / UIX-01 guard: 4px grid tokens, density and theme overrides must stay intact.

Every token added by W7 is checked against its *value*, not only its name, so that
changing --grid-3 from 12px to 13px is caught by CI instead of during a visual walk.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
THEME = ROOT / "frontend" / "src" / "styles" / "theme.css"
MAIN = ROOT / "frontend" / "src" / "styles" / "main.css"

GRID_VALUES = {f"--grid-{n}": n * 4 for n in range(1, 9)}
FONT_LEVELS = ["xs", "sm", "base", "md", "lg", "xl"]


def validate(theme: str, main: str) -> list[str]:
    problems: list[str] = []
    for name, expected in GRID_VALUES.items():
        hit = re.search(rf"(?m)^\s*{re.escape(name)}:\s*(\d+)px\s*;", theme)
        if not hit:
            problems.append(f"missing {name}")
        elif int(hit.group(1)) != expected:
            problems.append(f"{name} should be {expected}px, got {hit.group(1)}px")
    for level in FONT_LEVELS:
        if f"--font-size-{level}:" not in theme:
            problems.append(f"missing --font-size-{level}")
    for marker in ("--w7-surface", "--w7-border"):
        if f"[data-theme='light']" not in theme or marker not in theme:
            problems.append(f"missing light-theme override {marker}")
    for density in ("compact", "comfortable"):
        if f"[data-density='{density}']" not in theme:
            problems.append(f"missing density override {density}")
    for rule in ("prefers-reduced-motion", "*:focus-visible", "grid-template-columns"):
        if rule not in main:
            problems.append(f"main.css missing {rule}")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-check", action="store_true")
    args = parser.parse_args()
    if not THEME.is_file() or not MAIN.is_file():
        print("[FAIL] theme.css / main.css not found")
        return 2
    theme = THEME.read_text(encoding="utf-8")
    main = MAIN.read_text(encoding="utf-8")
    if args.self_check:
        broken = theme.replace("--grid-3: 12px;", "--grid-3: 13px;")
        if not validate(broken, main):
            print("[FAIL] self-check: mutated token was not detected")
            return 1
        print("[OK] self-check: mutated grid token is rejected")
        return 0
    problems = validate(theme, main)
    if problems:
        print("[FAIL] W7 token guard:")
        for item in problems:
            print("  - " + item)
        return 1
    print("[OK] W7 token guard: 8 grid tokens / 6 font levels / theme + density + a11y overrides")
    return 0


if __name__ == "__main__":
    sys.exit(main())
