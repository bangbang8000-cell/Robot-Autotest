#!/usr/bin/env python3
"""检查前端 `var(--xxx)` 引用的 CSS 变量是否真的定义过（v2.1 ORCH-02 起进门禁）。

为什么需要这一条
----------------
写错变量名**不会报错**：CSS 自定义属性在计算期才解析，引用不存在的名字时该声明直接失效，
属性回落到初始值。`fill` 的初始值是**黑色**——于是 SVG 节点变成黑方块、深色文字压在上面，
整张图「渲染成功、内容不可见」。`vue-tsc` 管不到 CSS，`vitest`（jsdom）不算样式，
`vite build` 也不校验，三关全绿。

ORCH-02 首轮真实浏览器验收就是这么撞上的（见 `docs/evidence/v2.1/ORCH-02/README.md` §4）：
组件里写了 `--surface-sunken` / `--text-link` / `--border-subtle` 这些**主题里不存在**的名字，
4 个文件 20 处，全部静默失效。

判据（可脚本化，故进门禁）
--------------------------
① 引用的变量名必须能在某个 `.css` / `.vue` 里找到定义（scoped 里自定义的局部变量也算）；
② 动态拼接（`var(--preset-${color})`）按**前缀**判：至少有一个已定义变量以该前缀开头。

用法：python3 tools/check_css_vars.py
退出码：0 = 全部有定义；1 = 存在未定义引用（逐条打印 文件:行）。
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "frontend" / "src"
SUFFIXES = {".css", ".vue", ".ts"}

DEF_RE = re.compile(r"^\s*(--[a-z0-9-]+)\s*:", re.MULTILINE)
USE_RE = re.compile(r"var\((--[a-z0-9-]*)")


def collect() -> tuple[set[str], list[tuple[Path, int, str]]]:
    """返回（已定义的变量名集合，未定义的引用清单）。"""
    defined: set[str] = set()
    files = [p for p in SRC.rglob("*") if p.is_file() and p.suffix in SUFFIXES]
    for path in files:
        try:
            defined.update(DEF_RE.findall(path.read_text(encoding="utf-8")))
        except UnicodeDecodeError:
            continue

    missing: list[tuple[Path, int, str]] = []
    for path in files:
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for line_no, line in enumerate(text.splitlines(), start=1):
            # 只扫 `var(--x` 出现处；注释里的写法也一并检查——
            # 注释里留个不存在的名字，后来的人会照着抄。
            for name in USE_RE.findall(line):
                if name in defined:
                    continue
                # 动态拼接：`var(--preset-${c})` 会截出 `--preset-`，按前缀放行
                if name.endswith("-") and any(d.startswith(name) for d in defined):
                    continue
                missing.append((path.relative_to(ROOT), line_no, name))
    return defined, missing


def main() -> int:
    if not SRC.is_dir():
        print(f"[FAIL] 找不到前端源码目录：{SRC}", file=sys.stderr)
        return 1

    defined, missing = collect()
    if not missing:
        print(f"[OK] CSS 变量引用全部有定义（已定义 {len(defined)} 个）")
        return 0

    print(f"[FAIL] {len(missing)} 处引用了未定义的 CSS 变量"
          f"（写错名字不会报错，属性会静默回落到初始值，SVG fill 会变黑色）：", file=sys.stderr)
    for path, line_no, name in missing:
        print(f"  {path}:{line_no}  {name}", file=sys.stderr)
    print("\n修法：改成 styles/theme.css 里真实存在的令牌名"
          "（如 --text / --text-muted / --panel / --surface-2 / --border / --accent / --fail / --warn）。",
          file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
