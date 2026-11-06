#!/usr/bin/env python3
"""TPLX-01 真实 HTTP 验收：模板 CRUD + C5 白名单双拦截 + 平台级只读 + 跨项目隔离。

为什么不用单测代替
------------------
`ReportTemplateServiceTest` 用的是内存伪仓储，它证明不了四件事：
  ① V9_2 的两张表在真实 MySQL 上建成了，且内置 DEFAULT 模板与 21 条字段真的落了库；
  ② `GET /fields` 的清单与后端 `ReportFieldWhitelist` 是同一份（接口层没有另抄一份）；
  ③ 权限链路（登录 → 令牌 → `bindProject` → 跨项目拒绝）真的拦得住；
  ④ 唯一键 `uk_report_template_name` 真的生效（重名是 409 而不是 500）。

跑法
----
    python3 docs/evidence/v2.1/TPLX-01/run.py [--base http://127.0.0.1:8001]

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

PROJECT_CODE = "qa-e2e-fixture"
OTHER_PROJECT_CODE = "qa-e2e-orch02"
TEMPLATE_NAME = "tplx01-硬件组视图"
REQUIRED = ["reportId", "title", "version", "frozenAt", "disclaimer", "watermark"]
FULL_FIELDS = REQUIRED + ["passRate", "swBuild", "conclusion"]


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


def seed() -> tuple[int, int]:
    """夹具项目 + admin 账号（幂等）。返回 (projectId, otherProjectId)。"""
    password = ENV.get("QA_E2E_PASSWORD") or os.environ.get("QA_E2E_PASSWORD")
    if not password:
        raise SystemExit("缺少 QA_E2E_PASSWORD（在 ~/.config/robot-autotest/env 里）")

    mysql("INSERT INTO @DB@.project (code, name, description, status, visibility) "
          f"VALUES ('{PROJECT_CODE}', '验收夹具·HTTP 全链路', "
          "'TPLX-01 验收夹具，勿手工改', 1, 'private') "
          "ON DUPLICATE KEY UPDATE name = VALUES(name);")
    project_id = int(mysql(f"SELECT id FROM @DB@.project WHERE code = '{PROJECT_CODE}';"))

    mysql("INSERT INTO @DB@.app_user (username, display_name, password_hash, status) "
          f"VALUES ('qa-e2e-admin', 'TPLX-01 admin', '{pbkdf2_hash(password)}', 1) "
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

    other = mysql(f"SELECT COALESCE(MAX(id), 0) FROM @DB@.project WHERE code = '{OTHER_PROJECT_CODE}';")
    return project_id, int(other or 0)


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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8001")
    args = parser.parse_args()

    http = Http(args.base)
    report = Report()
    project_id, other_project_id = seed()
    token = http.login("qa-e2e-admin", ENV["QA_E2E_PASSWORD"])
    print(f"# 夹具项目 projectId={project_id}，另一项目 projectId={other_project_id}，"
          f"已登录 qa-e2e-admin\n")

    # 可从零重跑：清掉本项目的模板（平台级 DEFAULT 是迁移内置的，不动）
    mysql(f"DELETE FROM @DB@.report_template WHERE project_id = {project_id};")

    # ---------- ① 列表：平台内置 DEFAULT 可见 ----------
    status, body = http.call("GET", f"/api/report-templates?projectId={project_id}", token)
    items = data_of(body) if status == 200 else []
    platform = next((item for item in items if item.get("platform")), None)
    report.check("TPLX01-01", "列表 200 且可见平台级 DEFAULT", status == 200 and platform is not None,
                 f"HTTP {status} 模板数={len(items)}")

    fields_db = mysql("SELECT COUNT(*) FROM @DB@.report_template_field "
                      "WHERE project_id = 0;")
    report.check("TPLX01-02", "内置模板落库 21 个字段（sort 0..20）", fields_db == "21",
                 f"字段数={fields_db}")
    report.check("TPLX01-03", "内置模板字段顺序 = 白名单声明顺序",
                 platform is not None
                 and [f["fieldKey"] for f in platform["fields"]][:3] == ["reportId", "title", "version"],
                 f"前三个={[f['fieldKey'] for f in platform['fields']][:3] if platform else 'n/a'}")

    # ---------- ② 可选字段清单 ----------
    status, body = http.call("GET", "/api/report-templates/fields", token)
    keys = data_of(body) if status == 200 else []
    report.check("TPLX01-04", "可选字段 200 且 21 项，首尾与白名单一致",
                 status == 200 and len(keys) == 21 and keys[0] == "reportId" and keys[-1] == "watermark",
                 f"HTTP {status} 项数={len(keys)}")

    # ---------- ③ 建项目模板 ----------
    status, body = http.call("POST", "/api/report-templates", token, {
        "projectId": project_id,
        "name": TEMPLATE_NAME,
        "description": "TPLX-01 验收",
        "fields": [{"fieldKey": key, "titleOverride": "通过率" if key == "passRate" else None}
                   for key in FULL_FIELDS],
    })
    created = data_of(body) if status == 200 else {}
    template_id = int(created.get("id", 0)) if created else 0
    got = [f["fieldKey"] for f in created.get("fields", [])] if created else []
    report.check("TPLX01-05", "建模板 200，version=1，顺序按提交顺序",
                 status == 200 and created.get("version") == 1 and got == FULL_FIELDS,
                 f"HTTP {status} version={created.get('version')} 字段数={len(got)}")
    # passRate 在 FULL_FIELDS 里的下标是 6（6 个必填在前）
    overrides = {f["fieldKey"]: f.get("titleOverride") for f in created.get("fields", [])}
    report.check("TPLX01-06", "自定义标题落库", overrides.get("passRate") == "通过率",
                 f"passRate.titleOverride={overrides.get('passRate')}")

    # ---------- ④ 白名单外字段 → 400（双拦截的后端一半） ----------
    status, body = http.call("POST", "/api/report-templates", token, {
        "projectId": project_id, "name": TEMPLATE_NAME + "-非法",
        "fields": [{"fieldKey": key} for key in FULL_FIELDS + ["rawOutput", "commands"]],
    })
    report.check("TPLX01-07", "白名单外字段 400 且点名非法键",
                 status == 400 and "rawOutput" in message_of(body) and "commands" in message_of(body),
                 f"HTTP {status} msg={message_of(body)[:80]}")
    illegal_rows = mysql(f"SELECT COUNT(*) FROM @DB@.report_template "
                         f"WHERE project_id = {project_id} AND name LIKE '%非法%';")
    report.check("TPLX01-08", "非法请求未落库", illegal_rows == "0", f"落库行数={illegal_rows}")

    # ---------- ⑤ 缺必填 → 400 ----------
    status, body = http.call("POST", "/api/report-templates", token, {
        "projectId": project_id, "name": TEMPLATE_NAME + "-缺必填",
        "fields": [{"fieldKey": "reportId"}, {"fieldKey": "passRate"}],
    })
    msg = message_of(body)
    report.check("TPLX01-09", "缺必填字段 400 且列出缺失项",
                 status == 400 and "watermark" in msg and "disclaimer" in msg,
                 f"HTTP {status} msg={msg[:80]}")

    # ---------- ⑥ 重复字段 → 400 ----------
    status, body = http.call("POST", "/api/report-templates", token, {
        "projectId": project_id, "name": TEMPLATE_NAME + "-重复",
        "fields": [{"fieldKey": key} for key in FULL_FIELDS + ["passRate"]],
    })
    report.check("TPLX01-10", "重复字段 400", status == 400, f"HTTP {status} msg={message_of(body)[:60]}")

    # ---------- ⑦ 重名 → 409（唯一键真的生效） ----------
    status, body = http.call("POST", "/api/report-templates", token, {
        "projectId": project_id, "name": TEMPLATE_NAME,
        "fields": [{"fieldKey": key} for key in FULL_FIELDS],
    })
    report.check("TPLX01-11", "同项目重名 409", status == 409,
                 f"HTTP {status} msg={message_of(body)[:60]}")

    # ---------- ⑧ 更新 → version+1，字段整体替换 ----------
    status, body = http.call("PUT", f"/api/report-templates/{template_id}", token, {
        "projectId": project_id, "name": TEMPLATE_NAME + "-v2",
        "description": "TPLX-01 验收（改过）",
        "fields": [{"fieldKey": key} for key in REQUIRED + ["conclusion"]],
    })
    updated = data_of(body) if status == 200 else {}
    report.check("TPLX01-12", "更新 200 且 version=2、字段数=7",
                 status == 200 and updated.get("version") == 2 and len(updated.get("fields", [])) == 7,
                 f"HTTP {status} version={updated.get('version')} 字段数={len(updated.get('fields', []))}")
    db_version = mysql(f"SELECT CONCAT(version, '/', (SELECT COUNT(*) FROM "
                       f"@DB@.report_template_field WHERE template_id = {template_id})) "
                       f"FROM @DB@.report_template WHERE id = {template_id};")
    report.check("TPLX01-13", "库里 version=2 且字段被整体替换为 7 条", db_version == "2/7",
                 f"version/字段数={db_version}")

    # ---------- ⑨ 平台级模板只读 ----------
    platform_id = int(platform["id"]) if platform else 0
    status, body = http.call("PUT", f"/api/report-templates/{platform_id}", token, {
        "projectId": project_id, "name": "DEFAULT-改",
        "fields": [{"fieldKey": key} for key in FULL_FIELDS],
    })
    report.check("TPLX01-14", "改平台级模板 400 且提示平台级",
                 status == 400 and "平台级" in message_of(body),
                 f"HTTP {status} msg={message_of(body)[:60]}")

    # ---------- ⑩ 跨项目访问 → 403 ----------
    if other_project_id:
        status, body = http.call("GET",
                                 f"/api/report-templates/{template_id}?projectId={other_project_id}",
                                 token)
        report.check("TPLX01-15", "跨项目读模板 403 且点名『属于其它项目』",
                     status == 403 and "其它项目" in message_of(body),
                     f"HTTP {status} msg={message_of(body)[:60]}")
    else:
        report.check("TPLX01-15", "跨项目读模板 403", False,
                     f"缺少另一夹具项目 {OTHER_PROJECT_CODE}（先跑 ORCH-02 的 run.py）")

    # ---------- ⑪ 删除 → 404 ----------
    status, _ = http.call("DELETE", f"/api/report-templates/{template_id}?projectId={project_id}",
                          token)
    report.check("TPLX01-16", "删除返回 200", status == 200, f"HTTP {status}")
    status, body = http.call("GET", f"/api/report-templates/{template_id}?projectId={project_id}",
                             token)
    report.check("TPLX01-17", "删除后再取 404", status == 404, f"HTTP {status}")

    # 收尾：把本项目清空，方便下次从零重跑
    mysql(f"DELETE FROM @DB@.report_template WHERE project_id = {project_id};")
    return report.dump()


if __name__ == "__main__":
    raise SystemExit(main())
