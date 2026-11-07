#!/usr/bin/env python3
"""A8 报告真实链路取证：3-run 聚合 / 冻结后删源 run 仍可渲染（hash 一致） / 三格式导出 / TC-67 downloadUrl。

口径与判据见同目录 README.md。所有读数来自真实 HTTP + 真库 + 真实文件，不造数据。
"""
import gzip
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:8001"
ROOT = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(ROOT, "raw")
ENV_FILE = os.path.expanduser("~/.config/robot-autotest/env")
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


def http(method, path, body=None, token=None, raw=False):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            payload = resp.read()
            status = resp.status
            ctype = resp.headers.get("Content-Type", "")
    except urllib.error.HTTPError as e:
        payload = e.read()
        status = e.code
        ctype = e.headers.get("Content-Type", "")
    if raw:
        return status, payload, ctype
    try:
        return status, json.loads(payload.decode()) if payload else None, ctype
    except Exception:  # noqa: BLE001
        return status, {"_text": payload[:1000].decode("utf-8", "replace")}, ctype


def sha256(b):
    return hashlib.sha256(b).hexdigest()


def write(name, text):
    with open(os.path.join(RAW, name), "w", encoding="utf-8") as f:
        f.write(text)


META = """*** Settings ***
Documentation     A8 报告链路取证夹具
Metadata          CASE_ID      {case}
Metadata          RUN_ID       {run}
Metadata          QA_PROJECT   a8-report-fixture
Metadata          DUT          LOCAL-HOST
Metadata          LINK         LOCAL
Metadata          SW_BUILD     v2.1-a8
Metadata          BASELINE     n/a
Metadata          OPERATOR     大师
Metadata          STARTED_AT   {started}
Metadata          CASE_KIND    report-chain

*** Test Cases ***
"""

SUITES = [
    ("suites/a8/pass2.robot", META.format(case="A8-RPT-PASS2", run="A8-RPT-PASS2-{stamp}",
                                          started="{started}") + """
A8 PASS 01
    Log    a8 pass 01
A8 PASS 02
    Log    a8 pass 02
"""),
    ("suites/a8/skip1.robot", META.format(case="A8-RPT-SKIP1", run="A8-RPT-SKIP1-{stamp}",
                                          started="{started}") + """
A8 PASS 01
    Log    a8 pass 01
A8 SKIP 01
    Skip    A8 跳过样本（验证通过率口径）
"""),
    ("suites/a8/fail1.robot", META.format(case="A8-RPT-FAIL1", run="A8-RPT-FAIL1-{stamp}",
                                          started="{started}") + """
A8 PASS 01
    Log    a8 pass 01
A8 FAIL 01
    Fail    A8 失败样本（验证失败分布）
"""),
]


def login():
    status, body, _ = http("POST", "/api/auth/login",
                           {"username": "qa-e2e-admin", "password": ENV["QA_E2E_PASSWORD"]})
    return status, body["data"]["token"]


def wait_state(token, project_id, run_id, timeout=240):
    """直接读 run 表轮询终态（HTTP 单 run 查询接口在 v2.0 未暴露，A7 亦用库轮询）。"""
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        rows = db_rows("SELECT run_status, failure_reason, failure_message FROM run "
                       "WHERE run_id='%s'" % run_id)
        if rows:
            last = rows[0][0]
            if last in ("finished", "failed", "canceled"):
                return last, {"run_status": rows[0][0], "failure_reason": rows[0][1],
                              "failure_message": rows[0][2]}
        time.sleep(2)
    return "TIMEOUT-WAIT", {"last_state": last}


def main():
    stamp = time.strftime("%Y%m%d%H%M%S")
    result = {"stamp": stamp, "checks": {}, "runs": [], "exports": {}, "notes": []}

    # 0) 备份 dev 库（授权口径：前后强制 mysqldump）
    dump_path = os.path.join(RAW, "db-before.sql.gz")
    with open(dump_path, "wb") as f:
        p = subprocess.run(["mysqldump", "-u", ENV["DB_USERNAME"], "-h", ENV.get("DB_HOST", "127.0.0.1"),
                            "-P", ENV.get("DB_PORT", "3306"), ENV.get("DB_NAME", "robot_dev")],
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                           env=dict(os.environ, MYSQL_PWD=ENV["DB_PASSWORD"]))
        f.write(gzip.compress(p.stdout))
    result["db_before_gz_bytes"] = os.path.getsize(dump_path)
    result["db_before_sha256"] = sha256(open(dump_path, "rb").read())
    p29 = db_rows("SELECT COUNT(*) FROM run_artifact a JOIN run r ON r.id=a.run_id WHERE r.project_id=29")
    result["p29_artifacts_before"] = p29[0][0] if p29 else None

    # 1) 登录 + 建隔离项目
    st, token = login()
    result["login_status"] = st
    st, body, _ = http("POST", "/api/projects",
                       {"code": "a8-report-" + stamp, "name": "A8 报告链路夹具 " + stamp,
                        "description": "A8 取证用隔离项目（3-run 聚合 / 冻结 / 导出 / 保留策略）",
                        "visibility": "private"}, token=token)
    project_id = body["data"]["id"] if body and body.get("data") else None
    result["project"] = {"status": st, "projectId": project_id}
    result["checks"]["project_created"] = st == 200 and project_id is not None

    # 2) 上传 3 个含 10 项 metadata 的套件
    started_utc = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() - 60))
    result["started_at_utc"] = started_utc
    for path, content in SUITES:
        content = content.replace("{stamp}", stamp).replace("{started}", started_utc)
        st, body, _ = http("PUT", "/api/editor/save",
                           {"projectId": project_id, "path": path, "content": content,
                            "parseOk": True, "comment": "A8 fixture"}, token=token)
        result.setdefault("uploads", []).append({"path": path, "status": st})
    result["checks"]["suites_uploaded"] = all(u["status"] == 200 for u in result["uploads"])

    # 3) 三个真实 run（串行）
    for idx, (path, _c) in enumerate(SUITES, start=1):
        run_req = "A8-%s-r%d" % (stamp, idx)
        st, body, _ = http("POST", "/api/execution/runs",
                           {"projectId": project_id, "suitePaths": [path], "concurrency": 1,
                            "timeoutSeconds": 150, "runRequestId": run_req}, token=token)
        run_id = body["data"]["runId"] if body and body.get("data") else None
        entry = {"index": idx, "suite": path, "runRequestId": run_req, "create_status": st, "runId": run_id}
        if run_id:
            state, detail = wait_state(token, project_id, run_id)
            entry["terminal_state"] = state
            entry["failure_reason"] = detail.get("failure_reason")
            entry["failure_message"] = detail.get("failure_message")
        result["runs"].append(entry)
    # 产品语义：pabot 退出码非 0（含「有用例失败」）即记 failed；第 3 个 run 故意含 1 条失败用例
    states = [r.get("terminal_state") for r in result["runs"]]
    result["terminal_states"] = states
    result["checks"]["runs_terminal"] = all(s in ("finished", "failed") for s in states)
    result["checks"]["test_failure_marks_run_failed"] = states[:2] == ["finished", "finished"] \
        and states[2] == "failed"
    result["checks"]["test_failure_message_not_confusing"] = "ok" not in (result["runs"][2].get("failure_message") or "")

    # 4) 解析 + 明细计数 + 库内计数比对
    for entry in result["runs"]:
        rid = entry["runId"]
        st, body, _ = http("POST", "/api/reports/%s/parse?projectId=%d" % (rid, project_id), token=token)
        entry["parse_status"] = st
        st, body, _ = http("GET", "/api/reports/%s?projectId=%d" % (rid, project_id), token=token)
        entry["detail_status"] = st
        entry["detail_counts"] = (body or {}).get("data", {}).get("counts") if body else None
        entry["detail_exportable"] = (body or {}).get("data", {}).get("exportable") if body else None
        rows = db_rows("SELECT t.verdict, COUNT(*) FROM test_result t JOIN run r ON r.id=t.run_id "
                       "WHERE r.run_id='%s' GROUP BY t.verdict" % rid)
        entry["db_verdicts"] = {k: int(v) for k, v in rows if k}
        entry["run_row"] = {}
        r2 = db_rows("SELECT id, run_status, pass_count, fail_count, skip_count, exportable, artifacts_dir "
                     "FROM run WHERE run_id='%s'" % rid)
        if r2:
            keys = ["id", "run_status", "pass_count", "fail_count", "skip_count", "exportable", "artifacts_dir"]
            entry["run_row"] = dict(zip(keys, r2[0]))
    ok_counts = True
    for entry in result["runs"]:
        c = entry.get("detail_counts") or {}
        v = entry.get("db_verdicts") or {}
        ok_counts = ok_counts and int(c.get("pass", -1)) == v.get("PASS", 0) \
            and int(c.get("fail", -1)) == v.get("FAIL", 0) \
            and int(c.get("skip", -1)) == v.get("SKIP", 0)
    result["checks"]["detail_counts_match_db"] = ok_counts

    # 5) 3-run 聚合（digest）
    st, body, _ = http("GET", "/api/projects/%d/report-digest?days=30" % project_id, token=token)
    digest = (body or {}).get("data") if body else None
    result["digest_status"] = st
    result["digest"] = digest
    recent = (digest or {}).get("recentReports") or []
    ours = [r for r in recent if r.get("runId") in [x["runId"] for x in result["runs"]]]
    result["checks"]["digest_recent_contains_3"] = len(ours) >= 3
    tot = {"pass": sum(int(r["pass"]) for r in ours), "fail": sum(int(r["fail"]) for r in ours),
           "skip": sum(int(r["skip"]) for r in ours)}
    result["aggregate"] = tot
    # 实现口径：pass/(pass+fail+skip)；PRD FR-v2.0-04 期望：pass/(pass+fail)（跳过不计入分母）
    impl_map, prd_map = {}, {}
    for entry in result["runs"]:
        c = entry.get("detail_counts") or {}
        p, f, s = int(c.get("pass", 0)), int(c.get("fail", 0)), int(c.get("skip", 0))
        impl_map[entry["runId"]] = round(p * 10000 / max(1, p + f + s))
        prd_map[entry["runId"]] = round(p * 10000 / max(1, p + f))
    trend = (digest or {}).get("passRateTrend") or []
    trend_map = {p.get("runId"): p.get("passRateBp") for p in trend}
    result["passrate"] = {"impl_expected": impl_map, "prd_expected": prd_map,
                          "trend_actual": {k: v for k, v in trend_map.items() if k in impl_map},
                          "aggregate_impl_bp": round(tot["pass"] * 10000 / max(1, tot["pass"] + tot["fail"] + tot["skip"])),
                          "aggregate_prd_bp": round(tot["pass"] * 10000 / max(1, tot["pass"] + tot["fail"]))}
    result["checks"]["passrate_trend_matches_impl"] = all(
        trend_map.get(k) == v for k, v in impl_map.items())
    result["checks"]["passrate_excludes_skip_as_prd"] = all(
        trend_map.get(k) == v for k, v in prd_map.items())
    # 多 run 聚合报告：PRD FR-v2.0-03 要求「选 1..N 个 run」，实测用 runIds 数组发起创建
    st_multi, body_multi, _ = http("POST", "/api/test-reports",
                                   {"projectId": project_id,
                                    "runIds": [r["runId"] for r in result["runs"]],
                                    "title": "A8 multi-run " + stamp}, token=token)
    result["multi_run_create"] = {"status": st_multi, "body": body_multi}
    result["checks"]["multi_run_report_supported"] = st_multi == 200
    failed = (digest or {}).get("topFailedCases") or []
    result["failed_cases"] = failed
    result["checks"]["failed_distribution_matches"] = any(f.get("caseKey", "").find("A8 FAIL 01") >= 0
                                                          and int(f.get("failures", 0)) == 1 for f in failed)

    # 6) 冻结（取 run1）
    target = result["runs"][0]
    st, body, _ = http("POST", "/api/test-reports",
                       {"projectId": project_id, "runId": target["runId"],
                        "title": "A8 冻结报告 " + stamp}, token=token)
    tr_id = body["data"]["id"] if body and body.get("data") else None
    target["testReportId"] = tr_id
    st, body, _ = http("POST", "/api/test-reports/%s/freeze" % tr_id, token=token)
    target["freeze_status"] = st
    target["freeze_state"] = (body or {}).get("data", {}).get("state") if body else None
    tr_row = db_rows("SELECT state, version, snapshot_hash, frozen_at FROM test_report WHERE id=%s" % tr_id)
    target["test_report_row"] = dict(zip(["state", "version", "snapshot_hash", "frozen_at"], tr_row[0])) if tr_row else {}
    result["checks"]["report_frozen"] = target.get("freeze_state") in ("FROZEN", "Frozen") \
        and bool(target["test_report_row"].get("snapshot_hash"))

    # 7) 冻结后渲染（删源 run 之前）
    st, body, _ = http("GET", "/api/test-reports/%s?external=true" % tr_id, token=token, raw=True)
    before_bytes = body if st == 200 else b""
    target["render_before"] = {"status": st, "bytes": len(before_bytes), "sha256": sha256(before_bytes)}
    write("render-before.json", json.dumps(json.loads(before_bytes.decode()) if before_bytes else {},
                                           ensure_ascii=False, indent=2))
    render_view_before = json.loads(before_bytes.decode()) if before_bytes else {}

    # 8) 删源 run（库行 + 产物目录）——只删本次自己创建的 run
    rid = target["runId"]
    dbid = target["run_row"].get("id")
    art_dir = target["run_row"].get("artifacts_dir")
    qs = []
    qs.append("DELETE FROM keyword_result WHERE test_result_id IN (SELECT id FROM test_result WHERE run_id=%s)" % dbid)
    qs.append("DELETE FROM test_result WHERE run_id=%s" % dbid)
    qs.append("DELETE FROM run_artifact WHERE run_id=%s" % dbid)
    qs.append("DELETE FROM run_attempt WHERE run_id=%s" % dbid)
    qs.append("DELETE FROM run_metadata WHERE run_id=%s" % dbid)
    qs.append("DELETE FROM run WHERE id=%s" % dbid)
    del_result = [db(x) for x in qs]
    target["delete_sql"] = del_result
    moved_to = None
    if art_dir and os.path.isdir(art_dir):
        moved_to = os.path.join(RAW, "deleted-run-artifacts")
        os.makedirs(moved_to, exist_ok=True)
        shutil.move(art_dir, os.path.join(moved_to, os.path.basename(art_dir)))
    target["artifacts_moved_to"] = moved_to
    st_gone, body_gone, _ = http("GET", "/api/reports/%s?projectId=%d" % (rid, project_id), token=token)
    target["source_report_after_delete"] = {"status": st_gone}
    result["checks"]["source_run_api_gone"] = st_gone in (404, 400, 500) or st_gone != 200

    # 9) 冻结后再次渲染：与删除前逐字节一致（同参数同内容）
    st, body, _ = http("GET", "/api/test-reports/%s?external=true" % tr_id, token=token, raw=True)
    after_bytes = body if st == 200 else b""
    target["render_after"] = {"status": st, "bytes": len(after_bytes), "sha256": sha256(after_bytes)}
    write("render-after.json", json.dumps(json.loads(after_bytes.decode()) if after_bytes else {},
                                          ensure_ascii=False, indent=2))
    result["checks"]["render_identical_after_source_deleted"] = \
        target["render_before"]["sha256"] == target["render_after"]["sha256"] and st == 200
    tr_row2 = db_rows("SELECT snapshot_hash FROM test_report WHERE id=%s" % tr_id)
    target["snapshot_hash_after"] = tr_row2[0][0] if tr_row2 else None
    result["checks"]["snapshot_hash_unchanged"] = target["snapshot_hash_after"] == target["test_report_row"].get("snapshot_hash")

    # 10) 三格式导出（冻结报告）
    for fmt, magic, label in (("pdf", b"%PDF", "pdf"), ("docx", b"PK", "docx"), ("html", b"<", "html")):
        st, payload, ctype = http("GET", "/api/test-reports/%s/export?format=%s" % (tr_id, fmt),
                                  token=token, raw=True)
        path = os.path.join(RAW, "export-%s" % fmt)
        with open(path, "wb") as f:
            f.write(payload)
        result["exports"][fmt] = {"status": st, "bytes": len(payload), "contentType": ctype,
                                  "sha256": sha256(payload), "magic_ok": payload[:len(magic)] == magic,
                                  "path": os.path.relpath(path, ROOT)}
    result["checks"]["three_formats_exported"] = all(
        v["status"] == 200 and v["magic_ok"] and v["bytes"] > 0 for v in result["exports"].values())

    # 10b) 三格式内容一致性 / 页眉（版本+冻结时间）/ 敏感信息扫描
    def _read(name):
        with open(os.path.join(RAW, name), "rb") as f:
            return f.read()

    html_b = _read("export-html").decode("utf-8", "replace")
    docx_b = _read("export-docx")
    pdf_b = _read("export-pdf")
    in_docx = ""
    try:
        import zipfile
        with zipfile.ZipFile(os.path.join(RAW, "export-docx")) as z:
            in_docx = z.read("word/document.xml").decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001
        in_docx = ""
        result["notes"].append("docx 解包失败: %s" % e)
    pdf_text = pdf_b.decode("latin-1", "replace")
    version = (target["test_report_row"] or {}).get("version") or ""
    content_checks = {
        "html_has_version_and_frozen": ("version" in html_b and str(version) in html_b
                                        and ("frozenAt" in html_b or "frozen_at" in html_b)),
        "docx_has_version_and_frozen": ("version" in in_docx and ("frozenAt" in in_docx or "frozen_at" in in_docx)),
        "pdf_has_version_and_frozen": ("version" in pdf_text and "frozenAt" in pdf_text),
        "pdf_preserves_cjk_title": "????" not in pdf_text,
        "html_preserves_cjk_title": "A8 冻结报告" in html_b,
    }
    result["export_content"] = content_checks
    sensitive = ["127.0.0.1", "192.168", "/home/evergreen", "robot_dev", "qa-e2e", ENV.get("DB_PASSWORD", "\0")]
    leak = {fmt: [s for s in sensitive if s and s in txt]
            for fmt, txt in (("html", html_b), ("docx", in_docx), ("pdf", pdf_text))}
    result["export_leaks"] = leak
    result["checks"]["exports_headers_ok"] = all(content_checks.values())
    result["checks"]["exports_no_sensitive_data"] = not any(leak.values())

    # 11) TC-67：run 级导出 downloadUrl 经 8001 可下载且 sha256 与库一致
    payer = result["runs"][1]
    print_base = ""
    pid = subprocess.run(["systemctl", "--user", "show", "-p", "MainPID", "--value",
                          "robot-autotest-backend.service"], capture_output=True, text=True).stdout.strip()
    if pid:
        try:
            envs = open("/proc/%s/environ" % pid, "rb").read().decode("utf-8", "replace").split("\0")
            for kv in envs:
                if kv.startswith("REPORT_PRINT_BASE_URL="):
                    print_base = kv.split("=", 1)[1]
        except Exception:  # noqa: BLE001
            print_base = ""
    st, body, _ = http("POST", "/api/reports/%s/export?projectId=%d&format=pdf&mode=external"
                       % (payer["runId"], project_id), token=token)
    exp = (body or {}).get("data") if body else None
    result["run_export"] = {"status": st, "body": exp, "print_base": print_base}
    if exp and exp.get("downloadUrl"):
        url = exp["downloadUrl"]
        absolute = BASE + url
        st2, payload, ctype = http("GET", url, token=token, raw=True)
        result["run_export"]["download"] = {"status": st2, "bytes": len(payload),
                                            "sha256": sha256(payload), "url": url, "absolute": absolute,
                                            "via_entry": True}
        db_sha = db_rows("SELECT sha256 FROM export_record WHERE id=%s" % exp.get("exportId"))
        result["run_export"]["download"]["db_sha256"] = db_sha[0][0] if db_sha else None
        result["checks"]["pdf_downloadurl_via_8001"] = st2 == 200
        result["checks"]["pdf_download_sha256_matches_db"] = bool(db_sha) and db_sha[0][0] == sha256(payload)
    result["checks"]["print_base_is_8001"] = print_base.endswith(":8001")

    # 12) 保留策略 dry-run 与实删（阈值已在 drop-in 临时调小）
    st, body, _ = http("POST", "/api/reports/retention/run?projectId=%d&dryRun=true" % project_id, token=token)
    dry = (body or {}).get("data") if body else None
    result["retention_dry"] = {"status": st, "data": dry}
    st, body, _ = http("POST", "/api/reports/retention/run?projectId=%d&dryRun=false" % project_id, token=token)
    real = (body or {}).get("data") if body else None
    result["retention_real"] = {"status": st, "data": real}
    if dry and real:
        dry_ids = [i.get("runId") for i in dry.get("items", [])]
        real_ids = [i.get("runId") for i in real.get("items", [])]
        result["checks"]["retention_dryrun_equals_real"] = dry_ids == real_ids
        result["checks"]["retention_deleted_files"] = int(real.get("deletedFiles", 0)) > 0
        # 有导出记录/冻结/pinned 的 run 受保护：run2 已导出 PDF、run1 已冻结并已删源
        result["checks"]["retention_protects_exported_run"] = payer["runId"] not in dry_ids
        result["checks"]["retention_targets_plain_run"] = result["runs"][2]["runId"] in dry_ids
        kept = db_rows("SELECT COUNT(*) FROM run WHERE project_id=%d" % project_id)
        result["runs_kept_in_db"] = int(kept[0][0]) if kept else None
        # 保留策略只删产物文件，不删 run / test_result 行（本次 3 个 run 里 1 个已被「删源」步骤移除，应剩 2）
        result["checks"]["retention_keeps_run_rows"] = result["runs_kept_in_db"] == 2
    # 误删检查：项目 29 的产物条数在本次前后不变
    p29_before = result.get("p29_artifacts_before")
    p29_after = db_rows("SELECT COUNT(*) FROM run_artifact a JOIN run r ON r.id=a.run_id WHERE r.project_id=29")
    result["p29_artifacts_after"] = p29_after[0][0] if p29_after else None
    if p29_before:
        result["checks"]["no_cross_project_purge"] = p29_before == result["p29_artifacts_after"]
    # 审计
    audit = db_rows("SELECT COUNT(*) FROM audit_log WHERE action='artifact_purge'")
    result["audit_artifact_purge_rows"] = audit[0][0] if audit else None

    # 13) 取证后再次 mysqldump（授权口径：前后各一份）
    dump_after = os.path.join(RAW, "db-after.sql.gz")
    with open(dump_after, "wb") as f:
        p = subprocess.run(["mysqldump", "-u", ENV["DB_USERNAME"], "-h", ENV.get("DB_HOST", "127.0.0.1"),
                            "-P", ENV.get("DB_PORT", "3306"), ENV.get("DB_NAME", "robot_dev")],
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                           env=dict(os.environ, MYSQL_PWD=ENV["DB_PASSWORD"]))
        f.write(gzip.compress(p.stdout))
    result["db_after_gz_bytes"] = os.path.getsize(dump_after)
    result["db_after_sha256"] = sha256(open(dump_after, "rb").read())

    result["PASS"] = all(bool(v) for k, v in result["checks"].items())
    write("a8-result.json", json.dumps(result, ensure_ascii=False, indent=2, default=str))
    print(json.dumps({"PASS": result["PASS"], "checks": result["checks"],
                      "aggregate": result["aggregate"], "passrate": result["passrate"],
                      "retention_dry": dry, "retention_real": real}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
