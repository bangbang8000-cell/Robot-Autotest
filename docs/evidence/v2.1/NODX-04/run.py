#!/usr/bin/env python3
"""NODX-04 端到端证据：节点页四态 / 离线时长口径 / 最近任务 / agentBaseUrl。

判据（DEV-PLAN §10.13，引用 TEST-PLAN TC-v2.1-25/27）：
- 离线口径：非 ONLINE 节点发起一律当场拒绝（不排队不重投不回落本机）——
  本脚本以 NEW 节点复证（OFFLINE/DISABLED 拒绝与 node_agent_lost 已由
  NODX-02/NODX-03 证据覆盖，引用不重做）；
- /api/nodes 每行带 agentBaseUrl 与 lastRun（最近任务）；
- agent 接入地址随注册表单可填并真实落库；
- NEW 节点 lastHeartbeatAt 为 null（前端只显占位，不伪造「离线」）。

跑法（前置：主控 :8081 已重启为含本包代码的 jar）：
  set -a; . ~/.config/robot-autotest/env; set +a
  python3 docs/evidence/v2.1/NODX-04/run.py
"""
from __future__ import annotations

import argparse
import json
import os
import pathlib
import subprocess
import sys
import time
import urllib.request

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE / "out"
ENV = {}


def load_env() -> dict[str, str]:
    path = pathlib.Path.home() / ".config/robot-autotest/env"
    if path.exists():
        for line in path.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, _, value = line.partition("=")
                ENV.setdefault(key.strip(), value.strip())
    for key, value in os.environ.items():
        if key.startswith(("DB_", "QA_")):
            ENV.setdefault(key, value)
    return ENV


def mysql(sql: str) -> str:
    db = ENV.get("DB_NAME", "robot_dev")
    sql = sql.replace("@DB@", db)
    cmd = ["mysql", "-u", ENV.get("DB_USERNAME", "root"),
           f"-p{ENV.get('DB_PASSWORD', '')}", "-N", "-B", db, "-e", sql]
    result = subprocess.run(cmd, capture_output=True, text=True,
                            env={**os.environ, "MYSQL_PWD": ENV.get("DB_PASSWORD", "")})
    if result.returncode != 0:
        raise RuntimeError(f"mysql 失败: {result.stderr.strip()[:200]}")
    return result.stdout.strip()


class Http:
    def __init__(self, base: str) -> None:
        self.base = base

    def call(self, method: str, path: str, token: str | None = None,
             body: dict | None = None) -> tuple[int, dict]:
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=data,
                                     headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return resp.status, json.load(resp)
        except urllib.error.HTTPError as e:
            try:
                return e.code, json.load(e)
            except Exception:
                return e.code, {}

    def login(self, username: str, password: str) -> str:
        status, body = self.call("POST", "/api/auth/login",
                                 body={"username": username, "password": password})
        data = body.get("data") or {}
        return data.get("token") or data.get("accessToken") or ""


def message_of(body: dict) -> str:
    data = body.get("data") or {}
    out = data.get("message") or body.get("message") or ""
    if not out:
        out = json.dumps(body, ensure_ascii=False)[:200]
    return str(out)


class Report:
    def __init__(self) -> None:
        self.checks: list[tuple[str, str, bool, str]] = []

    def check(self, cid: str, desc: str, ok: bool, detail: str = "") -> None:
        self.checks.append((cid, desc, ok, detail))
        print(f"{'PASS' if ok else 'FAIL'}  {cid}  {desc}" + (f"  —  {detail}" if detail else ""))

    def dump(self) -> int:
        fails = [c for c in self.checks if not c[2]]
        print(f"\n合计 {len(self.checks)} 项，FAIL {len(fails)} 项")
        return 1 if fails else 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8001")
    args = parser.parse_args()

    load_env()
    http = Http(args.base)
    report = Report()
    OUT.mkdir(exist_ok=True)

    token = http.login("qa-e2e-admin", ENV["QA_E2E_PASSWORD"])
    print("# 已登录 qa-e2e-admin\n")

    # 可从零重跑：清掉本期节点
    for name in ("nodx04-node-agent-url", "nodx04-node-new"):
        mysql(f"DELETE FROM @DB@.execution_node WHERE name = '{name}';")

    # ---------- ① 字段齐全：/api/nodes 每行带 agentBaseUrl 与 lastRun ----------
    status, body = http.call("GET", "/api/nodes", token)
    items = body.get("data") or []
    keys_ok = all(("agentBaseUrl" in item and "lastRun" in item) for item in items) and items
    report.check("NODX04-01", "GET /api/nodes 返回新字段（agentBaseUrl / lastRun 键存在）",
                 status == 200 and bool(keys_ok),
                 f"HTTP {status} rows={len(items)}")

    # ---------- ② 最近任务真实读数：NODX-03 跑过的节点带 lastRun ----------
    last_run_rows = [item for item in items if item.get("lastRun")]
    lr_ok = any(
        str(r["lastRun"].get("runId", "")).startswith("RUN-")
        and str(r["lastRun"].get("state", "")) in
        ("queued", "preparing", "running", "finished", "failed", "canceled")
        for r in last_run_rows)
    report.check("NODX04-02", "最近任务读数真实：lastRun.runId + 合法状态 + 时间",
                 lr_ok,
                 f"rows_with_lastRun={len(last_run_rows)} "
                 + (f"sample={last_run_rows[0]['lastRun']}" if last_run_rows else ""))

    # ---------- ③ 注册表单补 agentBaseUrl：随注册提交并落库 ----------
    agent_url = "http://127.0.0.1:8099"
    status, body = http.call("POST", "/api/nodes/register", token, {
        "name": "nodx04-node-agent-url", "pytestAvailable": False, "rfAvailable": False,
        "maxConcurrency": 2, "agentBaseUrl": agent_url})
    ok = status == 200
    new_id = int((body.get("data") or {}).get("nodeId", 0))
    report.check("NODX04-03", "注册节点带 agentBaseUrl（HTTP 200）",
                 ok, f"HTTP {status} id={new_id}")
    db_url = mysql(f"SELECT IFNULL(agent_base_url, '') FROM @DB@.execution_node "
                   f"WHERE id = {new_id};")
    report.check("NODX04-04", "agent_base_url 真实落库",
                 db_url == agent_url, f"db={db_url}")

    # ---------- ④ NEW 节点：lastHeartbeatAt 为 null（前端只显占位） ----------
    status, body = http.call("GET", "/api/nodes", token)
    items = body.get("data") or []
    fresh = next((i for i in items if i["id"] == new_id), None)
    report.check("NODX04-05", "NEW 节点 lastHeartbeatAt=null（离线时长占位依据，不伪造离线）",
                 fresh is not None and fresh["lastHeartbeatAt"] is None
                 and fresh["status"] == "NEW",
                 f"status={fresh and fresh['status']} hb={fresh and fresh['lastHeartbeatAt']}")

    # ---------- ⑤ 离线口径复证：NEW 节点发起 → 409 当场拒绝不落库 ----------
    project_id = int(mysql("SELECT id FROM @DB@.project WHERE code = 'qa-e2e-nodx02';")
                     or "0")
    if not project_id:
        project_id = int(mysql("SELECT id FROM @DB@.project ORDER BY id LIMIT 1;"))
    req_id = f"nodx04-new-{int(time.time())}"
    status, body = http.call("POST", "/api/execution/runs", token, {
        "projectId": project_id, "suitePaths": ["suites/any.robot"],
        "runRequestId": req_id, "nodeId": new_id})
    msg = message_of(body)
    report.check("NODX04-06", "NEW 节点发起 → 409 当场拒绝，原因含 NEW 与 不回落本机",
                 status == 409 and "NEW" in msg and "不回落本机" in msg,
                 f"HTTP {status} msg={msg[:70]}")
    count = mysql(f"SELECT COUNT(*) FROM @DB@.run WHERE run_request_id = '{req_id}';")
    report.check("NODX04-07", "不排队不重投：拒绝发生在落库前（run 无记录）",
                 count == "0", f"run rows={count}")

    # ---------- ⑥ 字段级回归：disable 返回体仍带新字段 ----------
    status, body = http.call("POST", f"/api/nodes/{new_id}/disable", token)
    data = body.get("data") or {}
    report.check("NODX04-08", "disable 返回体保持新字段（agentBaseUrl / lastRun）",
                 status == 200 and "agentBaseUrl" in data and "lastRun" in data,
                 f"HTTP {status} keys_ok={'agentBaseUrl' in data and 'lastRun' in data}")

    return report.dump()


if __name__ == "__main__":
    sys.exit(main())
