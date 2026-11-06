# -*- coding: utf-8 -*-
"""W2 收口：真实执行 V8_1 迁移 + 核对三张表 + WBS 五包 AI 验收回写。幂等。"""
from __future__ import annotations

import os
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path.cwd()
ENV_FILE = pathlib.Path.home() / ".config" / "robot-autotest" / "env"
cfg: dict[str, str] = {}
for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
    stripped = line.strip()
    if stripped and not stripped.startswith("#") and "=" in stripped:
        key, value = stripped.split("=", 1)
        cfg[key.strip()] = value.strip().strip('"').strip("'")

env = {**os.environ, **cfg}
child = {**env, "MYSQL_PWD": cfg.get("DB_PASSWORD", "")}

EVID = ROOT / "docs/evidence/v1.0/w2-report"
log_lines: list[str] = ["# W2 / V8_1 迁移实跑日志（2026-11-06）", ""]

# ---- 1. 迁移 ----
result = subprocess.run(["bash", "scripts/db-migrate.sh"], cwd=ROOT, env=env,
                        capture_output=True, text=True, check=False)
tail = (result.stdout + result.stderr).strip().splitlines()[-20:]
log_lines += ["## 1. bash scripts/db-migrate.sh", "```", *tail, "```", ""]
print("\n".join(tail))
if result.returncode != 0:
    raise SystemExit(f"FAIL 迁移失败 exit={result.returncode}")

# ---- 2. 表核对 ----
queries = {
    "三张新表": "SHOW TABLES LIKE 'test_report%'",
    "test_report 列数": "SELECT COUNT(*) FROM information_schema.columns "
                        "WHERE table_schema = DATABASE() AND table_name = 'test_report'",
    "test_report_export 列数": "SELECT COUNT(*) FROM information_schema.columns "
                               "WHERE table_schema = DATABASE() AND table_name = 'test_report_export'",
    "test_report_audit 列数": "SELECT COUNT(*) FROM information_schema.columns "
                              "WHERE table_schema = DATABASE() AND table_name = 'test_report_audit'",
    "flyway 最新版本": "SELECT version, description, success FROM flyway_schema_history "
                       "WHERE success = 1 ORDER BY installed_rank DESC LIMIT 3",
}
counts: dict[str, str] = {}
for title, sql in queries.items():
    out = subprocess.run(
        ["mysql", "-h", cfg.get("DB_HOST", "127.0.0.1"), "-P", cfg.get("DB_PORT", "3306"),
         "-u", cfg.get("DB_USERNAME", "robot"), "-N", "-B",
         cfg.get("DB_NAME", "robot_dev"), "-e", sql],
        env=child, capture_output=True, text=True, check=False)
    if out.returncode != 0:
        raise SystemExit(f"FAIL 查询失败 {title}: {out.stderr.strip()}")
    counts[title] = out.stdout.strip()
    log_lines += [f"---- {title} ----", "```", out.stdout.strip(), "```", ""]
    print(f"---- {title} ----\n{out.stdout.strip()}")

tables = set(counts["三张新表"].splitlines())
expected = {"test_report", "test_report_export", "test_report_audit"}
if not expected <= tables:
    raise SystemExit(f"FAIL 新表不全，实际 {sorted(tables)}")
if counts["test_report 列数"] != "13":
    raise SystemExit(f"FAIL test_report 列数 {counts['test_report 列数']}（期望 13）")
print("[PASS] 三张新表齐全且结构正确")

log_lines += ["## 3. 结论", "",
              "V8_1 已在本机库真实执行；`test_report` 13 列，导出与审计表齐全。",
              "新增表由 `scripts/rollback.sh` 的全库 mysqldump 自动纳入备份（C9 实测订正）。", ""]
(EVID / "migration.log").write_text("\n".join(log_lines), encoding="utf-8")

# ---- 3. WBS 五包回写 ----
WBS = ROOT / "docs/plan/WBS-STATUS.md"
t = WBS.read_text(encoding="utf-8")
if "W2 收口（RPTX-01..05）" in t:
    print("[SKIP] WBS 已回写 W2")
else:
    evidence = {
        "103": "db/migration/V8_1__test_report.sql、ReportState、TestReportService、"
               "docs/evidence/v1.0/w2-report/（migration.log + 4 条状态机单测）",
        "104": "ReportSnapshotService 物化 JSON/HTML + SHA-256；ReportSnapshotTest 4 条",
        "105": "ReportViewRenderer.external() 走 C5 白名单；TestReportsView.vue",
        "106": "ReportViewRenderer.internal() 关键字树 / 命令 / 原始输出入口",
        "107": "ReportExportService HTML/PDF/DOCX + 水印 + 保留；export-sample.* + sensitive-scan.log",
    }
    lines = t.splitlines(keepends=True)
    changed = 0
    out = []
    for line in lines:
        m = re.match(r"^\| (10[3-7]) \|", line)
        if m and "| 进行中 |" in line:
            pkg = m.group(1)
            line = line.replace("| 进行中 | — |", "| 已完成 | " + evidence[pkg] + " |", 1)
            line = line.replace("**未开工**", "**AI 验收 2026-11-06**")
            if "AI 验收 2026-11-06" not in line:
                line = line.rstrip("\n") + " **AI 验收 2026-11-06** |\n"
            changed += 1
        out.append(line)
    if changed != 5:
        raise SystemExit(f"FAIL WBS 期望改 5 行，实际 {changed}")
    t = "".join(out)
    t = t.replace("| 130 个任务包 | **0** | **28** | **8** | **7** | **87** |",
                  "| 130 个任务包 | **0** | **23** | **8** | **7** | **92** |", 1)
    t = t.replace("**当前进度：87 / 130（约 66.9% · 8 待验 · 7 待现场 · 0 未开始 · 28 进行中）**",
                  "**当前进度：92 / 130（约 70.8% · 8 待验 · 7 待现场 · 0 未开始 · 23 进行中）**", 1)
    t = t.rstrip() + ("\n\n> **W2 收口（RPTX-01..05）2026-11-06**：报告双实体、快照冻结、双视图、"
                      "三格式导出与 C5 字段白名单全部实现并本机实测。\n"
                      "证据 `docs/evidence/v1.0/w2-report/`（迁移日志 / 三格式样件 / 敏感扫描 0 命中 / 12 条新增单测）。\n"
                      "边界：三格式样件为本机内部验证件，**真实链路导出抽样与 G8 第 5 条删源演练仍待现场**；\n"
                      "G4/G5 未判过，本波「已完成」指功能实现 + 机器门全绿，不冒充门禁通过。\n")
    WBS.write_text(t, encoding="utf-8")
    print("[PASS] WBS W2 五包转已完成，汇总 92/130")

r = subprocess.run([sys.executable, "tools/check_wbs_consistency.py"],
                   capture_output=True, text=True)
print(r.stdout.strip().splitlines()[-1] if r.stdout else r.stderr.strip())
if r.returncode:
    raise SystemExit("FAIL WBS 自洽校验未通过")
print("[W2-CLOSE] DONE")
