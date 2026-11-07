#!/usr/bin/env python3
"""A9 AI 真实模型调用取证：面板真实 HTTP 动作 + 审计可查 + 无 CLI 降级不报错 + 1 条真实模型输出（脱敏后留档）。

口径与判据见同目录 README.md。所有读数来自真实 HTTP / 真库 / 真实 CLI，不造数据。
"""
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8001"
ROOT = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(ROOT, "raw")
ENV_FILE = os.path.expanduser("~/.config/robot-autotest/env")
PROJECT_ID = int(os.environ.get("A9_PROJECT_ID", "29"))
USER = os.environ.get("A9_USER", "qa-e2e-admin")
os.makedirs(RAW, exist_ok=True)


def load_env():
    out = {}
    for line in open(ENV_FILE, encoding="utf-8"):
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip().strip("'\"")
    return out


ENV = load_env()


def db(sql):
    cmd = ["mysql", "-u", ENV["DB_USERNAME"], "-h", ENV.get("DB_HOST", "127.0.0.1"),
           "-P", ENV.get("DB_PORT", "3306"), "-D", ENV.get("DB_NAME", "robot_dev"),
           "-N", "-B", "-e", sql]
    p = subprocess.run(cmd, capture_output=True, text=True,
                       env=dict(os.environ, MYSQL_PWD=ENV["DB_PASSWORD"]))
    if p.returncode != 0:
        return {"rc": p.returncode, "out": (p.stdout or "") + (p.stderr or "")}
    return {"rc": 0, "out": (p.stdout or "").strip()}


def db_rows(sql):
    r = db(sql)
    if r["rc"] != 0 or not r["out"]:
        return []
    return [line.split("\t") for line in r["out"].splitlines()]


def http(method, path, body=None, token=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            payload, status = resp.read(), resp.status
    except urllib.error.HTTPError as e:
        payload, status = e.read(), e.code
    try:
        return status, json.loads(payload.decode()) if payload else None
    except Exception:  # noqa: BLE001
        return status, {"_text": payload[:2000].decode("utf-8", "replace")}


def write(name, text):
    with open(os.path.join(RAW, name), "w", encoding="utf-8") as f:
        f.write(text)


# 与产品 ReportViewRenderer.scrub() 同一套判据（A9 只校验留档产物是否还含敏感串）
PATTERNS = {
    "ip": re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b"),
    "credential": re.compile(r"(?i)(password|passwd|api[_-]?key|secret|token|authorization)\s*[:=]\s*\S+"),
    "abs_path": re.compile(r"(?:/home/|/root/|/mnt/|/var/)\S+"),
    "command": re.compile(r"(?i)(robot|pabot|pytest|iperf3|ssh|mysql)\s+-{1,2}[A-Za-z]\S*"),
}


def scan_secrets(text):
    hits = {}
    for name, pat in PATTERNS.items():
        found = pat.findall(text)
        if found:
            hits[name] = sorted({m if isinstance(m, str) else " ".join(m) for m in found})[:5]
    return hits


def run(cmd, timeout=300, cwd=None):
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, cwd=cwd,
                       env=dict(os.environ, NO_COLOR="1", TERM="dumb"))
    return {"argv": cmd, "rc": p.returncode,
            "stdout": p.stdout, "stderr": p.stderr}


def main():
    result = {"stamp": time.strftime("%Y%m%d%H%M%S"), "projectId": PROJECT_ID, "checks": {}}
    checks = result["checks"]

    st, body = http("POST", "/api/auth/login",
                    {"username": USER, "password": ENV["QA_E2E_PASSWORD"]})
    token = ((body or {}).get("data") or {}).get("token")
    checks["login_ok"] = st == 200 and bool(token)
    result["login"] = {"status": st, "user": USER}
    if not checks["login_ok"]:
        write("a9-result.json", json.dumps(result, ensure_ascii=False, indent=2))
        print("LOGIN FAILED", st, body)
        return 1

    # 1) 面板状态：产品眼中的 CLI 可用性
    st, body = http("GET", "/api/ai/state?projectId=%d" % PROJECT_ID, token=token)
    state = (body or {}).get("data") or {}
    result["state"] = {"status": st, "data": state}
    checks["state_has_probe_fields"] = st == 200 and all(
        k in state for k in ("claude", "codex", "gemini", "aider",
                             "anyAvailable", "maxLevel", "mode",
                             "timeoutSeconds", "encoding", "contextMaxChars",
                             "executeRequiresConfirmation"))
    product_mode = str(state.get("mode"))
    result["product_cli_mode"] = product_mode

    # 2) 开会话
    st, body = http("POST", "/api/ai/session?projectId=%d&userId=1" % PROJECT_ID, token=token)
    session_id = (body or {}).get("data")
    checks["session_open_ok"] = st == 200 and isinstance(session_id, int)
    result["session"] = {"status": st, "sessionId": session_id}

    # 3) L1 建议：带敏感串的上下文，只回摘要，绝不因无 CLI 报错
    ctx = {
        "case": "PERF-IPERF-200G-001",
        "host": "10.202.54.106",
        "cmd": "ssh -o StrictHostKeyChecking=no 10.202.54.107",
        "password": "SuperSecret!42",
        "log": "/home/evergreen/robot-test/results/run_x/output.xml",
        "note": "A9 脱敏取证",
    }
    st, body = http("POST", "/api/ai/suggest",
                    {"projectId": PROJECT_ID, "sessionId": session_id, "context": ctx}, token=token)
    sug = (body or {}).get("data") or {}
    result["suggest"] = {"status": st, "data": sug}
    checks["suggest_ok_no_error"] = st == 200 and sug.get("level") == "L1"
    leaked = scan_secrets(json.dumps(sug, ensure_ascii=False))
    checks["suggest_response_no_secret"] = not leaked
    result["suggest_response_secret_hits"] = leaked

    # 4) 二次确认硬闸门：未确认必须 403，不得静默执行
    st, body = http("POST", "/api/ai/execute",
                    {"projectId": PROJECT_ID, "sessionId": session_id,
                     "target": "PERF-IPERF-200G-001", "confirmed": False}, token=token)
    checks["execute_without_confirm_403"] = st == 403
    result["execute_unconfirmed"] = {"status": st, "body": body}

    # 5) 确认后执行：有 CLI → NORMAL；无 CLI → DEGRADED 且 200 不报错
    st, body = http("POST", "/api/ai/execute",
                    {"projectId": PROJECT_ID, "sessionId": session_id,
                     "target": "PERF-IPERF-200G-001", "confirmed": True}, token=token)
    ex = (body or {}).get("data") or {}
    result["execute_confirmed"] = {"status": st, "data": ex}
    if ex.get("degraded"):
        checks["execute_degraded_returns_200"] = st == 200 and bool(ex.get("summary"))
    else:
        checks["execute_normal_returns_200"] = st == 200 and bool(ex.get("summary"))

    # 6) 一键终止
    st, body = http("POST", "/api/ai/terminate?sessionId=%s&projectId=%d"
                    % (session_id, PROJECT_ID), token=token)
    result["terminate"] = {"status": st, "data": (body or {}).get("data")}
    checks["terminate_ok"] = st == 200

    # 7) 动作审计：本会话 5 条动作全部可查
    rows = db_rows(
        "SELECT action,resource_type,resource_id,LEFT(detail,120),occurred_at "
        "FROM audit_log WHERE resource_id='%s' AND action LIKE 'AI\\_%%' "
        "ORDER BY id;" % session_id)
    result["audit_rows"] = rows
    actions = [r[0] for r in rows]
    checks["audit_session_open"] = "AI_SESSION_OPEN" in actions
    checks["audit_suggest"] = "AI_SUGGEST" in actions
    checks["audit_execute"] = ("AI_EXECUTE" in actions) or ("AI_EXECUTE_DEGRADED" in actions)
    checks["audit_terminate"] = "AI_TERMINATE" in actions

    # 8) 真实 CLI：版本 + 产品 argv 模板 vs 真实 CLI 契约
    cli = None
    for cand in ("claude", "codex", "gemini", "aider"):
        if shutil.which(cand):
            cli = cand
            break
    result["cli_found"] = cli
    checks["cli_on_path"] = cli is not None
    if cli:
        ver = run([cli, "--version"], timeout=60)
        result["cli_version"] = {"rc": ver["rc"], "out": (ver["stdout"] + ver["stderr"]).strip()[:300]}
        checks["cli_version_ok"] = ver["rc"] == 0 and bool((ver["stdout"] + ver["stderr"]).strip())

        prompt_file = os.path.join(RAW, "a9-prompt.txt")
        prompt = ("你是测试平台助手。请只输出一行中文，回答：判定一次自动化测试是否通过，"
                  "必须同时具备哪三类证据？不要输出任何 IP、路径、命令。")
        write("a9-prompt.txt", prompt + "\n")
        if cli == "claude":
            product_argv = [cli, "--print", "--output-format", "text", "--prompt-file", prompt_file]
            real_argv = [cli, "-p", prompt, "--output-format", "text"]
        elif cli == "codex":
            product_argv = [cli, "--print", "--output-format", "text", "--prompt-file", prompt_file]
            real_argv = [cli, "exec", "--skip-git-repo-check", prompt]
        else:
            product_argv = [cli, "--print", "--output-format", "text", "--prompt-file", prompt_file]
            real_argv = [cli, "-p", prompt]

        prod = run(product_argv, timeout=90)
        result["product_argv"] = {"argv": product_argv, "rc": prod["rc"],
                                  "stderr": prod["stderr"].strip()[:600],
                                  "stdout": prod["stdout"].strip()[:600]}
        checks["product_argv_matches_real_cli"] = prod["rc"] == 0

        real = run(real_argv, timeout=300)
        out = (real["stdout"] or "").strip()
        err = (real["stderr"] or "").strip()
        result["real_call"] = {"cli": cli, "argv": real_argv[:3] + ["<prompt>"],
                               "rc": real["rc"], "chars": len(out),
                               "stdout": out, "stderr": err[:1200]}
        checks["real_model_call_ok"] = real["rc"] == 0 and len(out) >= 10
        hits = scan_secrets(out + "\n" + err)
        result["real_output_secret_hits"] = hits
        checks["real_output_redacted"] = not hits
        write("a9-real-model-output.txt", out + "\n")
    else:
        checks["cli_version_ok"] = False
        checks["product_argv_matches_real_cli"] = False
        checks["real_model_call_ok"] = False
        checks["real_output_redacted"] = True

    result["pass"] = sum(1 for v in checks.values() if v)
    result["fail"] = sum(1 for v in checks.values() if not v)
    result["failed_checks"] = [k for k, v in checks.items() if not v]
    write("a9-result.json", json.dumps(result, ensure_ascii=False, indent=2))
    print(json.dumps({"pass": result["pass"], "fail": result["fail"],
                      "failed": result["failed_checks"],
                      "product_mode": product_mode, "cli": cli,
                      "real_chars": (result.get("real_call") or {}).get("chars")},
                     ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
