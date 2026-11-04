#!/usr/bin/env python3
"""token 引用完整性守门（UX-01b / FR-v0.2-54）。

为什么需要这个工具
------------------
`--muted` 这个 token **从来没在 theme.css 里定义过**，但全站有 21 处 `var(--muted)`。
CSS 的规则是：引用不存在的变量 → 该属性在计算值阶段失效 → `color` 退回继承值。
表现是"辅助文字看起来和正文一样重"，页面完全正常、功能测试全过，所以没人发现（D1）。

本脚本做两件事：
  1. **悬空引用**：`var(--x)` 里出现的每个 `--x` 必须在某个 CSS 里被定义过；
  2. **px 白名单**：字面量 `Npx` 只能取 `tools/px-allow.txt` 里已有的值。
     UX-01b 阶段还没到"字号/间距 token 化"（那是 UX-04），这一条的作用是**防止继续摊大**：
     新写一个 37px 会立刻被拦，逼你要么复用已有值，要么显式评审后加白名单。
     白名单不是"合法值清单"，是"当时现状的快照"。

用法
----
    python3 tools/check_tokens.py                # 常规检查
    python3 tools/check_tokens.py --self-test    # 自检：注入假 var / 假 px，必须被拦
    python3 tools/check_tokens.py --write-px-allow   # 重建 px 白名单基线（需人工评审后提交）

退出码：0 = 通过；1 = 有违规；2 = 扫描本身失败（文件读不到 / 计数过低，疑似假绿）。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "frontend" / "src"
PX_ALLOW = ROOT / "tools" / "px-allow.txt"

SCAN_SUFFIXES = {".css", ".vue"}
DEF_RE = re.compile(r"(--[\w-]+)\s*:")
USE_RE = re.compile(r"var\(\s*(--[\w-]+)")
PX_RE = re.compile(r"\b(\d+(?:\.\d+)?)px\b")
CSS_COMMENT_RE = re.compile(r"/\*.*?\*/", re.S)
HTML_COMMENT_RE = re.compile(r"<!--.*?-->", re.S)

# 计数下限：低于这个数说明扫描没真正跑到文件（比如路径改了 / 后缀漏了），
# 而不是"项目变干净了"。当前实测：定义 ~60 个 token、引用 ~200 处。
MIN_DEFINED = 20
MIN_REFS = 50


def iter_files() -> list[Path]:
    return sorted(p for p in SRC.rglob("*") if p.suffix in SCAN_SUFFIXES)


def clean(text: str) -> str:
    return HTML_COMMENT_RE.sub("", CSS_COMMENT_RE.sub("", text))


def collect(files: list[Path]) -> tuple[dict[str, set[str]], dict[Path, set[str]], dict[Path, list[tuple[int, str]]]]:
    """返回 (已定义变量集合, 每文件悬空引用, 每文件 px 字面量及行号)。"""
    defined: dict[str, set[str]] = {}
    dangling: dict[Path, set[str]] = {}
    px_hits: dict[Path, list[tuple[int, str]]] = {}

    for path in files:
        raw = path.read_text(encoding="utf-8")
        text = clean(raw)
        for name in DEF_RE.findall(text):
            defined.setdefault(name, set()).add(str(path))
        used = set(USE_RE.findall(text))
        if used:
            dangling[path] = used
        for lineno, line in enumerate(text.splitlines(), start=1):
            for value in PX_RE.findall(line):
                px_hits.setdefault(path, []).append((lineno, f"{value}px"))

    return defined, dangling, px_hits


def scan_snippet(snippet: str) -> tuple[set[str], set[str]]:
    """自检用：对一段文本返回 (引用的变量, px 值)。"""
    text = clean(snippet)
    return set(USE_RE.findall(text)), {f"{v}px" for v in PX_RE.findall(text)}


def self_test() -> int:
    failures = 0

    used, _ = scan_snippet("a { color: var(--muted); }")
    if used != {"--muted"}:
        print(f"[FAIL] 自检：var 扫描没抓到 --muted（实际 {used}）", file=sys.stderr)
        failures += 1

    _, px = scan_snippet("a { margin: 37px; }")
    allowed = read_allowlist()
    if "37px" in allowed:
        print("[FAIL] 自检：37px 已在白名单里，无法用它验拦截效果（请换一个未使用的值）",
              file=sys.stderr)
        failures += 1
    if "37px" not in px:
        print(f"[FAIL] 自检：px 扫描没抓到 37px（实际 {px}）", file=sys.stderr)
        failures += 1

    # 真跑一遍全量扫描，确认主干逻辑没坏
    files = iter_files()
    defined, dangling, _ = collect(files)
    if len(defined) < MIN_DEFINED:
        print(f"[FAIL] 自检：全量扫描只解析出 {len(defined)} 个定义（下限 {MIN_DEFINED}）",
              file=sys.stderr)
        failures += 1

    if failures:
        return 1
    print("[SELFTEST] 通过：假 var 与未登记 px 均可被识别")
    return 0


def read_allowlist() -> set[str]:
    if not PX_ALLOW.is_file():
        return set()
    return {line.strip() for line in PX_ALLOW.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.startswith("#")}


def write_allowlist(px_hits: dict[Path, list[tuple[int, str]]]) -> int:
    values = sorted({v for hits in px_hits.values() for _, v in hits},
                    key=lambda s: float(s[:-2]))
    lines = [
        "# px 字面量白名单（UX-01b 基线快照，由 tools/check_tokens.py --write-px-allow 生成）",
        "# 规则：新出现的 px 值不在本表即 FAIL。加值前先问自己能不能复用已有档位；",
        "# 真正的收敛（字号 6 档 / 间距 8 档）在 UX-04 做，届时本表应显著变短。",
        *values,
    ]
    PX_ALLOW.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"[OK] 已写入 {PX_ALLOW}（{len(values)} 个取值）")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="token 引用完整性 + px 白名单守门")
    parser.add_argument("--self-test", action="store_true", help="破坏性自检")
    parser.add_argument("--write-px-allow", action="store_true", help="重建 px 白名单基线")
    args = parser.parse_args()

    if not SRC.is_dir():
        print(f"[FAIL] 找不到前端源码目录 {SRC}", file=sys.stderr)
        return 2

    files = iter_files()
    if not files:
        print(f"[FAIL] {SRC} 下没有可扫描的 .css/.vue 文件", file=sys.stderr)
        return 2

    if args.self_test:
        return self_test()

    defined, dangling, px_hits = collect(files)

    refs = sum(len(v) for v in dangling.values())
    print(f"扫描 {len(files)} 个文件：{len(defined)} 个 token 定义，{refs} 处不同引用")
    if len(defined) < MIN_DEFINED or refs < MIN_REFS:
        print(f"[FAIL] 计数过低（定义 {len(defined)} / 引用 {refs}；下限 "
              f"{MIN_DEFINED}/{MIN_REFS}），疑似扫描范围失效", file=sys.stderr)
        return 2

    if args.write_px_allow:
        return write_allowlist(px_hits)

    violations = 0
    for path, used in sorted(dangling.items()):
        missing = sorted(name for name in used if name not in defined)
        if not missing:
            continue
        violations += len(missing)
        for name in missing:
            print(f"[FAIL] 悬空引用 var({name}) @ {path.relative_to(ROOT)}")

    allowed = read_allowlist()
    if not allowed:
        print(f"[FAIL] 缺少 px 白名单 {PX_ALLOW}，先跑 --write-px-allow 生成基线",
              file=sys.stderr)
        return 2

    unknown: list[str] = []
    for path, hits in sorted(px_hits.items()):
        for lineno, value in hits:
            if value not in allowed:
                unknown.append(f"{path.relative_to(ROOT)}:{lineno}  {value}")
    if unknown:
        violations += len(unknown)
        print(f"[FAIL] 以下 px 取值不在白名单（共 {len(unknown)} 处）：")
        for item in unknown[:30]:
            print(f"  - {item}")
        if len(unknown) > 30:
            print(f"  ... 另有 {len(unknown) - 30} 处")
        print("  处理：优先复用已有档位；确需新增则跑 --write-px-allow 并人工评审 diff")

    if violations:
        print(f"\n[FAIL] 共 {violations} 项违规", file=sys.stderr)
        return 1

    px_total = sum(len(h) for h in px_hits.values())
    print(f"[PASS] var 引用 0 悬空；px 字面量 {px_total} 处全部在白名单内（{len(allowed)} 个取值）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
