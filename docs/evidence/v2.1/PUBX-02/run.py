#!/usr/bin/env python3
"""PUBX-02 真实 HTTP 验收：差异三类 + 同步/分叉 + 审计。

为什么不用单测代替
------------------
`AssetUpgradeServiceTest` 用的是内存仓储，它证明不了四件事：
  ① sync 的「导入缺失用例 → 重建条目 → 重算 hash → 推 synced 读数」在**真实 MySQL 事务**
     里全部生效（跨请求读回，而不是内存对象自查自）；
  ② 分叉（diverge）真的**不动 test_set_item**——假实现最爱在分叉里顺手改内容；
  ③ diff 的三类方向在真实库上不反（REMOVED 是「平台删了」，不是「副本多的」）；
  ④ ASSET_SYNC / ASSET_DIVERGE 真的落进 audit_log（审计查询可直接取证）。

跑法
----
    python3 docs/evidence/v2.1/PUBX-02/run.py [--base http://127.0.0.1:8001]

前置：后端已用**含本包代码**的 jar 启动；`~/.config/robot-autotest/env` 里有
`QA_E2E_PASSWORD`。退出码 0 = 全部断言通过。
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
OWNER_CODE = "qa-e2e-platform-fixture"
PLATFORM_SET_CODE = "PUBX02-平台基线集"
SET_B_CODE = "PUBX02-项目副本集"
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
          "'PUBX-02 验收夹具，勿手工改', 1, 'private') "
          "ON DUPLICATE KEY UPDATE name = VALUES(name);")
    project_id = int(mysql(f"SELECT id FROM @DB@.project WHERE code = '{PROJECT_CODE}';"))
    mysql("INSERT INTO @DB@.app_user (username, display_name, password_hash, status) "
          f"VALUES ('qa-e2e-admin', 'PUBX-02 admin', '{pbkdf2_hash(password)}', 1) "
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


def cleanup(project_id: int) -> None:
    set_ids = mysql(f"SELECT COALESCE(GROUP_CONCAT(id), '') FROM @DB@.test_set "
                    f"WHERE project_id = {project_id} "
                    f"OR (owner_scope = 'PLATFORM' AND set_code = '{PLATFORM_SET_CODE}');")
    if set_ids:
        mysql(f"DELETE FROM @DB@.test_set_item WHERE test_set_id IN ({set_ids});")
        mysql(f"DELETE FROM @DB@.asset_version WHERE asset_type = 'TESTSET' "
              f"AND asset_id IN ({set_ids});")
        mysql(f"DELETE FROM @DB@.test_set WHERE id IN ({set_ids});")
    for pid in (project_id,):
        mysql(f"DELETE FROM @DB@.test_case WHERE project_id = {pid} "
              "AND case_code LIKE 'PUBX02-TC-%';")
    owner_id = mysql(f"SELECT id FROM @DB@.project WHERE code = '{OWNER_CODE}';")
    if owner_id:
        mysql(f"DELETE FROM @DB@.test_case WHERE project_id = {owner_id} "
              "AND case_code LIKE 'PUBX02-TC-%';")


def seed_admin_member(project_id: int) -> None:
    user_id = int(mysql("SELECT id FROM @DB@.app_user WHERE username = 'qa-e2e-admin';"))
    mysql("INSERT INTO @DB@.project_member (project_id, user_id, member_role) "
          f"VALUES ({project_id}, {user_id}, 'ADMIN') "
          "ON DUPLICATE KEY UPDATE member_role = VALUES(member_role);")


def seed_platform_owner() -> int:
    """平台属主项目：平台基线集的用例**必须**与副本项目的用例是两行——

    平台资产副本的语义是「各项目有自己的用例行，按 case_code 对齐」。
    如果夹具图省事让两边引用同一行 test_case，改标题两边一起变，
    MODIFIED 永远不出现（首轮实测就踩了这坑）。
    """
    mysql("INSERT INTO @DB@.project (code, name, description, status, visibility) "
          f"VALUES ('{OWNER_CODE}', '验收夹具·平台属主', 'PUBX-02 平台用例属主', 1, 'private') "
          "ON DUPLICATE KEY UPDATE name = VALUES(name);")
    owner_id = int(mysql(f"SELECT id FROM @DB@.project WHERE code = '{OWNER_CODE}';"))
    seed_admin_member(owner_id)
    return owner_id


def seed_cases(project_id: int, codes: list[str]) -> dict[str, int]:
    all_cases = [("PUBX02-TC-001", "登录成功", "P1"),
                 ("PUBX02-TC-002", "登出", "P2"),
                 ("PUBX02-TC-003", "改密后旧口令失效", "P1")]
    ids: dict[str, int] = {}
    for code, title, priority in [c for c in all_cases if c[0] in codes]:
        mysql("INSERT INTO @DB@.test_case (project_id, case_code, title, priority, review_status) "
              f"VALUES ({project_id}, '{code}', '{title}', '{priority}', 'APPROVED') "
              "ON DUPLICATE KEY UPDATE title = VALUES(title), priority = VALUES(priority);")
        ids[code] = int(mysql(f"SELECT id FROM @DB@.test_case "
                              f"WHERE project_id = {project_id} AND case_code = '{code}';"))
    return ids


def seed_platform_set(case_ids: list[int]) -> int:
    mysql("INSERT INTO @DB@.test_set (set_code, name, description, project_id, owner_scope) "
          f"VALUES ('{PLATFORM_SET_CODE}', '平台基线集（PUBX-02 夹具）', '平台共享', NULL, 'PLATFORM');")
    platform_id = int(mysql(f"SELECT id FROM @DB@.test_set WHERE owner_scope = 'PLATFORM' "
                            f"AND set_code = '{PLATFORM_SET_CODE}';"))
    for seq, case_id in enumerate(case_ids, start=1):
        mysql("INSERT INTO @DB@.test_set_item (test_set_id, test_case_id, seq_no) "
              f"VALUES ({platform_id}, {case_id}, {seq});")
    return platform_id


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
    OUT.mkdir(exist_ok=True)

    project_id = seed_project()
    cleanup(project_id)
    owner_id = seed_platform_owner()
    # 平台属主项目：3 条都用（TC-003 只在平台侧，同步时才会被导入副本项目）
    owner_cases = seed_cases(owner_id, ["PUBX02-TC-001", "PUBX02-TC-002", "PUBX02-TC-003"])
    # 副本项目：只有 TC-001 / TC-002（模拟「副本落后于平台」的真实起点）
    cases = seed_cases(project_id, ["PUBX02-TC-001", "PUBX02-TC-002"])
    platform_set_id = seed_platform_set([owner_cases["PUBX02-TC-001"],
                                         owner_cases["PUBX02-TC-002"]])
    token = http.login("qa-e2e-admin", ENV["QA_E2E_PASSWORD"])
    print(f"# 夹具 projectId={project_id} ownerProject={owner_id} "
          f"platformSet={platform_set_id}，已登录 qa-e2e-admin\n")

    base = f"/api/projects/{project_id}/case-sets"
    av = f"/api/test-sets"

    # ---------- ⓪ 平台集经管理路径登记版本行（bindCopy 的前置） ----------
    status, body = http.call("POST",
                             f"{av}/{platform_set_id}/asset-version/refresh?projectId=0", token)
    report.check("PUBX02-00", "平台集 refresh(projectId=0) → v1（登记版本行）",
                 status == 200 and data_of(body).get("assetVersion") == 1,
                 f"HTTP {status} v={data_of(body).get('assetVersion')}")

    # ---------- ① 副本建集 + 绑定 → diff 为空 ----------
    status, body = http.call("POST", base, token,
                             {"setCode": SET_B_CODE, "name": "项目副本集",
                              "description": "PUBX-02 验收"})
    set_b = data_of(body) if status == 200 else {}
    set_b_id = int(set_b.get("id", 0)) if set_b else 0
    for key in ("PUBX02-TC-001", "PUBX02-TC-002"):
        http.call("POST", f"{base}/{set_b_id}/items", token, {"caseId": cases[key]})
    status, body = http.call("POST",
                             f"{av}/{set_b_id}/asset-version/bind-copy"
                             f"?projectId={project_id}&platformSetId={platform_set_id}", token)
    report.check("PUBX02-01", "bindCopy 200（副本已对齐平台 v1）",
                 status == 200 and data_of(body).get("forkState") == "SYNCED", f"HTTP {status}")

    status, body = http.call("GET",
                             f"{av}/{set_b_id}/asset-version/diff?projectId={project_id}", token)
    report.check("PUBX02-02", "刚对齐时 diff 为空", status == 200 and data_of(body) == [],
                 f"HTTP {status} rows={data_of(body)}")

    # ---------- ② 平台变更：改属主项目的 TC-001 标题 / 删 TC-002 / 加 TC-003 ----------
    mysql(f"UPDATE @DB@.test_case SET title = '登录成功（平台改名）' "
          f"WHERE id = {owner_cases['PUBX02-TC-001']};")
    mysql(f"DELETE FROM @DB@.test_set_item WHERE test_set_id = {platform_set_id} "
          f"AND test_case_id = {owner_cases['PUBX02-TC-002']};")
    mysql(f"INSERT INTO @DB@.test_set_item (test_set_id, test_case_id, seq_no) "
          f"VALUES ({platform_set_id}, {owner_cases['PUBX02-TC-003']}, 2);")
    status, body = http.call("POST",
                             f"{av}/{platform_set_id}/asset-version/refresh?projectId=0", token)
    platform_v2 = data_of(body) if status == 200 else {}
    report.check("PUBX02-03", "平台 refresh → v2", status == 200
                 and platform_v2.get("assetVersion") == 2,
                 f"v={platform_v2.get('assetVersion')}")

    status, body = http.call("GET",
                             f"{av}/{set_b_id}/asset-version/diff?projectId={project_id}", token)
    diff_rows = data_of(body) if status == 200 else []
    by_type: dict[str, list[dict]] = {}
    for row in diff_rows or []:
        by_type.setdefault(row["type"], []).append(row)
    report.check("PUBX02-04", "diff 三类各 1 条且方向不反（改=TC-001 删=TC-002 增=TC-003）",
                 len(by_type.get("MODIFIED", [])) == 1
                 and by_type["MODIFIED"][0]["caseCode"] == "PUBX02-TC-001"
                 and len(by_type.get("REMOVED", [])) == 1
                 and by_type["REMOVED"][0]["caseCode"] == "PUBX02-TC-002"
                 and len(by_type.get("ADDED", [])) == 1
                 and by_type["ADDED"][0]["caseCode"] == "PUBX02-TC-003",
                 f"共 {len(diff_rows)} 行：{[(r['type'], r['caseCode']) for r in diff_rows or []]}")

    # ---------- ③ 分叉：只置状态，内容与条目不动 ----------
    items_before = http.call("GET", f"{base}/{set_b_id}/items", token)[1]
    status, body = http.call("POST",
                             f"{av}/{set_b_id}/asset-version/diverge?projectId={project_id}",
                             token)
    diverged = data_of(body) if status == 200 else {}
    items_after = http.call("GET", f"{base}/{set_b_id}/items", token)[1]
    db_fork = mysql(f"SELECT fork_state FROM @DB@.asset_version WHERE asset_type = 'TESTSET' "
                    f"AND asset_id = {set_b_id};")
    report.check("PUBX02-05", "diverge 200：fork_state=DIVERGED（接口与库同读数）",
                 status == 200 and diverged.get("forkState") == "DIVERGED"
                 and db_fork == "DIVERGED", f"HTTP {status} 库={db_fork}")
    report.check("PUBX02-06", "分叉不动内容：条目逐字节不变、版本号不变",
                 items_before == items_after and diverged.get("assetVersion") == 2,
                 f"items 一致={items_before == items_after} v={diverged.get('assetVersion')}")

    audit_diverge = mysql(f"SELECT COUNT(*) FROM @DB@.audit_log WHERE project_id = {project_id} "
                          "AND action = 'ASSET_DIVERGE' "
                          f"AND resource_id = '{set_b_id}';")
    report.check("PUBX02-07", "audit_log 出现 ASSET_DIVERGE", audit_diverge == "1",
                 f"count={audit_diverge}")

    # ---------- ④ 同步：导入缺失用例 + 重建条目 + 对齐读数 ----------
    status, body = http.call("POST",
                             f"{av}/{set_b_id}/asset-version/sync?projectId={project_id}", token)
    synced = data_of(body) if status == 200 else {}
    imported = mysql(f"SELECT COUNT(*) FROM @DB@.test_case WHERE project_id = {project_id} "
                     "AND case_code = 'PUBX02-TC-002';")
    items_now = [row.split("\t")[1] for row in mysql(
        f"SELECT i.seq_no, c.case_code FROM @DB@.test_set_item i "
        f"JOIN @DB@.test_case c ON c.id = i.test_case_id "
        f"WHERE i.test_set_id = {set_b_id} ORDER BY i.seq_no;").splitlines()]
    db_sync = mysql(f"SELECT CONCAT(fork_state, '/', synced_asset_version) FROM @DB@.asset_version "
                    f"WHERE asset_type = 'TESTSET' AND asset_id = {set_b_id};")
    report.check("PUBX02-08", "sync 200：fork_state=SYNCED、synced=平台 v2（库同读数）",
                 status == 200 and synced.get("forkState") == "SYNCED"
                 and synced.get("syncedAssetVersion") == 2 and db_sync == "SYNCED/2",
                 f"HTTP {status} 库={db_sync}")
    report.check("PUBX02-09", "同步后条目 = 平台内容（TC-001 复用本项目行，TC-003 自动导入）",
                 items_now == ["PUBX02-TC-001", "PUBX02-TC-003"],
                 f"条目={items_now}")
    imported_row = mysql(f"SELECT COUNT(*) FROM @DB@.test_case WHERE project_id = {project_id} "
                         "AND case_code = 'PUBX02-TC-003';")
    report.check("PUBX02-09b", "缺失的 TC-003 真的导入本项目（test_case 新行）",
                 imported_row == "1", f"count={imported_row}")
    report.check("PUBX02-10", "hash 对齐（副本内容 hash = 平台 v2 hash）",
                 synced.get("contentHash") == platform_v2.get("contentHash"),
                 f"副本={str(synced.get('contentHash'))[:12]}… "
                 f"平台={str(platform_v2.get('contentHash'))[:12]}…")
    status, body = http.call("GET",
                             f"{av}/{set_b_id}/asset-version/diff?projectId={project_id}", token)
    report.check("PUBX02-11", "同步后 diff 为空", status == 200 and data_of(body) == [],
                 f"rows={data_of(body)}")

    audit_sync = mysql(f"SELECT COUNT(*) FROM @DB@.audit_log WHERE project_id = {project_id} "
                       f"AND action = 'ASSET_SYNC' AND resource_id = '{set_b_id}';")
    report.check("PUBX02-12", "audit_log 出现 ASSET_SYNC", audit_sync == "1",
                 f"count={audit_sync}")

    # 审计明细落证据
    audit_rows = mysql(f"SELECT action, resource_id, detail FROM @DB@.audit_log "
                       f"WHERE project_id = {project_id} "
                       f"AND action IN ('ASSET_SYNC', 'ASSET_DIVERGE') ORDER BY occurred_at;")
    (OUT / "audit.log").write_text(audit_rows + "\n", encoding="utf-8")

    # ---------- ⑤ 负向：未绑定的集 ----------
    status, body = http.call("POST", base, token,
                             {"setCode": "PUBX02-未绑定", "name": "未绑定集", "description": ""})
    loose_id = int(data_of(body).get("id", 0)) if status == 200 else 0
    status, body = http.call("POST",
                             f"{av}/{loose_id}/asset-version/sync?projectId={project_id}", token)
    report.check("PUBX02-13", "未绑定平台资产的集 sync → 400", status == 400,
                 f"HTTP {status} msg={message_of(body)[:60]}")

    # 收尾
    cleanup(project_id)
    print(f"\n# 审计取证落在 {OUT / 'audit.log'}；夹具已清理（test_case 保留 PUBX02-TC-*）")
    return report.dump()


if __name__ == "__main__":
    raise SystemExit(main())
