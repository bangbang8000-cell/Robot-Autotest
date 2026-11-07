#!/usr/bin/env python3
"""A6 · 演练前后两次 mysqldump 的差异审计。

判据不是「sha256 相同」（mysqldump 页脚带完成时间，永远不同），而是：
  1) 67 张业务表**行数**是否一致（drill.py 已算）
  2) 逐表内容哈希：哪些表真的变了
  3) 对变化的表做**列级**定位：只改时间戳（心跳）还是改了业务值

输出 raw/db-diff.json，并在 stdout 打印摘要。
"""
import gzip
import hashlib
import json
import os
import re

RAW = os.path.expanduser("~/robot-autotest/docs/evidence/v2.0/a6-entry/raw")

# 只给「实际报差异」的两张表做列名映射，值来自 `SHOW COLUMNS`（见 raw/db-diff.json 旁的说明）
COLUMNS = {
    "api_token": ["id", "user_id", "name", "token_hash", "last_used_at", "expires_at", "revoked_at",
                  "created_at", "created_by", "updated_at", "updated_by", "token_kind", "project_id", "scopes"],
    "resource_lease": ["id", "job_id", "run_id", "project_id", "resource_key", "lease_state", "release_reason",
                       "leased_at", "last_heartbeat_at", "expires_at", "created_at", "created_by",
                       "updated_at", "updated_by"],
}


def read(path):
    return gzip.open(path, "rt", encoding="utf-8", errors="replace").read()


def table_blocks(text):
    out, cur = {}, None
    for line in text.splitlines():
        m = re.match(r"(INSERT INTO|CREATE TABLE|DROP TABLE IF EXISTS|LOCK TABLES|ALTER TABLE) `?([a-z0-9_]+)`?",
                     line, re.I)
        if m:
            cur = m.group(2)
        if cur and not line.startswith("-- Dump completed"):
            out.setdefault(cur, []).append(line)
    return out


def tuples(line):
    """把一整行 INSERT ... VALUES (..),(..) 拆成逐行的列值列表。"""
    if "VALUES" not in line:
        return []
    tail = line[line.index("VALUES") + 7:]
    rows = []
    for part in re.split(r"\),\(", tail.strip().rstrip(";")):
        cleaned = part.strip().lstrip("(").rstrip(")")
        rows.append([t if t is not None else s.strip()
                     for t, s in re.findall(r"'([^']*)'|([^,'()]+)", cleaned)])
    return rows


def col_diff(old_line, new_line):
    """逐行逐列比较，返回「第几行第几列：旧值 -> 新值」。"""
    ro, rn = tuples(old_line or ""), tuples(new_line or "")
    diffs = []
    if len(ro) != len(rn):
        return [{"kind": "row-count", "old_rows": len(ro), "new_rows": len(rn)}]
    for ri, (o_row, n_row) in enumerate(zip(ro, rn)):
        for ci, (a, b) in enumerate(zip(o_row, n_row)):
            if a != b:
                diffs.append({"kind": "value", "row": ri, "col": ci, "old": a, "new": b,
                              "pk": o_row[0] if o_row else None})
    return diffs


def main():
    before = table_blocks(read(os.path.join(RAW, "db-before.sql.gz")))
    after = table_blocks(read(os.path.join(RAW, "db-after.sql.gz")))
    report = {"tables_total": len(set(before) | set(after)), "changed": {}}
    for t in sorted(set(before) | set(after)):
        hb = hashlib.sha256("\n".join(before.get(t, [])).encode()).hexdigest()
        ha = hashlib.sha256("\n".join(after.get(t, [])).encode()).hexdigest()
        if hb == ha:
            continue
        pairs = [(a, b) for a, b in zip(before.get(t, []), after.get(t, [])) if a != b]
        cols = COLUMNS.get(t, [])
        rows = []
        for a, b in pairs:
            cells = col_diff(a, b)
            for c in cells:
                if c.get("kind") == "value":
                    c["column"] = cols[c["col"]] if c["col"] < len(cols) else f"col#{c['col']}"
            rows.append({"cols": cells})
        report["changed"][t] = rows
    report["changed_count"] = len(report["changed"])
    report["rows_inserted_or_deleted"] = "0（67 表行数逐一相同，见 drill.py 的 db_before/db_after counts）"
    path = os.path.join(RAW, "db-diff.json")
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(report, fh, ensure_ascii=False, indent=2)
    print(json.dumps({"tables_total": report["tables_total"],
                      "changed": {k: len(v) for k, v in report["changed"].items()}}, ensure_ascii=False, indent=1))
    for t, rows in report["changed"].items():
        for row in rows:
            for c in row["cols"]:
                if c.get("kind") == "value":
                    print(f"  {t}.{c['column']}: {c['old']} -> {c['new']}")
                else:
                    print(f"  {t} {c}")


if __name__ == "__main__":
    main()
