#!/usr/bin/env python3
"""P1 收口的数据勘查（只读）：一级分类分布、domain_tag 空值率、owner_id 状态。

为什么单独写脚本：Windows Git Bash 调 `wsl.exe bash -lc '... $VAR ...'` 时外层会吞掉
shell 变量（2026-11-06 已踩两次），数据库口令又不能出现在命令行里，所以连接参数
一律在 Python 内读 ~/.config/robot-autotest/env，经 MYSQL_PWD 传给客户端。
"""
from __future__ import annotations

import os
import pathlib
import subprocess

ENV = pathlib.Path.home() / ".config" / "robot-autotest" / "env"
cfg: dict[str, str] = {}
for line in ENV.read_text(encoding="utf-8").splitlines():
    stripped = line.strip()
    if stripped and not stripped.startswith("#") and "=" in stripped:
        key, value = stripped.split("=", 1)
        cfg[key.strip()] = value.strip().strip('"').strip("'")

child_env = {**os.environ, "MYSQL_PWD": cfg.get("DB_PASSWORD", "")}
QUERIES = {
    "一级分类(case_kind)分布": "SELECT COALESCE(case_kind,'<NULL>'), COUNT(*) FROM test_case "
                          "GROUP BY case_kind ORDER BY 2 DESC LIMIT 30",
    "已有 domain_tag": "SELECT COALESCE(domain_tag,'<NULL>'), COUNT(*) FROM test_case "
                       "GROUP BY domain_tag ORDER BY 2 DESC LIMIT 20",
    "domain_tag 覆盖": "SELECT COUNT(*) AS total, "
                        "SUM(CASE WHEN domain_tag IS NULL OR domain_tag = '' THEN 1 ELSE 0 END) AS blank_cnt "
                        "FROM test_case",
    "项目 owner 状态": "SELECT COUNT(*) AS projects, "
                        "SUM(CASE WHEN owner_id IS NULL THEN 1 ELSE 0 END) AS owner_null "
                        "FROM project",
    "旧 owner 角色残留": "SELECT COUNT(*) FROM project_member WHERE member_role = 'owner'",
}

for title, sql in QUERIES.items():
    result = subprocess.run(
        [
            "mysql",
            "-h", cfg.get("DB_HOST", "127.0.0.1"),
            "-P", cfg.get("DB_PORT", "3306"),
            "-u", cfg.get("DB_USERNAME", "robot"),
            "-N", "-B", cfg.get("DB_NAME", "robot_dev"),
            "-e", sql,
        ],
        env=child_env,
        capture_output=True,
        text=True,
        check=False,
    )
    print(f"---- {title} ----")
    print(result.stdout.strip() if result.returncode == 0 else f"ERROR: {result.stderr.strip()}")
