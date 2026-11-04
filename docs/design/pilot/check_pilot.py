#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""UX-04 试点静态守门：颜色唯一真源 / 单向派生 / 无硬编码色值。

判据（对应美术计划 §2.2 步骤 0 第 2 个评审维度）：
  1. 试点自写文件里不得出现 hex / rgb() 等硬编码色值；
  2. 适配层引用的**输入侧** semantic token 必须全部在 theme.css 有定义；
  3. 两个候选的页面都必须经过 tokenAdapter，不能自己写主题字面量。

注意：`--el-*` 是适配层**产出**的库变量，不是输入 token，不参与判据 2。
第一版把两者混为一谈，误报 15 处（守门自咬），已在 token_refs() 收窄。

用法：
    python3 check_pilot.py                # 正常检查，exit 0 = 干净
    python3 check_pilot.py --self-test    # 破坏性自检：注入真违规必红、还原必绿
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
SRC = HERE / "src"
# theme.css 是从主仓复制的 token 真源，其 primitives 有色板字面量，不参与本检查。
CHECKED = [
    p
    for p in SRC.rglob("*")
    if p.is_file() and p.suffix in {".vue", ".ts", ".css"} and p.name != "theme.css"
]
HEX = re.compile(r"#[0-9a-fA-F]{3,8}\b")
RGB = re.compile(r"\b(?:rgb|rgba|hsl|hsla)\s*\(")
THEME = (SRC / "theme.css").read_text(encoding="utf-8")
ADAPTER = SRC / "tokenAdapter.ts"


def token_refs() -> list[str]:
    """只取输入侧 token；--el-* 是输出变量，排除。"""
    text = ADAPTER.read_text(encoding="utf-8")
    keys = {k.strip("'") for k in re.findall(r"'--[a-z0-9-]+'", text)}
    return sorted(k for k in keys if not k.startswith("--el-"))


def violations() -> list[str]:
    out: list[str] = []
    for p in CHECKED:
        for n, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
            if HEX.search(line) or RGB.search(line):
                out.append(f"{p.relative_to(HERE)}:{n}: {line.strip()[:90]}")
    for k in token_refs():
        if not re.search(re.escape(k) + r"\s*:", THEME):
            out.append(f"tokenAdapter.ts 引用了 theme.css 未定义的 {k}")
    for lib in ("naive", "element"):
        app = (SRC / lib / "App.vue").read_text(encoding="utf-8")
        if "tokenAdapter" not in app:
            out.append(f"{lib}/App.vue 未经过 tokenAdapter（破坏单向派生）")
    return out


def run_check() -> int:
    bad = violations()
    if bad:
        print(f"[FAIL] 试点静态守门发现 {len(bad)} 处问题：")
        for b in bad[:20]:
            print("  -", b)
        return 1
    print(
        f"[PASS] 试点静态守门：{len(CHECKED)} 个文件、0 硬编码色值、"
        f"{len(token_refs())} 个输入 token 全部有定义、两版均单向派生"
    )
    return 0


def self_test() -> int:
    """注入真硬编码 -> 必须 FAIL 且点名该行；还原字节一致 -> 必须 PASS。"""
    target = SRC / "pilot.css"
    original = target.read_bytes()
    try:
        target.write_bytes(original + b"\n.pilot-injected { color: #ffffff; }\n")
        r = subprocess.run(
            [sys.executable, str(pathlib.Path(__file__).resolve())],
            capture_output=True, text=True,
        )
        if r.returncode == 0:
            print("[SELFTEST FAIL] 注入 #ffffff 后守门仍放行（假绿）")
            return 1
        if "pilot-injected" not in r.stdout:
            print("[SELFTEST FAIL] 守门没有点名注入行")
            return 1
    finally:
        target.write_bytes(original)
    if target.read_bytes() != original:
        print("[SELFTEST FAIL] 还原后字节不一致")
        return 1
    r2 = subprocess.run(
        [sys.executable, str(pathlib.Path(__file__).resolve())],
        capture_output=True, text=True,
    )
    if r2.returncode != 0:
        print("[SELFTEST FAIL] 还原后守门未恢复 PASS：" + r2.stdout[-200:])
        return 1
    print("[SELFTEST PASS] 注入真违规必红、点名命中行、还原字节一致后必绿")
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if "--self-test" in sys.argv else run_check())
