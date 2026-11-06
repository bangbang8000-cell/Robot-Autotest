#!/usr/bin/env python3
"""IAM-05 / HRD-01 / EDT-05 真实 HTTP 全链路验收（本机 WSL + 真实库 + 真实账号）。

背景
----
这三个包的缺口都是同一句：「矩阵是控制器级 MockMvc，真实账号 HTTP 全链路未跑」。
本脚本把 MockMvc 断言的**同一批端点**打成真实 HTTP（经 8001 统一入口 → 8081），
账号是本地库里真实存在的行、真实登录拿令牌，因此堵的正是那条缺口。

它不替代 MockMvc 测试：MockMvc 覆盖 36 / 113 条细粒度矩阵，本脚本只复刻其中
「需要真实链路才有意义」的那一批（认证 → 授权 → 跨项目 → 防枚举 → 编辑器只读）。

夹具（幂等，可反复跑）
--------------------
- 项目 `qa-e2e-fixture`：专供本脚本，不动「演示项目」「AST-05 共享演示」
- 账号 `qa-e2e-{viewer,engineer,pm,admin,nobody}`，口令见 FIXTURE_PASSWORD
- 角色写入 `project_member.member_role`；admin 另加平台 `user_role=admin`
- 刻意不碰既有 4 个账号的口令（无需知道，也不该改）

用法
----
    python3 docs/evidence/v1.0/auth-http-e2e/run.py [--base http://127.0.0.1:8001]
退出码 0 = 全部断言通过；1 = 有失败（明细见输出与 log 文件）。
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import pathlib
import subprocess
import sys
import urllib.error
import urllib.request
import uuid

FIXTURE_PASSWORD = "QaE2e!2026-fixture"
PROJECT_CODE = "qa-e2e-fixture"
PROJECT_NAME = "验收夹具·HTTP 全链路"
ACCOUNTS = {
    "viewer": ("qa-e2e-viewer", "VIEWER"),
    "engineer": ("qa-e2e-engineer", "ENGINEER"),
    "pm": ("qa-e2e-pm", "PM"),
    "admin": ("qa-e2e-admin", "ADMIN"),
    "nobody": ("qa-e2e-nobody", None),
}

ROOT = pathlib.Path(__file__).resolve().parents[4]
LOGFILE = pathlib.Path(__file__).resolve().parent / "run.log"


# ---------------------------------------------------------------- 数据库底座
def load_db_config() -> dict:
    env_file = pathlib.Path.home() / ".config" / "robot-autotest" / "env"
    cfg: dict[str, str] = {}
    for line in env_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        cfg[k.strip()] = v.strip().strip('"').strip("'")
    return cfg


def mysql(sql: str, cfg: dict) -> str:
    db = cfg.get("DB_NAME") or "robot_dev"
    cmd = [
        "mysql", "-u", cfg.get("DB_USERNAME", "root"),
        "-h", cfg.get("DB_HOST", "127.0.0.1"),
        "-P", cfg.get("DB_PORT", "3306"),
        "-N", "-B", "-e", sql.replace("@DB@", db),
    ]
    r = subprocess.run(cmd, env=dict(os.environ, MYSQL_PWD=cfg.get("DB_PASSWORD", "")),
                       capture_output=True, text=True)
    if r.returncode != 0:
        raise SystemExit(f"SQL 失败: {sql[:80]} :: {r.stderr.strip()[:300]}")
    return r.stdout.strip()


def pbkdf2_hash(raw: str) -> str:
    """与 PasswordHasher.hash 同格式：pbkdf2$sha256$120000$saltB64$hashB64。"""
    salt = os.urandom(16)
    derived = hashlib.pbkdf2_hmac("sha256", raw.encode("utf-8"), salt, 120_000, 32)
    return (f"pbkdf2$sha256$120000$"
            f"{base64.b64encode(salt).decode()}$"
            f"{base64.b64encode(derived).decode()}")


def seed(cfg: dict) -> tuple[int, dict[str, int]]:
    """建项目 + 账号 + 成员关系；返回 (projectId, {角色: userId})。"""
    mysql(
        "INSERT INTO @DB@.project (code, name, description, status, visibility) "
        f"VALUES ('{PROJECT_CODE}', '{PROJECT_NAME}', 'HTTP 全链路验收夹具，勿手工改', 1, 'private') "
        "ON DUPLICATE KEY UPDATE name = VALUES(name);", cfg)
    project_id = int(mysql(f"SELECT id FROM @DB@.project WHERE code = '{PROJECT_CODE}';", cfg))

    pwd_hash = pbkdf2_hash(FIXTURE_PASSWORD)
    user_ids: dict[str, int] = {}
    for role, (username, member_role) in ACCOUNTS.items():
        mysql(
            "INSERT INTO @DB@.app_user (username, display_name, password_hash, status) "
            f"VALUES ('{username}', 'HTTP E2E {role}', '{pwd_hash}', 1) "
            "ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), "
            "status = 1, failed_login_count = 0, locked_until = NULL, password_expires_at = NULL;",
            cfg)
        user_ids[role] = int(mysql(f"SELECT id FROM @DB@.app_user WHERE username = '{username}';", cfg))
        if member_role is None:
            mysql("DELETE FROM @DB@.project_member "
                  f"WHERE project_id = {project_id} AND user_id = {user_ids[role]};", cfg)
            continue
        mysql(
            "INSERT INTO @DB@.project_member (project_id, user_id, member_role) "
            f"VALUES ({project_id}, {user_ids[role]}, '{member_role}') "
            "ON DUPLICATE KEY UPDATE member_role = VALUES(member_role);", cfg)

    # admin 需要平台级 admin 角色才能跨项目读
    mysql(
        "INSERT INTO @DB@.user_role (user_id, role_id) "
        f"SELECT {user_ids['admin']}, id FROM @DB@.role WHERE code = 'admin' "
        "AND NOT EXISTS (SELECT 1 FROM @DB@.user_role ur WHERE ur.user_id = "
        f"{user_ids['admin']} AND ur.role_id = @DB@.role.id);", cfg)
    return project_id, user_ids


def second_project_id(cfg: dict) -> int:
    """取一个非夹具项目，用于「跨项目」与「防枚举」断言。"""
    out = mysql(f"SELECT id FROM @DB@.project WHERE code <> '{PROJECT_CODE}' ORDER BY id LIMIT 1;", cfg)
    return int(out) if out else 0


# ---------------------------------------------------------------- HTTP 底座
class Http:
    def __init__(self, base: str) -> None:
        self.base = base.rstrip("/")

    def request(self, method: str, path: str, token: str | None = None,
                body: dict | None = None, headers: dict | None = None) -> tuple[int, str]:
        url = self.base + path
        data = None
        hdrs = {"Accept": "application/json"}
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            hdrs["Content-Type"] = "application/json"
        if token:
            hdrs["Authorization"] = "Bearer " + token
        if headers:
            hdrs.update(headers)
        req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
        try:
            with urllib.request.urlopen(req, timeout=20) as resp:
                return resp.status, resp.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as exc:
            return exc.code, exc.read().decode("utf-8", "replace")
        except urllib.error.URLError as exc:
            return 0, f'{{"transportError":"{exc.reason}"}}'

    def login(self, username: str, password: str) -> tuple[int, str]:
        return self.request("POST", "/api/auth/login",
                            body={"username": username, "password": password})


# ---------------------------------------------------------------- 断言框架
class Case:
    def __init__(self) -> None:
        self.rows: list[tuple[str, str, bool, str]] = []

    def check(self, cid: str, desc: str, actual: int, expect: tuple[int, ...],
              detail: str = "") -> None:
        ok = actual in expect
        self.rows.append((cid, desc, ok, f"HTTP {actual} (期望 {expect}) {detail}".strip()))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:8001")
    args = ap.parse_args()

    cfg = load_db_config()
    project_id, users = seed(cfg)
    other = second_project_id(cfg)
    http = Http(args.base)
    c = Case()

    out: list[str] = []
    out.append("# IAM-05 / HRD-01 / EDT-05 真实 HTTP 全链路验收")
    out.append("")
    out.append(f"- 入口：`{args.base}`（8001 → 8081 反代）")
    out.append(f"- 夹具项目：`{PROJECT_CODE}` id={project_id}；跨项目对照 id={other}")
    out.append(f"- 账号：{', '.join(u for u, _ in ACCOUNTS.values())}（口令仅本地 dev 夹具）")
    out.append("")

    # 1. 未认证
    c.check("IAM05-HTTP-01", "缺少令牌 GET 用例列表", http.request("GET", f"/api/projects/{project_id}/cases")[0], (401,))
    c.check("IAM05-HTTP-02", "伪造令牌 GET 用例列表",
            http.request("GET", f"/api/projects/{project_id}/cases", "tk-forged")[0], (401,))
    c.check("IAM05-HTTP-03", "空令牌（Bearer 后双空格）GET 用例列表",
            http.request("GET", f"/api/projects/{project_id}/cases", "  ")[0], (401,))

    # 2. 登录
    tokens: dict[str, str] = {}
    for role, (username, _) in ACCOUNTS.items():
        code, body = http.login(username, FIXTURE_PASSWORD)
        ok = code == 200
        if ok:
            payload = json.loads(body)
            tokens[role] = (payload.get("data") or {}).get("token") or ""
            ok = bool(tokens[role])
        c.rows.append((f"AUTH-HTTP-login-{role}", f"{username} 登录并取令牌", ok,
                       f"HTTP {code} token={'有' if tokens.get(role) else '无'}"))
        if not ok:
            out.append(f"> 登录失败：{role} HTTP {code} {body[:200]}")

    # 3. 访客（VIEWER）
    v = tokens.get("viewer", "")
    c.check("IAM05-HTTP-04", "访客读用例列表", http.request("GET", f"/api/projects/{project_id}/cases", v)[0], (200,))
    c.check("IAM05-HTTP-05", "访客建用例", http.request("POST", f"/api/projects/{project_id}/cases", v,
                                                   {"caseCode": "TC-E2E-V", "title": "访客越权建用例"})[0], (403,))
    c.check("IAM05-HTTP-06", "访客建运行", http.request("POST", "/api/execution/runs", v,
                                                   {"projectId": project_id, "suitePaths": ["suites/a.robot"],
                                                    "concurrency": 1, "priority": 0,
                                                    "timeoutSeconds": 60})[0], (403,))
    c.check("IAM05-HTTP-07", "访客删设备", http.request("DELETE", f"/api/projects/{project_id}/devices/1", v)[0], (403,))

    # 4. 工程师（ENGINEER）
    e = tokens.get("engineer", "")
    # 随机 caseCode + 建完即删：固定编号在第二轮会撞唯一键返回 409，把「权限断言」变成「脏数据断言」
    code_c, body_c = http.request("POST", f"/api/projects/{project_id}/cases", e,
                                  {"caseCode": f"TC-E2E-{uuid.uuid4().hex[:8]}", "title": "工程师建用例"})
    c.rows.append(("IAM05-HTTP-08", "工程师建用例", code_c in (200, 201), f"HTTP {code_c}"))
    if code_c in (200, 201):
        try:
            created_id = (json.loads(body_c).get("data") or {}).get("id")
        except json.JSONDecodeError:
            created_id = None
        if created_id:
            code_rm, _ = http.request("DELETE", f"/api/projects/{project_id}/cases/{created_id}", e)
            c.rows.append(("IAM05-HTTP-08b", "工程师删除刚建的用例（恢复夹具现场）",
                           code_rm in (200, 204), f"HTTP {code_rm}"))
    c.check("IAM05-HTTP-09", "工程师删设备", http.request("DELETE", f"/api/projects/{project_id}/devices/1", e)[0], (403,))
    c.check("IAM05-HTTP-10", "工程师管成员", http.request("POST", f"/api/projects/{project_id}/members", e,
                                                   {"userId": users["nobody"], "roleCode": "VIEWER"})[0], (403,))

    # 5. 项目经理（PM）
    pm = tokens.get("pm", "")
    c.check("IAM05-HTTP-11", "项目经理管成员", http.request("POST", f"/api/projects/{project_id}/members", pm,
                                                   {"userId": users["nobody"], "roleCode": "VIEWER"})[0], (200, 201))
    # 幂等：PM 断言把 nobody 加成了 VIEWER，必须立刻撤销，否则「无角色用户」两条断言下一轮失效
    code_del, _ = http.request("DELETE", f"/api/projects/{project_id}/members/{users['nobody']}", pm)
    c.rows.append(("IAM05-HTTP-11b", "项目经理移除刚加的成员（恢复夹具现场）", code_del in (200, 204),
                   f"HTTP {code_del}"))

    c.check("IAM05-HTTP-12", "项目经理建项目", http.request("POST", "/api/projects", pm,
                                                   {"code": "pm-should-fail", "name": "PM 越权建项目"})[0], (403,))

    # 6. 平台管理员（ADMIN）
    a = tokens.get("admin", "")
    c.check("IAM05-HTTP-13", "管理员跨项目读设备列表",
            http.request("GET", f"/api/projects/{other}/devices", a)[0], (200,))

    # 7. 跨项目（viewer 不属于 other）
    c.check("IAM05-HTTP-14", "非成员读别的项目设备", http.request("GET", f"/api/projects/{other}/devices", v)[0], (403,))
    c.check("IAM05-HTTP-15", "非成员读别的项目用例", http.request("GET", f"/api/projects/{other}/cases", v)[0], (403,))
    c.check("IAM05-HTTP-16", "非成员读别的项目文件工作区",
            http.request("GET", f"/api/projects/{other}/files", v)[0], (403,))

    # 8. 防枚举：存在 vs 不存在必须同响应体
    code_exist, body_exist = http.request("GET", f"/api/projects/{other}/devices", v)
    code_absent, body_absent = http.request("GET", "/api/projects/999999/devices", v)
    same = code_exist == code_absent == 403 and body_exist == body_absent
    c.rows.append(("IAM05-HTTP-17", "存在项目与不存在项目同 403 同响应体", same,
                   f"exist={code_exist} absent={code_absent} bodySame={body_exist == body_absent}"))

    # 9. 无角色用户
    n = tokens.get("nobody", "")
    code_n, body_n = http.request("GET", "/api/projects", n)
    empty = code_n == 200 and body_n.replace(" ", "").find('"data":[]') >= 0
    c.rows.append(("IAM05-HTTP-18", "无角色用户项目列表为空集", empty, f"HTTP {code_n} {body_n[:120]}"))
    c.check("IAM05-HTTP-19", "无角色用户访问项目资源", http.request("GET", f"/api/projects/{project_id}/cases", n)[0], (403,))

    # 10. 项目头校验
    code_h, _ = http.request("GET", f"/api/projects/{project_id}/cases", v, headers={"X-Project-Id": "abc"})
    c.check("IAM05-HTTP-20", "X-Project-Id 非数字", code_h, (400,))

    # 11. EDT-05：只读用户的编辑器写闸门
    code_p, body_p = http.request("GET", f"/api/editor/permissions?projectId={project_id}", v)
    readonly_ok = code_p == 200 and '"canWrite":false' in body_p.replace(" ", "")
    c.rows.append(("EDT05-HTTP-01", "访客读编辑器权限且 canWrite=false", readonly_ok,
                   f"HTTP {code_p} {body_p[:120]}"))
    code_pe, body_pe = http.request("GET", f"/api/editor/permissions?projectId={project_id}", e)
    writable_ok = code_pe == 200 and '"canWrite":true' in body_pe.replace(" ", "")
    c.rows.append(("EDT05-HTTP-04", "工程师读编辑器权限且 canWrite=true", writable_ok,
                   f"HTTP {code_pe} {body_pe[:120]}"))
    c.check("EDT05-HTTP-02", "访客写文件工作区",
            http.request("PUT", f"/api/projects/{project_id}/files", v,
                         {"path": "guard.robot", "content": "*** Test Cases ***\n"})[0], (403,))
    c.check("EDT05-HTTP-03", "工程师写文件工作区",
            http.request("PUT", f"/api/projects/{project_id}/files", e,
                         {"path": "guard.robot", "content": "*** Test Cases ***\n"})[0], (200, 201))

    # 12. EDT-04：V2_5 迁移落地 + 平台级词条（project_id=0）写权限
    flyway = mysql("SELECT version FROM @DB@.flyway_schema_history "
                   "WHERE version LIKE '2.5%' ORDER BY installed_rank DESC LIMIT 1;", cfg)
    c.rows.append(("EDT04-KW-01", "V2_5 编辑器词条迁移已在真实库应用", flyway.startswith("2.5"),
                   f"flyway version={flyway or '缺失'}"))

    total = mysql("SELECT COUNT(*) FROM @DB@.editor_keyword;", cfg)
    platform = mysql("SELECT COUNT(*) FROM @DB@.editor_keyword WHERE project_id = 0;", cfg)
    builtin = mysql("SELECT COUNT(*) FROM @DB@.editor_keyword WHERE project_id = 0 AND source = 'BuiltIn';", cfg)
    seeds_ok = int(total or 0) >= 29 and int(platform or 0) >= 29 and int(builtin or 0) >= 18
    c.rows.append(("EDT04-KW-02", "种子词条 ≥29 条且平台级 ≥29（BuiltIn ≥18）", seeds_ok,
                   f"total={total} platform={platform} builtin={builtin}"))

    code_kv, body_kv = http.request("GET", "/api/editor/keywords?projectId=0&q=*&limit=5", v)
    c.check("EDT04-KW-03", "访客可检索平台级词条", code_kv, (200,))

    code_ke, _ = http.request("POST", "/api/editor/keywords", e,
                              {"projectId": 0, "library": "BuiltIn", "name": "QA 越权词条",
                               "args": [], "doc": "engineer 不该能写平台级", "source": "library"})
    c.check("EDT04-KW-04", "工程师写平台级词条（project_id=0）", code_ke, (403,))

    code_ka, body_ka = http.request("POST", "/api/editor/keywords", a,
                                    {"projectId": 0, "library": "BuiltIn", "name": "QA-E2E-KW",
                                     "args": [], "doc": "admin 写入验证", "source": "library"})
    created_kw = None
    if code_ka == 200:
        try:
            created_kw = (json.loads(body_ka).get("data") or {}).get("id")
        except json.JSONDecodeError:
            created_kw = None
    c.rows.append(("EDT04-KW-05", "管理员写平台级词条", code_ka == 200, f"HTTP {code_ka}"))
    if created_kw:
        code_kd, _ = http.request("DELETE", f"/api/editor/keywords/{created_kw}?projectId=0", a)
        c.rows.append(("EDT04-KW-05b", "管理员删除刚建的词条（恢复夹具现场）",
                       code_kd in (200, 204), f"HTTP {code_kd}"))

    # ------------------------------------------------------------- 输出
    passed = sum(1 for r in c.rows if r[2])
    total = len(c.rows)
    out.append("## 断言明细")
    out.append("")
    out.append("| # | 断言 | 结果 | 实测 |")
    out.append("|---|---|---|---|")
    for cid, desc, ok, detail in c.rows:
        out.append(f"| {cid} | {desc} | {'PASS' if ok else '**FAIL**'} | {detail} |")
    out.append("")
    out.append(f"**合计 {passed}/{total} 通过**")
    out.append("")

    text = "\n".join(out)
    LOGFILE.write_text(text, encoding="utf-8")
    print(text)
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
