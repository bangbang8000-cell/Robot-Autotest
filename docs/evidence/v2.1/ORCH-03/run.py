#!/usr/bin/env python3
"""ORCH-03 真实 HTTP 验收：失败聚类的两级结构、归一化、确定性、重跑拒绝。

为什么不用单测代替
------------------
`FailureClusterServiceTest` 用的是内存仓储，证明不了：
  ① 用例级失败文本真的来自 `test_result.failure_msg`（run 表上的 failure_class 是整轮结论，
     不是每条用例的——这一点 PRD 原文写错过，见证据 §4）；
  ② JOIN run / test_case 在真实数据上不漏行（verdict 大小写、case_id 为 NULL 的行）；
  ③ 同一 runId 两次查询**逐字节一致**（确定性是 FR-v2.1-04 的硬判据）。

跑法
----
    python3 docs/evidence/v2.1/ORCH-03/run.py [--base http://127.0.0.1:8001]

前置：后端已用含本包代码的 jar 启动；env 里有 `QA_E2E_PASSWORD`。
夹具 run 落在 `qa-e2e-fixture` 项目内，每次运行前清理同名 run，可反复跑。
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

PROJECT_CODE = "qa-e2e-fixture"
RUN_ID = "RUN-ORCH03-FIXTURE"

FAILURES = [
    ("ORCH03-CASE-1", "FAIL", "/a/b/x.py:12\nAssertionError: 3 != 5"),
    ("ORCH03-CASE-2", "FAIL", "/c/d/y.py:98\nAssertionError: 3 != 7"),
    ("ORCH03-CASE-3", "FAIL", "Connection refused to 10.0.0.1:22"),
]
PASSED = ("ORCH03-CASE-4", "PASS", None)


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
          "'ORCH-03 验收夹具，勿手工改', 1, 'private') "
          "ON DUPLICATE KEY UPDATE name = VALUES(name);")
    project_id = int(mysql(f"SELECT id FROM @DB@.project WHERE code = '{PROJECT_CODE}';"))

    mysql("INSERT INTO @DB@.app_user (username, display_name, password_hash, status) "
          f"VALUES ('qa-e2e-admin', 'ORCH-03 admin', '{pbkdf2_hash(password)}', 1) "
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

    # 清掉同名的旧 run（含 test_result 级联），保证从零重跑
    old = mysql(f"SELECT id FROM @DB@.run WHERE project_id = {project_id} "
                f"AND run_id = '{RUN_ID}';")
    if old:
        mysql(f"DELETE FROM @DB@.test_result WHERE run_id = {old};")
        mysql(f"DELETE FROM @DB@.run WHERE id = {old};")

    mysql("INSERT INTO @DB@.run (run_id, project_id, trigger_type, run_status, "
          "started_at, finished_at, operator_name) "
          f"VALUES ('{RUN_ID}', {project_id}, 'manual', 'failed', NOW(3), NOW(3), 'orch03');")
    run_pk = int(mysql(f"SELECT id FROM @DB@.run WHERE project_id = {project_id} "
                       f"AND run_id = '{RUN_ID}';"))

    for code, verdict, message in [*FAILURES, PASSED]:
        # case_id 留 NULL：真实的导入行也可能没有关联台账，正好覆盖「caseId 为空」的分支
        escaped = "NULL" if message is None else "'" + message.replace("'", "''") + "'"
        mysql("INSERT INTO @DB@.test_result (run_id, case_id, round_no, verdict, "
              "duration_ms, failure_msg) "
              f"VALUES ({run_pk}, NULL, 1, '{verdict}', 120, {escaped});")
    return project_id


class Http:
    def __init__(self, base: str) -> None:
        self.base = base.rstrip("/")

    def call(self, method: str, path: str, token: str | None = None,
             body: dict | None = None) -> tuple[int, str]:
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
                return resp.status, resp.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as exc:
            return exc.code, exc.read().decode("utf-8", "replace")
        except urllib.error.URLError as exc:
            return 0, f'{{"transportError":"{exc.reason}"}}'

    def login(self, username: str, password: str) -> str:
        status, raw = self.call("POST", "/api/auth/login",
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
    print(f"# 夹具项目 projectId={project_id}，runId={RUN_ID}\n")

    status, first_raw = http.call("GET", f"/api/failure-clusters"
                                         f"?projectId={project_id}&runId={RUN_ID}", token)
    first = json.loads(first_raw)["data"] if status == 200 else {}

    report.check("ORCH03-01", "查询返回 200", status == 200, f"HTTP {status}")
    report.check("ORCH03-02", "总失败数=3（PASS 行不计）", first.get("total") == 3,
                 f"total={first.get('total')}")

    classes = {group["failureClass"]: group for group in first.get("classes", [])}
    report.check("ORCH03-03", "分成 ASSERT 与 ENV 两类",
                 set(classes) == {"ASSERT", "ENV"}, f"classes={sorted(classes)}")

    assert_clusters = classes.get("ASSERT", {}).get("clusters", [])
    env_clusters = classes.get("ENV", {}).get("clusters", [])
    report.check("ORCH03-04", "路径与数字不同的两条断言失败归为 1 簇",
                 len(assert_clusters) == 1 and assert_clusters[0]["count"] == 2,
                 f"簇数={len(assert_clusters)} "
                 f"键={assert_clusters[0]['key'] if assert_clusters else '-'}")
    report.check("ORCH03-05", "环境失败单独成簇（不混进断言簇）",
                 len(env_clusters) == 1 and env_clusters[0]["count"] == 1,
                 f"簇数={len(env_clusters)} "
                 f"键={env_clusters[0]['key'] if env_clusters else '-'}")

    status2, second_raw = http.call("GET", f"/api/failure-clusters"
                                          f"?projectId={project_id}&runId={RUN_ID}", token)
    same = status2 == 200 and json.dumps(json.loads(first_raw), sort_keys=True) == \
        json.dumps(json.loads(second_raw), sort_keys=True)
    report.check("ORCH03-06", "同一 runId 两次查询逐字节一致", same, "diff 为空" if same else "两次结果不同")

    status3, raw3 = http.call("POST", "/api/failure-clusters/rerun", token,
                              {"projectId": project_id, "runId": RUN_ID,
                               "clusterKey": "no-such-cluster"})
    message = ""
    if status3 != 200:
        try:
            message = json.loads(raw3).get("message", "")
        except json.JSONDecodeError:
            message = raw3[:60]
    report.check("ORCH03-07", "不存在的簇重跑返回 404", status3 == 404,
                 f"HTTP {status3} message={message[:50]}")

    # case_id 全为 NULL 时，簇的 caseIds 为空 → 重跑必须被拒（不是发一个空 run）
    if assert_clusters:
        status4, raw4 = http.call("POST", "/api/failure-clusters/rerun", token,
                                  {"projectId": project_id, "runId": RUN_ID,
                                   "clusterKey": assert_clusters[0]["key"]})
        report.check("ORCH03-08", "簇内无用例时重跑返回 400", status4 == 400,
                     f"HTTP {status4} raw={raw4[:70]}")

    return report.dump()


if __name__ == "__main__":
    raise SystemExit(main())
