#!/usr/bin/env python3
"""TPLX-03 验收：四格式一致性校验器的正/负向自检。

判据
----
- 一致 → 退出码 0（四种格式都是 21 个字段、顺序相同）；
- **人为从 xlsx 删一列 → 必须非 0 且点名缺哪个字段**；
- 只判退出码不够：退出码相同的「解析失败」会把「不一致」盖住，所以同时断言输出里
  出现具体字段名与判定词。这是本仓一条老教训——RF `--dryrun` 语法错误也返回 0，
  只判退出码等于没判。

跑法
----
    mvn -o -B test -Dtest=ReportFourFormatDumpTest      # 先用当前导出器产出四份文件
    python3 docs/evidence/v2.1/TPLX-03/run.py

退出码 0 = 全部断言通过。产物：`samples/` 下四份原样 + 两份故意改坏的 xlsx，
`positive.log` / `negative-*.log` 为原始输出。
"""
from __future__ import annotations

import pathlib
import shutil
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[3]
SAMPLES = HERE / "samples"
SOURCE = ROOT / "backend" / "target" / "evidence" / "four-formats"
CHECKER = ROOT / "tools" / "check_report_consistency.py"
FORMATS = ("html", "docx", "xlsx", "pdf")


def run_checker(paths: list[pathlib.Path]) -> tuple[int, str]:
    result = subprocess.run([sys.executable, str(CHECKER), *map(str, paths)],
                            capture_output=True, text=True)
    return result.returncode, (result.stdout + result.stderr).strip()


def mutate(source: pathlib.Path, target: pathlib.Path, action: str) -> None:
    """用 openpyxl 造「坏文件」：删一行 / 换两行顺序。"""
    import openpyxl

    workbook = openpyxl.load_workbook(source)
    sheet = workbook[workbook.sheetnames[0]]
    rows = {sheet.cell(row=r, column=1).value: r for r in range(2, sheet.max_row + 1)}
    if action == "drop":
        sheet.delete_rows(rows["passRate"])
    elif action == "swap":
        first, second = rows["title"], rows["version"]
        for column in range(1, 4):
            left = sheet.cell(row=first, column=column).value
            right = sheet.cell(row=second, column=column).value
            sheet.cell(row=first, column=column).value = right
            sheet.cell(row=second, column=column).value = left
    else:
        raise ValueError(action)
    workbook.save(target)


def main() -> int:
    if not SOURCE.is_dir():
        raise SystemExit(f"缺少 {SOURCE}，先跑 mvn -o -B test -Dtest=ReportFourFormatDumpTest")
    SAMPLES.mkdir(exist_ok=True)
    for fmt in FORMATS:
        shutil.copy2(SOURCE / f"report.{fmt}", SAMPLES / f"report.{fmt}")

    rows: list[tuple[str, str, bool, str]] = []

    def check(cid: str, desc: str, ok: bool, detail: str = "") -> None:
        rows.append((cid, desc, ok, detail))

    base = [SAMPLES / f"report.{fmt}" for fmt in FORMATS]

    # ---------- ① 正向：四格式一致 ----------
    code, out = run_checker(base)
    (HERE / "positive.log").write_text(out + "\n", encoding="utf-8")
    check("TPLX3-01", "四格式字段一致 → 退出码 0", code == 0, f"exit={code}")
    check("TPLX3-02", "输出含每种格式的 PASS 与字段数 21",
                 out.count("[PASS]") == 3 and "每种 21 个字段" in out,
                 f"PASS 行={out.count('[PASS]')}")

    # ---------- ② 负向：xlsx 删掉 passRate 一行 ----------
    missing = SAMPLES / "report-missing-passRate.xlsx"
    mutate(base[2], missing, "drop")
    code, out = run_checker([base[0], base[1], missing, base[3]])
    (HERE / "negative-xlsx-missing.log").write_text(out + "\n", encoding="utf-8")
    check("TPLX3-03", "xlsx 缺一列 → 退出码非 0", code != 0, f"exit={code}")
    check("TPLX3-04", "且点名缺哪个字段、只报缺不误报多",
                 "缺 passRate" in out and "多 " not in out,
                 next((line for line in out.splitlines() if "缺" in line), out[:60]))

    # ---------- ③ 负向：xlsx 只换顺序 ----------
    reordered = SAMPLES / "report-reordered.xlsx"
    mutate(base[2], reordered, "swap")
    code, out = run_checker([base[0], base[1], reordered, base[3]])
    (HERE / "negative-xlsx-order.log").write_text(out + "\n", encoding="utf-8")
    check("TPLX3-05", "字段集合相同但顺序不同 → 退出码非 0 且判定为顺序问题",
                 code != 0 and "顺序" in out, f"exit={code}")

    # ---------- ④ 边界：文件不存在必须与「不一致」区分（退出码 2） ----------
    code, out = run_checker([base[0], SAMPLES / "no-such.docx"])
    (HERE / "negative-missing-file.log").write_text(out + "\n", encoding="utf-8")
    check("TPLX3-06", "文件不存在 → 退出码 2（不与不一致的 1 混淆）",
                 code == 2 and "文件不存在" in out, f"exit={code}")

    width = max(len(row[1]) for row in rows) + 2
    for cid, desc, ok, detail in rows:
        print(f"{'PASS' if ok else 'FAIL'}  {cid:<10}{desc:<{width}}{detail}")
    failed = [row for row in rows if not row[2]]
    print(f"\n合计 {len(rows)} 项，FAIL {len(failed)} 项")
    return 0 if not failed else 1


if __name__ == "__main__":
    raise SystemExit(main())
