#!/usr/bin/env python3
"""A6 · `deploy → health → rollback → health` 一次真实演练（dev 库）。

判据（06 计划 §3 A6）：握手 101 有记录；回滚后 `/healthz` 与 `/api/health` 均 200；
RTO 计时入表。授权口径（拍板第 3 条）：dev 库演练，**前后强制 mysqldump**，只回本次写入。

做法（全部真命令，无打桩）：
  0  前置：取基线 build-info / jar+dist 快照 / mysqldump / 全表行数
  1  bash scripts/deploy.sh            （构建 + 部署 + 自检），计时
  2  外部复核：/healthz 200、/api/health 200 且 database=UP、build-info 已变
  3  bash scripts/rollback.sh --to a6-baseline --yes  （备份→部署上一版→起服→健康检查），计时
  4  外部复核：两个健康端点 200、build-info 回到基线、jar 与 dist 摘要回到基线
  5  mysqldump + 全表行数对比：本次演练写入量必须为 0；非 0 则打印差异清单（不自动改库）

用法：
  python3 drill.py            # 真实演练
  python3 drill.py --dry-run  # 只打印将执行的命令（不构建、不启停服务）
"""
import argparse
import gzip
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = os.path.expanduser("~/robot-autotest")
HERE = os.path.join(ROOT, "docs/evidence/v2.0/a6-entry")
RAW = os.path.join(HERE, "raw")
ENV_FILE = os.path.expanduser("~/.config/robot-autotest/env")
BASE = "http://127.0.0.1:8001"
JAR = os.path.join(ROOT, "backend/target/robot-autotest-backend-0.1.0-SNAPSHOT.jar")
DIST = os.path.join(ROOT, "frontend/dist")
REL = os.path.join(ROOT, "releases/a6-baseline")
TAG = "a6-baseline"


def load_env():
    env = dict(os.environ)
    if os.path.exists(ENV_FILE):
        with open(ENV_FILE, encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                k, v = line.split("=", 1)
                env[k.strip()] = v.strip().strip("'\"")
    return env


ENV = load_env()
DB = ENV.get("DB_NAME", "robot_dev")
# mysql/mariadb 客户端只认 MYSQL_PWD（不进 argv，避免口令出现在 ps / 日志里）
if ENV.get("DB_PASSWORD"):
    ENV["MYSQL_PWD"] = ENV["DB_PASSWORD"]


def db_client():
    for name in ("mariadb", "mysql"):
        if shutil.which(name):
            return name
    raise RuntimeError("本机没有 mysql / mariadb 客户端")


def curl_json(path, token=None, timeout=5):
    req = urllib.request.Request(BASE + path)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.status, json.loads(resp.read().decode("utf-8"))


def http_code(path, timeout=5):
    try:
        req = urllib.request.Request(BASE + path)
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status
    except urllib.error.HTTPError as ex:
        return ex.code
    except Exception:
        return 0


def login():
    body = json.dumps({
        "username": "qa-e2e-admin",
        "password": ENV.get("QA_E2E_PASSWORD", ""),
    }).encode()
    req = urllib.request.Request(BASE + "/api/auth/login", data=body,
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=5) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    return payload["data"]["token"]


def build_info(token):
    _, payload = curl_json("/api/system/build-info", token)
    return payload["data"]


def mysql_exec(sql):
    cmd = [db_client(), "-h", ENV.get("DB_HOST", "127.0.0.1"), "-P", ENV.get("DB_PORT", "3306"),
           "-u", ENV.get("DB_USERNAME", "robot"), "-N", "-B", DB, "-e", sql]
    out = subprocess.run(cmd, env=ENV, capture_output=True, text=True, check=True)
    return out.stdout.strip()


def table_counts():
    names = [r for r in mysql_exec(
        "SELECT table_name FROM information_schema.tables "
        f"WHERE table_schema='{DB}' AND table_type='BASE TABLE' ORDER BY table_name").splitlines() if r]
    if not names:
        return {}
    parts = [f"SELECT '{n}', COUNT(*) FROM `{n}`" for n in names]
    sql = " UNION ALL ".join(parts)
    rows = mysql_exec(sql)
    counts = {}
    for line in rows.splitlines():
        n, c = line.split("\t")
        counts[n] = int(c)
    return counts


def dump(path, tag):
    with open(path, "wb") as fh:
        p1 = subprocess.Popen(["mysqldump", "--host", ENV.get("DB_HOST", "127.0.0.1"),
                               "--port", ENV.get("DB_PORT", "3306"),
                               "--user", ENV.get("DB_USERNAME", "robot"),
                               "--single-transaction", "--routines", "--triggers",
                               "--default-character-set=utf8mb4", "--databases", DB],
                              env=ENV, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        p2 = subprocess.Popen(["gzip", "-9"], stdin=p1.stdout, stdout=fh)
        p1.stdout.close()
        err = p1.stderr.read().decode()
        p2.wait()
        rc = p1.wait()
    if rc != 0 or p2.returncode != 0:
        raise RuntimeError(f"{tag} mysqldump 失败 rc={rc}/{p2.returncode}: {err[:400]}")
    return sha256(path)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def sha256_dir(path):
    h = hashlib.sha256()
    for name in sorted(os.listdir(path)):
        full = os.path.join(path, name)
        if os.path.isfile(full):
            h.update(name.encode())
            h.update(sha256(full).encode())
    return h.hexdigest()


def run_script(args, log_path, dry_run=False):
    printable = " ".join(args)
    with open(log_path, "w", encoding="utf-8") as fh:
        fh.write(f"$ {printable}\n")
        if dry_run:
            fh.write("(dry-run：未执行)\n")
            return 0, 0.0
        t0 = time.time()
        proc = subprocess.run(args, cwd=ROOT, env=ENV, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True)
        fh.write(proc.stdout)
        fh.write(f"\nexit={proc.returncode} wall={round(time.time() - t0, 1)}s\n")
        return proc.returncode, round(time.time() - t0, 1)


def wait_healthy(timeout=180):
    """轮询 /healthz 与 /api/health，返回 (秒数, healthz_code, api_health)。"""
    t0 = time.time()
    last = None
    while time.time() - t0 < timeout:
        hz = http_code("/healthz")
        try:
            _, payload = curl_json("/api/health")
            last = payload.get("data", payload)
        except Exception as ex:
            last = {"error": str(ex)}
        if hz == 200 and isinstance(last, dict) and last.get("status") == "UP" \
                and last.get("database") == "UP":
            return round(time.time() - t0, 1), hz, last
        time.sleep(1)
    return None, http_code("/healthz"), last


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    os.makedirs(RAW, exist_ok=True)
    result = {"dry_run": args.dry_run, "tag": TAG, "db": DB}

    token = login()
    result["baseline_build_info"] = build_info(token)
    result["preflight"] = {
        "healthz": http_code("/healthz"),
        "api_health": curl_json("/api/health")[1]["data"],
        "index": http_code("/"),
    }
    result["artifact_before"] = {"jar_sha256": sha256(JAR), "dist_sha256": sha256_dir(DIST)}
    result["db_before"] = {"counts": table_counts(), "dump_sha256": None, "dump_bytes": 0}
    if not args.dry_run:
        os.makedirs(REL, exist_ok=True)
        shutil.copy2(JAR, os.path.join(REL, "robot-autotest-backend.jar"))
        if os.path.isdir(os.path.join(REL, "dist")):
            shutil.rmtree(os.path.join(REL, "dist"))
        shutil.copytree(DIST, os.path.join(REL, "dist"))
        before = os.path.join(RAW, "db-before.sql.gz")
        result["db_before"]["dump_sha256"] = dump(before, "before")
        result["db_before"]["dump_bytes"] = os.path.getsize(before)

    rc_deploy, t_deploy = run_script(["bash", "scripts/deploy.sh"],
                                     os.path.join(RAW, "deploy.log"), args.dry_run)
    result["deploy"] = {"rc": rc_deploy, "wall_s": t_deploy}
    result["deploy"]["health_s"], result["deploy"]["healthz"], result["deploy"]["api_health"] = wait_healthy()
    after_deploy_info = build_info(token)
    result["deploy"]["build_info"] = after_deploy_info
    result["deploy"]["build_info_changed"] = after_deploy_info.get("buildTime") != \
        result["baseline_build_info"].get("buildTime")

    rc_rollback, t_rollback = run_script(
        ["bash", "scripts/rollback.sh", "--to", TAG, "--yes",
         "--service", "robot-autotest-backend.service",
         "--jar", JAR, "--dist", DIST, "--base-url", BASE],
        os.path.join(RAW, "rollback.log"), args.dry_run)
    result["rollback"] = {"rc": rc_rollback, "wall_s": t_rollback}
    result["rollback"]["health_s"], result["rollback"]["healthz"], result["rollback"]["api_health"] = wait_healthy()
    after_info = build_info(token)
    result["rollback"]["build_info"] = after_info
    result["rollback"]["build_info_reverted"] = (
        after_info.get("buildTime") == result["baseline_build_info"].get("buildTime")
        and after_info.get("commit") == result["baseline_build_info"].get("commit"))
    result["artifact_after"] = {"jar_sha256": sha256(JAR), "dist_sha256": sha256_dir(DIST)}
    result["artifact_restored"] = (
        result["artifact_after"]["jar_sha256"] == result["artifact_before"]["jar_sha256"]
        and result["artifact_after"]["dist_sha256"] == result["artifact_before"]["dist_sha256"])

    result["db_after"] = {"counts": table_counts(), "dump_sha256": None, "dump_bytes": 0}
    if not args.dry_run:
        after = os.path.join(RAW, "db-after.sql.gz")
        result["db_after"]["dump_sha256"] = dump(after, "after")
        result["db_after"]["dump_bytes"] = os.path.getsize(after)
    diffs = {k: [result["db_before"]["counts"].get(k, 0), result["db_after"].get("counts", {}).get(k, 0)]
             for k in set(result["db_before"]["counts"]) | set(result.get("db_after", {}).get("counts", {}))
             if result["db_before"]["counts"].get(k, 0) != result.get("db_after", {}).get("counts", {}).get(k, 0)}
    result["db_row_count_diff"] = diffs

    # 行长数相同不等于内容相同：跑 dbdiff.py 逐表逐列定位（心跳时间戳 vs 业务值）
    content = {"changed": {}, "changed_count": None}
    if not args.dry_run:
        subprocess.run([sys.executable, os.path.join(HERE, "dbdiff.py")], check=False)
        with open(os.path.join(RAW, "db-diff.json"), encoding="utf-8") as fh:
            content = json.load(fh)
    result["db_content_diff"] = content
    changed_cols = {c.get("column") for rows in (content.get("changed") or {}).values()
                    for row in rows for c in row["cols"] if c.get("kind") == "value"}
    heartbeat_only = changed_cols <= {"last_used_at", "updated_at", "last_heartbeat_at", "expires_at"}
    result["db_content_diff_summary"] = {
        "changed_tables": sorted((content.get("changed") or {}).keys()),
        "changed_columns": sorted(x for x in changed_cols if x),
        "heartbeat_timestamps_only": heartbeat_only,
    }
    result["rto_table"] = {
        "deploy_build_deploy_s": t_deploy,
        "deploy_health_verify_s": result["deploy"]["health_s"],
        "rollback_backup_deploy_restart_s": t_rollback,
        "rollback_health_verify_s": result["rollback"]["health_s"],
        "loop_total_s": round(t_deploy + (result["deploy"]["health_s"] or 0)
                              + t_rollback + (result["rollback"]["health_s"] or 0), 2),
    }

    def ok(key):
        return bool(result[key])

    result["verdict"] = {
        "deploy_rc0": rc_deploy == 0,
        "deploy_health_200": result["deploy"]["healthz"] == 200
                             and result["deploy"]["api_health"]["database"] == "UP",
        "deploy_build_info_changed": result["deploy"]["build_info_changed"],
        "rollback_rc0": rc_rollback == 0,
        "rollback_health_200": result["rollback"]["healthz"] == 200
                               and result["rollback"]["api_health"]["database"] == "UP",
        "build_info_reverted": result["rollback"]["build_info_reverted"],
        "artifacts_restored": result["artifact_restored"],
        "row_counts_identical": not diffs,
        "no_business_value_changed": heartbeat_only,
    }
    result["PASS"] = all(result["verdict"].values())

    with open(os.path.join(RAW, "a6-result.json"), "w", encoding="utf-8") as fh:
        json.dump(result, fh, ensure_ascii=False, indent=2)

    print(json.dumps({"verdict": result["verdict"], "PASS": result["PASS"],
                      "rto_table": result["rto_table"],
                      "db_writes": diffs}, ensure_ascii=False, indent=2))
    return 0 if result["PASS"] else 1


if __name__ == "__main__":
    sys.exit(main())
