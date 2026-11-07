#!/usr/bin/env python3
"""W5 · 把 examples/ 下的示例 RF 套件写入 robot_file（内容唯一真源）。

为什么走 API 而不是直接 INSERT：示例用例必须证明「平台自己的工作区保存路径」
可用（CAS-04 的 `PUT /api/projects/{id}/files`），绕过它插库只能证明 SQL 能写。
每次保存都会在 `robot_file_version` 留一版快照，所以本脚本先读后写：
内容与库内一致就跳过，重复执行不会制造无意义的版本。

用法：
  python3 tools/seed_sample_cases.py                 # 项目 29，写 6 条示例
  python3 tools/seed_sample_cases.py --project 3 --dry-run
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"
BASE = os.environ.get("SEED_BASE", "http://127.0.0.1:8001").rstrip("/")
USER = os.environ.get("SEED_USER", "qa-e2e-admin")

# 明确列出要入库的文件，不扫目录——避免把临时调试脚本一起塞进项目。
SAMPLE_FILES = [
    ("examples/01_quick_check.robot", "示例 1：最小可执行套件"),
    ("examples/02_list_ops.robot", "示例 2：列表操作"),
    ("examples/03_math.robot", "示例 3：数值与容差"),
    ("examples/04_string_ops.robot", "示例 4：字符串处理"),
    ("examples/05_dict_ops.robot", "示例 5：字典读写"),
    ("examples/06_timing.robot", "示例 6：日志与时间线"),
]

_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def env_value(key: str) -> str | None:
    if os.environ.get(key):
        return os.environ[key]
    path = Path.home() / ".config" / "robot-autotest" / "env"
    try:
        for line in path.read_text(encoding="utf-8").splitlines():
            stripped = line.strip()
            if not stripped or stripped.startswith("#") or "=" not in stripped:
                continue
            name, value = stripped.split("=", 1)
            if name.strip() == key:
                return value.strip()
    except OSError:
        return None
    return None


def call(method: str, path: str, token: str | None = None,
         body: dict | None = None, allow_404: bool = False) -> dict | None:
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(f"{BASE}{path}", data=data, headers=headers, method=method)
    try:
        with _OPENER.open(req, timeout=30) as resp:
            return json.loads(resp.read() or b"{}")
    except urllib.error.HTTPError as exc:
        if allow_404 and exc.code == 404:
            return None
        detail = exc.read()[:300].decode("utf-8", "replace")
        raise SystemExit(f"HTTP {exc.code} {method} {path}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise SystemExit(f"无法连接 {BASE}: {exc.reason}（先启动 :8001）") from exc


def login() -> str:
    password = env_value("QA_E2E_PASSWORD")
    if not password:
        raise SystemExit("缺少 QA_E2E_PASSWORD（~/.config/robot-autotest/env）")
    payload = call("POST", "/api/auth/login", body={"username": USER, "password": password})
    token = ((payload or {}).get("data") or {}).get("token")
    if not token:
        raise SystemExit(f"登录失败: {json.dumps(payload, ensure_ascii=False)[:200]}")
    return token


def main() -> int:
    parser = argparse.ArgumentParser(description="示例套件写入 robot_file")
    parser.add_argument("--project", type=int, default=int(os.environ.get("SEED_PROJECT_ID", "29")))
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    token = login()
    written = skipped = 0
    print(f"SEED_BASE {BASE} project={args.project} dryRun={args.dry_run}")
    print(f"{'path':34} {'action':8} {'version':>7} {'bytes':>7}")
    for rel, comment in SAMPLE_FILES:
        source = EXAMPLES / Path(rel).name
        if not source.exists():
            raise SystemExit(f"示例文件不存在: {source}")
        content = source.read_text(encoding="utf-8")
        current = call("GET", f"/api/projects/{args.project}/files/content?"
                       + urllib.parse.urlencode({"path": rel}), token, allow_404=True)
        data = (current or {}).get("data") or {}
        if data.get("content") == content:
            skipped += 1
            print(f"{rel:34} {'skip':8} {data.get('currentVersion', '-'):>7} "
                  f"{len(content.encode()):>7}")
            continue
        if args.dry_run:
            print(f"{rel:34} {'would':8} {'-':>7} {len(content.encode()):>7}")
            continue
        saved = call("PUT", f"/api/projects/{args.project}/files", token,
                     {"path": rel, "content": content, "comment": comment})
        record = (saved or {}).get("data") or {}
        written += 1
        print(f"{rel:34} {'write':8} {record.get('currentVersion', '-'):>7} "
              f"{len(content.encode()):>7}")

    print(f"SEED_RESULT {'PASS' if written + skipped == len(SAMPLE_FILES) else 'FAIL'} "
          f"written={written} skipped={skipped} total={len(SAMPLE_FILES)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
