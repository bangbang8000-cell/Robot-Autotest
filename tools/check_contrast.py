#!/usr/bin/env python3
"""对比度守门：确认两套主题的语义 token 组合都够读（UX-01 / FR-v0.2-34）。

为什么需要这个工具
------------------
"亮色能切"和"亮色能用"是两件事。最容易漏的是把暗色那套亮蓝直接用到白底上：
`#3fa7ff` 在 `#ffffff` 上只有 2.2:1，文字会"看得见但读不清"，而页面看起来完全正常。
这种问题截图看不出来、功能测试也过，只有算相对亮度才暴露。

检查的是**语义 token 与它常见底色的组合**，不是全部像素组合——后者会产生大量
"永远不会同时出现"的假告警。组合表按实际用法写：正文坐 panel/bg，状态色坐各自的 soft 底。

阈值（WCAG 2.1 AA）：
  - 正文 / 表格文字 / 按钮文字：≥ 4.5:1
  - 小号辅助文字（muted）：≥ 4.5:1（它是 12~13px，不享受 3:1 的大字放宽）
  - 状态色文字、边框/图标：≥ 3:1

用法
----
    python tools/check_contrast.py
    python tools/check_contrast.py --theme light   # 只看一套

退出码：0 = 全部达标；1 = 有不达标项。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
THEME_CSS = ROOT / "frontend" / "src" / "styles" / "theme.css"

BLOCK_RE = re.compile(r"([^{}]+)\{([^{}]*)\}", re.S)
DECL_RE = re.compile(r"(--[\w-]+)\s*:\s*([^;]+);")
VAR_RE = re.compile(r"^var\(\s*(--[\w-]+)\s*\)$")
ACCENT_RE = re.compile(r"""data-accent\s*=\s*['"]([^'"]+)['"]""")
THEME_RE = re.compile(r"""data-theme\s*=\s*['"](light|dark)['"]""")

# (前景, 背景, 阈值, 说明)
CHECKS: list[tuple[str, str, float, str]] = [
    ("text", "bg", 4.5, "正文 / 页面底"),
    ("text", "panel", 4.5, "正文 / 卡片"),
    ("text", "surface-2", 4.5, "正文 / 次级底（日志面板、端口格）"),
    ("text-muted", "bg", 4.5, "辅助文字 / 页面底"),
    ("text-muted", "panel", 4.5, "辅助文字 / 卡片"),
    ("accent-contrast", "accent", 4.5, "主按钮文字 / 按钮底"),
    ("accent", "panel", 4.5, "链接与强调文字 / 卡片"),
    ("accent", "accent-soft", 4.5, "强调文字 / 强调软底（徽标）"),
    ("ok", "panel", 4.5, "成功文字 / 卡片"),
    ("ok", "ok-soft", 4.5, "成功文字 / 成功软底（徽标、diff 行）"),
    ("fail", "panel", 4.5, "失败文字 / 卡片"),
    ("fail", "fail-soft", 4.5, "失败文字 / 失败软底"),
    ("warn", "panel", 4.5, "告警文字 / 卡片"),
    ("warn", "warn-soft", 4.5, "告警文字 / 告警软底（顶部横条）"),
    ("neutral-soft", "panel", 3.0, "取消态色块 / 卡片（非文字，3:1 即可）"),
    ("chart-1", "panel", 3.0, "图表系列 1 / 卡片"),
    ("chart-2", "panel", 3.0, "图表系列 2 / 卡片"),
    ("chart-3", "panel", 3.0, "图表系列 3 / 卡片"),
    ("chart-4", "panel", 3.0, "图表系列 4 / 卡片"),
    ("border", "panel", 1.2, "边框 / 卡片（只要不是完全同色看不见）"),
]


def parse_theme(path: Path) -> tuple[dict[str, str], dict[tuple[str, str], dict[str, str]]]:
    """返回 (primitives, groups)。

    groups 的键是 **(主题, 强调色)**，值是该组合下生效的完整变量表。

    为什么要分组（D15）：早期版本把所有含 `data-theme` 的块 `update` 进同一份表，
    后出现的 `[data-accent='teal'][data-theme='dark']` 会把默认暗色的 `--accent` 覆盖掉。
    结果是"默认态"根本没被测，但脚本仍打印全 PASS —— 典型的假绿。分组后
    每个 (主题 × 强调色) 各自独立合成，变体块无法污染默认块。
    """
    text = path.read_text(encoding="utf-8")
    # 必须先剥注释：文件头有大段 /* ... */，不剥的话第一个匹配到的选择器会变成
    # "注释 + :root"，既不等于 ":root" 也不含 data-theme，整块 primitives 就丢了，
    # 结果所有 var(--gray-*) 都解析不出来而被静默跳过（看着"全过"，其实一项没测）。
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)

    primitives: dict[str, str] = {}
    defaults: dict[str, dict[str, str]] = {}
    variants: dict[tuple[str, str], dict[str, str]] = {}

    for selector, body in BLOCK_RE.findall(text):
        sel = " ".join(selector.split())
        decls = dict(DECL_RE.findall(body))
        if not decls:
            continue
        theme_match = THEME_RE.search(sel)
        accent_match = ACCENT_RE.search(sel)
        if theme_match:
            theme = theme_match.group(1)
            if accent_match:
                variants.setdefault((theme, accent_match.group(1)), {}).update(decls)
            else:
                defaults.setdefault(theme, {}).update(decls)
        elif sel == ":root":
            primitives.update(decls)

    groups: dict[tuple[str, str], dict[str, str]] = {}
    for theme, base in defaults.items():
        groups[(theme, "default")] = {**primitives, **base}
        for (v_theme, accent), overrides in variants.items():
            if v_theme == theme:
                groups[(theme, accent)] = {**primitives, **base, **overrides}
    # 变体块没有对应主题基础块时也要能单独成立（防止只写了变体没写默认的漏配置）
    for (theme, accent), overrides in variants.items():
        groups.setdefault((theme, accent), {**primitives, **overrides})
    return primitives, groups


def resolve(name: str, variables: dict[str, str], depth: int = 0) -> str:
    """把 var(--x) 解析成最终字面值；解析不出来返回原值（调用方会跳过）。"""
    if depth > 8:
        return name
    raw = variables.get(name)
    if raw is None:
        return name
    value = raw.strip()
    match = VAR_RE.match(value)
    if match:
        return resolve(match.group(1), variables, depth + 1)
    return value


def to_rgb(value: str) -> tuple[int, int, int] | None:
    value = value.strip()
    if not value.startswith("#"):
        return None
    hexpart = value[1:]
    if len(hexpart) == 3:
        hexpart = "".join(c * 2 for c in hexpart)
    if len(hexpart) not in (6, 8):
        return None
    try:
        return int(hexpart[0:2], 16), int(hexpart[2:4], 16), int(hexpart[4:6], 16)
    except ValueError:
        return None


def luminance(rgb: tuple[int, int, int]) -> float:
    def channel(c: int) -> float:
        s = c / 255
        return s / 12.92 if s <= 0.03928 else ((s + 0.055) / 1.055) ** 2.4

    r, g, b = (channel(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(fg: str, bg: str) -> float | None:
    a, b = to_rgb(fg), to_rgb(bg)
    if a is None or b is None:
        return None
    la, lb = luminance(a), luminance(b)
    lighter, darker = max(la, lb), min(la, lb)
    return (lighter + 0.05) / (darker + 0.05)


def main() -> int:
    parser = argparse.ArgumentParser(description="检查两套主题的 token 对比度")
    parser.add_argument("--theme", choices=["dark", "light"], help="只检查一套主题")
    parser.add_argument("--accent", help="只检查某个强调色分组（default / blue / teal ...）")
    args = parser.parse_args()

    if not THEME_CSS.is_file():
        print(f"[FAIL] 找不到 {THEME_CSS}", file=sys.stderr)
        return 2

    primitives, groups = parse_theme(THEME_CSS)

    # 自检 1：解析出 0 项时所有检查都会被"跳过"，脚本会打印 PASS —— 那是最坏的假绿。
    # 自检 2：分组数下限。UX-04 六预设落地后是「2 主题 × (默认 + 6 预设)」= 14 组，
    # 要求 ≥14；如果 theme.css 结构又变了（比如变体块挪进 @media），这里会先炸，
    # 而不是静默少测。计划 §3.2 TC-S1-05 的「96 组」是下限判据，14 组 × 20 项 = 280 项
    # 已远超它，所以这里按实际结构设更严的断言。
    if len(groups) < 14:
        print(f"[FAIL] 只解析出 {len(groups)} 个 (主题 × 强调色) 分组，"
              f"疑似 theme.css 结构变化导致解析失败（而不是真的没有变量）",
              file=sys.stderr)
        return 2

    # 六预设 × 两主题必须逐组存在：只写了 dark 或漏了 teal，分组数仍可能凑够 14。
    required = {"blue", "cyan", "indigo", "violet", "teal", "fuchsia"}
    missing = [
        f"{theme_name}/{name}"
        for theme_name in ("light", "dark")
        for name in sorted(required)
        if (theme_name, name) not in groups
    ]
    if missing:
        print(f"[FAIL] 缺少强调色变体块：{', '.join(missing)}", file=sys.stderr)
        return 2

    failures = 0
    skipped: list[str] = []
    rows = 0

    for (theme_name, accent), variables in sorted(groups.items()):
        if args.theme and theme_name != args.theme:
            continue
        if args.accent and accent != args.accent:
            continue
        # 每组独立自检：组内 token 太少说明这一组是空壳（变体块没继承到基础块）
        if len(variables) < 30:
            print(f"[FAIL] {theme_name}/{accent} 只解析出 {len(variables)} 个 token，"
                  f"疑似分组合成失败", file=sys.stderr)
            return 2

        print(f"\n=== {theme_name} / accent={accent} ===")
        for fg_name, bg_name, threshold, label in CHECKS:
            fg = resolve(f"--{fg_name}", variables)
            bg = resolve(f"--{bg_name}", variables)
            ratio = contrast(fg, bg)
            if ratio is None:
                skipped.append(f"{theme_name}/{accent}: {fg_name} / {bg_name}（非 hex：{fg} / {bg}）")
                continue
            rows += 1
            ok = ratio >= threshold
            mark = "PASS" if ok else "FAIL"
            print(f"  [{mark}] {ratio:5.2f} : 1  (需 ≥{threshold})  {label}  {fg} on {bg}")
            if not ok:
                failures += 1

    # 14 组 × 20 项 = 280 项；计划判据只要求 96 项，这里按实际结构设更严的下限。
    if rows < 240:
        print(f"[FAIL] 实际只判定了 {rows} 项（下限 240），说明分组或值解析大面积失败",
              file=sys.stderr)
        return 2

    if skipped:
        print("\n跳过（值不是 hex，脚本不判定）：")
        for item in skipped:
            print(f"  - {item}")

    if failures:
        print(f"\n[FAIL] {failures} 项对比度不达标。改 theme.css 的两套语义值后重跑。",
              file=sys.stderr)
        return 1

    checked = [
        key for key in groups
        if (not args.theme or key[0] == args.theme)
        and (not args.accent or key[1] == args.accent)
    ]

    print(f"\n[PASS] {len(checked)} 组（主题 × 强调色）共 {rows} 项对比度全部达标")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
