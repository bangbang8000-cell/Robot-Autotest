#!/usr/bin/env python3
"""UX-04 强调色六预设的色值派生（唯一真源，不要手改 theme.css 变体块）。

为什么要脚本生成
----------------
六预设 × 亮暗 = 12 套 accent 三态 / soft / selected / focus，全靠手挑 hex 有两个必然后果：
 ① 某一套的 `accent-contrast / accent` 或 `accent / accent-soft` 掉到 4.5 以下，
    页面看着正常但读不清，`check_contrast.py` 要等到跑守门才发现；
 ② 下次调色只改一处，其余 11 处漂移，token 不再单向派生。

这里固定派生规则：
  - soft        = 亮色 accent×10% + 白；暗色 accent×16% + --gray-900
  - hover/active= 亮色压深 12%/24%；暗色提亮 12%/24%
  - contrast    = 亮色白字；暗色近黑字（#06131f）
  - selected / focus-ring = soft / accent
每个色相给一组候选主色，脚本挑**第一组同时满足三项 WCAG 判据**的值，并打印实测比值。
蓝（blue）是 UX-01b 时代人工微调过的基准，保留原值不重新生成，见 theme.css §4 注释。

用法
----
    python3 tools/gen_accent_tokens.py                 # 打印全部候选比值 + 生成结果
    python3 tools/gen_accent_tokens.py --check        # 只校验，不输出 CSS（CI 可接）
    python3 tools/gen_accent_tokens.py --write BLOCKS # 把生成块写入指定文件

退出码：0 = 六色 × 两主题全部达标；1 = 有候选全不达标（此时禁止改 theme.css）。
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from check_contrast import contrast  # noqa: E402

# 面板底色必须与 theme.css 的 semantic 层一致（亮 #fff / 暗 --gray-750）
PANEL = {"light": "#ffffff", "dark": "#1a2634"}
DARK_BASE = "#0f1720"  # --gray-900
LIGHT_TEXT = "#ffffff"
DARK_TEXT = "#06131f"

# 候选按「先试更稳的深色/亮色」排序；每项都是可读的 hex，不引入第二套色板命名
CANDIDATES: dict[str, dict[str, list[str]]] = {
    "cyan": {
        "light": ["#0e7490", "#0b6e88", "#076678", "#155e75", "#075985"],
        "dark": ["#22d3ee", "#67e8f9", "#06b6d4", "#a5f3fc", "#00bcd4"],
    },
    "indigo": {
        "light": ["#4338ca", "#3730a3", "#3b3bb6", "#4f46e5", "#312e81"],
        "dark": ["#818cf8", "#a5b4fc", "#6366f1", "#c7d2fe", "#8ea2ff"],
    },
    "violet": {
        "light": ["#6d28d9", "#5b21b6", "#5a2ca8", "#7c3aed", "#4c1d95"],
        "dark": ["#a78bfa", "#c4b5fd", "#8b5cf6", "#ddd6fe", "#b794ff"],
    },
    "teal": {
        "light": ["#0f766e", "#115e59", "#0b5f58", "#0d6e63", "#0d5250"],
        "dark": ["#2dd4bf", "#5eead4", "#14b8a6", "#99f6e4", "#3ee0c8"],
    },
    "fuchsia": {
        "light": ["#a21caf", "#86198f", "#8d1a9c", "#9d174d", "#701a75"],
        "dark": ["#e879f9", "#f0abfc", "#d946ef", "#f5d0fe", "#ef7bff"],
    },
}


def to_rgb(value: str) -> tuple[int, int, int]:
    return tuple(int(value[i : i + 2], 16) for i in (1, 3, 5))  # type: ignore[return-value]


def blend(a: str, b: str, ratio: float) -> str:
    """ratio 是 a 的占比；返回 #rrggbb。"""
    ra, rb = to_rgb(a), to_rgb(b)
    return "#" + "".join(f"{round(x * ratio + y * (1 - ratio)):02x}" for x, y in zip(ra, rb))


def derive(accent: str, theme: str) -> dict[str, str]:
    if theme == "light":
        soft = blend(accent, "#ffffff", 0.10)
        hover = blend(accent, "#000000", 0.88)
        active = blend(accent, "#000000", 0.76)
        on_accent = LIGHT_TEXT
    else:
        soft = blend(accent, DARK_BASE, 0.16)
        hover = blend(accent, "#ffffff", 0.88)
        active = blend(accent, "#ffffff", 0.76)
        on_accent = DARK_TEXT
    return {
        "accent": accent,
        "accent-hover": hover,
        "accent-active": active,
        "accent-soft": soft,
        "accent-contrast": on_accent,
        "surface-selected": soft,
        "focus-ring": accent,
    }


def judge(values: dict[str, str], theme: str) -> dict[str, float | None]:
    return {
        "text/accent": contrast(values["accent-contrast"], values["accent"]),
        "accent/panel": contrast(values["accent"], PANEL[theme]),
        "accent/soft": contrast(values["accent"], values["accent-soft"]),
    }


def pick(name: str, theme: str) -> tuple[dict[str, str] | None, list[str]]:
    log: list[str] = []
    for candidate in CANDIDATES[name][theme]:
        values = derive(candidate, theme)
        ratios = judge(values, theme)
        ok = all(r is not None and r >= 4.5 for r in ratios.values())
        log.append(
            f"  {name:8s} {theme:5s} {candidate}  "
            + "  ".join(f"{k}={v:.2f}" for k, v in ratios.items() if v is not None)
            + ("  PASS" if ok else "  FAIL")
        )
        if ok:
            return values, log
    return None, log


def render(name: str, theme: str, values: dict[str, str]) -> str:
    lines = [f":root[data-accent='{name}'][data-theme='{theme}'] {{"]
    for key, value in values.items():
        lines.append(f"  --{key}: {value};")
    lines.append("}")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="派生并校验六预设强调色 token")
    parser.add_argument("--check", action="store_true", help="只校验，不打印 CSS 块")
    parser.add_argument("--write", type=Path, help="把生成的 CSS 块写入文件")
    args = parser.parse_args()

    blocks: list[str] = []
    failures = 0
    print("=== 候选主色对比度实测（阈值 ≥ 4.5:1） ===")
    for name in CANDIDATES:
        for theme in ("light", "dark"):
            values, log = pick(name, theme)
            print("\n".join(log))
            if values is None:
                print(f"[FAIL] {name}/{theme} 所有候选都不达标", file=sys.stderr)
                failures += 1
                continue
            if not args.check:
                blocks.append(render(name, theme, values))

    print(f"\n=== 结果：{len(CANDIDATES) * 2 - failures}/{len(CANDIDATES) * 2} 组达标 ===")
    if failures:
        return 1

    css = "\n\n".join(blocks) + "\n"
    if args.write:
        args.write.parent.mkdir(parents=True, exist_ok=True)
        args.write.write_text(css, encoding="utf-8")
        print(f"[OK] 已写入 {args.write}（{len(blocks)} 个变体块）")
    elif not args.check:
        print("\n" + css)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
