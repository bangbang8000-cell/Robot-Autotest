#!/usr/bin/env python3
"""A7 取证 · 双内核取消/超时进程树（真实进程，禁伪造）。

证据分三段：
  1) RF/pabot 取消：真实平台 API 建 run → 真实 CancelService 杀进程树；
  2) RF/pabot 超时：真实平台 API 建 run（timeoutSeconds=20）→ PabotOrchestrator 超时杀树；
  3) pytest：产品无 run 入口（只读 preview），故用产品 `PytestKernel.command` 的 argv 形状
     + 产品同一个 `ProcessTreeKiller` 类（Java 探针）真实终止 pytest 进程树。
每段都在终止前后各抓一次 `ps -ww -eo` 全表，按 runId / 测试路径做闭包比对，断言无残留。

用法：
    python3 probe.py                 # 全量
    python3 probe.py --only rf       # 只跑 RF 两段
    python3 probe.py --only pytest
环境：~/.config/robot-autotest/env（DB_* / QA_E2E_PASSWORD）
"""
import json
import os
import re
import shutil
import subprocess
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(ROOT, "raw")
BASE = os.environ.get("A7_BASE", "http://127.0.0.1:8001")
ENV_FILE = os.path.expanduser("~/.config/robot-autotest/env")
PROJECT_ID = 29
STAMP = time.strftime("%Y%m%d_%H%M%S")
RUN_TAG = "A7-" + STAMP

E = {}


def load_env():
    global E
    for line in open(ENV_FILE, encoding="utf-8"):
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            E[k.strip()] = v.strip()


def http(method, path, token=None, body=None, timeout=40):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        txt = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(txt)
        except Exception:  # noqa: BLE001
            return e.code, txt[:800]


def db(sql):
    env = dict(os.environ)
    env["MYSQL_PWD"] = E["DB_PASSWORD"]
    cmd = ["mysql", "-h", E["DB_HOST"], "-P", E["DB_PORT"], "-u", E["DB_USERNAME"],
           "-N", "-B", "-e", sql, E["DB_NAME"]]
    p = subprocess.run(cmd, capture_output=True, text=True, env=env, timeout=30)
    if p.returncode != 0:
        raise RuntimeError("SQL 失败: " + p.stderr.strip()[:300])
    return [line.split("\t") for line in p.stdout.strip().splitlines() if line]


def db1(sql, default=""):
    rows = db(sql)
    return rows[0][0] if rows else default


def write_raw(name, obj):
    path = os.path.join(RAW, name)
    with open(path, "w", encoding="utf-8") as f:
        if isinstance(obj, str):
            f.write(obj)
        else:
            json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return path


# ------------------------------------------------------------------ 进程快照
def ps_table():
    out = subprocess.run(["ps", "-ww", "-eo", "pid,ppid,pgid,etimes,stat,args"],
                         capture_output=True, text=True, timeout=20).stdout
    rows, header = [], True
    for line in out.splitlines():
        if header:
            header = False
            continue
        parts = line.split(None, 5)
        if len(parts) < 6:
            continue
        rows.append({"pid": int(parts[0]), "ppid": int(parts[1]), "pgid": int(parts[2]),
                     "etimes": int(parts[3]), "stat": parts[4], "args": parts[5]})
    return rows


def tree_for(rows, markers):
    """按 args 命中任一标记取根，再按 ppid 闭包展开成进程树。"""
    if isinstance(markers, str):
        markers = (markers,)
    by_pid = {r["pid"]: r for r in rows}
    children = {}
    for r in rows:
        children.setdefault(r["ppid"], []).append(r["pid"])
    roots = [r["pid"] for r in rows if any(m and m in r["args"] for m in markers)]
    seen, stack = set(), list(roots)
    while stack:
        pid = stack.pop()
        if pid in seen or pid not in by_pid:
            continue
        seen.add(pid)
        stack.extend(children.get(pid, []))
    return [by_pid[p] for p in sorted(seen)]


def alive(pids):
    out = subprocess.run(["ps", "-ww", "-eo", "pid"], capture_output=True, text=True, timeout=20).stdout
    live = {int(x) for x in out.split()[1:] if x.isdigit()}
    return sorted(set(pids) & live)


def residual_for(rows, pids, markers):
    if isinstance(markers, str):
        markers = (markers,)
    hit_args = [r for r in rows if any(m and m in r["args"] for m in markers)]
    return {"pids_still_alive": alive(pids), "args_still_matching": hit_args}


# ------------------------------------------------------------------ RF 两段
SLOW_B = """*** Settings ***
Documentation     A7 进程树取证专用（长任务，用于取消/超时杀树）
Metadata          CASE_ID      A7-TREE-001
Metadata          RUN_ID       A7-PROC-TREE
Metadata          QA_PROJECT   robot-autotest
Metadata          DUT          local-wsl
Metadata          LINK         platform->pabot->robot
Metadata          SW_BUILD     Ubuntu 24.04 / RF 7.x

*** Test Cases ***
A7 长任务 B
    Log To Console    [heartbeat] slow-b start
    Sleep    180s
"""


def ensure_slow_b(token):
    path = "suites/a7/slow-b.robot"
    status, body = http("PUT", "/api/editor/save", token,
                        {"projectId": PROJECT_ID, "fileId": None, "path": path,
                         "content": SLOW_B, "parseOk": True,
                         "comment": "A7 进程树取证夹具（临时，收尾可删）"})
    row = db("select id, length(content) from robot_file "
             "where project_id=%d and path='%s'" % (PROJECT_ID, path))
    return {"endpoint": "PUT /api/editor/save", "status": status, "path": path,
            "db_row": row[0] if row else None, "response": body if status != 200 else "ok"}


def create_run(token, suite_paths, concurrency, timeout_seconds, tag):
    body = {"projectId": PROJECT_ID, "suitePaths": suite_paths,
            "concurrency": concurrency, "timeoutSeconds": timeout_seconds,
            "runRequestId": tag}
    status, resp = http("POST", "/api/execution/runs", token, body)
    run_id = (resp.get("data") or {}).get("runId") if isinstance(resp, dict) else None
    return status, resp, run_id


def wait_state(run_id, want, seconds):
    deadline, last = time.time() + seconds, "-"
    while time.time() < deadline:
        last = db1("select run_status from run where run_id='%s'" % run_id, "-")
        if last in want:
            return last
        time.sleep(0.5)
    return last


def run_row(run_id):
    rows = db("select run_status, failure_reason, failure_class, failure_message, "
              "artifacts_dir, workspace_dir from run where run_id='%s'" % run_id)
    if not rows:
        return {}
    r = rows[0]
    started = db1("select started_at from run where run_id='%s'" % run_id, "")
    finished = db1("select finished_at from run where run_id='%s'" % run_id, "")
    elapsed = db1("select if(finished_at is null, -1, "
                  "timestampdiff(MICROSECOND, started_at, finished_at)/1000000) "
                  "from run where run_id='%s'" % run_id, "-1")
    return {"run_status": r[0], "failure_reason": r[1], "failure_class": r[2],
            "failure_message": r[3], "artifacts_dir": r[4], "workspace_dir": r[5],
            "started_at": started, "finished_at": finished, "elapsed_s": elapsed}


def run_log(token, run_id, limit=300):
    status, resp = http("GET", "/api/execution/runs/%s/log?projectId=%d&limit=%d"
                        % (run_id, PROJECT_ID, limit), token)
    data = resp.get("data") if isinstance(resp, dict) else None
    if not isinstance(data, list):
        return {"status": status, "lines": []}
    return {"status": status, "lines": data[-limit:]}


def rf_scenario(token, mode, suite_paths, concurrency, timeout_seconds):
    """mode=cancel 或 timeout，返回该段全部读数与判据。"""
    status, resp, run_id = create_run(token, suite_paths, concurrency, timeout_seconds,
                                      "%s-%s" % (RUN_TAG, mode))
    out = {"mode": mode, "create_status": status, "run_id": run_id,
           "request": {"suitePaths": suite_paths, "concurrency": concurrency,
                       "timeoutSeconds": timeout_seconds}}
    if not run_id:
        out["error"] = resp
        return out
    state = wait_state(run_id, {"running"}, 30)
    out["state_when_snapshot"] = state
    row0 = run_row(run_id)
    markers = (run_id, row0.get("workspace_dir"), row0.get("artifacts_dir"))
    out["markers"] = [m for m in markers if m]
    rows = ps_table()
    before = tree_for(rows, markers)
    before_pids = [p["pid"] for p in before]
    out["ps_before"] = {"file": "ps-%s-before.txt" % mode,
                        "tree_size": len(before),
                        "pabot": [p for p in before if "pabot" in p["args"]],
                        "robot_children": [p for p in before if "robot" in p["args"]],
                        "all": before}
    write_raw("ps-%s-before.txt" % mode,
              "\n".join("%d %d %d %d %s %s" % (p["pid"], p["ppid"], p["pgid"],
                                               p["etimes"], p["stat"], p["args"])
                        for p in rows))
    if mode == "cancel":
        cstatus, cresp = http("POST", "/api/execution/runs/%s/cancel?projectId=%d"
                              % (run_id, PROJECT_ID), token, {"reason": "A7 取消取证"})
        out["cancel"] = {"status": cstatus,
                         "state": (cresp.get("data") or {}).get("state")
                         if isinstance(cresp, dict) else None,
                         "message": (cresp.get("data") or {}).get("failureMessage")
                         if isinstance(cresp, dict) else None}
        terminal = wait_state(run_id, {"canceled", "cancelled", "failed"}, 60)
    else:
        terminal = wait_state(run_id, {"failed", "canceled", "cancelled", "finished"}, 60)
    time.sleep(3)
    rows_after = ps_table()
    out["terminal_state"] = terminal
    out["after_shutdown_s"] = 3
    out["ps_after"] = residual_for(rows_after, before_pids, markers)
    write_raw("ps-%s-after.txt" % mode,
              "\n".join("%d %d %d %d %s %s" % (p["pid"], p["ppid"], p["pgid"],
                                               p["etimes"], p["stat"], p["args"])
                        for p in rows_after))
    out["row"] = run_row(run_id)
    log = run_log(token, run_id)
    out["log"] = log
    kernel_tokens = ("pabot", "robot", "Robot Framework", "内核", "kernel")
    log_text = json.dumps(log["lines"], ensure_ascii=False).lower()
    message = (out["row"].get("failure_message") or "").lower()
    out["kernel_named_in_failure_message"] = any(t.lower() in message for t in kernel_tokens)
    out["kernel_named_in_log"] = any(t.lower() in log_text for t in kernel_tokens)
    out["verdict"] = {
        "created": status == 200,
        "ran_and_snapshot_nonempty": len(before) >= 1,
        "terminal": terminal in ({"canceled", "cancelled"} if mode == "cancel"
                                  else {"failed"}),
        "no_residual_pids": not out["ps_after"]["pids_still_alive"],
        "no_residual_args": not out["ps_after"]["args_still_matching"],
        "failure_reason": out["row"].get("failure_reason"),
    }
    return out


# ------------------------------------------------------------------ pytest 段
def pytest_probe(token, xdist):
    art = os.path.join(RAW, "pytest-art-%s" % ("xdist" if xdist else "plain"))
    os.makedirs(art, exist_ok=True)
    target = os.path.join(ROOT, "pytest_slow", "test_slow.py")
    status, resp = http("GET", "/api/kernels/command?projectId=%d&kernel=pytest" % PROJECT_ID, token)
    preview = (resp.get("data") or {}).get("argv") if isinstance(resp, dict) else None
    argv = ["pytest",
            "--json-report",
            "--json-report-file=" + os.path.join(art, "pytest-report.json"),
            "--junitxml=" + os.path.join(art, "junit.xml"),
            "-q"]
    if xdist:
        argv += ["-n", "2"]
    argv += [target]
    classes = os.path.join(os.path.dirname(ROOT), "..", "..", "..", "backend", "target", "classes")
    classes = os.path.abspath(classes)
    harness_src = os.path.join(ROOT, "kill_probe", "KillProbe.java")
    harness_out = os.path.join(ROOT, "kill_probe", "classes")
    os.makedirs(harness_out, exist_ok=True)
    subprocess.run(["javac", "-cp", classes, "-d", harness_out, harness_src],
                   check=True, capture_output=True, text=True, timeout=120)
    rows_before = ps_table()
    hb = os.path.join(art, "heartbeat.txt")
    if os.path.exists(hb):
        os.unlink(hb)
    cmd = ["java", "-cp", classes + ":" + harness_out, "KillProbe",
           "20", art] + argv
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=120,
                       env=dict(os.environ, A7_HEARTBEAT=hb))
    write_raw("kill-%s-stdout.txt" % ("xdist" if xdist else "plain"), p.stdout)
    write_raw("kill-%s-stderr.txt" % ("xdist" if xdist else "plain"), p.stderr)
    try:
        result = json.loads(p.stdout)
    except Exception:  # noqa: BLE001
        result = {"parse_error": p.stdout[-400:], "rc": p.returncode}
    rows_after = ps_table()
    result["product_preview_argv"] = preview
    result["probe_argv"] = argv
    result["marker"] = target
    result["residual_ps"] = residual_for(rows_after, [d["pid"] for d in
                                                      result.get("descendants_before_kill", [])],
                                         target)
    result["heartbeat_file"] = hb
    result["heartbeat"] = (open(hb, encoding="utf-8").read().strip().splitlines()
                           if os.path.exists(hb) else [])
    result["xdist"] = xdist
    return result


# ------------------------------------------------------------------ main
def main():
    only = None
    if "--only" in __import__("sys").argv:
        only = __import__("sys").argv[__import__("sys").argv.index("--only") + 1]
    os.makedirs(RAW, exist_ok=True)
    load_env()
    status, resp = http("POST", "/api/auth/login",
                        body={"username": "qa-e2e-admin", "password": E["QA_E2E_PASSWORD"]})
    if status != 200:
        raise SystemExit("登录失败: %s %s" % (status, resp))
    token = resp["data"]["token"]
    report = {"run_tag": RUN_TAG, "base": BASE, "project_id": PROJECT_ID,
              "env": {"default_run_timeout_seconds_from_code": 3600}}
    if only in (None, "rf"):
        report["fixture"] = ensure_slow_b(token)
        suites = ["suites/e2e-nodx03/demo-slow.robot", "suites/a7/slow-b.robot"]
        report["rf_cancel"] = rf_scenario(token, "cancel", suites, 2, 60)
        report["rf_timeout"] = rf_scenario(token, "timeout", suites, 2, 20)
    if only in (None, "pytest"):
        report["pytest_plain"] = pytest_probe(token, False)
        report["pytest_xdist"] = pytest_probe(token, True)

    checks = {}
    if "rf_cancel" in report:
        checks["rf_cancel"] = all(v for k, v in report["rf_cancel"].get("verdict", {}).items()
                                  if isinstance(v, bool))
        checks["rf_cancel_tree_has_pabot_and_robot"] = (
            len(report["rf_cancel"]["ps_before"]["pabot"]) >= 1
            and len(report["rf_cancel"]["ps_before"]["robot_children"]) >= 1)
    if "rf_timeout" in report:
        checks["rf_timeout"] = all(v for k, v in report["rf_timeout"].get("verdict", {}).items()
                                   if isinstance(v, bool))
        checks["rf_timeout_elapsed_near_20s"] = (
            12 <= float(report["rf_timeout"]["row"].get("elapsed_s") or -1) <= 45)
    if "pytest_plain" in report:
        plain = report["pytest_plain"]
        checks["pytest_plain_killed"] = (plain.get("kill", {}).get("terminated") is True
                                        and not plain.get("parent_alive_after_2s", True)
                                        and not plain.get("residual_descendants"))
        checks["pytest_plain_no_residual"] = not plain.get("residual_ps", {}).get("args_still_matching")
        checks["pytest_plain_was_executing"] = len(plain.get("heartbeat") or []) >= 1
    if "pytest_xdist" in report:
        xd = report["pytest_xdist"]
        checks["pytest_xdist_had_children"] = len(xd.get("descendants_before_kill", [])) >= 1
        checks["pytest_xdist_killed"] = (xd.get("kill", {}).get("terminated") is True
                                        and not xd.get("parent_alive_after_2s", True)
                                        and not xd.get("residual_descendants"))
        checks["pytest_xdist_no_residual"] = not xd.get("residual_ps", {}).get("args_still_matching")
        checks["pytest_xdist_was_executing"] = len(xd.get("heartbeat") or []) >= 1
    if "rf_timeout" in report:
        checks["timeout_message_names_kernel"] = report["rf_timeout"].get("kernel_named_in_failure_message")
        checks["cancel_message_names_kernel"] = report["rf_cancel"].get(
            "kernel_named_in_failure_message", False)
    report["checks"] = checks
    report["PASS"] = all(checks.values())
    write_raw("a7-result.json", report)
    print(json.dumps(checks, ensure_ascii=False, indent=2))
    print("PASS =", report["PASS"])


if __name__ == "__main__":
    main()
