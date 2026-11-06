#!/usr/bin/env python3
"""订正七版 05-TEST-REPORT.md 里的机器门读数（按最终门禁日志，不沿用估算值）。

实测（`docs/evidence/v1.0/policy-20261106/logs/`，被 .gitignore 忽略、可复跑重建）：
  05-backend_test.log  → `Tests run: 644, Failures: 0, Errors: 0` / BUILD SUCCESS
  02-frontend_test.log → `Test Files 30 passed (30)` / `Tests 221 passed (221)`
原生成器写 642 / 220，是 W1 新增测试类与 changelog 旧格式回退用例之前的旧读数。
按「被实测推翻的假设逐条改掉并留修订记录」办理，重生成七版并在 §6 加 v1.2。
"""
from __future__ import annotations

import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[4]
GEN = ROOT / "docs/evidence/v1.0/p1-closeout/gen_test_reports.py"

PAIRS = [
    ("642 / 642 通过，0 失败 0 错误", "644 / 644 通过，0 失败 0 错误"),
    ("30 文件 / 220 用例通过", "30 文件 / 221 用例通过"),
    ("130 包五数自洽", "130 包 / 87 已完成，五数自洽"),
]


def main() -> int:
    text = GEN.read_text(encoding="utf-8")
    if "644 / 644 通过" in text:
        print("SKIP  生成器读数已订正")
    else:
        for old, new in PAIRS:
            hits = text.count(old)
            if hits != 1:
                raise SystemExit(f"FAIL  生成器读数 {old!r} 命中 {hits} 次")
            text = text.replace(old, new, 1)
            print(f"PASS  读数订正：{old} → {new}")

        rev_line = ('        "| 2026-11-06 | v1.1 | TC 台账改为复用 '
                    '`tools/check_release_docs.py::extract_ids()`，'
                    '补齐区间展开与两种编号形态（v1.0 生成器漏 30 条被机检判 FAIL） |",')
        if text.count(rev_line) != 1:
            raise SystemExit("FAIL  v1.1 修订记录行命中次数不为 1")
        v12_line = ('        "| 2026-11-06 | v1.2 | 机器门读数按最终门禁日志订正：'
                    '后端 642→644、前端 220→221，WBS 补 87/130；不沿用估算值 |",\n')
        # 在修订记录表首行之前插入 v1.2（newest first）
        text = text.replace(rev_line, v12_line + rev_line, 1)
        GEN.write_text(text, encoding="utf-8")
        print("PASS  修订记录 v1.2")

    out = subprocess.run([sys.executable, str(GEN.relative_to(ROOT))], cwd=ROOT,
                         capture_output=True, text=True)
    if out.returncode != 0:
        print(out.stdout, out.stderr, file=sys.stderr)
        raise SystemExit("FAIL  重新生成七版报告")
    print(f"PASS  重新生成 {len(out.stdout.strip().splitlines())} 份测试报告")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
