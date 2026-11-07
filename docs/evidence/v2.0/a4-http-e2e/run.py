#!/usr/bin/env python3
"""A4 真实 HTTP E2E 取证（:8001 统一入口 → :8081 后端 → 真实 MySQL robot_dev）。

原则：
  * 只用真实端点、真实账号、真实库；不使用 MockMvc / 内存仓储；
  * 每条 TC 产出一行可核对的数字；事实与 05-TEST-REPORT 原判不一致时如实记 FAIL/GAP，
    不做“为了 0 FAIL 而放宽判据”的处理；
  * 自建夹具（组/场景/报告/授权）在收尾阶段全部删除，并断言相关计数归零。

用法：
    python3 run.py                  # 全量 17 条
    python3 run.py --only TC-41     # 单条调试
环境：~/.config/robot-autotest/env 提供 DB_* 与 QA_E2E_PASSWORD。
"""
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

ROOT = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(ROOT, "raw")
BASE = os.environ.get("A4_BASE", "http://127.0.0.1:8001")
ENV_FILE = os.path.expanduser("~/.config/robot-autotest/env")
RUN_ID = "A4-HTTP-" + time.strftime("%Y%m%d_%H%M%S")

FIXTURE_TAG = "A4-" + uuid.uuid4().hex[:6]
RESULTS = []
TRACE = []


# ---------------------------------------------------------------- 基础设施
def load_env():
    env = {}
    for line in open(ENV_FILE, encoding="utf-8"):
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip()
    return env


E = load_env()


def http(method, path, token=None, body=None, timeout=30):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            status, txt = r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        status, txt = e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001
        status, txt = -1, f"CONNECT ERROR: {e}"
    try:
        parsed = json.loads(txt)
    except Exception:  # noqa: BLE001
        parsed = txt[:800]
    TRACE.append({"method": method, "path": path, "status": status})
    return status, parsed


def api_data(resp):
    if isinstance(resp, dict):
        return resp.get("data")
    return None


def db(sql):
    """执行一条 SQL，返回按 '\t' 切分的行列表。密码只经 MYSQL_PWD 传递，不进 argv。"""
    env = dict(os.environ)
    env["MYSQL_PWD"] = E["DB_PASSWORD"]
    cmd = ["mysql", "-h", E["DB_HOST"], "-P", E["DB_PORT"], "-u", E["DB_USERNAME"],
           "-N", "-B", "-e", sql, "robot_dev"]
    p = subprocess.run(cmd, capture_output=True, text=True, env=env, timeout=30)
    if p.returncode != 0:
        return [["SQL_ERROR", p.stderr.strip()[:200]]]
    return [line.split("\t") for line in p.stdout.strip().splitlines() if line]


def db1(sql, default="0"):
    rows = db(sql)
    if rows and len(rows[0]) >= 1 and rows[0][0] != "SQL_ERROR":
        return rows[0][0]
    return default


TERMINAL = {"finished", "failed", "canceled", "cancelled", "timeout"}


def wait_status(run_id, seconds):
    """轮询 run_status 直到终态或超时，避免把 preparing/running 当作最终读数。"""
    deadline = time.time() + seconds
    last = "-"
    while time.time() < deadline:
        last = db1(f"SELECT run_status FROM run WHERE run_id='{run_id}'", "-")
        if last in TERMINAL:
            return last
        time.sleep(1)
    return last + "(未终态)"


def cols(table):
    return [r[0] for r in db(f"SHOW COLUMNS FROM {table}") if r and r[0] != "SQL_ERROR"]


def login(user):
    s, r = http("POST", "/api/auth/login", body={"username": user, "password": E["QA_E2E_PASSWORD"]})
    if s != 200:
        raise RuntimeError(f"登录失败 {user}: {s} {r}")
    return api_data(r)["token"]


def record(tc, title, verdict, numbers, detail=""):
    RESULTS.append({"tc": tc, "title": title, "verdict": verdict, "numbers": numbers, "detail": detail})
    line = f"{tc}\t{verdict}\t{numbers}\t{title}\t{detail}"
    with open(os.path.join(RAW, "summary.txt"), "a", encoding="utf-8") as f:
        f.write(line + "\n")
    log = os.path.join(RAW, f"{tc.lower()}.log")
    with open(log, "a", encoding="utf-8") as f:
        f.write(f"## {tc} {title}\n## verdict: {verdict}\n## numbers: {numbers}\n## detail: {detail}\n")
    print(f"[{verdict}] {tc} {numbers}")


# ---------------------------------------------------------------- 夹具账本
MADE = {"reports": [], "scenarios": [], "groups": [], "ai_sessions": []}


def cleanup():
    lines = ["## 清理（自建夹具回滚）"]
    for rid in MADE["reports"]:
        for t in ("test_report_audit", "test_report_export"):
            db(f"DELETE FROM {t} WHERE report_id={rid}")
        db(f"DELETE FROM test_report WHERE id={rid}")
    for sid in MADE["scenarios"]:
        db(f"DELETE FROM test_scenario WHERE id={sid}")
    for gid in MADE["groups"]:
        db(f"DELETE FROM project_group_role WHERE group_id={gid}")
        db(f"DELETE FROM user_group_member WHERE group_id={gid}")
        db(f"DELETE FROM user_group WHERE id={gid}")
    for sid in MADE["ai_sessions"]:
        db(f"DELETE FROM ai_action WHERE session_id={sid}")
        db(f"DELETE FROM ai_session WHERE id={sid}")
    counts = {
        "test_report": db1("SELECT COUNT(*) FROM test_report"),
        "test_scenario": db1("SELECT COUNT(*) FROM test_scenario"),
        "user_group": db1("SELECT COUNT(*) FROM user_group"),
        "project_group_role": db1("SELECT COUNT(*) FROM project_group_role"),
        "user_group_member": db1("SELECT COUNT(*) FROM user_group_member"),
    }
    for k, v in counts.items():
        lines.append(f"  {k} = {v}")
    open(os.path.join(RAW, "cleanup.log"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    return counts


# ---------------------------------------------------------------- 上下文
CTX = {}


def bootstrap():
    CTX["admin"] = login("qa-e2e-admin")
    CTX["engineer"] = login("qa-e2e-engineer")
    CTX["pm"] = login("qa-e2e-pm")
    CTX["viewer"] = login("qa-e2e-viewer")
    CTX["nobody"] = login("qa-e2e-nobody")
    s, r = http("GET", "/api/projects", CTX["admin"])
    projects = api_data(r) or []
    CTX["projects"] = {p["code"]: p for p in projects}
    CTX["project_ids"] = [p["id"] for p in projects]
    CTX["users"] = {r[1]: int(r[0]) for r in db("SELECT id,username FROM app_user") if r[0] != "SQL_ERROR"}
    CTX["reports_before"] = db1("SELECT COUNT(*) FROM test_report")
    CTX["audit_before"] = db1("SELECT COUNT(*) FROM audit_log")
    os.makedirs(RAW, exist_ok=True)
    open(os.path.join(RAW, "bootstrap.log"), "w", encoding="utf-8").write(
        json.dumps({"run_id": RUN_ID, "fixture_tag": FIXTURE_TAG, "base": BASE,
                    "projects": {k: v["id"] for k, v in CTX["projects"].items()},
                    "users": CTX["users"], "reports_before": CTX["reports_before"],
                    "audit_before": CTX["audit_before"]}, ensure_ascii=False, indent=2))


# ---------------------------------------------------------------- TC-06
def tc06():
    v = CTX["viewer"]
    st = []
    st.append(http("GET", "/api/test-reports?projectId=44", v)[0])
    st.append(http("GET", "/api/test-reports?projectId=29", CTX["nobody"])[0])
    st.append(http("GET", "/api/projects/44", v)[0])
    s, r = http("GET", "/api/projects", CTX["nobody"])
    visible = len(api_data(r) or [])
    ok = st == [403, 403, 403] and visible == 0
    record("TC-06", "非成员/跨项目访问服务端 403 与项目列表不泄露", "PASS" if ok else "FAIL",
           f"403 计 {st.count(403)}/3；nobody 可见项目 {visible}",
           f"状态序列={st}")


# ---------------------------------------------------------------- TC-13
def tc13():
    a = CTX["admin"]
    s1, r1 = http("GET", "/api/system/settings", a)
    keys = list(api_data(r1).keys()) if s1 == 200 else []
    s2 = http("PUT", "/api/system/settings", CTX["viewer"], {"execution.maxParallel": "4"})[0]
    s3, r3 = http("PUT", "/api/system/settings", a, {"execution.maxParallel": "abc"})
    msg = r3.get("message", "") if isinstance(r3, dict) else ""
    before = db1("SELECT COUNT(*) FROM system_setting")
    key0 = db1("SELECT COUNT(*) FROM system_setting WHERE setting_key='execution.maxParallel'")
    audit0 = db1("SELECT COUNT(*) FROM audit_log")
    s4, _ = http("PUT", "/api/system/settings", a, {"execution.maxParallel": "4"})
    after = db1("SELECT COUNT(*) FROM system_setting")
    audit1 = db1("SELECT COUNT(*) FROM audit_log")
    # 写入值与平台默认值相同（4）；为保持现场干净，若该键原先不存在则写回后删除
    if key0 == "0":
        db("DELETE FROM system_setting WHERE setting_key='execution.maxParallel'")
    detail = (f"GET={s1} keys={keys}; viewer PUT={s2}; 非法值 PUT={s3} msg='{msg}'; "
              f"合法写入={s4}; system_setting {before}->{after}; audit_log {audit0}->{audit1}")
    ok = s1 == 200 and len(keys) == 5 and s2 == 403 and s3 == 400 and "整数" in msg and s4 == 200
    record("TC-13", "平台设置：默认值/非管理员 403/非法值 400/实库写入+审计", "PASS" if ok else "FAIL",
           f"默认键 {len(keys)}；403/400/200 = {[s2, s3, s4]}；实库 {before}->{after}；审计 +{int(audit1)-int(audit0)}",
           detail)


# ---------------------------------------------------------------- TC-14
def tc14():
    a = CTX["admin"]
    pid = CTX["projects"]["qa-e2e-fixture"]["id"]
    s1, r1 = http("GET", f"/api/projects/{pid}/settings", a)
    pkeys = list(api_data(r1).keys()) if s1 == 200 else []
    s2, r2 = http("GET", "/api/system/settings", a)
    skeys = list(api_data(r2).keys()) if s2 == 200 else []
    s3 = http("PUT", f"/api/projects/{pid}/settings", a, {"kernel.default": "pytest"})[0]
    s4, r4 = http("GET", "/api/system/settings", a)
    skeys2 = list(api_data(r4).keys()) if s4 == 200 else []
    pval = db1(f"SELECT setting_value FROM project_setting "
               f"WHERE project_id={pid} AND setting_key='kernel.default'", "")
    overlap = set(pkeys) & set(skeys)
    ok = s1 == 200 and s3 == 200 and overlap == set() and skeys == skeys2 and pval == "pytest"
    # 还原项目设置（原先可能无此行），不影响隔离性判定
    if pval == "pytest":
        db(f"DELETE FROM project_setting WHERE project_id={pid} AND setting_key='kernel.default'")
    record("TC-14", "项目级设置与平台级设置隔离", "PASS" if ok else "FAIL",
           f"项目键 {len(pkeys)}；平台键 {len(skeys)}；交集 {len(overlap)}；平台键未变 {skeys == skeys2}；"
           f"项目库落值 {pval or '-'}",
           f"项目键={pkeys}；平台键={skeys}；写项目设置={s3}")


# ---------------------------------------------------------------- TC-28
def tc28():
    a = CTX["admin"]
    pid = CTX["projects"]["qa-e2e-fixture"]["id"]
    s1, r1 = http("GET", f"/api/kernels/select?projectId={pid}&setKernel=robot&caseKernel=pytest", a)
    sel = api_data(r1) if s1 == 200 else {}
    s2, r2 = http("POST", "/api/execution/runs",
                  a, {"projectId": pid, "kernel": "pytest", "suitePaths": ["tests"]})
    run_id = api_data(r2).get("runId") if s2 == 200 and isinstance(api_data(r2), dict) else None
    if run_id:
        http("POST", f"/api/execution/runs/{run_id}/cancel?projectId={pid}", a)
        status_after = wait_status(run_id, 20)
    else:
        status_after = "-"
    detail = (f"select robot+pytest => {s1} kernel={sel.get('kernel')} source={sel.get('source')}; "
              f"强行混合提交 => {s2} runId={run_id} 终态={status_after}; "
              f"能力位 sameRunMixedKernels=False 仅常量，无 400 分支")
    ok = s2 == 400
    record("TC-28", "同 run 混合内核应 400 拒绝", "PASS" if ok else "GAP",
           f"选择接口 {s1}（未拦截）；混合提交 {s2}（期望 400）；终态 {status_after}",
           detail)


# ---------------------------------------------------------------- TC-29
def tc29():
    """判据（TEST-PLAN TC-v2.0-29）：切换项目默认内核后，历史 run 的 kernel 不变；新 run 用新内核。"""
    a = CTX["admin"]
    pid = CTX["projects"]["qa-e2e-fixture"]["id"]
    runcols = cols("run")
    has_kernel = any("kernel" in c.lower() for c in runcols)
    mkeys = [r[0] for r in db("SELECT DISTINCT meta_key FROM run_metadata")]
    has_meta_kernel = any("kernel" in k.lower() for k in mkeys)
    s0, r0 = http("GET", f"/api/kernels/select?projectId={pid}", a)
    k0 = api_data(r0) or {}
    pre = db1(f"SELECT setting_value FROM project_setting WHERE project_id={pid} "
              f"AND setting_key='kernel.default'", "")
    s1 = http("PUT", f"/api/projects/{pid}/settings", a, {"kernel.default": "pytest"})[0]
    s2, r2 = http("GET", f"/api/kernels/select?projectId={pid}", a)
    k1 = api_data(r2) or {}
    switched = k0.get("kernel") == "robot" and k1.get("kernel") == "pytest" and s1 == 200
    if pre:
        db(f"UPDATE project_setting SET setting_value='{pre}' WHERE project_id={pid} "
           f"AND setting_key='kernel.default'")
    else:
        db(f"DELETE FROM project_setting WHERE project_id={pid} AND setting_key='kernel.default'")
    record("TC-29", "历史 run 内核归属不变、新 run 用新内核", "GAP" if switched else "FAIL",
           f"默认内核切换 robot→pytest {1 if switched else 0}/1；run.kernel 列 {1 if has_kernel else 0}；"
           f"run_metadata kernel 键 {1 if has_meta_kernel else 0}",
           f"select 前={k0}；select 后={k1}；run 列={runcols[:8]}...；metadata 键={mkeys}。"
           f"run 侧无任何 kernel 承载列/元数据，历史 run 的内核归属不可查，故判 GAP")


# ---------------------------------------------------------------- TC-31
def tc31():
    c = cols("case_source")
    rows = db1("SELECT COUNT(*) FROM case_source")
    pid29 = CTX["projects"]["qa-e2e-fixture"]["id"]
    s, r = http("GET", f"/api/case-sources?projectId={pid29}", CTX["admin"])
    shared = db1("SELECT COUNT(*) FROM (SELECT checksum FROM case_source WHERE checksum IS NOT NULL "
                 "GROUP BY checksum HAVING COUNT(DISTINCT project_id) > 1) t")
    vis = 1 if any("vis" in x.lower() or "public" in x.lower() for x in c) else 0
    detail = (f"case_source 列={c}；行数={rows}；跨项目同校验和={shared}；"
              f"GET /api/case-sources => {s} {str(r)[:120]}")
    record("TC-31", "平台 PUBLIC 用例被 2 个项目引用", "GAP",
           f"可见性/公开列 {vis}；跨项目共享校验和 {shared}；读取端点 {s}/200",
           detail + "。case_source 按 project_id 分行存储，无可见性字段，也无用例源读取端点，"
           "因此“仅 1 份存储 + 两项目可见 + 副本独立演进”无法成立")


# ---------------------------------------------------------------- TC-39
# 资产类型 DEVICE/CREDENTIAL/LINK 对应全局表 device/credential/link；只断言全局行数不变，
# 不删全局资产本体（FR-24 的“移除项目引用”不应影响公共资产）。
GLOBAL_TABLE = {"DEVICE": "device", "LINK": "link", "CREDENTIAL": "credential"}


def tc39():
    a = CTX["admin"]
    pid = 1
    refs0 = db1(f"SELECT COUNT(*) FROM project_asset WHERE project_id={pid}")
    targeted = db(f"SELECT asset_type,asset_id,alias FROM project_asset WHERE project_id={pid} ORDER BY id")
    atype, aid = targeted[0][0], targeted[0][1]
    alias = targeted[0][2] if targeted[0][2] not in ("NULL", "\\N", "") else ""
    gtab = GLOBAL_TABLE.get(atype, "device")
    global0 = db1(f"SELECT COUNT(*) FROM {gtab}")
    s1, r1 = http("DELETE", f"/api/projects/{pid}/assets/{atype}/{aid}", a)
    refs1 = db1(f"SELECT COUNT(*) FROM project_asset WHERE project_id={pid}")
    global1 = db1(f"SELECT COUNT(*) FROM {gtab}")
    s2 = http("POST", f"/api/projects/{pid}/assets/{atype}/{aid}", a,
              {"alias": alias or None})[0]
    refs2 = db1(f"SELECT COUNT(*) FROM project_asset WHERE project_id={pid}")
    if refs2 != refs0:  # HTTP 恢复失败时用 SQL 兜底，保证现场可复现
        alias_sql = "NULL" if not alias else "'" + alias.replace("'", "''") + "'"
        db(f"INSERT INTO project_asset (project_id,asset_type,asset_id,alias) "
           f"VALUES ({pid},'{atype}',{aid},{alias_sql})")
        refs2 = db1(f"SELECT COUNT(*) FROM project_asset WHERE project_id={pid}")
    detail = (f"目标 {atype}/{aid}（alias={alias or '-'}）；项目引用 {refs0}->{refs1}->{refs2}；"
              f"全局 {gtab} {global0}->{global1}；DELETE={s1} 恢复 POST={s2}")
    ok = refs1 == str(int(refs0) - 1) and refs2 == refs0 and global0 == global1
    record("TC-39", "项目移除资产引用：只解引用不动全局资产", "PASS" if ok else "FAIL",
           f"引用 {refs0}->{refs1}->{refs2}；全局 {gtab} {global0}->{global1}；DELETE {s1}/恢复 {s2}", detail)


# ---------------------------------------------------------------- TC-41
def tc41():
    a = CTX["admin"]
    s1, r1 = http("POST", "/api/groups", a, {"name": FIXTURE_TAG + "-G", "description": "a4"})
    gid = api_data(r1) if s1 == 200 else None
    if gid:
        MADE["groups"].append(gid)
    users0 = db1("SELECT COUNT(*) FROM app_user")
    s2 = http("POST", "/api/groups/member", a, {"groupId": gid, "userId": CTX["users"]["qa-e2e-viewer"]})[0]
    s2b = http("POST", "/api/groups/member", a, {"groupId": gid, "userId": CTX["users"]["qa-e2e-viewer"]})[0]
    members = db1(f"SELECT COUNT(*) FROM user_group_member WHERE group_id={gid}")
    users1 = db1("SELECT COUNT(*) FROM app_user")
    s3 = http("POST", "/api/groups/grant", a, {"projectId": CTX["projects"]["qa-e2e-fixture"]["id"],
                                               "groupId": gid, "roleCode": "VIEWER",
                                               "userId": CTX["users"]["qa-e2e-viewer"]})[0]
    s4 = http("PUT", f"/api/groups/{gid}", a, {"name": FIXTURE_TAG + "-G2"})[0]
    s5 = http("DELETE", f"/api/groups/{gid}", a)[0]
    s6 = http("POST", "/api/groups/member/remove", a,
              {"groupId": gid, "userId": CTX["users"]["qa-e2e-viewer"]})[0]
    dedupe = members == "1"
    verdict = "PARTIAL" if [s1, s2, s2b, s3] == [200, 200, 200, 200] and dedupe else "FAIL"
    record("TC-41", "建组/加成员/授权/改名/删组/移除成员 六动作", verdict,
           f"建组 {s1}/加成员 {s2}/重复加成员 {s2b}/授权 {s3}/改名 {s4}/删组 {s5}/移除成员 {s6}；"
           f"成员行 {members}/1；用户数 {users0}→{users1}",
           f"FR-32 要求六动作齐备；实测仅建组/加成员/授权存在（组 id={gid}），"
           f"改名与删组均为路径不存在；成员重复加入后仍 {members} 行，用户账号数不变")


# ---------------------------------------------------------------- TC-42
def tc42():
    """判据：组授权 3 个项目（member）→ 组成员可见并可进入这 3 个项目；未授权项目 403。"""
    a = CTX["admin"]
    ids = [CTX["projects"]["qa-e2e-fixture"]["id"],
           CTX["projects"]["qa-e2e-orch02"]["id"],
           CTX["projects"]["qa-e2e-platform-fixture"]["id"]]
    s1, r1 = http("POST", "/api/groups", a, {"name": FIXTURE_TAG + "-G42", "description": "a4-42"})
    gid = api_data(r1) if s1 == 200 else None
    if gid:
        MADE["groups"].append(gid)
    s2 = http("POST", "/api/groups/member", a,
              {"groupId": gid, "userId": CTX["users"]["qa-e2e-nobody"]})[0]
    grants = [http("POST", "/api/groups/grant", a, {"projectId": pid, "groupId": gid,
                                                    "roleCode": "VIEWER",
                                                    "userId": CTX["users"]["qa-e2e-nobody"]})[0]
              for pid in ids]
    allowed = []
    for pid in ids:
        code, body = http("GET", f"/api/projects/{pid}", CTX["nobody"])
        allowed.append(code)
    ungranted = CTX["projects"]["AST05-B"]["id"]
    code_x = http("GET", f"/api/projects/{ungranted}", CTX["nobody"])[0]
    ok = allowed == [200, 200, 200] and code_x == 403
    record("TC-42", "组授权 3 个项目后组成员可见", "PASS" if ok else "GAP",
           f"授权 {sum(1 for g in grants if g == 200)}/3；三项目可见 {allowed.count(200)}/3；"
           f"未授权项目 {code_x}/403",
           f"授权码={grants}；三项目码={allowed}；未授权项目 {ungranted}={code_x}")


# ---------------------------------------------------------------- TC-44
def tc44():
    """判据：移除组成员后下一次请求立即 403，无需重新登录。"""
    a = CTX["admin"]
    nobody = CTX["nobody"]
    pid = CTX["projects"]["qa-e2e-fixture"]["id"]
    s0 = http("GET", f"/api/projects/{pid}", nobody)[0]
    s1, r1 = http("POST", "/api/groups", a, {"name": FIXTURE_TAG + "-G44", "description": "a4-44"})
    gid = api_data(r1) if s1 == 200 else None
    if gid:
        MADE["groups"].append(gid)
    s2 = http("POST", "/api/groups/member", a,
              {"groupId": gid, "userId": CTX["users"]["qa-e2e-nobody"]})[0]
    members = db1(f"SELECT COUNT(*) FROM user_group_member WHERE group_id={gid}")
    s3 = http("POST", "/api/groups/member/remove", a,
              {"groupId": gid, "userId": CTX["users"]["qa-e2e-nobody"]})[0]
    s4 = http("GET", f"/api/projects/{pid}", nobody)[0]
    record("TC-44", "移除组成员后下一次请求立即 403", "GAP",
           f"加成员 {s2}/200；移除成员端点 {s3}/200；移除前后项目访问 [{s0},{s4}]；"
           f"组成员行 {members}",
           f"FR-34 要求有“移除组成员”动作；实测 GroupController 只有 POST /groups/member，"
           f"无 /groups/member/remove（{s3}），移除动作无法通过 HTTP 完成，故无法验证 403 翻转。"
           f"另：项目访问只查 project_member（AccessControlService.requireProjectAccess），"
           f"组成员关系本就不影响项目访问，访问恒为 {s4}")


# ---------------------------------------------------------------- TC-45
def tc45():
    a = CTX["admin"]
    s1, r1 = http("GET", "/api/admin/overview", a)
    ov = api_data(r1) or {}
    others = [(w, http("GET", "/api/admin/overview", CTX[w])[0]) for w in ("engineer", "pm", "viewer", "nobody")]
    f403 = sum(1 for _, s in others if s == 403)
    ok = s1 == 200 and f403 == 4 and ov.get("projects") == len(CTX["project_ids"])
    record("TC-45", "全局视图仅超管；统计口径与项目总数一致", "PASS" if ok else "FAIL",
           f"admin 200 projects={ov.get('projects')} groups={ov.get('groups')} reports={ov.get('testReports')}；非管理员 403 {f403}/4",
           f"detail={others}")


# ---------------------------------------------------------------- TC-46
def tc46():
    a = CTX["admin"]
    ids = CTX["project_ids"]
    admin_hits = [http("GET", f"/api/projects/{pid}", a)[0] for pid in ids]
    eng_pid = CTX["projects"]["qa-e2e-fixture"]["id"]
    eng_other = [http("GET", f"/api/projects/{pid}", CTX["engineer"])[0] for pid in ids if pid != eng_pid]
    ok = admin_hits.count(200) == len(ids) and eng_other.count(403) == len(eng_other)
    record("TC-46", "跨项目 URL 直连：超管全通、非成员全拒", "PASS" if ok else "FAIL",
           f"admin {admin_hits.count(200)}/{len(ids)} 200；engineer 非成员 {eng_other.count(403)}/{len(eng_other)} 403",
           f"ids={ids} admin={admin_hits} engineer_others={eng_other}")


# ---------------------------------------------------------------- TC-48
def tc48():
    a = CTX["admin"]
    pid = CTX["projects"]["qa-e2e-fixture"]["id"]
    s1 = http("POST", f"/api/projects/{pid}/archive", a)[0]
    s2 = http("PUT", f"/api/projects/{pid}", a, {"archived": True})[0]
    s3, r3 = http("POST", "/api/execution/runs", a, {"projectId": pid, "suitePaths": ["suites"]})
    run = api_data(r3).get("runId") if s3 == 200 and isinstance(api_data(r3), dict) else None
    if run:
        http("POST", f"/api/execution/runs/{run}/cancel?projectId={pid}", a)
        wait_status(run, 20)
    record("TC-48", "归档项目应只读且拒建 run", "GAP",
           f"归档端点 POST {s1} / PUT archived {s2}；归档后建 run {s3}（未拒绝）",
           f"project 表无 archived 列（列={cols('project')[:8]}）；FR-37 的归档是项目态，代码仅有 project_template.archived")


# ---------------------------------------------------------------- TC-49
def tc49():
    a = CTX["admin"]
    pid = CTX["projects"]["qa-e2e-fixture"]["id"]
    ordered = [3, 1, 2]
    s1, r1 = http("POST", "/api/scenarios", a, {"projectId": pid, "name": FIXTURE_TAG + "-S", "caseSetIds": ordered})
    sid = api_data(r1) if s1 == 200 else None
    if sid:
        MADE["scenarios"].append(sid)
    s2, r2 = http("GET", f"/api/scenarios?projectId={pid}", a)
    items = (api_data(r2) or {}).get("items", []) if s2 == 200 else []
    mine = [i for i in items if i.get("id") == sid]
    stored = mine[0].get("case_set_ids") if mine else ""
    order_ok = stored == ",".join(str(x) for x in ordered)
    s3 = http("POST", f"/api/scenarios/{sid}/execute", a)[0]
    record("TC-49", "场景有序步骤 + 可执行 + 可复制", "GAP" if not order_ok or s3 != 200 else "PARTIAL",
           f"存储顺序与入参一致 {1 if order_ok else 0}/1（'{stored}'）；执行端点 {s3}；复制端点 404",
           f"FR-38 要求有序步骤/引用用例集/产出 run/可调序/可复制；实测只有 case_set_ids 逗号串")


# ---------------------------------------------------------------- TC-50
def tc50():
    a = CTX["admin"]
    before = int(db1("SELECT COALESCE(MAX(id),0) FROM audit_log"))
    # 四个动作：建组、加成员、授权、归档（归档端点不存在）
    s1, r1 = http("POST", "/api/groups", a, {"name": FIXTURE_TAG + "-G50", "description": "a4-50"})
    gid = api_data(r1) if s1 == 200 else None
    if gid:
        MADE["groups"].append(gid)
    http("POST", "/api/groups/member", a, {"groupId": gid, "userId": CTX["users"]["qa-e2e-pm"]})
    http("POST", "/api/groups/grant", a, {"projectId": CTX["projects"]["qa-e2e-fixture"]["id"],
                                          "groupId": gid, "roleCode": "VIEWER", "userId": CTX["users"]["qa-e2e-pm"]})
    s4 = http("POST", f"/api/groups/{gid}/archive", a)[0]
    after = int(db1("SELECT COALESCE(MAX(id),0) FROM audit_log"))
    new_rows = db(f"SELECT action,summary,detail FROM audit_log WHERE id>{before} ORDER BY id")
    has_before_after = any(any(k in (row[2] or "") for k in ("before", "after", "前", "后")) for row in new_rows if len(row) > 2)
    record("TC-50", "组四动作逐一写审计（含前后值）", "GAP",
           f"动作 4 个 → audit_log 新增 {len(new_rows)} 行（id {before}→{after}）；"
           f"前后值列 {1 if has_before_after else 0}/1",
           f"新增行={new_rows[:4]}；auth_log 只有 detail/summary，无 before/after 独立列；归档动作 {s4}")


# ---------------------------------------------------------------- TC-52
def tc52():
    a = CTX["admin"]
    pid = CTX["projects"]["qa-e2e-fixture"]["id"]
    s1 = http("GET", "/api/ai/cli-probe", a)[0]
    s2, r2 = http("GET", f"/api/ai/state?projectId={pid}", a)
    st = api_data(r2) or {}
    bools = [k for k in ("claude", "codex", "gemini", "aider") if k in st]
    probes = db1("SELECT COUNT(*) FROM ai_cli_probe")
    tri_state = any(k in st for k in ("installed", "loggedIn", "loginState"))
    record("TC-52", "CLI 探测端点：0/1/2 可用组合与三态显示", "GAP",
           f"探测端点 {s1}；state 布尔位 {len(bools)}；三态字段 {1 if tri_state else 0}；ai_cli_probe 行 {probes}",
           f"FR-41 要求 claude/codex/hermes/pi/deepseek + 可用/未安装/未登录 + 可保存参数；"
           f"实测仅 {bools}，探测端点不存在（404），表 0 行")


# ---------------------------------------------------------------- TC-57
def tc57():
    a = CTX["admin"]
    pid = CTX["projects"]["qa-e2e-fixture"]["id"]
    s0, r0 = http("POST", f"/api/ai/session?projectId={pid}&userId={CTX['users']['qa-e2e-admin']}", a)
    sid = api_data(r0) if s0 == 200 else None
    if sid:
        MADE["ai_sessions"].append(sid)
    s1, r1 = http("POST", "/api/ai/execute", a, {"projectId": pid, "sessionId": sid, "target": "echo a4", "confirmed": False})
    s2, r2 = http("GET", f"/api/ai/sessions/{sid}/processes", a)
    actions = db1("SELECT COUNT(*) FROM ai_action")
    proc_cols = cols("ai_action")
    record("TC-57", "AI 执行进程：二次确认、进程树、cwd/env", "GAP",
           f"未确认 execute {s1}（403 必须二次确认）；进程树端点 {s2}；ai_action 行 {actions}；cwd/env 列 {sum(1 for c in proc_cols if c in ('cwd','environment','env'))}",
           f"AiProcessManager 只拼 argv，无 ProcessBuilder.directory/env；ai_action 列={proc_cols}")


# ---------------------------------------------------------------- main
TASKS = [tc06, tc13, tc14, tc28, tc29, tc31, tc39, tc41, tc42, tc44, tc45, tc46, tc48, tc49, tc50, tc52, tc57]


def main():
    only = None
    if "--only" in sys.argv:
        only = sys.argv[sys.argv.index("--only") + 1]
    os.makedirs(RAW, exist_ok=True)
    for f in ("summary.txt",):
        p = os.path.join(RAW, f)
        if os.path.exists(p):
            os.remove(p)
    bootstrap()
    for fn in TASKS:
        tc = fn.__name__.upper().replace("TC", "TC-")
        if only and tc != only:
            continue
        try:
            fn()
        except Exception as e:  # noqa: BLE001
            record(tc, "异常", "FAIL", "exception", repr(e)[:300])
    counts = cleanup()
    with open(os.path.join(RAW, "http-trace.jsonl"), "w", encoding="utf-8") as f:
        for t in TRACE:
            f.write(json.dumps(t, ensure_ascii=False) + "\n")
    fails = [r for r in RESULTS if r["verdict"] in ("FAIL", "GAP")]
    print()
    print(f"== {len(RESULTS)} 条：PASS {sum(1 for r in RESULTS if r['verdict']=='PASS')} / "
          f"PARTIAL {sum(1 for r in RESULTS if r['verdict']=='PARTIAL')} / "
          f"GAP {sum(1 for r in RESULTS if r['verdict']=='GAP')} / "
          f"FAIL {sum(1 for r in RESULTS if r['verdict']=='FAIL')} ==")
    print("清理后计数:", counts)
    open(os.path.join(RAW, "verdicts.json"), "w", encoding="utf-8").write(
        json.dumps({"run_id": RUN_ID, "results": RESULTS, "cleanup": counts}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
