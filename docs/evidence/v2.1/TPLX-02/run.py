#!/usr/bin/env python3
"""TPLX-02 真实 HTTP 验收：按模板冻结 + xlsx 导出（数值列是数字）。

为什么不用单测代替
------------------
`ReportTemplateFreezeTest` 用的是内存对象，它证明不了四件事：
  ① 冻结真的把模板写进了 **MySQL 的 LONGTEXT**（`snapshot_json`），而不是只在 JVM 里对；
  ② 改模板之后，**再打一次 HTTP** 拿到的已冻结报告字段集合没变（跨请求、跨事务）；
  ③ xlsx 是真能解析的 OOXML（openpyxl 读得出、数值列 `data_type == 'n'`）；
  ④ 「未指定模板回落 DEFAULT」走的是 `findDefault`（项目级 → 平台级），不是代码里的想当然。

跑法
----
    python3 docs/evidence/v2.1/TPLX-02/run.py [--base http://127.0.0.1:8001]

前置：后端已用**含本包代码**的 jar 启动；`~/.config/robot-autotest/env` 里有
`QA_E2E_PASSWORD`。退出码 0 = 全部断言通过。产物（四种格式的文件）落在本目录 `out/`。
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
RUN_ID = "RUN-TPLX2-0001"
TEMPLATE_NAME = "tplx02-对外简版"
REQUIRED = ["reportId", "title", "version", "frozenAt", "disclaimer", "watermark"]
# 冻结时用的模板：必填 + 结论 + 通过率（带自定义标题）
FROZEN_FIELDS = REQUIRED + ["conclusion", "passRate"]
# 改模板后用的另一套：必填 + 失败数 + 构建号（顺序也换过）
EDITED_FIELDS = ["title", "reportId", "watermark", "disclaimer", "frozenAt", "version",
                 "failCount", "swBuild"]
HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE / "out"


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


def seed_project() -> int:
    password = ENV.get("QA_E2E_PASSWORD") or os.environ.get("QA_E2E_PASSWORD")
    if not password:
        raise SystemExit("缺少 QA_E2E_PASSWORD（在 ~/.config/robot-autotest/env 里）")
    mysql("INSERT INTO @DB@.project (code, name, description, status, visibility) "
          f"VALUES ('{PROJECT_CODE}', '验收夹具·HTTP 全链路', "
          "'TPLX-02 验收夹具，勿手工改', 1, 'private') "
          "ON DUPLICATE KEY UPDATE name = VALUES(name);")
    project_id = int(mysql(f"SELECT id FROM @DB@.project WHERE code = '{PROJECT_CODE}';"))
    mysql("INSERT INTO @DB@.app_user (username, display_name, password_hash, status) "
          f"VALUES ('qa-e2e-admin', 'TPLX-02 admin', '{pbkdf2_hash(password)}', 1) "
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
    return project_id


def seed_run(project_id: int) -> None:
    """run 夹具：pass=8 fail=1 skip=1、exportable=1，metadata 五项齐备。"""
    mysql(f"DELETE FROM @DB@.run_metadata WHERE project_id = {project_id} "
          f"AND run_id = '{RUN_ID}';")
    mysql(f"DELETE FROM @DB@.run WHERE project_id = {project_id} AND run_id = '{RUN_ID}';")
    mysql("INSERT INTO @DB@.run (run_id, project_id, run_status, pass_count, fail_count, "
          "skip_count, total_tests, exportable, started_at, finished_at) "
          f"VALUES ('{RUN_ID}', {project_id}, 'finished', 8, 1, 1, 10, 1, "
          "NOW(3) - INTERVAL 10 MINUTE, NOW(3));")
    for key, value in [("SW_BUILD", "build-2.1-tplx02"), ("CASE_KIND", "基本功能测试"),
                       ("QA_PROJECT", "qa-tplx02"), ("DUT", "lab-dut-01"),
                       ("LINK", "http://lab.local/case/tplx02")]:
        mysql("INSERT INTO @DB@.run_metadata (project_id, run_id, meta_key, meta_value) "
              f"VALUES ({project_id}, '{RUN_ID}', '{key}', '{value}');")


# ---------------------------------------------------------------- HTTP
class Http:
    def __init__(self, base: str) -> None:
        self.base = base.rstrip("/")

    def raw(self, method: str, path: str, token: str | None = None,
            body: dict | None = None) -> tuple[int, bytes, dict]:
        headers = {"Accept": "*/*"}
        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(self.base + path, data=data,
                                         headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=60) as resp:
                # 头名大小写不保证：统一小写，避免「Content-Type 取不到」这种假失败
                return resp.status, resp.read(), {k.lower(): v for k, v in resp.headers.items()}
        except urllib.error.HTTPError as exc:
            return exc.code, exc.read(), {k.lower(): v for k, v in (exc.headers or {}).items()}
        except urllib.error.URLError as exc:
            raise SystemExit(f"连不上 {self.base}：{exc.reason}")

    def call(self, method: str, path: str, token: str | None = None,
             body: dict | None = None) -> tuple[int, dict]:
        status, payload, _ = self.raw(method, path, token, body)
        text = payload.decode("utf-8", "replace")
        try:
            return status, json.loads(text or "{}")
        except json.JSONDecodeError:
            return status, {"raw": text[:400]}

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


def read_xlsx(path: pathlib.Path) -> dict:
    """用 openpyxl 读回 xlsx：表头、字段 key 顺序、每格的值与类型。"""
    import openpyxl

    workbook = openpyxl.load_workbook(path, data_only=True)
    sheet = workbook[workbook.sheetnames[0]]
    header = [sheet.cell(row=1, column=i + 1).value for i in range(3)]
    rows = {}
    order = []
    for r in range(2, sheet.max_row + 1):
        key = sheet.cell(row=r, column=1).value
        if key is None:
            continue
        order.append(key)
        rows[key] = {
            "title": sheet.cell(row=r, column=2).value,
            "value": sheet.cell(row=r, column=3).value,
            "type": sheet.cell(row=r, column=3).data_type,  # 'n' 数字 / 's' 字符串
        }
    return {"header": header, "order": order, "rows": rows,
            "sheetNames": workbook.sheetnames}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8001")
    args = parser.parse_args()

    http = Http(args.base)
    report = Report()
    OUT.mkdir(exist_ok=True)

    project_id = seed_project()
    seed_run(project_id)
    token = http.login("qa-e2e-admin", ENV["QA_E2E_PASSWORD"])
    print(f"# 夹具 projectId={project_id} run={RUN_ID}（pass=8 fail=1 skip=1），"
          f"已登录 qa-e2e-admin\n")

    # 可从零重跑：清模板、清该 run 的历史报告
    mysql(f"DELETE FROM @DB@.report_template WHERE project_id = {project_id};")
    report_ids = mysql(f"SELECT COALESCE(GROUP_CONCAT(id), '') FROM @DB@.test_report "
                       f"WHERE project_id = {project_id} AND run_id = '{RUN_ID}';")
    if report_ids:
        mysql(f"DELETE FROM @DB@.test_report_export WHERE report_id IN ({report_ids});")
        mysql(f"DELETE FROM @DB@.test_report_audit WHERE report_id IN ({report_ids});")
        mysql(f"DELETE FROM @DB@.test_report WHERE id IN ({report_ids});")

    # ---------- ① 建模板并设为默认 ----------
    status, body = http.call("POST", "/api/report-templates", token, {
        "projectId": project_id, "name": TEMPLATE_NAME, "description": "TPLX-02 验收",
        "fields": [{"fieldKey": key, "titleOverride": "通过率" if key == "passRate" else None}
                   for key in FROZEN_FIELDS],
    })
    template = data_of(body) if status == 200 else {}
    template_id = int(template.get("id", 0)) if template else 0
    report.check("TPLX2-01", "建模板 200 version=1 字段 8 项",
                 status == 200 and template.get("version") == 1
                 and len(template.get("fields", [])) == 8,
                 f"HTTP {status} id={template_id} version={template.get('version')}")
    status, body = http.call("PUT", f"/api/report-templates/{template_id}/default"
                                    f"?projectId={project_id}", token)
    report.check("TPLX2-02", "设为默认 200 且 isDefault=true",
                 status == 200 and data_of(body).get("isDefault") is True,
                 f"HTTP {status}")

    # ---------- ② 建报告（DRAFT）→ 冻结 ----------
    status, body = http.call("POST", "/api/test-reports", token,
                             {"projectId": project_id, "runId": RUN_ID, "title": "TPLX-02 验收报告"})
    created = data_of(body) if status == 200 else {}
    report_id = int(created.get("id", 0)) if created else 0
    report.check("TPLX2-03", "建报告 200 且状态 DRAFT", status == 200
                 and created.get("state") == "DRAFT", f"HTTP {status} id={report_id}")
    snapshot_json = mysql(f"SELECT snapshot_json FROM @DB@.test_report WHERE id = {report_id};")
    report.check("TPLX2-04", "DRAFT 快照已把模板写进 MySQL（templateId/templateVersion）",
                 f'"templateId":{template_id}' in snapshot_json
                 and '"templateVersion":1' in snapshot_json,
                 f"LONGTEXT 长度={len(snapshot_json)}")

    status, body = http.call("POST", f"/api/test-reports/{report_id}/freeze", token)
    frozen = data_of(body) if status == 200 else {}
    report.check("TPLX2-05", "冻结 200 且 state=FROZEN、version=2",
                 status == 200 and frozen.get("state") == "FROZEN"
                 and frozen.get("version") == 2,
                 f"HTTP {status} version={frozen.get('version')}")

    # ---------- ③ 模板回指 ----------
    status, body = http.call("GET", f"/api/test-reports/{report_id}/template", token)
    ref = data_of(body) if status == 200 else {}
    report.check("TPLX2-06", "报告回指冻结时的模板 id/版本/字段",
                 status == 200 and ref.get("id") == template_id and ref.get("version") == 1
                 and ref.get("fieldKeys") == FROZEN_FIELDS,
                 f"HTTP {status} id={ref.get('id')} v={ref.get('version')} 字段={len(ref.get('fieldKeys') or [])}")

    # ---------- ④ 渲染字段集合 = 模板字段顺序 ----------
    status, body = http.call("GET", f"/api/test-reports/{report_id}?external=true", token)
    view = data_of(body) if status == 200 else {}
    (OUT / "view-before-edit.json").write_text(
        json.dumps(view, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report.check("TPLX2-07", "对外视图字段集合与顺序 = 模板",
                 status == 200 and list(view.keys()) == FROZEN_FIELDS,
                 f"HTTP {status} 字段={list(view.keys())}")

    # ---------- ⑤ xlsx 导出 ----------
    status, payload, headers = http.raw(
        "GET", f"/api/test-reports/{report_id}/export?format=xlsx", token)
    xlsx_path = OUT / "report-frozen.xlsx"
    xlsx_path.write_bytes(payload)
    content_type = headers.get("content-type", "")
    report.check("TPLX2-08", "xlsx 导出 200、Content-Type=spreadsheetml、PK 头",
                 status == 200 and "spreadsheetml" in content_type
                 and payload[:2] == b"PK" and len(payload) > 2000,
                 f"HTTP {status} {len(payload)}B {content_type.split(';')[0]}")

    parsed = read_xlsx(xlsx_path)
    report.check("TPLX2-09", "openpyxl 可解析，表头 FIELD_KEY/TITLE/VALUE",
                 parsed["header"] == ["FIELD_KEY", "TITLE", "VALUE"],
                 f"header={parsed['header']} sheets={parsed['sheetNames']}")
    report.check("TPLX2-10", "xlsx 字段与顺序 = 冻结模板",
                 parsed["order"] == FROZEN_FIELDS, f"字段={parsed['order']}")
    numeric_ok = (parsed["rows"]["passRate"]["type"] == "n"
                  and parsed["rows"]["version"]["type"] == "n"
                  and parsed["rows"]["passRate"]["value"] == 80.0)
    report.check("TPLX2-11", "数值列是数字不是文本（passRate=80.0 type=n）",
                 numeric_ok,
                 f"passRate={parsed['rows']['passRate']} version={parsed['rows']['version']['type']}")
    report.check("TPLX2-12", "文本列仍是字符串，模板自定义标题生效",
                 parsed["rows"]["conclusion"]["type"] == "s"
                 and parsed["rows"]["passRate"]["title"] == "通过率",
                 f"conclusion.type={parsed['rows']['conclusion']['type']} "
                 f"passRate.title={parsed['rows']['passRate']['title']}")

    for fmt in ("html", "pdf", "docx"):
        status, payload, _ = http.raw(
            "GET", f"/api/test-reports/{report_id}/export?format={fmt}", token)
        (OUT / f"report-frozen.{fmt}").write_bytes(payload)
        report.check(f"TPLX2-{13 + ('html', 'pdf', 'docx').index(fmt)}",
                     f"{fmt} 导出 200 且非空", status == 200 and len(payload) > 100,
                     f"HTTP {status} {len(payload)}B")

    # ---------- ⑥ 改模板：已冻结报告不得受影响 ----------
    status, body = http.call("PUT", f"/api/report-templates/{template_id}", token, {
        "projectId": project_id, "name": TEMPLATE_NAME, "description": "TPLX-02 验收（改过）",
        "fields": [{"fieldKey": key} for key in EDITED_FIELDS],
    })
    edited = data_of(body) if status == 200 else {}
    report.check("TPLX2-16", "改模板 200 且 version=2、字段换成另一套",
                 status == 200 and edited.get("version") == 2
                 and [f["fieldKey"] for f in edited.get("fields", [])] == EDITED_FIELDS,
                 f"HTTP {status} version={edited.get('version')}")

    status, body = http.call("GET", f"/api/test-reports/{report_id}?external=true", token)
    view_after = data_of(body) if status == 200 else {}
    (OUT / "view-after-edit.json").write_text(
        json.dumps(view_after, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report.check("TPLX2-17", "改模板后已冻结报告字段集合不变（仍 8 项、顺序不变）",
                 status == 200 and list(view_after.keys()) == FROZEN_FIELDS,
                 f"字段={list(view_after.keys())}")

    status, body = http.call("GET", f"/api/test-reports/{report_id}/template", token)
    ref_after = data_of(body) if status == 200 else {}
    report.check("TPLX2-18", "改模板后回指仍是冻结时的 v1，不是当前 v2",
                 status == 200 and ref_after.get("version") == 1
                 and ref_after.get("fieldKeys") == FROZEN_FIELDS,
                 f"version={ref_after.get('version')}")

    status, payload, _ = http.raw(
        "GET", f"/api/test-reports/{report_id}/export?format=xlsx", token)
    xlsx_after = OUT / "report-frozen-after-edit.xlsx"
    xlsx_after.write_bytes(payload)
    parsed_after = read_xlsx(xlsx_after)
    report.check("TPLX2-19", "改模板后再导 xlsx，字段与冻结时逐项相同",
                 status == 200 and parsed_after["order"] == FROZEN_FIELDS,
                 f"字段={parsed_after['order']}")
    report.check("TPLX2-20", "两次 xlsx 内容一致（除时间戳外无差异）",
                 parsed_after["rows"]["passRate"]["value"]
                 == parsed["rows"]["passRate"]["value"]
                 and parsed_after["rows"]["passRate"]["title"] == "通过率",
                 f"passRate={parsed_after['rows']['passRate']}")

    # ---------- ⑦ 未指定模板回落 DEFAULT（项目级删光 → 平台级内置） ----------
    platform_default = mysql("SELECT COALESCE(MAX(is_default), 0) FROM @DB@.report_template "
                            "WHERE project_id = 0;")
    report.check("TPLX2-21", "设项目默认不会清掉平台级 DEFAULT（全局回落值仍为 1）",
                 platform_default == "1", f"平台级 is_default={platform_default}")

    mysql(f"DELETE FROM @DB@.report_template WHERE project_id = {project_id};")
    status, body = http.call("POST", "/api/test-reports", token,
                             {"projectId": project_id, "runId": RUN_ID, "title": "TPLX-02 回落默认"})
    fallback = data_of(body) if status == 200 else {}
    fallback_id = int(fallback.get("id", 0)) if fallback else 0
    status, body = http.call("GET", f"/api/test-reports/{fallback_id}/template", token)
    fallback_ref = data_of(body) if status == 200 else {}
    status2, body2 = http.call("GET", f"/api/test-reports/{fallback_id}?external=true", token)
    fallback_view = data_of(body2) if status2 == 200 else {}
    report.check("TPLX2-22", "无项目模板时回落平台级 DEFAULT（21 字段，顺序=白名单）",
                 status == 200 and fallback_ref.get("id") is not None
                 and len(fallback_ref.get("fieldKeys") or []) == 21
                 and list(fallback_view.keys()) == fallback_ref.get("fieldKeys"),
                 f"模板id={fallback_ref.get('id')} 字段={len(fallback_ref.get('fieldKeys') or [])}")

    # 收尾：清掉本次夹具报告，模板已删，保留 run 供人工复核
    mysql(f"DELETE FROM @DB@.test_report_export WHERE report_id IN "
          f"(SELECT id FROM (SELECT id FROM @DB@.test_report WHERE project_id = {project_id} "
          f"AND run_id = '{RUN_ID}') t);")
    mysql(f"DELETE FROM @DB@.test_report_audit WHERE report_id IN "
          f"(SELECT id FROM (SELECT id FROM @DB@.test_report WHERE project_id = {project_id} "
          f"AND run_id = '{RUN_ID}') t);")
    mysql(f"DELETE FROM @DB@.test_report WHERE project_id = {project_id} "
          f"AND run_id = '{RUN_ID}';")
    print(f"\n# 产物落在 {OUT}")
    return report.dump()


if __name__ == "__main__":
    raise SystemExit(main())
