#!/usr/bin/env python3
"""W1 新页面的硬编码色值 → 语义 token（守门 `tools/check_colors.py`）。

2026-11-06 第一次八步门禁在 lint 步拦下 4 个文件共 11 处；本脚本按语义映射修：
  ok 绿 → var(--ok-border)，fail 红 → var(--fail-border)，
  面板底色/文字 → var(--panel) / var(--text)（主题变量已随 data-theme 切换，故删掉 dark 覆盖行）。
"""
from __future__ import annotations

import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[4]

REPLACEMENTS: dict[str, list[tuple[str, str]]] = {
    "frontend/src/views/AboutView.vue": [
        ("#2e9e5b", "var(--ok-border)"),
        ("#c94a4a", "var(--fail-border)"),
    ],
    "frontend/src/views/ChangelogView.vue": [
        ("#2e9e5b", "var(--ok-border)"),
        ("#c94a4a", "var(--fail-border)"),
    ],
    "frontend/src/views/SettingsView.vue": [
        ("#2e9e5b", "var(--ok-border)"),
        ("#c94a4a", "var(--fail-border)"),
    ],
    "frontend/src/components/CommandPalette.vue": [
        ("background: var(--surface, #fff); color: var(--text, #1a1a1a);",
         "background: var(--panel); color: var(--text);"),
        (":global([data-theme='dark']) .palette { background: #1e1e22; color: #f2f2f4; }",
         "/* 亮/暗底色由 --panel / --text 随 data-theme 切换，不在这里写死第二套色 */"),
    ],
}

changed = 0
for rel, pairs in REPLACEMENTS.items():
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    original = text
    for old, new in pairs:
        text = text.replace(old, new)
    if text != original:
        path.write_text(text, encoding="utf-8")
        changed += 1
        print(f"PASS  {rel}")
    else:
        print(f"SKIP  {rel}")
print(f"DONE  改动 {changed} 个文件")
