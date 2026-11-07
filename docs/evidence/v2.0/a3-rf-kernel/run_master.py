#!/usr/bin/env python3
"""A3：把主控参照命令按原样执行（不改造），落盘「实际执行的 argv」与原始 stdout/stderr。

用法: python3 run_master.py master.json agent.json stdout.log stderr.log
退出码 = pabot 的退出码（RF 测试失败也为 1，需按 output.xml 判读）。
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 5:
        print(__doc__)
        return 2
    master_path, agent_path, out_log, err_log = map(Path, sys.argv[1:])
    payload = json.loads(master_path.read_text(encoding="utf-8"))
    argv = payload["argv"]

    home = Path.home()
    venv_bin = home / "rf-stack" / ".venv" / "bin"
    env = dict(os.environ)
    env["PATH"] = f"{venv_bin}{os.pathsep}" + env.get("PATH", "")
    env["TZ"] = "Asia/Shanghai"
    env["LANG"] = "C.UTF-8"
    env["PYTHONUNBUFFERED"] = "1"

    with out_log.open("wb") as out, err_log.open("wb") as err:
        proc = subprocess.run(argv, cwd=str(Path.home() / "robot-autotest"),
                              env=env, stdout=out, stderr=err, timeout=1800)

    # 实际执行的 argv（与 master 逐项一致；本文件是「执行侧留痕」，用于 diff 对照）
    agent_path.write_text(json.dumps({"runId": payload.get("runId"), "argv": argv},
                                     ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"exit={proc.returncode} argv_items={len(argv)}")
    return proc.returncode


if __name__ == "__main__":
    raise SystemExit(main())
