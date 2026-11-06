#!/usr/bin/env python3
"""NODX-02 真实 HTTP 验收：指定节点执行 + 离线拒绝 + node_id 落库。

为什么不用单测代替
------------------
`RunDispatchNodeTest` 用内存仓储与桩派发器，它证明不了三件事：
  ① 拒绝链路是真实 HTTP 409/404 带明确原因文案（CI 端点 → RunService 落库前校验）；
  ② run.node_id 真实写进 MySQL（`UPDATE run ... WHERE project_id = ?` 过租户守卫）；
  ③ 指定节点的 run **不落本机 pabot**——执行线程真实走 executeOnNode，
     判 FAILED 且 failure_reason = node_channel_unavailable（NODX-03 实装投递前的诚实占位）。

跑法
----
    python3 docs/evidence/v2.1/NODX-02/run.py [--base http://127.0.0.1:8001]

前置：后端已用含本包代码的 jar 启动。退出码 0 = 全部断言通过。
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import pathlib
import subprocess
import time
import urllib.error
import urllib.request

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE / "out"
PROJECT_CODE = "qa-e2e-fixture"


def load_env() -> dict[str, str]:
    cfg: dict[str, str] = {}
    path = pathlib.Path.home() / ".config" / "robot-autotest" / "env"
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        cfg[key.strip()] = value.strip().strip('"').strip("'")
    return cfg


ENV = load_env()


def mysql(sql: str) -> str:
    db = ENV.get("DB_NAME") or "robot_dev"
    cmd = ["mysql", "-u", ENV.get("DB_USERNAME", "root"),
           "-h", ENV.get("DB_HOST", "127.0.0.1"),
           "-P", str(ENV.get("DB_PORT", "3306")),
           "-N", "-B", "-e", sql.replace("@DB@", db)]
    result = subprocess.run(cmd, env=dict(os.environ, MYSQL_PWD=ENV.get("DB_PASSWORD", "")),
                            capture_output=True, text=True)
    if result.returncode != 0:
        raise SystemExit(f"SQL 失败: {sql[:90]} :: {result.stderr.strip()[:300]}")
    return result.stdout.strip()


def pbkdf2_hash(raw: str) -> str:
    salt = os.urandom(16)
    derived = hashlib.pbkdf2_hmac("sha256", raw.encode("utf-8"), salt, 120_000, 32)
    return (f"pbkdf2$sha256$120000${base64.b64encode(salt).decode()}"
            f"${base64.b64encode(derived).decode()}")


def seed() -> int:
    password = ENV.get("QA_E2E_PASSWORD") or os.environ.get("QA_E2E_PASSWORD")
    if not password:
        raise SystemExit("缺少 QA_E2E_PASSWORD")
    mysql("INSERT INTO @DB@.project (code, name, description, status, visibility) "
          f"VALUES ('{PROJECT_CODE}', 'NODX-02 fixture', 'e2e', 1, 'PRIVATE') "
          "ON DUPLICATE KEY UPDATE name = VALUES(name);")
    project_id = int(mysql(f"SELECT id FROM @DB@.project WHERE code = '{PROJECT_CODE}';"))
    mysql("INSERT INTO @DB@.app_user (username, display_name, password_hash, status) "
          f"VALUES ('qa-e2e-admin', 'NODX-02 admin', '{pbkdf2_hash(password)}', 1) "
          "ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), status = 1, "
          "failed_login_count = 0, locked_until = NULL, password_expires_at = NULL;")
    user_id = int(mysql("SELECT id FROM @DB@.app_user WHERE username = 'qa-e2e-admin';"))
    mysql("INSERT INTO @DB@.user_role (user_id, role_id) "
          f"SELECT {user_id}, id FROM @DB@.role WHERE code = 'admin' "
          f"AND NOT EXISTS (SELECT 1 FROM @DB@.user_role ur WHERE ur.user_id = {user_id} "
          "AND ur.role_id = @DB@.role.id);")
    return project_id


class Http:
    def __init__(self, base: str) -> None:
        self.base = base.rstrip("/")

    def call(self, method: str, path: str, token: str | None = None,
             body: dict | None = None, headers: dict | None = None) -> tuple[int, dict]:
        all_headers = {"Accept": "*/*"}
        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            all_headers["Content-Type"] = "application/json"
        if token:
            all_headers["Authorization"] = "Bearer " + token
        if headers:
            all_headers.update(headers)
        request = urllib.request.Request(self.base + path, data=data,
                                         headers=all_headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=60) as resp:
                return resp.status, json.loads(resp.read().decode("utf-8", "replace") or "{}")
        except urllib.error.HTTPError as exc:
            return exc.code, json.loads(exc.read().decode("utf-8", "replace") or "{}")
        except urllib.error.URLError as exc:
            raise SystemExit(f"连不上 {self.base}：{exc.reason}")

    def login(self, username: str, password: str) -> str:
        status, body = self.call("POST", "/api/auth/login",
                                 body={"username": username, "password": password})
        if status != 200:
            raise SystemExit(f"登录失败 HTTP {status}: {body}")
        data = body.get("data") or body
        return data.get("token") or data.get("accessToken")


def message_of(body: dict) -> str:
    return str(body.get("message") or body.get("error") or "")


class Report:
    def __init__(self) -> None:
        self.rows: list[tuple[str, str, bool, str]] = []

    def check(self, cid: str, desc: str, ok: bool, detail: str = "") -> None:
        self.rows.append((cid, desc, ok, detail))

    def dump(self) -> int:
        width = max(len(row[1]) for row in self.rows) + 2
        for cid, desc, ok, detail in self.rows:
            print(f"{'PASS' if ok else 'FAIL'}  {cid:<12}{desc:<{width}}{detail}")
        failed = [row for row in self.rows if not row[2]]
        print(f"\n合计 {len(self.rows)} 项，FAIL {len(failed)} 项")
        return 0 if not failed else 1


def register_and_online(http: Http, token: str, name: str, concurrency: int) -> tuple[int, str]:
    """注册 + 心跳，返回 (node_id, 明文令牌)。"""
    status, body = http.call("POST", "/api/nodes/register", token, {
        "name": name, "rfAvailable": True, "pytestAvailable": False,
        "maxConcurrency": concurrency})
    data = body.get("data") or {}
    node_id = int(data.get("nodeId", 0))
    plain = str(data.get("token", ""))
    if status != 200 or node_id <= 0:
        raise SystemExit(f"注册失败 HTTP {status}: {body}")
    status, _ = http.call("POST", f"/api/nodes/{node_id}/heartbeat", None,
                          {"rfAvailable": True, "pytestAvailable": False},
                          headers={"X-Node-Token": plain})
    if status != 200:
        raise SystemExit(f"心跳失败 HTTP {status}")
    return node_id, plain


def wait_failed(token_http: Http, token: str, project_id: int, run_id: str,
                seconds: int = 20) -> tuple[str, str]:
    """轮询 run 到 FAILED（执行线程异步判失败），返回 (failureReason, failureMessage)。"""
    deadline = time.time() + seconds
    last = ("", "")
    while time.time() < deadline:
        status, body = token_http.call(
            "GET", f"/api/execution/runs/{run_id}?projectId={project_id}", token)
        data = body.get("data") or {}
        state = str(data.get("state", ""))
        last = (str(data.get("failureReason") or ""),
                str(data.get("failureMessage") or ""))
        if state == "FAILED":
            return last
        time.sleep(1)
    return (f"timeout", f"state={last!r}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8001")
    args = parser.parse_args()

    http = Http(args.base)
    report = Report()
    OUT.mkdir(exist_ok=True)

    project_id = seed()
    token = http.login("qa-e2e-admin", ENV["QA_E2E_PASSWORD"])
    print("# 已登录 qa-e2e-admin\n")

    # 可从零重跑：清掉旧的同名节点与本期 run
    mysql("DELETE FROM @DB@.run WHERE run_id LIKE 'RUN-NODX02%';")
    for name in ("nodx02-node-online", "nodx02-node-new", "nodx02-node-new2"):
        mysql(f"DELETE FROM @DB@.execution_node WHERE name = '{name}';")

    # ---------- ① 正：ONLINE 节点发起，run.node_id 落库，执行体不落本机 ----------
    online_id, _online_token = register_and_online(http, token, "nodx02-node-online", 1)

    req_id = f"nodx02-{int(time.time())}"
    status, body = http.call("POST", "/api/execution/runs", token, {
        "projectId": project_id, "suitePaths": ["suites/any.robot"],
        "runRequestId": req_id, "nodeId": online_id})
    data = body.get("data") or {}
    run_id = str(data.get("runId", ""))
    report.check("NODX02-01", "ONLINE 节点发起 200：run 创建成功",
                 status == 200 and run_id.startswith("RUN-"),
                 f"HTTP {status} runId={run_id}")

    db_node = mysql(f"SELECT IFNULL(node_id, -1) FROM @DB@.run WHERE run_id = '{run_id}';")
    report.check("NODX02-02", "run.node_id = 指定节点（真实写库，租户守卫通过）",
                 db_node == str(online_id),
                 f"node_id={db_node} 期望={online_id}")

    audit = mysql("SELECT detail FROM @DB@.audit_log WHERE action = 'RUN_CREATE' "
                  f"AND resource_id = '{run_id}' ORDER BY id DESC LIMIT 1;")
    report.check("NODX02-03", "RUN_CREATE 审计含 node=（派发去向可追溯）",
                 f"node={online_id}" in audit,
                 f"detail={audit[:60]}")

    reason, message = wait_failed(http, token, project_id, run_id)
    report.check("NODX02-04",
                 "执行体不落本机：run FAILED，原因码 node_channel_unavailable + 文案不回落本机",
                 "node_channel_unavailable" in reason and "不回落本机" in message,
                 f"reason={reason} msg={message[:60]}")

    # ---------- ② 负：NEW（未心跳）节点拒绝 ----------
    new_id, _ = register_and_online(http, token, "nodx02-node-new", 1)
    # 注册并心跳后是 ONLINE；这里再注册一个只注册不心跳的节点
    status, body = http.call("POST", "/api/nodes/register", token, {
        "name": "nodx02-node-new", "pytestAvailable": False, "rfAvailable": False,
        "maxConcurrency": 1})
    report.check("NODX02-05", "NEW 节点准备：重名注册被拒（NODX-01 语义保持）",
                 status == 409,
                 f"HTTP {status}")
    # 直接 SQL 制造一个 NEW 节点（注册接口因重名不可复用）
    mysql("INSERT INTO @DB@.execution_node (name, token_hash, status, rf_available, "
          "pytest_available, max_concurrency) VALUES ('nodx02-node-new2', "
          f"'{'a' * 64}', 'NEW', 0, 0, 1);")
    new_node_id = int(mysql("SELECT id FROM @DB@.execution_node "
                            "WHERE name = 'nodx02-node-new2';"))

    req2 = f"nodx02-new-{int(time.time())}"
    status, body = http.call("POST", "/api/execution/runs", token, {
        "projectId": project_id, "suitePaths": ["suites/any.robot"],
        "runRequestId": req2, "nodeId": new_node_id})
    msg = message_of(body)
    report.check("NODX02-06", "NEW 节点发起 → 409，原因含 NEW 与 不回落本机",
                 status == 409 and "NEW" in msg and "不回落本机" in msg,
                 f"HTTP {status} msg={msg[:60]}")
    count = mysql(f"SELECT COUNT(*) FROM @DB@.run WHERE run_request_id = '{req2}';")
    report.check("NODX02-07", "拒绝发生在落库前：run_request_id 无对应 run",
                 count == "0", f"run rows={count}")

    # ---------- ③ 负：OFFLINE 节点拒绝 ----------
    mysql(f"UPDATE @DB@.execution_node SET status = 'OFFLINE' WHERE id = {online_id};")
    req3 = f"nodx02-off-{int(time.time())}"
    status, body = http.call("POST", "/api/execution/runs", token, {
        "projectId": project_id, "suitePaths": ["suites/any.robot"],
        "runRequestId": req3, "nodeId": online_id})
    msg = message_of(body)
    report.check("NODX02-08", "OFFLINE 节点发起 → 409，原因含 OFFLINE 与 不回落本机",
                 status == 409 and "OFFLINE" in msg and "不回落本机" in msg,
                 f"HTTP {status} msg={msg[:70]}")

    # ---------- ④ 负：DISABLED 节点拒绝 ----------
    disable_status, _ = http.call("POST", f"/api/nodes/{online_id}/disable", token)
    status, body = http.call("POST", "/api/execution/runs", token, {
        "projectId": project_id, "suitePaths": ["suites/any.robot"],
        "runRequestId": f"nodx02-dis-{int(time.time())}", "nodeId": online_id})
    msg = message_of(body)
    report.check("NODX02-09",
                 "DISABLED 节点发起 → 409，原因含 DISABLED（不回落本机）",
                 disable_status == 200 and status == 409 and "DISABLED" in msg,
                 f"disable={disable_status} HTTP {status} msg={msg[:50]}")

    # ---------- ⑤ 负：节点不存在 ----------
    status, body = http.call("POST", "/api/execution/runs", token, {
        "projectId": project_id, "suitePaths": ["suites/any.robot"],
        "runRequestId": f"nodx02-404-{int(time.time())}", "nodeId": 99999})
    report.check("NODX02-10", "节点不存在 → 404（执行节点不存在: 99999）",
                 status == 404 and "99999" in message_of(body),
                 f"HTTP {status} msg={message_of(body)[:40]}")

    # ---------- ⑥ 正：未指定节点走本机（node_id NULL） ----------
    req_local = f"nodx02-local-{int(time.time())}"
    status, body = http.call("POST", "/api/execution/runs", token, {
        "projectId": project_id, "suitePaths": ["suites/any.robot"],
        "runRequestId": req_local})
    data = body.get("data") or {}
    local_run = str(data.get("runId", ""))
    db_node = mysql(f"SELECT IFNULL(node_id, -1) FROM @DB@.run WHERE run_id = '{local_run}';")
    report.check("NODX02-11", "未指定 nodeId → 本机：run 落库且 node_id IS NULL",
                 status == 200 and db_node == "-1",
                 f"HTTP {status} runId={local_run} node_id={db_node}")

    # ---------- ⑦ 节点列表带占用读数 ----------
    status, body = http.call("GET", "/api/nodes", token)
    views = body.get("data") or []
    fields_ok = bool(views) and all(
        "runningRuns" in node and "maxConcurrency" in node for node in views)
    (OUT / "nodes-view.json").write_text(json.dumps(views, ensure_ascii=False, indent=2),
                                         encoding="utf-8")
    report.check("NODX02-12", "节点列表每行带 runningRuns 占用读数（NODX-02 补齐）",
                 status == 200 and fields_ok,
                 f"HTTP {status} nodes={len(views)}")

    return report.dump()


if __name__ == "__main__":
    raise SystemExit(main())
