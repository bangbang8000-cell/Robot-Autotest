#!/usr/bin/env python3
"""NODX-03 端到端验收：本机 :8082 起节点 agent，真实跑 1 条示例用例 + 崩溃注入。

为什么不用单测代替
------------------
`NodeAgentRunnerTest` 用桩执行器，证明不了四件事：
  ① 同参数命令 diff 为空——主控参照命令与节点实际命令经
     tools/kernel_cmd_diff.py 归一化对比，两个真实进程各自生成；
  ② 产物完整回传——output.xml 经真实 HTTP 从节点回到主控并解析入库
     （test_result 行与 metadata 门禁同链路）；
  ③ agent 崩溃不留悬挂 run——kill 节点进程后心跳停，主控监视器按
     连续 3 周期缺席判 OFFLINE，同时把该节点上的 run 判 FAILED（node_agent_lost）；
  ④ 同 jar 双角色真能跑——--spring.profiles.active=node 的进程独立受理任务。

跑法（前置：主控 :8081 已重启为含本包代码的 jar）
--------------------------------------------------
    python3 docs/evidence/v2.1/NODX-03/run.py [--base http://127.0.0.1:8001]

退出码 0 = 全部断言通过。脚本结束会清理两个节点进程。
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import pathlib
import signal
import subprocess
import time
import urllib.error
import urllib.request

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE / "out"
JAR = pathlib.Path.home() / "robot-autotest/backend/target/robot-autotest-backend-0.1.0-SNAPSHOT.jar"
PROJECT_CODE = "qa-e2e-fixture"

DEMO_ROBOT = """*** Settings ***
Documentation     NODX-03 端到端示例（本机合成用例，ADR-0013 禁区外）
Metadata          CASE_ID      NODX03-001
Metadata          RUN_ID       e2e-node-demo
Metadata          QA_PROJECT   qa-e2e-fixture
Metadata          DUT          LOCAL-HOST
Metadata          LINK         LOCAL
Metadata          SW_BUILD     v2.1-e2e

*** Test Cases ***
NODX03 示例通过
    [Documentation]  节点真实执行的最小 RF 用例
    Log    ok
"""

DEMO_SLOW_ROBOT = """*** Settings ***
Documentation     NODX-03 崩溃注入专用（长任务，让 run 稳定停在 RUNNING）
Metadata          CASE_ID      NODX03-901
Metadata          RUN_ID       e2e-node-crash
Metadata          QA_PROJECT   qa-e2e-fixture
Metadata          DUT          LOCAL-HOST
Metadata          LINK         LOCAL
Metadata          SW_BUILD     v2.1-e2e

*** Test Cases ***
NODX03 长任务
    Sleep    180s
"""


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
          f"VALUES ('{PROJECT_CODE}', 'NODX-03 fixture', 'e2e', 1, 'PRIVATE') "
          "ON DUPLICATE KEY UPDATE name = VALUES(name);")
    project_id = int(mysql(f"SELECT id FROM @DB@.project WHERE code = '{PROJECT_CODE}';"))
    mysql("INSERT INTO @DB@.app_user (username, display_name, password_hash, status) "
          f"VALUES ('qa-e2e-admin', 'NODX-03 admin', '{pbkdf2_hash(password)}', 1) "
          "ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), status = 1, "
          "failed_login_count = 0, locked_until = NULL, password_expires_at = NULL;")
    user_id = int(mysql("SELECT id FROM @DB@.app_user WHERE username = 'qa-e2e-admin';"))
    mysql("INSERT INTO @DB@.user_role (user_id, role_id) "
          f"SELECT {user_id}, id FROM @DB@.role WHERE code = 'admin' "
          f"AND NOT EXISTS (SELECT 1 FROM @DB@.user_role ur WHERE ur.user_id = {user_id} "
          "AND ur.role_id = @DB@.role.id);")
    # 示例用例走 DB robot_file（EXEC-05：脚本真源在数据库，materialize 只落库内脚本；
    # 写磁盘 workspace-root 不参与落盘——首轮端到端实测踩过这个坑）
    suite_escaped = DEMO_ROBOT.replace("\\", "\\\\").replace("'", "''")
    mysql("INSERT INTO @DB@.robot_file (project_id, path, content, current_version) "
          f"VALUES ({project_id}, 'suites/e2e-nodx03/demo.robot', '{suite_escaped}', 1) "
          "ON DUPLICATE KEY UPDATE content = VALUES(content), current_version = 1;")
    slow_escaped = (DEMO_SLOW_ROBOT.replace("\\", "\\\\")
                    .replace("'", "''"))
    mysql("INSERT INTO @DB@.robot_file (project_id, path, content, current_version) "
          f"VALUES ({project_id}, 'suites/e2e-nodx03/demo-slow.robot', '{slow_escaped}', 1) "
          "ON DUPLICATE KEY UPDATE content = VALUES(content), current_version = 1;")
    return project_id


class Http:
    def __init__(self, base: str) -> None:
        self.base = base.rstrip("/")

    def call(self, method: str, path: str, token: str | None = None,
             body: dict | None = None, headers: dict | None = None,
             raw: str | None = None) -> tuple[int, dict]:
        all_headers = {"Accept": "*/*"}
        data = None
        if raw is not None:
            data = raw.encode("utf-8")
            all_headers["Content-Type"] = "application/json"
        elif body is not None:
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
        print(f"{'PASS' if ok else 'FAIL'}  {cid:<12}{desc[:52]:<54}{detail}")

    def dump(self) -> int:
        failed = [row for row in self.rows if not row[2]]
        print(f"\n合计 {len(self.rows)} 项，FAIL {len(failed)} 项")
        return 0 if not failed else 1


def start_node(port: int, node_id: int, token: str, log_path: pathlib.Path) -> subprocess.Popen:
    env = dict(os.environ)
    env.update({k: v for k, v in ENV.items() if k.startswith(("DB_", "CRED_"))})
    env.update({"NODE_ID": str(node_id), "NODE_TOKEN": token,
                "NODE_MASTER_URL": "http://127.0.0.1:8081", "NODE_PORT": str(port)})
    log = log_path.open("w")
    proc = subprocess.Popen(
        ["java", "-jar", str(JAR), "--spring.profiles.active=node",
         "--server.port=" + str(port)],
        env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    return proc


def wait_node_online(http: Http, token: str, node_id: int, seconds: int = 60) -> bool:
    deadline = time.time() + seconds
    while time.time() < deadline:
        status, body = http.call("GET", "/api/nodes", token)
        for view in (body.get("data") or []):
            if view.get("id") == node_id and view.get("status") == "ONLINE":
                return True
        time.sleep(2)
    return False


def poll_run(http: Http, token: str, project_id: int, run_id: str, seconds: int,
             states: set[str]) -> dict | None:
    deadline = time.time() + seconds
    last = {}
    while time.time() < deadline:
        status, body = http.call(
            "GET", f"/api/execution/runs/{run_id}?projectId={project_id}", token)
        last = body.get("data") or {}
        if str(last.get("state", "")) in states:
            return last
        time.sleep(2)
    return {"state": f"timeout({last.get('state')!r})",
            "failureReason": str(last.get("failureReason") or "")}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8001")
    parser.add_argument("--skip-crash", action="store_true",
                        help="只跑正常链路（崩溃注入约需 3 分钟）")
    args = parser.parse_args()

    http = Http(args.base)
    report = Report()
    OUT.mkdir(exist_ok=True)

    project_id = seed()
    token = http.login("qa-e2e-admin", ENV["QA_E2E_PASSWORD"])
    print("# 已登录 qa-e2e-admin\n")

    home = pathlib.Path.home()
    mysql("DELETE FROM @DB@.run WHERE run_id LIKE 'RUN-NODX03%';")
    for name in ("nodx03-node", "nodx03-node-crash"):
        mysql(f"DELETE FROM @DB@.execution_node WHERE name = '{name}';")

    # 示例套件（本机合成用例 = ADR-0013 禁区外）
    suite_dir = home / "robot-test/infra-monitor/suites/e2e-nodx03"
    suite_dir.mkdir(parents=True, exist_ok=True)
    suite_file = suite_dir / "demo.robot"
    suite_file.write_text(DEMO_ROBOT, encoding="utf-8")

    procs: list[subprocess.Popen] = []
    try:
        # ---------- ① 正常链路：:8082 节点真实执行 ----------
        status, body = http.call("POST", "/api/nodes/register", token, {
            "name": "nodx03-node", "rfAvailable": True, "pytestAvailable": False,
            "maxConcurrency": 1, "agentBaseUrl": "http://127.0.0.1:8082"})
        data = body.get("data") or {}
        node_id = int(data.get("nodeId", 0))
        node_token = str(data.get("token", ""))
        report.check("NODX03-01", "注册节点并登记 agent 接入地址（agentBaseUrl 落库）",
                     status == 200 and node_id > 0,
                     f"HTTP {status} id={node_id}")
        db_url = mysql(f"SELECT IFNULL(agent_base_url, 'NULL') FROM @DB@.execution_node "
                       f"WHERE id = {node_id};")
        report.check("NODX03-02", "V9_5 迁移：agent_base_url 真实落库",
                     db_url == "http://127.0.0.1:8082", f"agent_base_url={db_url}")

        node = start_node(8082, node_id, node_token, OUT / "node-8082.log")
        procs.append(node)
        online = wait_node_online(http, token, node_id)
        report.check("NODX03-03", "agent 心跳保活：节点置 ONLINE（同 jar node profile）",
                     online, f"waited={'yes' if online else 'timeout'}")

        status, body = http.call("POST", "/api/execution/runs", token, {
            "projectId": project_id, "suitePaths": ["suites/e2e-nodx03/demo.robot"],
            "runRequestId": f"nodx03-e2e-{int(time.time())}", "nodeId": node_id})
        data = body.get("data") or {}
        run_id = str(data.get("runId", ""))
        report.check("NODX03-04", "指定节点发起 200", status == 200 and run_id.startswith("RUN-"),
                     f"HTTP {status} runId={run_id}")

        final = poll_run(http, token, project_id, run_id, 240,
                         {"FINISHED", "FAILED"})
        report.check("NODX03-05", "节点真实执行 1 条示例用例 → run FINISHED",
                     final.get("state") == "FINISHED",
                     f"state={final.get('state')} reason={final.get('failureReason')}")

        tr_count = mysql(f"SELECT COUNT(*) FROM @DB@.test_result tr "
                         f"JOIN @DB@.run r ON tr.run_id = r.id "
                         f"WHERE r.run_id = '{run_id}';")
        report.check("NODX03-06", "回传后主控解析入库：test_result 行存在（同链路门禁）",
                     tr_count >= "1", f"test_result rows={tr_count}")

        run_dir = home / "robot-test/artifacts" / run_id
        master_json = run_dir / "node-cmd-master.json"
        agent_json = run_dir / "node-cmd-agent.json"
        ok_files = master_json.exists() and agent_json.exists()
        if ok_files:
            diff = subprocess.run(
                ["python3", str(pathlib.Path.home() / "robot-autotest/tools/kernel_cmd_diff.py"),
                 str(master_json), str(agent_json)],
                capture_output=True, text=True)
            report.check("NODX03-07", "同参数命令 diff 为空（kernel_cmd_diff.py）",
                         diff.returncode == 0, (diff.stdout + diff.stderr).strip()[:80])
        else:
            report.check("NODX03-07", "同参数命令 diff 为空（kernel_cmd_diff.py）",
                         False, f"master={master_json.exists()} agent={agent_json.exists()}")

        xml = run_dir / "shard-0" / "output.xml"
        report.check("NODX03-08", "产物完整回传：output.xml 已落主控产物目录",
                     xml.exists() and xml.stat().st_size > 100,
                     f"size={xml.stat().st_size if xml.exists() else 0}")
        (OUT / "artifacts-list.txt").write_text(
            "\n".join(str(p.relative_to(run_dir)) for p in run_dir.rglob("*")
                      if p.is_file()) + "\n", encoding="utf-8")

        # ---------- ② 崩溃注入：kill 节点 → 心停 → 监视器判失败 ----------
        if not args.skip_crash:
            status, body = http.call("POST", "/api/nodes/register", token, {
                "name": "nodx03-node-crash", "rfAvailable": True, "pytestAvailable": False,
                "maxConcurrency": 1, "agentBaseUrl": "http://127.0.0.1:8083"})
            data = body.get("data") or {}
            crash_id = int(data.get("nodeId", 0))
            crash_token = str(data.get("token", ""))
            crash = start_node(8083, crash_id, crash_token, OUT / "node-8083.log")
            procs.append(crash)
            online = wait_node_online(http, token, crash_id)
            report.check("NODX03-09", "崩溃注入准备：第二节点 ONLINE",
                         online, f"id={crash_id}")

            # 等 agent HTTP 层真正可服务（Tomcat started 后仍可能拒连接——
            # 首轮端到端实测 stale keep-alive / 启动尾部竞态会吞掉首笔投递）
            agent_ready = False
            for _ in range(15):
                try:
                    with urllib.request.urlopen(
                            urllib.request.Request(
                                "http://127.0.0.1:8083/api/agent/run",
                                data=b"{}", method="POST",
                                headers={"Content-Type": "application/json"}),
                            timeout=5) as probe:
                        agent_ready = True
                        break
                except urllib.error.HTTPError:
                    agent_ready = True  # 4xx 也证明 HTTP 层在服务
                    break
                except Exception:
                    time.sleep(2)
            report.check("NODX03-PROBE", ":8083 agent HTTP 层可服务", agent_ready, "")

            def launch_crash_run() -> str:
                st, bd = http.call("POST", "/api/execution/runs", token, {
                    "projectId": project_id,
                    "suitePaths": ["suites/e2e-nodx03/demo-slow.robot"],
                    "runRequestId": f"nodx03-crash-{int(time.time())}",
                    "nodeId": crash_id})
                return str((bd.get("data") or {}).get("runId", ""))

            crash_run = launch_crash_run()
            pre = poll_run(http, token, project_id, crash_run, 45,
                           {"PREPARING", "RUNNING", "FAILED"})
            if pre.get("state") == "FAILED" and "node_dispatch_failed" in str(
                    pre.get("failureReason") or ""):
                # 投递失败但节点仍 ONLINE（stale 连接偶发）：清状态重发一次
                time.sleep(2)
                crash_run = launch_crash_run()
                pre = poll_run(http, token, project_id, crash_run, 45,
                               {"PREPARING", "RUNNING", "FAILED"})
            report.check("NODX03-10", "run 已到执行态（PREPARING/RUNNING）后注入崩溃",
                         pre.get("state") in ("PREPARING", "RUNNING"),
                         f"runId={crash_run} state={pre.get('state')} "
                         f"reason={pre.get('failureReason')}")

            import os as _os
            _os.killpg(crash.pid, signal.SIGKILL)  # 连 pabot 子进程一起终止
            procs.remove(crash)
            crash.wait(timeout=15)
            (OUT / "crash-inject.txt").write_text(
                f"killed node pid={crash.pid} at {time.strftime('%F %T')}, "
                f"run={crash_run} state={pre.get('state')}\n", encoding="utf-8")
            # 验证心跳真的停了（last_heartbeat_at 不再更新）
            hb1 = mysql(f"SELECT IFNULL(UNIX_TIMESTAMP(last_heartbeat_at), 0) "
                        f"FROM @DB@.execution_node WHERE id = {crash_id};")
            time.sleep(35)
            hb2 = mysql(f"SELECT IFNULL(UNIX_TIMESTAMP(last_heartbeat_at), 0) "
                        f"FROM @DB@.execution_node WHERE id = {crash_id};")
            heartbeat_dead = hb1 == hb2
            report.check("NODX03-HEARTBEAT", "kill 后心跳停止（last_heartbeat 不再更新）",
                         heartbeat_dead, f"{hb1} -> {hb2}")

            final = poll_run(http, token, project_id, crash_run, 200,
                             {"FAILED", "FINISHED", "CANCELED"})
            reason = str(final.get("failureReason") or "")
            report.check("NODX03-11", "agent 崩溃不留悬挂 run：监视器判 FAILED(node_agent_lost)",
                         final.get("state") == "FAILED" and "node_agent_lost" in reason,
                         f"state={final.get('state')} reason={reason[:60]}")

            db_status = mysql(f"SELECT status FROM @DB@.execution_node WHERE id = {crash_id};")
            report.check("NODX03-12", "崩溃节点被监视器置 OFFLINE",
                         db_status == "OFFLINE", f"status={db_status}")
    finally:
        for proc in procs:
            try:
                proc.kill()
            except Exception:
                pass
    print("\n# 节点进程已清理")
    return report.dump()


if __name__ == "__main__":
    raise SystemExit(main())
