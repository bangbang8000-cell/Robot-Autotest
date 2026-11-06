#!/usr/bin/env python3
"""ORCH-01 真实 HTTP 验收：计划组 CRUD、链式建边、幂等、成环拒绝、跨项目 403。

为什么不用单测代替
------------------
`PlanGroupServiceTest` 用的是内存伪仓储，它证明不了三件事：
  ① V9_1 的两张表在真实 MySQL 上真的建成了、外键与唯一键真的生效；
  ② 边真的落进了 `execution_plan_dependency`（而不是只改了内存对象）；
  ③ 权限链路（登录 → 令牌 → `bindProject` → 跨项目拒绝）真的拦得住。

跑法
----
    python3 docs/evidence/v2.1/ORCH-01/run.py [--base http://127.0.0.1:8001]

前置：后端已用**含本包代码**的 jar 启动；`~/.config/robot-autotest/env` 里有
`QA_E2E_PASSWORD`（夹具口令，不入库）。退出码 0 = 全部断言通过。
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import pathlib
import subprocess
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[4]
PROJECT_CODE = "qa-e2e-fixture"
PLAN_NAMES = ["orch01-chain-a", "orch01-chain-b", "orch01-chain-c"]
CRON = "0 0 3 * * *"


# ---------------------------------------------------------------- 环境与库
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
           "-P", ENV.get("DB_PORT", "3306"),
           "-N", "-B", "-e", sql.replace("@DB@", db)]
    result = subprocess.run(cmd, env=dict(os.environ, MYSQL_PWD=ENV.get("DB_PASSWORD", "")),
                            capture_output=True, text=True)
    if result.returncode != 0:
        raise SystemExit(f"SQL 失败: {sql[:90]} :: {result.stderr.strip()[:300]}")
    return result.stdout.strip()


def pbkdf2_hash(raw: str) -> str:
    """与 PasswordHasher.hash 同格式；口令只在本机 env 里，不写字面量。"""
    salt = os.urandom(16)
    derived = hashlib.pbkdf2_hmac("sha256", raw.encode("utf-8"), salt, 120_000, 32)
    return (f"pbkdf2$sha256$120000${base64.b64encode(salt).decode()}"
            f"${base64.b64encode(derived).decode()}")


def seed(cfg: dict[str, str]) -> int:
    """夹具项目 + admin 账号；幂等。返回 projectId。"""
    password = ENV.get("QA_E2E_PASSWORD") or os.environ.get("QA_E2E_PASSWORD")
    if not password:
        raise SystemExit("缺少 QA_E2E_PASSWORD（在 ~/.config/robot-autotest/env 里）")

    mysql("INSERT INTO @DB@.project (code, name, description, status, visibility) "
          f"VALUES ('{PROJECT_CODE}', '验收夹具·HTTP 全链路', "
          "'ORCH-01 验收夹具，勿手工改', 1, 'private') "
          "ON DUPLICATE KEY UPDATE name = VALUES(name);")
    project_id = int(mysql(
        f"SELECT id FROM @DB@.project WHERE code = '{PROJECT_CODE}';"))

    mysql("INSERT INTO @DB@.app_user (username, display_name, password_hash, status) "
          f"VALUES ('qa-e2e-admin', 'ORCH-01 admin', '{pbkdf2_hash(password)}', 1) "
          "ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), status = 1, "
          "failed_login_count = 0, locked_until = NULL, password_expires_at = NULL;")
    user_id = int(mysql(
        "SELECT id FROM @DB@.app_user WHERE username = 'qa-e2e-admin';"))
    mysql("INSERT INTO @DB@.project_member (project_id, user_id, member_role) "
          f"VALUES ({project_id}, {user_id}, 'ADMIN') "
          "ON DUPLICATE KEY UPDATE member_role = VALUES(member_role);")
    mysql("INSERT INTO @DB@.user_role (user_id, role_id) "
          f"SELECT {user_id}, id FROM @DB@.role WHERE code = 'admin' "
          f"AND NOT EXISTS (SELECT 1 FROM @DB@.user_role ur WHERE ur.user_id = {user_id} "
          "AND ur.role_id = @DB@.role.id);")
    return project_id


# ---------------------------------------------------------------- HTTP
class Http:
    def __init__(self, base: str) -> None:
        self.base = base.rstrip("/")

    def call(self, method: str, path: str, token: str | None = None,
             body: dict | None = None) -> tuple[int, dict]:
        headers = {"Accept": "application/json"}
        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(self.base + path, data=data,
                                         headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=30) as resp:
                return resp.status, json.loads(resp.read().decode("utf-8", "replace") or "{}")
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8", "replace")
            try:
                return exc.code, json.loads(raw or "{}")
            except json.JSONDecodeError:
                return exc.code, {"raw": raw}
        except urllib.error.URLError as exc:
            return 0, {"transportError": str(exc.reason)}

    def login(self, username: str, password: str) -> str:
        status, body = self.call("POST", "/api/auth/login",
                                 body={"username": username, "password": password})
        if status != 200:
            raise SystemExit(f"登录失败 HTTP {status}: {body}")
        data = body.get("data") or body
        token = data.get("token") or data.get("accessToken")
        if not token:
            raise SystemExit(f"登录响应里没有 token: {body}")
        return token


def data_of(body: dict):
    return body.get("data", body)


def message_of(body: dict) -> str:
    return str(body.get("message") or body.get("error") or "")


# ---------------------------------------------------------------- 断言
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


# ---------------------------------------------------------------- 主流程
def ensure_plan(http: Http, token: str, project_id: int, name: str) -> int:
    """按名称找计划，没有就建一个。返回 planId。"""
    status, body = http.call("GET", f"/api/plans?projectId={project_id}&page=1&size=100", token)
    if status != 200:
        raise SystemExit(f"查询计划失败 HTTP {status}: {body}")
    for plan in data_of(body).get("items", []):
        if plan.get("name") == name:
            return int(plan["id"])
    status, body = http.call("POST", "/api/plans", token, {
        "projectId": project_id,
        "name": name,
        "suitePaths": ["fixtures/samples/v2.1/robot/01_pass.robot"],
        "concurrency": 1,
        "timeoutSeconds": 600,
        "cron": CRON,
        "timezone": "Asia/Shanghai",
        "overlapPolicy": "SKIP",
        "dependencyIds": [],
    })
    if status not in (200, 201):
        raise SystemExit(f"建计划 {name} 失败 HTTP {status}: {body}")
    return int(data_of(body)["id"])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8001")
    args = parser.parse_args()

    http = Http(args.base)
    report = Report()
    project_id = seed(ENV)
    token = http.login("qa-e2e-admin", ENV["QA_E2E_PASSWORD"])
    print(f"# 夹具项目 projectId={project_id}，已登录 qa-e2e-admin\n")

    # ---------- 准备三条计划 ----------
    plan_ids = [ensure_plan(http, token, project_id, name) for name in PLAN_NAMES]
    print(f"# 计划 id = {plan_ids}\n")

    # 清掉上一次运行留下的组与边，保证可从零重跑
    mysql(f"DELETE FROM @DB@.plan_group WHERE project_id = {project_id};")
    mysql(f"DELETE FROM @DB@.execution_plan_dependency WHERE project_id = {project_id} "
          f"AND plan_id IN ({','.join(str(i) for i in plan_ids)});")

    # ---------- ① 建组 ----------
    status, body = http.call("POST", "/api/plan-groups", token,
                             {"projectId": project_id, "name": "orch01-chain",
                              "description": "ORCH-01 验收"})
    report.check("ORCH01-01", "建组返回 200", status == 200, f"HTTP {status}")
    group_id = int(data_of(body)["id"])

    # ---------- ② 加 3 个成员 ----------
    status, body = http.call("POST", f"/api/plan-groups/{group_id}/members", token,
                             {"projectId": project_id, "planIds": plan_ids})
    group = data_of(body)
    report.check("ORCH01-02", "加入 3 个成员后 items=3，edgeCount=2", status == 200
                 and len(group.get("items", [])) == 3 and group.get("edgeCount") == 2,
                 f"HTTP {status} items={len(group.get('items', []))} "
                 f"edgeCount={group.get('edgeCount')}")

    # ---------- ③ 重复加入幂等 ----------
    status, body = http.call("POST", f"/api/plan-groups/{group_id}/members", token,
                             {"projectId": project_id, "planIds": plan_ids})
    group = data_of(body)
    report.check("ORCH01-03", "重复加入不报错、成员数仍为 3", status == 200
                 and len(group.get("items", [])) == 3,
                 f"HTTP {status} items={len(group.get('items', []))}")

    # ---------- ④ 应用链式依赖：3 个成员 → 2 条边 ----------
    status, body = http.call("POST", f"/api/plan-groups/{group_id}/apply"
                             f"?projectId={project_id}", token)
    result = data_of(body)
    edges_db = mysql(
        "SELECT COUNT(*) FROM @DB@.execution_plan_dependency "
        f"WHERE project_id = {project_id} AND plan_id IN "
        f"({','.join(str(i) for i in plan_ids)});")
    report.check("ORCH01-04", "首次应用 created=2 且库内边数=2", status == 200
                 and result.get("created") == 2 and result.get("skipped") == 0
                 and edges_db == "2",
                 f"HTTP {status} created={result.get('created')} "
                 f"skipped={result.get('skipped')} 库内边数={edges_db}")

    # ---------- ⑤ 二次应用幂等 ----------
    status, body = http.call("POST", f"/api/plan-groups/{group_id}/apply"
                             f"?projectId={project_id}", token)
    result = data_of(body)
    edges_db = mysql(
        "SELECT COUNT(*) FROM @DB@.execution_plan_dependency "
        f"WHERE project_id = {project_id} AND plan_id IN "
        f"({','.join(str(i) for i in plan_ids)});")
    report.check("ORCH01-05", "二次应用 created=0 skipped=2 且边数不变", status == 200
                 and result.get("created") == 0 and result.get("skipped") == 2
                 and edges_db == "2",
                 f"HTTP {status} created={result.get('created')} "
                 f"skipped={result.get('skipped')} 库内边数={edges_db}")

    # ---------- ⑥ 成环拒绝（组内顺序与既有边相反） ----------
    mysql("DELETE FROM @DB@.execution_plan_dependency "
          f"WHERE project_id = {project_id} AND plan_id IN "
          f"({','.join(str(i) for i in plan_ids)});")
    # 既有边：第一个计划依赖第二个（与链式顺序相反）
    mysql("INSERT INTO @DB@.execution_plan_dependency "
          "(project_id, plan_id, depends_on_plan_id) "
          f"VALUES ({project_id}, {plan_ids[0]}, {plan_ids[1]});")
    status, body = http.call("POST", f"/api/plan-groups/{group_id}/apply"
                             f"?projectId={project_id}", token)
    message = message_of(body)
    report.check("ORCH01-06", "成环返回 400 且消息带计划名", status == 400
                 and PLAN_NAMES[0] in message and PLAN_NAMES[1] in message,
                 f"HTTP {status} message={message[:90]}")

    # ---------- ⑦ 跨项目引用返回 403 ----------
    other_project = mysql("SELECT id FROM @DB@.project "
                          f"WHERE code <> '{PROJECT_CODE}' ORDER BY id LIMIT 1;")
    other_plan = mysql("SELECT id FROM @DB@.execution_plan "
                       f"WHERE project_id = {other_project} ORDER BY id LIMIT 1;") \
        if other_project else ""
    if other_plan:
        status, body = http.call("POST", f"/api/plan-groups/{group_id}/members", token,
                                 {"projectId": project_id, "planIds": [int(other_plan)]})
        report.check("ORCH01-07", "跨项目计划返回 403", status == 403,
                     f"HTTP {status} message={message_of(body)[:60]}")
    else:
        print("# 跳过 ORCH01-07：库里没有第二个项目的计划")

    # ---------- ⑧ 删组不删边 ----------
    mysql("DELETE FROM @DB@.execution_plan_dependency "
          f"WHERE project_id = {project_id} AND plan_id IN "
          f"({','.join(str(i) for i in plan_ids)});")
    http.call("POST", f"/api/plan-groups/{group_id}/apply?projectId={project_id}", token)
    status, _ = http.call("DELETE", f"/api/plan-groups/{group_id}?projectId={project_id}", token)
    edges_after = mysql(
        "SELECT COUNT(*) FROM @DB@.execution_plan_dependency "
        f"WHERE project_id = {project_id} AND plan_id IN "
        f"({','.join(str(i) for i in plan_ids)});")
    groups_after = mysql(
        f"SELECT COUNT(*) FROM @DB@.plan_group WHERE project_id = {project_id};")
    report.check("ORCH01-08", "删组后组不存在但边保留（2 条）", status == 200
                 and groups_after == "0" and edges_after == "2",
                 f"HTTP {status} 组数={groups_after} 边数={edges_after}")

    return report.dump()


if __name__ == "__main__":
    raise SystemExit(main())
