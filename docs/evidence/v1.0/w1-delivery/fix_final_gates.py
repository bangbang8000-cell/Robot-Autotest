#!/usr/bin/env python3
"""最终八步门禁三道失败的统一修复（2026-11-06 W1 收口）。

第一次复跑 FAILS=3：
  05-backend_test  V7BackfillTest.domainBackfill 断言没去掉 SQL 标识符的反引号
  06-lint          4 个新 Vue 页面 11 处硬编码 hex（由 fix_colors.py 修，本脚本不重复）
  07-release_docs  七版 05-TEST-REPORT.md 的 TC 台账漏了区间展开 / 非填充编号

设计纪律：
  - 全部「内容匹配 + 命中次数断言 + 可重跑」，绝不按行号定位；
  - 生成器不复制机检逻辑，直接 import 同一个模块，从根上消除口径漂移；
  - 幂等：已修则 SKIP，重复执行不得重复插入。
"""
from __future__ import annotations

import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[4]


def patch(path: pathlib.Path, old: str, new: str, label: str, done_marker: str) -> None:
    text = path.read_text(encoding="utf-8")
    if done_marker in text and old not in text:
        print(f"SKIP  {label}（已修复）")
        return
    hits = text.count(old)
    if hits != 1:
        raise SystemExit(f"FAIL  {label}：期望命中 1 次，实际 {hits} 次，按纪律停下")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"PASS  {label}")


def fix_java() -> None:
    """V7_8 分支与 V7_7 一样，断言前要先剥掉 SQL 标识符的反引号。"""
    path = ROOT / "backend/src/test/java/com/qa/autotest/migration/V7BackfillTest.java"
    old = '        String sql = Files.readString(up, StandardCharsets.UTF_8);\n'
    new = (
        '        // SQL 里标识符带反引号，断言前统一去掉，避免把格式差异误判成缺规则\n'
        '        String sql = Files.readString(up, StandardCharsets.UTF_8).replace("`", "");\n'
    )
    patch(path, old, new, "05-backend_test / V7BackfillTest.domainBackfill", new.strip())


def fix_generator() -> None:
    """生成器改用机检同款 extract_ids / detect_prefix（含区间展开）。"""
    path = ROOT / "docs/evidence/v1.0/p1-closeout/gen_test_reports.py"
    text = path.read_text(encoding="utf-8")

    marker = "def _release_checker():"
    if marker in text:
        print("SKIP  07-release_docs / 生成器 TC 提取（已修复）")
    else:
        if text.count("import re\n") != 1:
            raise SystemExit("FAIL  生成器 import 区不符合预期")
        text = text.replace("import re\n", "import importlib.util\n", 1)

        old_tc = '''TC_RE = re.compile(r"TC-[A-Za-z0-9][A-Za-z0-9._-]*")


def tc_ids(test_plan: pathlib.Path) -> list[str]:
    if not test_plan.exists():
        return []
    seen: dict[str, None] = {}
    for match in TC_RE.finditer(test_plan.read_text(encoding="utf-8")):
        seen.setdefault(match.group(0).rstrip("."), None)
    return list(seen)
'''
        new_tc = '''def _release_checker():
    """加载门禁同款工具，避免「生成器一套正则、机检另一套」的口径漂移。"""
    spec = importlib.util.spec_from_file_location(
        "check_release_docs", ROOT / "tools" / "check_release_docs.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


CHECKER = _release_checker()


def tc_ids(test_plan: pathlib.Path) -> list[str]:
    """与 tools/check_release_docs.py 判据 3 使用同一套提取逻辑。

    机检 extract_ids() 会展开 `TC-v0.1-1 ~ 5` 区间，并同时保留零填充（001）
    与非填充（1）两种形态。生成器若另写正则，v0.1 报告会缺 30 条编号而被判
    「未执行 TC」。这里不复制实现，直接加载同一模块，从根上消除漂移。
    """
    if not test_plan.exists():
        return []
    content = test_plan.read_text(encoding="utf-8")
    prefix = CHECKER.detect_prefix(content, "TC")
    if not prefix:
        return []
    return sorted(CHECKER.extract_ids(content, prefix), key=CHECKER.id_sort_key)
'''
        if text.count(old_tc) != 1:
            raise SystemExit("FAIL  生成器 tc_ids 原文命中次数不为 1")
        text = text.replace(old_tc, new_tc, 1)
        path.write_text(text, encoding="utf-8")
        print("PASS  07-release_docs / 生成器 TC 提取改为复用机检模块")

    # 修订记录：按流程第 ⑦ 道留痕，不悄悄改数字
    rev_old = ('        "| 2026-11-06 | v1.0 | 首次补齐；'
               '按「机器门实测 + 历史证据 + 门禁真值」三层写实，不用通过模板 |",')
    rev_new = ('        "| 2026-11-06 | v1.1 | TC 台账改为复用 `tools/check_release_docs.py::extract_ids()`，'
               '补齐区间展开与两种编号形态（v1.0 生成器漏 30 条被机检判 FAIL） |",\n'
               + rev_old)
    path = ROOT / "docs/evidence/v1.0/p1-closeout/gen_test_reports.py"
    text = path.read_text(encoding="utf-8")
    if "v1.1 | TC 台账改为复用" in text:
        print("SKIP  修订记录 v1.1（已写入）")
    else:
        if text.count(rev_old) != 1:
            raise SystemExit("FAIL  修订记录原文命中次数不为 1")
        path.write_text(text.replace(rev_old, rev_new, 1), encoding="utf-8")
        print("PASS  修订记录 v1.1")


def regenerate() -> None:
    out = subprocess.run(
        [sys.executable, "docs/evidence/v1.0/p1-closeout/gen_test_reports.py"],
        cwd=ROOT, capture_output=True, text=True,
    )
    print(out.stdout.strip())
    if out.returncode != 0:
        print(out.stderr, file=sys.stderr)
        raise SystemExit("FAIL  重新生成七版测试报告")


def main() -> int:
    fix_java()
    fix_generator()
    regenerate()
    print("DONE  三道失败的代码侧修复完成；请复跑 gates.sh")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
