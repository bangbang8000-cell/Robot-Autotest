#!/usr/bin/env python3
"""EXE-02 真库并发唯一键验收（MySQL 8.0.46，本机 WSL）。

关掉的缺口
----------
WBS 原话：「**本机并发是内存语义**（`putIfAbsent` 复刻唯一键），真库
`uk_resource_lease_key` 的真实并发与回滚需按 `lease-conflict-0.sql` 在本地执行」。

本脚本把「内存语义」换成**真的 MySQL 并发热点**：
`resource_lease` 的唯一键 `uk_resource_lease_key(resource_key)` 是租约互斥的唯一机制
（`ResourceLeaseRepositoryJdbc.tryAcquire` 就是一条纯 INSERT，靠唯一约束挡并发），
所以用 N 个**独立数据库连接**在同一屏障后同时插同一个 resource_key，
成功数必须恰好为 1 —— 这就是「同一资源不会被两方同时持有」的数据库层证明。

同时跑 `lease-conflict-0.sql` 的三条判定查询（Q1 持有行数 / Q2 重复 key 空集 / Q3 历史重叠 0）。

夹具
----
- resource_key 前缀 `qa-e2e-lease-`，跑完按前缀清理（不碰真实租约）
- job_id 复用库内已存在的 `execution_job.id`（满足外键，不新建 job）

用法：`python3 docs/evidence/v0.4/lease-concurrency/run.py [--workers 64]`
"""
from __future__ import annotations

import argparse
import os
import pathlib
import subprocess
import sys
import threading
import uuid

KEY_PREFIX = "qa-e2e-lease-"
LOGFILE = pathlib.Path(__file__).resolve().parent / "run.log"


def load_db_config() -> dict:
    env_file = pathlib.Path.home() / ".config" / "robot-autotest" / "env"
    cfg: dict[str, str] = {}
    for line in env_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        cfg[k.strip()] = v.strip().strip('"').strip("'")
    return cfg


class Db:
    def __init__(self, cfg: dict) -> None:
        self.db = cfg.get("DB_NAME") or "robot_dev"
        self.env = dict(os.environ, MYSQL_PWD=cfg.get("DB_PASSWORD", ""))
        self.cmd = ["mysql", "-u", cfg.get("DB_USERNAME", "root"),
                    "-h", cfg.get("DB_HOST", "127.0.0.1"),
                    "-P", cfg.get("DB_PORT", "3306"), "-N", "-B"]

    def query(self, sql: str) -> str:
        r = subprocess.run(self.cmd + ["-e", sql], env=self.env, capture_output=True, text=True)
        if r.returncode != 0:
            raise SystemExit(f"SQL 失败: {sql[:70]} :: {r.stderr.strip()[:250]}")
        return r.stdout.strip()

    def execute(self, sql: str) -> tuple[int, str]:
        r = subprocess.run(self.cmd + ["-e", sql], env=self.env, capture_output=True, text=True)
        return r.returncode, (r.stderr or "").strip()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--workers", type=int, default=64)
    args = ap.parse_args()

    cfg = load_db_config()
    db = Db(cfg)
    tag = uuid.uuid4().hex[:8]
    key = f"{KEY_PREFIX}{tag}"

    job_row = db.query(f"SELECT id FROM {db.db}.execution_job ORDER BY id LIMIT 1;")
    if not job_row:
        raise SystemExit("FAIL 库内没有 execution_job 行，无法满足租约外键")
    job_id = int(job_row.splitlines()[0])

    db.execute(f"DELETE FROM {db.db}.resource_lease WHERE resource_key LIKE '{KEY_PREFIX}%';")

    insert = (f"INSERT INTO {db.db}.resource_lease "
              f"(job_id, resource_key, lease_state, leased_at, expires_at) VALUES "
              f"({job_id}, '{key}', 'held', NOW(3), DATE_ADD(NOW(3), INTERVAL 120 SECOND));")

    barrier = threading.Barrier(args.workers)
    results: list[tuple[int, str]] = []
    lock = threading.Lock()

    def worker() -> None:
        barrier.wait()
        rc, err = db.execute(insert)
        with lock:
            results.append((rc, err))

    threads = [threading.Thread(target=worker) for _ in range(args.workers)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    ok = [r for r in results if r[0] == 0]
    dup = [r for r in results if r[0] != 0 and "1062" in r[1]]
    other = [r for r in results if r[0] != 0 and "1062" not in r[1]]

    held = db.query(f"SELECT COUNT(*) FROM {db.db}.resource_lease WHERE resource_key = '{key}';")
    q2 = db.query(f"SELECT resource_key, COUNT(*) c FROM {db.db}.resource_lease "
                  f"GROUP BY resource_key HAVING COUNT(*) > 1;")
    q3 = db.query(f"""
        SELECT COUNT(*) FROM {db.db}.resource_lease_history a
          JOIN {db.db}.resource_lease_history b
            ON a.resource_key = b.resource_key
           AND a.job_id <> b.job_id
           AND a.acquired_at < COALESCE(b.released_at, NOW(3))
           AND b.acquired_at < COALESCE(a.released_at, NOW(3));""")
    q1 = db.query(f"SELECT COUNT(*) FROM {db.db}.resource_lease WHERE lease_state = 'held';")

    db.execute(f"DELETE FROM {db.db}.resource_lease WHERE resource_key LIKE '{KEY_PREFIX}%';")

    # 引擎名取运行时真实值：上一版把 MySQL 8.0.46 写成了 MariaDB，
    # 而 ADR-0002 里 MariaDB 一直是「待验」，不能让结论张冠李戴。
    raw_ver = db.query("SELECT VERSION();") or ""
    engine = (("MariaDB " if "MariaDB" in raw_ver else "MySQL ") + raw_ver) if raw_ver else "未知引擎"

    lines = [
        f"# EXE-02 真库并发唯一键验收（{engine}）",
        "",
        f"- 库：`{db.db}` · 引擎：`{engine}` · 并发连接数：**{args.workers}** · 屏障同时起跑",
        f"- 热点 resource_key：`{key}`（夹具，跑完已清理）",
        f"- job_id：{job_id}（复用既有 execution_job，满足外键）",
        "",
        "## 并发插入结果",
        "",
        "| 指标 | 值 | 期望 |",
        "|---|---|---|",
        f"| 并发请求数 | {args.workers} | — |",
        f"| 成功插入 | **{len(ok)}** | **恰好 1** |",
        f"| 唯一键冲突（1062） | {len(dup)} | {args.workers - 1} |",
        f"| 其他错误 | {len(other)} | 0 |",
        f"| 该 key 落库行数 | {held} | 1 |",
        "",
        "## lease-conflict-0.sql 三条判定",
        "",
        "| 查询 | 结果 | 判定 |",
        "|---|---|---|",
        f"| Q1 held 租约行数 | {q1} | 参考值（当前持有点数） |",
        f"| Q2 同一 resource_key 多行 | `{q2 or '空集'}` | {'PASS 空集' if not q2 else '**FAIL**'} |",
        f"| Q3 历史持有区间重叠对 | {q3 or '0'} | {'PASS 0' if (q3 or '0') == '0' else '**FAIL**'} |",
        "",
    ]

    passed = (len(ok) == 1 and len(dup) == args.workers - 1 and not other
              and held == "1" and not q2 and (q3 or "0") == "0")
    lines.append(f"**结论：{'PASS' if passed else 'FAIL'}** —— "
                 + (f"唯一键在真实 {engine} 并发下保证了单持有，内存语义之外的疑点已消除。"
                    if passed else "存在不符合预期的读数，逐项见上表。"))
    lines.append("")

    text = "\n".join(lines)
    LOGFILE.write_text(text, encoding="utf-8")
    print(text)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
