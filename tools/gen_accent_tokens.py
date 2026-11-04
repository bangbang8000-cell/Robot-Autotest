#!/usr/bin/env python3
"""UX-04 强调色六预设的色值派生（唯一真源，不要手改 theme.css 变体块）。

六预设（2026-11-05 定名，取代首版的 blue/cyan/indigo/violet/teal/fuchsia）：
    新年红 crimson ｜ 科技蓝 blue ｜ 土豪金 gold ｜ 活力橙 orange ｜ 魅力紫 violet ｜ 翡翠绿 jade

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

「土豪金」是两个主题里唯一必须分头取极值的色相——金色在白底上天然对比不足：
亮色只能在 #8a6300 这一档深金上过 4.5:1（观感偏古铜金），暗色才用 #f5c542 的亮金。
外观面板的色板取的是**暗色主色**，所以面板里看到的是亮眼金黄，与实际选中后的
亮色主题观感不同 —— 这是 WCAG 与「土豪金」不可兼得处的取舍，不要为了好看把亮色改浅。

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

# 候选按「先试更稳的深色/亮色」排序；每项都是可读的 hex，不引入第二套色板命名。
# 第一顺位就是实际选中的值（三项判据全过的第一个），后面是深一档 / 亮一档的备用，
# 所以调色时改第一项即可，候选尾部只在第一项被别处约束否决时才起作用。
CANDIDATES: dict[str, dict[str, list[str]]] = {
    # 新年红：亮色 #c81e1e 是正红压深一档，仍是"红"而不是砖红
    "crimson": {
        "light": ["#c81e1e", "#c62828", "#b91c1c", "#bd2130", "#a81c1c", "#d02020"],
        "dark": ["#ff6b6b", "#f87171", "#ff7a7a", "#ef5350", "#ff8f8f"],
    },
    # 土豪金：亮色受 4.5:1 约束只能用暗金，暗色才能用亮金（见模块 docstring）
    "gold": {
        "light": ["#8a6300", "#7c5a00", "#6f5200", "#806000", "#96700a", "#a07400"],
        "dark": ["#f5c542", "#ffd24a", "#f0b429", "#e8b73a", "#ffdf6b"],
    },
    # 活力橙：亮色 #ad4a05 已压到接近棕橙，再浅 soft 对比度就掉到 4.4
    "orange": {
        "light": ["#ad4a05", "#a34a08", "#b4530a", "#c2410c", "#9a3412", "#b85410"],
        "dark": ["#fb923c", "#ff8b3d", "#f97316", "#ffa05c"],
    },
    # 魅力紫：沿用首版 violet 的取值，视觉不变，只是名字从色相名改成语义名
    "violet": {
        "light": ["#6d28d9", "#5b21b6", "#7c3aed", "#7028c8"],
        "dark": ["#a78bfa", "#c4b5fd", "#8b5cf6", "#b794ff"],
    },
    # 翡翠绿：第六个，补「缺的色相」。它是唯一与状态色 --ok 共享色相的预设，
    # 靠用法纪律隔离（强调色只进按钮/链接/焦点环/选中行，状态色只进标签/图标/左边框），
    # 见 components/StatusTag.vue 顶部注释。
    "jade": {
        "light": ["#0d7a4f", "#047857", "#0b7a4b", "#0a6e4a", "#0e7c52"],
        "dark": ["#34d399", "#4ade80", "#3ddc97", "#5ee5a8", "#3ce0a0"],
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
