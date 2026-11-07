#!/usr/bin/env bash
# B5 真实 HTTP 计时与正确性断言：/api/audit 的 OFFSET 深翻 vs keyset。
# 前置：后端 :8081 已部署含 cursor 的 jar；QA_E2E_PASSWORD 在 ~/.config/robot-autotest/env。
set -u
cd ~/robot-autotest
set -a; . ~/.config/robot-autotest/env; set +a
export MYSQL_PWD="${DB_PASSWORD:-}"
M="mysql -h${DB_HOST:-127.0.0.1} -P${DB_PORT:-3306} -u${DB_USERNAME:-root} ${DB_NAME:-robot_dev}"
API=${API:-http://127.0.0.1:8081}
OUT=${OUT:-/tmp/b5-http}
mkdir -p "$OUT"
TOKEN=$(curl -s --noproxy '*' -X POST $API/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"qa-e2e-admin\",\"password\":\"$QA_E2E_PASSWORD\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["token"])')

CUR=$($M -N -e "SELECT occurred_at, id FROM audit_log WHERE project_id=-1 ORDER BY occurred_at DESC, id DESC LIMIT 1 OFFSET 500000")
CAT=${CUR%%$'\t'*}; CID=${CUR##*$'\t'}
CMILLIS=$($M -N -e "SELECT CAST(UNIX_TIMESTAMP('$CAT') * 1000 AS UNSIGNED)")
GCUR=$($M -N -e "SELECT occurred_at, id FROM audit_log ORDER BY occurred_at DESC, id DESC LIMIT 1 OFFSET 500000")
GAT=${GCUR%%$'\t'*}; GID=${GCUR##*$'\t'}
GMILLIS=$($M -N -e "SELECT CAST(UNIX_TIMESTAMP('$GAT') * 1000 AS UNSIGNED)")

get() { local label="$1" url="$2" out="$3" best=999999 t0 t1 ms
  for _ in 1 2 3; do t0=$(date +%s%N)
    curl -s --noproxy '*' -H "Authorization: Bearer $TOKEN" "$url" -o "$out"
    t1=$(date +%s%N); ms=$(( (t1 - t0) / 1000000 )); [ "$ms" -lt "$best" ] && best=$ms; done
  printf '%-42s %8s ms\n' "$label" "$best"; }

echo "# B5 HTTP @ $(date -Is)  audit_log rows=$($M -N -e 'select count(*) from audit_log')"
get "P-OFFSET page1"      "$API/api/audit?projectId=-1&page=1&size=20" "$OUT/p-offset-1.json"
get "P-OFFSET page25000"  "$API/api/audit?projectId=-1&page=25000&size=20" "$OUT/p-offset-25000.json"
get "P-OFFSET page25001"  "$API/api/audit?projectId=-1&page=25001&size=20" "$OUT/p-offset-25001.json"
get "P-KEYSET after 500k" "$API/api/audit?projectId=-1&size=20&cursor=${CMILLIS}_${CID}" "$OUT/p-keyset.json"
get "G-OFFSET page25000"  "$API/api/audit?page=25000&size=20" "$OUT/g-offset-25000.json"
get "G-KEYSET after 500k" "$API/api/audit?size=20&cursor=${GMILLIS}_${GID}" "$OUT/g-keyset.json"

python3 - "$OUT" <<'PY'
import json, pathlib, sys
raw = pathlib.Path(sys.argv[1])
def items(n): return json.loads((raw / n).read_text())["data"]["items"]
p25, p26, key = items("p-offset-25000.json"), items("p-offset-25001.json"), items("p-keyset.json")
checks = {
    "offset 每页 20 行": len(p25) == 20 and len(p26) == 20,
    "keyset 每页 20 行": len(key) == 20,
    "keyset 首行 = offset 下一页第 2 行（游标行不重复）": key[0]["id"] == p26[1]["id"],
    "keyset 与上一页无重叠": not ({r["id"] for r in p25} & {r["id"] for r in key}),
    "keyset occurred_at 降序": all(key[i]["occurredAt"] >= key[i+1]["occurredAt"] for i in range(len(key)-1)),
    "keyset 页内 id 降序": all(key[i]["id"] > key[i+1]["id"] for i in range(len(key)-1)),
}
for k, v in checks.items(): print(("PASS " if v else "FAIL ") + k)
sys.exit(0 if all(checks.values()) else 1)
PY
