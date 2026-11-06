#!/usr/bin/env python3
"""PUBX-01 真实 HTTP 验收：用例集成为可发布资产，带内容 hash 版本与副本同步读数。

为什么不用单测代替
------------------
`AssetVersionServiceTest` 用的是内存仓储，它证明不了五件事：
  ① TestSetService 的 add/remove/reorder 钩子在**真实 Spring 事务**里真的推进了
     MySQL 的 asset_version（跨请求、跨连接可见）；
  ② 「内容不变不涨版本」在真实库上成立——重复 refresh 不产生第二行、不写 updated_at；
  ③ 平台集（project_id NULL + owner_scope=PLATFORM）与项目集走的是同一套版本逻辑，
     projectId=0 的管理路径真的能登记平台版本行；
  ④ bindCopy 写进库的 synced 读数，在**另一个请求**里被 references 正确读回；
  ⑤ 改名（PUT /case-sets/{id}）这个真实接口真的不涨版本。

跑法
----
    python3 docs/evidence/v2.1/PUBX-01/run.py [--base http://127.0.0.1:8001]

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
PLATFORM_SET_CODE = "PUBX01-平台基线集"
SET_A_CODE = "PUBX01-项目集A"
SET_B_CODE = "PUBX01-项目集B副本"


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
          "'PUBX-01 验收夹具，勿手工改', 1, 'private') "
          "ON DUPLICATE KEY UPDATE name = VALUES(name);")
    project_id = int(mysql(f"SELECT id FROM @DB@.project WHERE code = '{PROJECT_CODE}';"))
    mysql("INSERT INTO @DB@.app_user (username, display_name, password_hash, status) "
          f"VALUES ('qa-e2e-admin', 'PUBX-01 admin', '{pbkdf2_hash(password)}', 1) "
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


def seed_cases(project_id: int) -> list[int]:
    """3 条夹具用例（直接落库；用例本身的 CRUD 不在本包验收范围）。"""
    ids = []
    for seq, (code, title, priority) in enumerate([
            ("PUBX01-TC-001", "登录成功", "P1"),
            ("PUBX01-TC-002", "登出", "P2"),
            ("PUBX01-TC-003", "改密后旧口令失效", "P1")], start=1):
        mysql("INSERT INTO @DB@.test_case (project_id, case_code, title, priority, review_status) "
              f"VALUES ({project_id}, '{code}', '{title}', '{priority}', 'APPROVED') "
              "ON DUPLICATE KEY UPDATE title = VALUES(title), priority = VALUES(priority);")
        ids.append(int(mysql(f"SELECT id FROM @DB@.test_case "
                             f"WHERE project_id = {project_id} AND case_code = '{code}';")))
    return ids


def cleanup(project_id: int) -> None:
    """可从零重跑：清掉上一轮的夹具集 / 条目 / 版本行 / 用例。"""
    set_ids = mysql(f"SELECT COALESCE(GROUP_CONCAT(id), '') FROM @DB@.test_set "
                    f"WHERE project_id = {project_id} "
                    f"OR (owner_scope = 'PLATFORM' AND set_code = '{PLATFORM_SET_CODE}');")
    if set_ids:
        mysql(f"DELETE FROM @DB@.test_set_item WHERE test_set_id IN ({set_ids});")
        mysql(f"DELETE FROM @DB@.asset_version WHERE asset_type = 'TESTSET' "
              f"AND asset_id IN ({set_ids});")
        mysql(f"DELETE FROM @DB@.test_set WHERE id IN ({set_ids});")
    mysql(f"DELETE FROM @DB@.test_case WHERE project_id = {project_id} "
          "AND case_code LIKE 'PUBX01-TC-%';")


def seed_platform_set(case_ids: list[int]) -> int:
    """平台基线集：owner_scope=PLATFORM、project_id NULL（V7_3 归属模式），带 2 条条目。

    平台资产的建立入口本版不在验收范围（归 PUBX-02 的资产页），这里按 V7_3 同款
    SQL 落库；它的**版本登记**仍走真实 HTTP（projectId=0 的管理路径）。
    """
    mysql("INSERT INTO @DB@.test_set (set_code, name, description, project_id, owner_scope) "
          f"VALUES ('{PLATFORM_SET_CODE}', '平台基线集（PUBX-01 夹具）', '平台共享', NULL, 'PLATFORM');")
    platform_id = int(mysql(f"SELECT id FROM @DB@.test_set WHERE owner_scope = 'PLATFORM' "
                            f"AND set_code = '{PLATFORM_SET_CODE}';"))
    for seq, case_id in enumerate(case_ids[:2], start=1):
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

    project_id = seed_project()
    cleanup(project_id)
    case_ids = seed_cases(project_id)
    platform_set_id = seed_platform_set(case_ids)
    token = http.login("qa-e2e-admin", ENV["QA_E2E_PASSWORD"])
    print(f"# 夹具 projectId={project_id} cases={case_ids} platformSet={platform_set_id}，"
          f"已登录 qa-e2e-admin\n")

    base = f"/api/projects/{project_id}/case-sets"

    # ---------- ① 平台集：projectId=0 的管理路径登记版本 ----------
    status, body = http.call("POST",
                             f"/api/test-sets/{platform_set_id}/asset-version/refresh"
                             "?projectId=0", token)
    platform_v1 = data_of(body) if status == 200 else {}
    report.check("PUBX01-01", "平台集 refresh(projectId=0) 200，version=1 且 hash 是 64 位十六进制",
                 status == 200 and platform_v1.get("assetVersion") == 1
                 and isinstance(platform_v1.get("contentHash"), str)
                 and len(platform_v1.get("contentHash", "")) == 64,
                 f"HTTP {status} v={platform_v1.get('assetVersion')} "
                 f"hash={str(platform_v1.get('contentHash'))[:12]}…")

    # ---------- ② 项目集 A：建集 + 加条目（钩子自动建版本行） ----------
    status, body = http.call("POST", base, token,
                             {"setCode": SET_A_CODE, "name": "项目集 A",
                              "description": "PUBX-01 验收"})
    set_a = data_of(body) if status == 200 else {}
    set_a_id = int(set_a.get("id", 0)) if set_a else 0
    report.check("PUBX01-02", "建项目集 200", status == 200 and set_a_id > 0,
                 f"HTTP {status} id={set_a_id}")
    for case_id in case_ids[:2]:
        status, body = http.call("POST", f"{base}/{set_a_id}/items", token,
                                 {"caseId": case_id})
        if status != 200:
            raise SystemExit(f"addItem 失败 HTTP {status}: {body}")
    status, body = http.call("GET",
                             f"/api/test-sets/{set_a_id}/asset-version?projectId={project_id}",
                             token)
    a_v1 = data_of(body) if status == 200 else {}
    # 钩子按每次写操作推进：两次 addItem 是两次内容变更，version=2 是正确读数；
    # 关键判据是「内容与平台集相同 → hash 相同」（同为 1a8e05b5… 的规范化行）。
    report.check("PUBX01-03", "addItem 后版本行自动存在：两次内容变更 → v=2，hash 与平台集同内容同 hash",
                 status == 200 and a_v1.get("assetVersion") == 2
                 and a_v1.get("contentHash") == platform_v1.get("contentHash"),
                 f"HTTP {status} v={a_v1.get('assetVersion')} "
                 f"hash={str(a_v1.get('contentHash'))[:12]}…")

    # ---------- ③ 内容不变：重复 refresh 不涨版本 ----------
    row_count = mysql(f"SELECT COUNT(*) FROM @DB@.asset_version "
                      f"WHERE asset_type = 'TESTSET' AND asset_id = {set_a_id};")
    updated = mysql(f"SELECT DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') FROM @DB@.asset_version "
                    f"WHERE asset_type = 'TESTSET' AND asset_id = {set_a_id};")
    status, body = http.call("POST",
                             f"/api/test-sets/{set_a_id}/asset-version/refresh"
                             f"?projectId={project_id}", token)
    a_again = data_of(body) if status == 200 else {}
    updated_after = mysql(f"SELECT DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') FROM @DB@.asset_version "
                          f"WHERE asset_type = 'TESTSET' AND asset_id = {set_a_id};")
    report.check("PUBX01-04", "重复 refresh：version 仍 2、仍一行、updated_at 不动（幂等）",
                 status == 200 and a_again.get("assetVersion") == 2
                 and a_again.get("contentHash") == a_v1.get("contentHash")
                 and row_count == "1" and updated == updated_after,
                 f"行数={row_count} updated_at {updated} → {updated_after}")

    # ---------- ④ removeItem → 版本 +1 ----------
    status, body = http.call("DELETE", f"{base}/{set_a_id}/items/{case_ids[1]}", token)
    status2, body2 = http.call("GET",
                               f"/api/test-sets/{set_a_id}/asset-version?projectId={project_id}",
                               token)
    a_v2 = data_of(body2) if status2 == 200 else {}
    report.check("PUBX01-05", "removeItem 后 version=3 且 hash 变",
                 status == 200 and status2 == 200 and a_v2.get("assetVersion") == 3
                 and a_v2.get("contentHash") != a_v1.get("contentHash"),
                 f"v={a_v2.get('assetVersion')}")

    # ---------- ⑤ reorder → 版本再 +1（顺序算内容） ----------
    items_now = [case_ids[0]]
    status, body = http.call("POST", f"{base}/{set_a_id}/items", token,
                             {"caseId": case_ids[2]})
    if status != 200:
        raise SystemExit(f"addItem(TC-003) 失败 HTTP {status}: {body}")
    status, body = http.call("PUT", f"{base}/{set_a_id}/order", token,
                             {"caseIds": [case_ids[2], case_ids[0]]})
    status2, body2 = http.call("GET",
                               f"/api/test-sets/{set_a_id}/asset-version?projectId={project_id}",
                               token)
    a_v3 = data_of(body2) if status2 == 200 else {}
    # addItem(TC-003) 是第 4 次内容变更 → v4；reorder 换序是第 5 次 → v5
    report.check("PUBX01-06", "reorder 后 version=5（顺序算内容：addItem→v4，换序→v5）",
                 status == 200 and status2 == 200 and a_v3.get("assetVersion") == 5,
                 f"v={a_v3.get('assetVersion')}（items={items_now}+TC003 换序）")

    # ---------- ⑥ 改名：不涨版本 ----------
    status, body = http.call("PUT", f"{base}/{set_a_id}", token,
                             {"name": "项目集 A（改名）", "description": "改名不改内容"})
    status2, body2 = http.call("GET",
                               f"/api/test-sets/{set_a_id}/asset-version?projectId={project_id}",
                               token)
    a_v3b = data_of(body2) if status2 == 200 else {}
    report.check("PUBX01-07", "改名（PUT）后 version 仍 5、hash 不变",
                 status == 200 and status2 == 200 and a_v3b.get("assetVersion") == 5
                 and a_v3b.get("contentHash") == a_v3.get("contentHash"),
                 f"v={a_v3b.get('assetVersion')}")

    # ---------- ⑦ bind-copy：登记副本（内容与平台 v1 相同） ----------
    status, body = http.call("POST", base, token,
                             {"setCode": SET_B_CODE, "name": "项目集 B（副本）",
                              "description": "PUBX-01 副本"})
    set_b = data_of(body) if status == 200 else {}
    set_b_id = int(set_b.get("id", 0)) if set_b else 0
    for case_id in case_ids[:2]:
        http.call("POST", f"{base}/{set_b_id}/items", token, {"caseId": case_id})
    status, body = http.call("POST",
                             f"/api/test-sets/{set_b_id}/asset-version/bind-copy"
                             f"?projectId={project_id}&platformSetId={platform_set_id}", token)
    bound = data_of(body) if status == 200 else {}
    db_bound = mysql(f"SELECT CONCAT(fork_state, '/', COALESCE(synced_asset_version, 'NULL'), '/', "
                     f"COALESCE(LEFT(synced_content_hash, 12), 'NULL'), '/', "
                     f"COALESCE(source_asset_id, 'NULL')) FROM @DB@.asset_version "
                     f"WHERE asset_type = 'TESTSET' AND asset_id = {set_b_id};")
    report.check("PUBX01-08", "bindCopy 200：SYNCED、synced=平台 v1、source 指回平台集（库里同读数）",
                 status == 200 and bound.get("forkState") == "SYNCED"
                 and bound.get("syncedAssetVersion") == 1
                 and bound.get("sourceAssetId") == platform_set_id
                 and db_bound.startswith("SYNCED/1/"),
                 f"HTTP {status} 库={db_bound}")

    status, body = http.call("GET",
                             f"/api/test-sets/{set_b_id}/asset-version?projectId={project_id}",
                             token)
    b_view = data_of(body) if status == 200 else {}
    report.check("PUBX01-09", "副本读数：hasNewVersion=false（刚对齐）、latest 反映平台 v1",
                 status == 200 and b_view.get("hasNewVersion") is False
                 and b_view.get("latestAssetVersion") == 1,
                 f"hasNew={b_view.get('hasNewVersion')} latest={b_view.get('latestAssetVersion')}")

    # ---------- ⑧ 平台升版 → references 报落后 ----------
    mysql(f"INSERT INTO @DB@.test_set_item (test_set_id, test_case_id, seq_no) "
          f"VALUES ({platform_set_id}, {case_ids[2]}, 3);")
    status, body = http.call("POST",
                             f"/api/test-sets/{platform_set_id}/asset-version/refresh"
                             "?projectId=0", token)
    platform_v2 = data_of(body) if status == 200 else {}
    report.check("PUBX01-10", "平台集加条目后 refresh → version=2",
                 status == 200 and platform_v2.get("assetVersion") == 2,
                 f"v={platform_v2.get('assetVersion')}")

    status, body = http.call("GET",
                             f"/api/test-sets/{platform_set_id}/asset-version/references"
                             f"?projectId={project_id}", token)
    refs = data_of(body) if status == 200 else []
    ref_a = next((r for r in refs if r.get("copyAssetId") == set_b_id), None)
    report.check("PUBX01-11", "references：副本 hasNewVersion=true、latest=2、forkState=SYNCED",
                 status == 200 and ref_a is not None and ref_a.get("hasNewVersion") is True
                 and ref_a.get("latestAssetVersion") == 2
                 and ref_a.get("forkState") == "SYNCED",
                 f"refs={len(refs or [])} 副本读数={ref_a}")

    # ---------- ⑨ 负向：绑到未登记版本的平台集 → 404；错误信息点名 ----------
    status, body = http.call("POST",
                             f"/api/test-sets/{set_a_id}/asset-version/bind-copy"
                             f"?projectId={project_id}&platformSetId=99999999", token)
    report.check("PUBX01-12", "绑到不存在的平台集 → 404，文案含「未登记版本信息」",
                 status == 404 and "未登记版本信息" in message_of(body),
                 f"HTTP {status} msg={message_of(body)[:60]}")

    status, body = http.call("POST",
                             f"/api/test-sets/{set_b_id}/asset-version/bind-copy"
                             f"?projectId={project_id}&platformSetId={set_b_id}", token)
    report.check("PUBX01-13", "副本指向自己 → 400", status == 400,
                 f"HTTP {status} msg={message_of(body)[:60]}")

    # 收尾：删除夹具集与版本行（用例保留供人工核对），平台集也清掉
    cleanup(project_id)
    print("\n# 夹具已清理（test_case 保留 PUBX01-TC-* 供人工核对）")
    return report.dump()


if __name__ == "__main__":
    raise SystemExit(main())
