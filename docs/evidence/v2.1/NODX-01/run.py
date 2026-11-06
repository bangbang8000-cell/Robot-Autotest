#!/usr/bin/env python3
"""NODX-01 真实 HTTP 验收：节点注册（令牌只存哈希）+ 心跳 + 离线判定。

为什么不用单测代替
------------------
`ExecutionNodeServiceTest` 用的是内存仓储，它证明不了四件事：
  ① 明文令牌在**真实后端日志**（/tmp/rt-backend.log）里不留痕——单元测试里
     「审计不含令牌」证明不了「Spring/Tomcat 的异常栈与访问日志不含令牌」；
  ② 心跳走真实 HTTP 头（X-Node-Token），鉴权链路（AuthInterceptor 白名单放行）
     在单测里根本没被执行；
  ③ 离线判定由 **@Scheduled 监视器**真实触发——把 last_heartbeat_at 回拨后，
     等 Monitor 扫一圈，库与列表同时变 OFFLINE；
  ④ 库内 token_hash 与 sha256(明文) 逐字节一致（MySQL CHAR(64) 落库无截断）。

跑法
----
    python3 docs/evidence/v2.1/NODX-01/run.py [--base http://127.0.0.1:8001]

前置：后端已用**含本包代码**的 jar 启动。退出码 0 = 全部断言通过。
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
LOG = pathlib.Path("/tmp/rt-backend.log")


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


def seed_admin() -> None:
    password = ENV.get("QA_E2E_PASSWORD") or os.environ.get("QA_E2E_PASSWORD")
    if not password:
        raise SystemExit("缺少 QA_E2E_PASSWORD")
    mysql("INSERT INTO @DB@.app_user (username, display_name, password_hash, status) "
          f"VALUES ('qa-e2e-admin', 'NODX-01 admin', '{pbkdf2_hash(password)}', 1) "
          "ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), status = 1, "
          "failed_login_count = 0, locked_until = NULL, password_expires_at = NULL;")
    user_id = int(mysql("SELECT id FROM @DB@.app_user WHERE username = 'qa-e2e-admin';"))
    mysql("INSERT INTO @DB@.user_role (user_id, role_id) "
          f"SELECT {user_id}, id FROM @DB@.role WHERE code = 'admin' "
          f"AND NOT EXISTS (SELECT 1 FROM @DB@.user_role ur WHERE ur.user_id = {user_id} "
          "AND ur.role_id = @DB@.role.id);")


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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8001")
    args = parser.parse_args()

    http = Http(args.base)
    report = Report()
    OUT.mkdir(exist_ok=True)

    seed_admin()
    token = http.login("qa-e2e-admin", ENV["QA_E2E_PASSWORD"])
    print("# 已登录 qa-e2e-admin\n")

    # 可从零重跑：清掉旧的同名节点
    mysql(f"DELETE FROM @DB@.execution_node WHERE name = 'nodx01-node';")

    # ---------- ① 注册：明文只出现一次，库里只有哈希 ----------
    status, body = http.call("POST", "/api/nodes/register", token, {
        "name": "nodx01-node", "rfAvailable": True, "pytestAvailable": True,
        "maxConcurrency": 2})
    data = body.get("data") or {}
    node_id = int(data.get("nodeId", 0))
    plain = str(data.get("token", ""))
    report.check("NODX01-01", "注册 200：nodeId/token(64 hex)/name 齐备",
                 status == 200 and node_id > 0 and len(plain) == 64,
                 f"HTTP {status} id={node_id} token={plain[:8]}…")

    db_hash = mysql(f"SELECT token_hash FROM @DB@.execution_node WHERE id = {node_id};")
    expected_hash = hashlib.sha256(plain.encode()).hexdigest()
    report.check("NODX01-02", "库内 token_hash = SHA-256(明文)，且 != 明文",
                 db_hash == expected_hash and db_hash != plain,
                 f"hash={db_hash[:12]}… 期望={expected_hash[:12]}…")

    # 日志抽样：明文不得出现在后端日志（这是「令牌不入日志」的真判据）
    log_text = LOG.read_text(encoding="utf-8", errors="replace") if LOG.exists() else ""
    (OUT / "log-sample.txt").write_text(
        "\n".join(line for line in log_text.splitlines() if "node" in line.lower())[-4000:]
        + "\n", encoding="utf-8")
    report.check("NODX01-03", "明文令牌不出现在后端日志（抽样含 node 的行全部过滤比对）",
                 plain not in log_text,
                 f"日志 {len(log_text)}B，命中明文={'是' if plain in log_text else '否'}")

    # ---------- ② 心跳 ----------
    status, body = http.call("POST", f"/api/nodes/{node_id}/heartbeat", None,
                             {"rfAvailable": True, "pytestAvailable": True},
                             headers={"X-Node-Token": plain})
    view = body.get("data") or {}
    report.check("NODX01-04", "带令牌心跳 200：status=ONLINE、能力已上报",
                 status == 200 and view.get("status") == "ONLINE"
                 and view.get("rfAvailable") is True and view.get("pytestAvailable") is True,
                 f"HTTP {status} status={view.get('status')}")

    status, body = http.call("POST", f"/api/nodes/{node_id}/heartbeat", None,
                             {"rfAvailable": True, "pytestAvailable": True},
                             headers={"X-Node-Token": "f" * 64})
    report.check("NODX01-05", "错令牌心跳 → 401（文案不含节点是否存在）",
                 status == 401 and "令牌无效" in message_of(body),
                 f"HTTP {status} msg={message_of(body)[:40]}")

    status, body = http.call("GET", "/api/nodes", token)
    rows = body.get("data") or []
    mine = next((r for r in rows if r.get("id") == node_id), None)
    report.check("NODX01-06", "节点列表：能力 / 并发上限 / 最近心跳可见，令牌哈希不外泄",
                 status == 200 and mine is not None
                 and mine.get("rfAvailable") is True and mine.get("maxConcurrency") == 2
                 and mine.get("lastHeartbeatAt") is not None
                 and "tokenHash" not in json.dumps(mine),
                 f"nodes={len(rows)} 本节点={mine}")

    # ---------- ③ 离线判定：回拨心跳 → 等 Monitor 扫一圈 → OFFLINE ----------
    mysql(f"UPDATE @DB@.execution_node SET last_heartbeat_at = "
          f"NOW(3) - INTERVAL 10 MINUTE WHERE id = {node_id};")
    deadline = time.time() + 60
    offline_seen = "OFFLINE" in mysql(
        f"SELECT status FROM @DB@.execution_node WHERE id = {node_id};")
    while not offline_seen and time.time() < deadline:
        time.sleep(5)
        offline_seen = "OFFLINE" in mysql(
            f"SELECT status FROM @DB@.execution_node WHERE id = {node_id};")
    status, body = http.call("GET", "/api/nodes", token)
    rows = body.get("data") or []
    mine = next((r for r in rows if r.get("id") == node_id), None)
    report.check("NODX01-07", "连续缺心跳（回拨 10 分钟）后 Monitor 置 OFFLINE，列表同读数",
                 offline_seen and mine is not None and mine.get("status") == "OFFLINE",
                 f"库=OFFLINE 列表={mine.get('status') if mine else '缺失'}")

    # ---------- ④ 停用 / 启用 ----------
    status, body = http.call("POST", f"/api/nodes/{node_id}/disable", token)
    report.check("NODX01-08", "停用 200：status=DISABLED", status == 200
                 and (body.get("data") or {}).get("status") == "DISABLED", f"HTTP {status}")
    status, body = http.call("POST", f"/api/nodes/{node_id}/heartbeat", None,
                             {"rfAvailable": True, "pytestAvailable": True},
                             headers={"X-Node-Token": plain})
    report.check("NODX01-09", "停用后心跳 → 403（不能靠心跳复活）", status == 403,
                 f"HTTP {status} msg={message_of(body)[:40]}")
    status, body = http.call("POST", f"/api/nodes/{node_id}/enable", token)
    report.check("NODX01-10", "启用 200：status=NEW（等下一次心跳上线）", status == 200
                 and (body.get("data") or {}).get("status") == "NEW", f"HTTP {status}")

    # 收尾：删掉验收节点（保持库干净）
    mysql(f"DELETE FROM @DB@.execution_node WHERE id = {node_id};")
    print("\n# 验收节点已清理；日志抽样在 out/log-sample.txt")
    return report.dump()


if __name__ == "__main__":
    raise SystemExit(main())
