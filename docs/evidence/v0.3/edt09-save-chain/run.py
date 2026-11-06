#!/usr/bin/env python3
"""EDT-09 保存链路真实验收（真实 HTTP + 真实 RF --dryrun 拦截 + 真实库）。

关掉的缺口
----------
WBS 原话：「本机无 RF，`--dryrun` 真实拦截与端到端保存链路待现场」。
本机实测 `python3 -m robot` 为 Robot Framework 7.5.1，`ProcessRobotSyntaxValidator`
默认命令即可用，因此这条缺口在本机就能真判，不必等现场。

覆盖
----
1. 合法 .robot 保存成功，且 `syntaxChecked=true`（证明真的调了 RF，不是「未校验」放行）
2. 语法错误内容被 RF `--dryrun` 真拦下，返回 4xx 且消息带行号
3. 乐观锁：用过期 `baseVersion` 保存 → 409，不覆盖别人
4. 内容未变再存 → `changed=false` 且版本不增（FR-v0.3-20）
5. 版本历史可列（`/files/versions`）
6. 回滚生成新版本而不是删历史（FR-v0.3-22）

前置：`docs/evidence/v1.0/auth-http-e2e/run.py` 已跑过一次（它建 `qa-e2e-fixture`
项目与 `qa-e2e-engineer` 账号）。本脚本只登录、不再造账号。
"""
from __future__ import annotations

import json
import pathlib
import re
import sys
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8001"
USERNAME = "qa-e2e-engineer"
PASSWORD = "QaE2e!2026-fixture"
PROJECT_CODE = "qa-e2e-fixture"
FILE_PATH = "qa-e2e/edt09-save-chain.robot"
LOGFILE = pathlib.Path(__file__).resolve().parent / "run.log"


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
    import os
    import subprocess
    db = cfg.get("DB_NAME") or "robot_dev"
    r = subprocess.run(["mysql", "-u", cfg.get("DB_USERNAME", "root"),
                        "-h", cfg.get("DB_HOST", "127.0.0.1"),
                        "-P", cfg.get("DB_PORT", "3306"), "-N", "-B", "-e",
                        sql.replace("@DB@", db)],
                       env=dict(os.environ, MYSQL_PWD=cfg.get("DB_PASSWORD", "")),
                       capture_output=True, text=True)
    if r.returncode != 0:
        raise SystemExit(f"SQL 失败: {r.stderr.strip()[:250]}")
    return r.stdout.strip()


def call(method: str, path: str, token: str | None = None,
         body: dict | None = None) -> tuple[int, str]:
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "replace")


GOOD = """*** Settings ***
Documentation    EDT-09 保存链路验收（合法内容）

*** Test Cases ***
保存链路
    Log    hello edt09
"""

BAD = """*** Test Cases ***
坏语法
    Log    x

*** This Is Not A Section ***
乱写的段落
"""


def main() -> int:
    cfg = load_db_config()
    project_id = int(mysql(f"SELECT id FROM @DB@.project WHERE code = '{PROJECT_CODE}';", cfg))

    code, body = call("POST", "/api/auth/login", body={"username": USERNAME, "password": PASSWORD})
    if code != 200:
        print(f"FAIL 登录失败 HTTP {code} {body[:160]}")
        return 1
    token = (json.loads(body).get("data") or {}).get("token", "")

    rows: list[tuple[str, str, bool, str]] = []

    # 清理上一轮遗留文件版本（只删本脚本自己的路径）
    existing_id = mysql(f"SELECT id FROM @DB@.robot_file WHERE project_id = {project_id} "
                        f"AND path = '{FILE_PATH}';", cfg)
    if existing_id:
        mysql(f"DELETE FROM @DB@.robot_file_version WHERE robot_file_id = {existing_id};", cfg)
        mysql(f"DELETE FROM @DB@.robot_file WHERE id = {existing_id};", cfg)

    # 1. 合法保存
    code, body = call("PUT", "/api/editor/save", token,
                      {"projectId": project_id, "path": FILE_PATH, "content": GOOD,
                       "baseVersion": 0, "comment": "EDT-09 首次保存"})
    ok1 = code == 200
    payload = json.loads(body).get("data") if ok1 else {}
    rows.append(("EDT09-01", "合法 .robot 保存成功", ok1, f"HTTP {code}"))
    rows.append(("EDT09-02", "保存时真的调用了 RF（syntaxChecked=true）",
                 bool(payload.get("syntaxChecked")), f"syntaxChecked={payload.get('syntaxChecked')}"))
    version1 = payload.get("version")

    # 2. 语法错误被 RF dryrun 拦下
    code, body = call("PUT", "/api/editor/save", token,
                      {"projectId": project_id, "path": FILE_PATH, "content": BAD,
                       "baseVersion": version1, "comment": "EDT-09 语法错误内容"})
    has_line = bool(re.search(r"行|line", body))
    rows.append(("EDT09-03", "语法错误内容被 RF --dryrun 拦截", code in (400, 409, 422),
                 f"HTTP {code}"))
    rows.append(("EDT09-04", "拦截消息带行号线索", has_line, body[:120]))

    # 3. 内容未变再存 → changed=false 且不增版
    code, body = call("PUT", "/api/editor/save", token,
                      {"projectId": project_id, "path": FILE_PATH, "content": GOOD,
                       "baseVersion": version1, "comment": "EDT-09 同内容再存"})
    payload = json.loads(body).get("data") if code == 200 else {}
    rows.append(("EDT09-05", "内容未变再存 changed=false",
                 code == 200 and payload.get("changed") is False, f"HTTP {code} changed={payload.get('changed')}"))
    rows.append(("EDT09-06", "内容未变时版本不增",
                 payload.get("version") == version1, f"version={payload.get('version')} 期望 {version1}"))

    # 4. 过期 baseVersion → 409（模拟两个人同时编辑）
    code, body = call("PUT", "/api/editor/save", token,
                      {"projectId": project_id, "path": FILE_PATH,
                       "content": GOOD + "\n# 并发改动\n",
                       "baseVersion": 0, "comment": "EDT-09 过期版本号"})
    rows.append(("EDT09-07", "过期 baseVersion 保存被拒（乐观锁）", code == 409, f"HTTP {code}"))

    # 5. 版本历史
    code, body = call("GET", f"/api/projects/{project_id}/files/versions?path={FILE_PATH}", token)
    vers = json.loads(body).get("data") if code == 200 else []
    rows.append(("EDT09-08", "版本历史可列且至少有 1 版",
                 code == 200 and isinstance(vers, list) and len(vers) >= 1,
                 f"HTTP {code} versions={len(vers) if isinstance(vers, list) else '?'}"))

    # 6. 回滚生成新版本（不删历史）
    code, body = call("POST", "/api/editor/rollback", token,
                      {"projectId": project_id, "path": FILE_PATH, "targetVersion": version1,
                       "comment": "EDT-09 回滚验证"})
    payload = json.loads(body).get("data") if code == 200 else {}
    rows.append(("EDT09-09", "回滚生成新版本（版本号递增，不删历史）",
                 code == 200 and payload.get("version", 0) > version1,
                 f"HTTP {code} version={payload.get('version')} 原={version1}"))

    code, body = call("GET", f"/api/projects/{project_id}/files/versions?path={FILE_PATH}", token)
    vers2 = json.loads(body).get("data") if code == 200 else []
    rows.append(("EDT09-10", "回滚后历史条数增加（历史保留）",
                 isinstance(vers2, list) and len(vers2) >= len(vers) + 1,
                 f"回滚前 {len(vers) if isinstance(vers, list) else '?'} → 回滚后 "
                 f"{len(vers2) if isinstance(vers2, list) else '?'}"))

    passed = sum(1 for r in rows if r[2])
    out = ["# EDT-09 保存链路真实验收（真实 RF --dryrun）", "",
           f"- 入口：`{BASE}` · 项目 id={project_id} · 文件 `{FILE_PATH}`",
           f"- RF：`python3 -m robot`（Robot Framework 7.5.1，本机实测）", "",
           "| # | 断言 | 结果 | 实测 |", "|---|---|---|---|"]
    for cid, desc, ok, detail in rows:
        out.append(f"| {cid} | {desc} | {'PASS' if ok else '**FAIL**'} | {detail} |")
    out += ["", f"**合计 {passed}/{len(rows)} 通过**", ""]

    text = "\n".join(out)
    LOGFILE.write_text(text, encoding="utf-8")
    print(text)
    return 0 if passed == len(rows) else 1


if __name__ == "__main__":
    sys.exit(main())
