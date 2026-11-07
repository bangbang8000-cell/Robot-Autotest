#!/usr/bin/env bash
# B8 造数：项目 29 里两个真实 run（同 DUT/LINK，第二轮 1 条新失败），供对比/趋势页浏览器回归
set -u
set -a; . "$HOME/.config/robot-autotest/env"; set +a
EV="$HOME/robot-autotest/docs/evidence/v2.0/b8-browser-compare-trend"
mkdir -p "$EV/raw"
API=http://127.0.0.1:8081
USER_NAME=qa-e2e-admin
TOKEN=$(curl -s --noproxy '*' -X POST $API/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_NAME\",\"password\":\"$QA_E2E_PASSWORD\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["token"])')
[ -z "$TOKEN" ] && { echo "login failed"; exit 1; }

PASS_SUITE='*** Settings ***
Documentation     B8 对比/趋势浏览器回归夹具（第一轮全通过）
Metadata          CASE_ID      B8-COMPARE-001
Metadata          RUN_ID       B8-COMPARE
Metadata          QA_PROJECT   robot-autotest
Metadata          DUT          LOCAL-HOST
Metadata          LINK         LOCAL
Metadata          SW_BUILD     Ubuntu 24.04 / RF 7.x

*** Test Cases ***
B8 用例甲
    Log    round1 ok
B8 用例乙
    Log    round1 ok
B8 用例丙
    Log    round1 ok
'
FAIL_SUITE='*** Settings ***
Documentation     B8 对比/趋势浏览器回归夹具（第二轮：乙失败）
Metadata          CASE_ID      B8-COMPARE-001
Metadata          RUN_ID       B8-COMPARE
Metadata          QA_PROJECT   robot-autotest
Metadata          DUT          LOCAL-HOST
Metadata          LINK         LOCAL
Metadata          SW_BUILD     Ubuntu 24.04 / RF 7.x

*** Test Cases ***
B8 用例甲
    Log    round2 ok
B8 用例乙
    Fail   B8 故意失败（用于对比页新失败标注）
B8 用例丙
    Log    round2 ok
'
save() {
  python3 - "$API" "$TOKEN" "$1" <<'PY'
import json, sys, urllib.request
api, token, content = sys.argv[1], sys.argv[2], sys.argv[3]
body = json.dumps({"projectId": 29, "fileId": None, "path": "suites/b8/compare-fixture.robot",
                   "content": content, "parseOk": True, "comment": "B8 浏览器回归夹具"}).encode()
req = urllib.request.Request(api + "/api/editor/save", data=body, method="PUT",
      headers={"Content-Type": "application/json", "Authorization": "Bearer " + token})
try:
    with urllib.request.urlopen(req, timeout=30) as r:
        print(r.status, r.read(120).decode())
except Exception as e:
    print("ERR", e)
PY
}
create_run() {
  python3 - "$API" "$TOKEN" "$1" <<'PY'
import json, sys, urllib.request
api, token, tag = sys.argv[1], sys.argv[2], sys.argv[3]
body = json.dumps({"projectId": 29, "suitePaths": ["suites/b8/compare-fixture.robot"],
                   "concurrency": 1, "timeoutSeconds": 180, "runRequestId": tag}).encode()
req = urllib.request.Request(api + "/api/execution/runs", data=body, method="POST",
      headers={"Content-Type": "application/json", "Authorization": "Bearer " + token})
with urllib.request.urlopen(req, timeout=30) as r:
    print(json.loads(r.read())["data"]["runId"])
PY
}
wait_run() {
  local rid="$1"
  for i in $(seq 1 120); do
    st=$(python3 - "$API" "$TOKEN" "$rid" <<'PY'
import json, sys, urllib.request
api, token, rid = sys.argv[1], sys.argv[2], sys.argv[3]
req = urllib.request.Request(f"{api}/api/execution/runs/{rid}?projectId=29",
      headers={"Authorization": "Bearer " + token})
with urllib.request.urlopen(req, timeout=15) as r:
    print(json.loads(r.read())["data"]["status"])
PY
)
    case "$st" in finished|failed|canceled) echo "$rid -> $st (${i}s)"; return 0;; esac
    sleep 1
  done
  echo "$rid -> TIMEOUT"; return 1
}

echo "== round1 (all pass) =="
save "$PASS_SUITE"
R1=$(create_run b8-r1); echo "run1=$R1"
wait_run "$R1"
echo "== round2 (1 new fail) =="
save "$FAIL_SUITE"
R2=$(create_run b8-r2); echo "run2=$R2"
wait_run "$R2"

echo "$R1" > "$EV/raw/run1.txt"; echo "$R2" > "$EV/raw/run2.txt"
echo "== parse + compare + trend (run2) =="
for ep in "reports/$R2/parse?projectId=29" "reports/$R2/compare?projectId=29" "reports/$R2/trend?projectId=29&caseKey=&limit=5"; do
  name=$(echo "$ep" | tr '/?&=' '____')
  curl -s --noproxy '*' -H "Authorization: Bearer $TOKEN" "$API/api/$ep" -o "$EV/raw/api-$name.json" -w "$ep http=%{http_code}\n"
done
python3 - "$EV" <<'PY'
import json, pathlib, sys
raw = pathlib.Path(sys.argv[1]) / "raw"
for p in raw.glob("api-*.json"):
    try:
        data = json.loads(p.read_text()).get("data")
    except Exception:
        continue
    if isinstance(data, dict) and "rows" in data:
        print("compare baseline:", data.get("baseline"), "rows:", len(data.get("rows") or []),
              "summary:", data.get("summary"))
    elif isinstance(data, dict) and "points" in data:
        print("trend points:", len(data.get("points") or []), "baselineMs:", data.get("baselineMs"))
PY
