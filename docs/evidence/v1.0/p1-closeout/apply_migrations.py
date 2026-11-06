#!/usr/bin/env python3
"""应用 V7_7 / V7_8 两条回填迁移并当场验证（PRJ-01 案 A、AST-04 案 A-②）。

连接参数只从 ~/.config/robot-autotest/env 读进内存，**不打印、不落盘**；
不用 shell 变量是因为外层 Git Bash 会吞掉 `wsl.exe bash -lc` 里的 `$VAR`。
"""
from __future__ import annotations

import os
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[4]
ENV = pathlib.Path.home() / ".config" / "robot-autotest" / "env"
cfg: dict[str, str] = {}
for line in ENV.read_text(encoding="utf-8").splitlines():
    stripped = line.strip()
    if stripped and not stripped.startswith("#") and "=" in stripped:
        key, value = stripped.split("=", 1)
        cfg[key.strip()] = value.strip().strip('"').strip("'")

env = {**os.environ, **cfg}
print("[migrate] bash scripts/db-migrate.sh")
result = subprocess.run(
    ["bash", "scripts/db-migrate.sh"], cwd=ROOT, env=env,
    capture_output=True, text=True, check=False,
)
tail = (result.stdout + result.stderr).strip().splitlines()[-25:]
print("\n".join(tail))
if result.returncode != 0:
    print(f"[migrate] FAIL exit={result.returncode}")
    raise SystemExit(1)

queries = {
    "domain_tag 空值数（期望 0）":
        "SELECT COUNT(*) FROM test_case WHERE domain_tag IS NULL OR domain_tag = ''",
    "domain_tag 分布":
        "SELECT domain_tag, COUNT(*) FROM test_case GROUP BY domain_tag ORDER BY 2 DESC",
    "owner_id 为空项目数（期望 0）":
        "SELECT COUNT(*) FROM project WHERE owner_id IS NULL",
    "旧 owner 角色行数（期望 0）":
        "SELECT COUNT(*) FROM project_member WHERE member_role = 'owner'",
}
child = {**env, "MYSQL_PWD": cfg.get("DB_PASSWORD", "")}
for title, sql in queries.items():
    out = subprocess.run(
        ["mysql", "-h", cfg.get("DB_HOST", "127.0.0.1"),
         "-P", cfg.get("DB_PORT", "3306"), "-u", cfg.get("DB_USERNAME", "robot"),
         "-N", "-B", cfg.get("DB_NAME", "robot_dev"), "-e", sql],
        env=child, capture_output=True, text=True, check=False,
    )
    print(f"---- {title} ----")
    print(out.stdout.strip() if out.returncode == 0 else f"ERROR: {out.stderr.strip()}")
    if out.returncode != 0:
        sys.exit(1)
