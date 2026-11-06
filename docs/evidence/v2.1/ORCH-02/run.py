#!/usr/bin/env python3
"""ORCH-02 真实 HTTP 验收：依赖图组装、分层、关键路径、孤立点、首屏预算。

为什么不用单测代替
------------------
`PlanGraphTest` 用的是内存仓储，证明不了：
  ① `GET /api/plan-graph` 在真实数据上把 52 个计划与 50 条边组装完整（JOIN 与分页拉取不漏）；
  ② 关键路径在真实库的超时秒数上算对（单测给的是构造权重，不是库里的值）；
  ③ 接口本身在 50+ 节点时的耗时（判据「首屏 ≤ 1s」里后端占的那部分）。

跑法
----
    python3 docs/evidence/v2.1/ORCH-02/run.py [--base http://127.0.0.1:8001]

夹具：`qa-e2e-orch02` 项目内 52 条计划（`orcho2-p01..p52`），每次运行前清空重建。
**独立项目**：图接口返回项目内**全部**计划，与 ORCH-01 的夹具混在同一项目会让节点数
不可断言（首轮实跑就撞上：期望 52 实得 55）。
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

PROJECT_CODE = "qa-e2e-orch02"
PREFIX = "orcho2-p"
CHAIN = 50               # p01..p50 链式依赖
TIMEOUT_CHAIN = 60       # 链上每条 60 秒
TAIL_TIMEOUT = 1200      # p51 依赖 p50，1200 秒
EXPECTED_NODES = CHAIN + 2
EXPECTED_EDGES = CHAIN   # 49 条链 + 1 条 p50->p51
# 链上 50 个节点各 60 秒 = 3000，再加尾节点 1200 = 4200
EXPECTED_CRITICAL_SECONDS = CHAIN * TIMEOUT_CHAIN + TAIL_TIMEOUT
BUDGET_MS = 1000


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
    # 必须带 -D：多表 DELETE（DELETE d FROM ... JOIN ...）要求有默认库，
    # 只写 db.table 前缀会报 "No database selected"。
    cmd = ["mysql", "-u", ENV.get("DB_USERNAME", "root"),
           "-h", ENV.get("DB_HOST", "127.0.0.1"),
           "-P", ENV.get("DB_PORT", "3306"),
           "-D", db, "-N", "-B", "-e", sql.replace("@DB@", db)]
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
          f"VALUES ('{PROJECT_CODE}', '验收夹具·HTTP 全链路', "
          "'ORCH-02 验收夹具，勿手工改', 1, 'private') "
          "ON DUPLICATE KEY UPDATE name = VALUES(name);")
    project_id = int(mysql(f"SELECT id FROM @DB@.project WHERE code = '{PROJECT_CODE}';"))

    mysql("INSERT INTO @DB@.app_user (username, display_name, password_hash, status) "
          f"VALUES ('qa-e2e-admin', 'ORCH-02 admin', '{pbkdf2_hash(password)}', 1) "
          "ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), status = 1, "
          "failed_login_count = 0, locked_until = NULL, password_expires_at = NULL;")
    user_id = int(mysql("SELECT id FROM @DB@.app_user WHERE username = 'qa-e2e-admin';"))
    mysql("INSERT INTO @DB@.project_member (project_id, user_id, member_role) "
          f"VALUES ({project_id}, {user_id}, 'ADMIN') "
          "ON DUPLICATE KEY UPDATE member_role = VALUES(member_role);")
    mysql("INSERT INTO @DB@.user_role (user_id, role_id) "
          f"SELECT {user_id}, id FROM @DB@.role WHERE code = 'admin' "
          f"AND NOT EXISTS (SELECT 1 FROM @DB@.user_role ur WHERE ur.user_id = {user_id} "
          "AND ur.role_id = @DB@.role.id);")

    # 清空本夹具的计划（先删边，再删计划；外键会挡住反序）
    mysql("DELETE d FROM @DB@.execution_plan_dependency d "
          "JOIN @DB@.execution_plan p ON p.id = d.plan_id "
          f"WHERE p.project_id = {project_id} AND p.name LIKE '{PREFIX}%';")
    mysql("DELETE FROM @DB@.execution_plan "
          f"WHERE project_id = {project_id} AND name LIKE '{PREFIX}%';")

    for index in range(1, CHAIN + 1):
        mysql("INSERT INTO @DB@.execution_plan (project_id, name, suite_paths_json, "
              "concurrency, timeout_seconds, cron, timezone, status) "
              f"VALUES ({project_id}, '{PREFIX}{index:02d}', "
              f"'[\"fixtures/samples/v2.1/robot/01_pass.robot\"]', 1, {TIMEOUT_CHAIN}, "
              "'0 0 3 * * *', 'Asia/Shanghai', 'enabled');")
    # 尾节点：依赖 p50，超时 1200 秒，让关键路径明显长于链本身
    mysql("INSERT INTO @DB@.execution_plan (project_id, name, suite_paths_json, "
          "concurrency, timeout_seconds, cron, timezone, status) "
          f"VALUES ({project_id}, '{PREFIX}51', "
          "'[\"fixtures/samples/v2.1/robot/01_pass.robot\"]', 1, "
          f"{TAIL_TIMEOUT}, '0 0 3 * * *', 'Asia/Shanghai', 'enabled');")
    # 孤立点：无前置也无后置
    mysql("INSERT INTO @DB@.execution_plan (project_id, name, suite_paths_json, "
          "concurrency, timeout_seconds, cron, timezone, status) "
          f"VALUES ({project_id}, '{PREFIX}52', "
          "'[\"fixtures/samples/v2.1/robot/01_pass.robot\"]', 1, 60, "
          "'0 0 3 * * *', 'Asia/Shanghai', 'enabled');")

    ids = {}
    rows = mysql("SELECT id, name FROM @DB@.execution_plan "
                 f"WHERE project_id = {project_id} AND name LIKE '{PREFIX}%';")
    for line in rows.splitlines():
        plan_id, name = line.split("\t")
        ids[name] = int(plan_id)

    # 链式边：p(i) 依赖 p(i-1)；再让 p51 依赖 p50
    for index in range(2, CHAIN + 1):
        mysql("INSERT INTO @DB@.execution_plan_dependency "
              "(project_id, plan_id, depends_on_plan_id) "
              f"VALUES ({project_id}, {ids[f'{PREFIX}{index:02d}']}, "
              f"{ids[f'{PREFIX}{index - 1:02d}']});")
    mysql("INSERT INTO @DB@.execution_plan_dependency (project_id, plan_id, depends_on_plan_id) "
          f"VALUES ({project_id}, {ids[f'{PREFIX}51']}, {ids[f'{PREFIX}{CHAIN:02d}']});")
    return project_id


class Http:
    def __init__(self, base: str) -> None:
        self.base = base.rstrip("/")

    def call(self, method: str, path: str, token: str | None = None,
             body: dict | None = None) -> tuple[int, str, float]:
        headers = {"Accept": "application/json"}
        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(self.base + path, data=data,
                                         headers=headers, method=method)
        start = time.perf_counter()
        try:
            with urllib.request.urlopen(request, timeout=60) as resp:
                raw = resp.read().decode("utf-8", "replace")
                return resp.status, raw, (time.perf_counter() - start) * 1000
        except urllib.error.HTTPError as exc:
            return exc.code, exc.read().decode("utf-8", "replace"), \
                (time.perf_counter() - start) * 1000
        except urllib.error.URLError as exc:
            return 0, f'{{"transportError":"{exc.reason}"}}', 0.0

    def login(self, username: str, password: str) -> str:
        status, raw, _ = self.call("POST", "/api/auth/login",
                                   body={"username": username, "password": password})
        if status != 200:
            raise SystemExit(f"登录失败 HTTP {status}: {raw}")
        data = json.loads(raw).get("data") or json.loads(raw)
        token = data.get("token") or data.get("accessToken")
        if not token:
            raise SystemExit(f"登录响应里没有 token: {raw}")
        return token


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
    project_id = seed()
    token = http.login("qa-e2e-admin", ENV["QA_E2E_PASSWORD"])
    print(f"# 夹具项目 projectId={project_id}，{EXPECTED_NODES} 个计划 / {EXPECTED_EDGES} 条边\n")

    status, raw, elapsed_ms = http.call(
        "GET", f"/api/plan-graph?projectId={project_id}", token)
    view = json.loads(raw)["data"] if status == 200 else {}

    report.check("ORCH02-01", "接口返回 200", status == 200, f"HTTP {status}")
    report.check("ORCH02-02", f"节点数={EXPECTED_NODES}", len(view.get("nodes", [])) == EXPECTED_NODES,
                 f"nodes={len(view.get('nodes', []))}")
    report.check("ORCH02-03", f"边数={EXPECTED_EDGES}", len(view.get("edges", [])) == EXPECTED_EDGES,
                 f"edges={len(view.get('edges', []))}")

    names = {node["id"]: node["name"] for node in view.get("nodes", [])}
    path = view.get("criticalPath", [])
    expected_path_names = [f"{PREFIX}{i:02d}" for i in range(1, CHAIN + 1)] + [f"{PREFIX}51"]
    actual_path_names = [names.get(plan_id, f"#{plan_id}") for plan_id in path]
    report.check("ORCH02-04", "关键路径与手算一致（p01→…→p50→p51）",
                 actual_path_names == expected_path_names,
                 f"长度={len(path)} 起={actual_path_names[:2]} 止={actual_path_names[-2:]}")
    report.check("ORCH02-05", f"关键路径总时长={EXPECTED_CRITICAL_SECONDS}s",
                 view.get("criticalSeconds") == EXPECTED_CRITICAL_SECONDS,
                 f"criticalSeconds={view.get('criticalSeconds')} "
                 f"(手算 {CHAIN}×{TIMEOUT_CHAIN}+{TAIL_TIMEOUT})")

    isolated = [node["name"] for node in view.get("nodes", []) if node["isolated"]]
    report.check("ORCH02-06", "孤立点只有 p52", isolated == [f"{PREFIX}52"], f"isolated={isolated}")

    layers = {node["name"]: node["layer"] for node in view.get("nodes", [])}
    layer_ok = layers.get(f"{PREFIX}01") == 0 and layers.get(f"{PREFIX}50") == 49 \
        and layers.get(f"{PREFIX}51") == 50 and layers.get(f"{PREFIX}52") == 0
    report.check("ORCH02-07", "分层正确（p01=0 / p50=49 / p51=50 / p52=0）", layer_ok,
                 f"p01={layers.get(f'{PREFIX}01')} p50={layers.get(f'{PREFIX}50')} "
                 f"p51={layers.get(f'{PREFIX}51')} p52={layers.get(f'{PREFIX}52')}")

    report.check("ORCH02-08", f"接口耗时 ≤ {BUDGET_MS}ms（首屏预算的后端部分）",
                 elapsed_ms <= BUDGET_MS, f"{elapsed_ms:.0f} ms")

    # 同参数两次请求结果一致（含顺序），保证首屏与刷新看到同一张图
    _, raw2, _ = http.call("GET", f"/api/plan-graph?projectId={project_id}", token)
    same = json.dumps(json.loads(raw), sort_keys=True) == json.dumps(json.loads(raw2), sort_keys=True)
    report.check("ORCH02-09", "两次请求结果逐字节一致", same, "diff 为空" if same else "不一致")

    return report.dump()


if __name__ == "__main__":
    raise SystemExit(main())
