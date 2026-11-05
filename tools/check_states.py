#!/usr/bin/env python3
"""交互态守门（UX-01b / FR-v0.2-55）。

为什么需要这个工具
------------------
改造前全站有 6 类可点/可聚焦元素，其中多数**只有默认态**：鼠标移上去毫无反馈，
键盘 Tab 走一遍看不出焦点在哪。这类问题不会被任何单测发现——DOM 和事件都对，
只是少了一段样式。今天的测试也测不出来，所以只能靠静态守门：

对每类元素要求三件事：
  1. 存在基础规则；
  2. 有 `:hover` 反馈（表格行给 `:hover`，表单类给 `:hover` 描边）；
  3. 有可见焦点：自身 `:focus-visible`，或全局 `*:focus-visible` 兜底；
  4. 基础规则里声明了 `transition`（状态变化不能硬跳）。

用法
----
    python3 tools/check_states.py
    python3 tools/check_states.py --self-test   # 删一条 hover 规则，必须报 FAIL

退出码：0 = 通过；1 = 缺项；2 = 样式文件读不到（扫描失败，别当通过）。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STYLE_DIR = ROOT / "frontend" / "src" / "styles"

BLOCK_RE = re.compile(r"([^{}]+)\{([^{}]*)\}", re.S)
COMMENT_RE = re.compile(r"/\*.*?\*/", re.S)

# 需要交互态的元素：基础选择器 → 允许的焦点兜底写法
#
# `.tab`（原生页签按钮）已于 UX-04b 期间退场：资产页是全站唯一使用处，换成 `NTabs` 后
# 这两条原生规则就没有消费者了。页签的三态改由库主题承担（composables/naiveTheme.ts
# 派生 Tab 的 hoverColor / barColor / textColor），不归本守门管辖 —— 本守门只扫自建 CSS。
REQUIRED: list[tuple[str, tuple[str, ...]]] = [
    (".btn", (".btn:focus-visible", "*:focus-visible")),
    (".btn-ghost", (".btn-ghost:focus-visible", "*:focus-visible")),
    (".link-btn", (".link-btn:focus-visible", "*:focus-visible")),
    (".input", (".input:focus-visible", "*:focus-visible")),
    ("select", ("select:focus-visible", "*:focus-visible")),
    (".app-nav-item", (".app-nav-item:focus-visible", "*:focus-visible")),
    (".table tbody tr", (".table tbody tr:focus-within", ".table tbody tr:focus-visible")),
]

MIN_RULES = 120


def parse_rules(text: str) -> dict[str, list[str]]:
    """选择器 → 规则体列表（同名选择器可能出现多次，全部保留，不能只留第一条）。"""
    rules: dict[str, list[str]] = {}
    for selector, body in BLOCK_RE.findall(COMMENT_RE.sub("", text)):
        for part in selector.split(","):
            name = " ".join(part.split())
            if name:
                rules.setdefault(name, []).append(body)
    return rules


def compile_css() -> str:
    files = sorted(STYLE_DIR.glob("*.css"))
    if not files:
        raise FileNotFoundError(f"{STYLE_DIR} 下没有 css")
    return "\n".join(p.read_text(encoding="utf-8") for p in files)


def audit(text: str) -> list[str]:
    rules = parse_rules(text)
    problems: list[str] = []

    if len(rules) < MIN_RULES:
        problems.append(f"只解析出 {len(rules)} 条规则（下限 {MIN_RULES}），疑似解析失败")
        return problems

    for selector, focus_alternatives in REQUIRED:
        if selector not in rules:
            problems.append(f"{selector} 缺基础规则")

        # 实际写法常带附加条件（`.btn:hover:not(:disabled)`），所以按前缀匹配，
        # 只判"选择器全等"会把明明有 hover 的规则判成缺失（假红）。
        if not any(name.startswith(f"{selector}:hover") for name in rules):
            problems.append(f"{selector} 缺 :hover 反馈")

        if not any(name.startswith(alt) for alt in focus_alternatives for name in rules):
            problems.append(f"{selector} 缺可见焦点（期望其一：{' / '.join(focus_alternatives)}）")

        related = [name for name in rules if name == selector or name.startswith(f"{selector}:")]
        if not any("transition" in body for name in related for body in rules[name]):
            problems.append(f"{selector} 未声明 transition（状态切换会硬跳）")

    return problems


def self_test() -> int:
    text = compile_css()
    if not audit(text):
        print("[SELFTEST] 通过：当前样式无缺项", flush=True)
    else:
        print("[FAIL] 自检基线不干净，先修样式再跑自检", file=sys.stderr)
        return 1

    # 破坏性自检：删掉 .btn 的 hover 规则，必须被拦
    broken = re.sub(r"\.btn:hover\s*:\s*not\(:disabled\)\s*\{[^}]*\}", "", text)
    if broken == text:
        print("[FAIL] 自检：没能从样式里删掉 .btn:hover，自检用例失效", file=sys.stderr)
        return 1
    problems = audit(broken)
    if any(".btn 缺 :hover" in item for item in problems):
        print("[SELFTEST] 通过：删掉 .btn:hover 后脚本按预期报 FAIL")
        return 0

    print(f"[FAIL] 自检：删掉 .btn:hover 后未被识别（实际 {problems}）", file=sys.stderr)
    return 1


def main() -> int:
    parser = argparse.ArgumentParser(description="交互态守门")
    parser.add_argument("--self-test", action="store_true", help="破坏性自检")
    args = parser.parse_args()

    try:
        text = compile_css()
    except FileNotFoundError as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2

    if args.self_test:
        return self_test()

    problems = audit(text)
    if problems:
        for item in problems:
            print(f"[FAIL] {item}")
        print(f"\n[FAIL] 共 {len(problems)} 项", file=sys.stderr)
        return 1

    print(f"[PASS] {len(REQUIRED)} 类元素均有默认 / 悬停 / 焦点三态与 transition")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
