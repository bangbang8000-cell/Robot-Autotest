#!/usr/bin/env python3
"""06-lint 最后一处失败：check_tokens.py 拦下 W1 新页面的 10 个新 px 值。

守门自己的处理提示是「优先复用已有档位；确需新增则跑 --write-px-allow 并人工评审 diff」。
本轮按前一条办：**不扩白名单**，把 10 处收敛到 tools/px-allow.txt 已有的档位。
副作用是三个系统页 max-width 统一为 999px，设计一致性反而变好。

| 原值 | 用途                          | 收敛到 | 理由                         |
|------|-------------------------------|--------|------------------------------|
| 620  | 命令面板宽度                  | 520    | 已有档位，命令面板常用宽度   |
| 60   | 面板阴影模糊                  | 56     | 视觉无差                     |
| 9    | 面板行 padding                | 8      | 间距档位                     |
| 900  | About / Changelog max-width    | 999    | 已有档位，数值最接近         |
| 160  | About kv 表 key 列宽          | 180    | 已有档位，中文标签不截断     |
| 7    | 表格单元格 padding            | 8      | 间距档位                     |
| 64   | 403 页 padding / 字号         | 56     | 已有档位                     |
| 980  | Settings max-width            | 999    | 与另两页统一                 |

幂等：按「文件 + 精确片段」替换，命中次数断言；已修则 SKIP。
"""
from __future__ import annotations

import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[4]

REPLACEMENTS: dict[str, list[tuple[str, str]]] = {
    "frontend/src/components/CommandPalette.vue": [
        ("min(620px, 92vw)", "min(520px, 92vw)"),
        ("0 20px 60px rgba(0, 0, 0, .3)", "0 20px 56px rgba(0, 0, 0, .3)"),
        ("padding: 9px 10px;", "padding: 8px 10px;"),
    ],
    "frontend/src/views/AboutView.vue": [
        ("max-width: 900px;", "max-width: 999px;"),
        ("grid-template-columns: 160px 1fr;", "grid-template-columns: 180px 1fr;"),
        ("padding: 7px 10px;", "padding: 8px 10px;"),
    ],
    "frontend/src/views/ChangelogView.vue": [
        ("max-width: 900px;", "max-width: 999px;"),
    ],
    "frontend/src/views/ForbiddenView.vue": [
        ("padding: 64px 24px;", "padding: 56px 24px;"),
        ("font-size: 64px;", "font-size: 56px;"),
    ],
    "frontend/src/views/SettingsView.vue": [
        ("max-width: 980px;", "max-width: 999px;"),
    ],
}


def main() -> int:
    applied = 0
    skipped = 0
    for rel, pairs in REPLACEMENTS.items():
        path = ROOT / rel
        text = path.read_text(encoding="utf-8")
        original = text
        for old, new in pairs:
            if new in text:
                continue
            hits = text.count(old)
            if hits != 1:
                raise SystemExit(f"FAIL  {rel}：片段 {old!r} 命中 {hits} 次（期望 1）")
            text = text.replace(old, new, 1)
        if text != original:
            path.write_text(text, encoding="utf-8")
            applied += 1
            print(f"PASS  {rel}")
        else:
            skipped += 1
            print(f"SKIP  {rel}（已收敛）")
    print(f"DONE  改动 {applied} 个文件，跳过 {skipped} 个")

    check = subprocess.run([sys.executable, "tools/check_tokens.py"], cwd=ROOT,
                           capture_output=True, text=True)
    tail = (check.stdout or "").strip().splitlines()[-4:]
    for line in tail:
        print(line)
    if check.returncode != 0:
        print(check.stdout, check.stderr, file=sys.stderr)
        raise SystemExit("FAIL  token 守门仍未通过")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
