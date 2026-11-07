#!/usr/bin/env bash
# B8 造数第二轮：run3 = 2 通过 + 1 跳过（run 状态 finished，报告可解析），用作 run1 的对比目标
set -u
set -a; . "$HOME/.config/robot-autotest/env"; set +a
EV="$HOME/robot-autotest/docs/evidence/v2.0/b8-browser-compare-trend"
API=http://127.0.0.1:8081
TOKEN=$(curl -s --noproxy '*' -X POST $API/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"qa-e2e-admin\",\"password\":\"$QA_E2E_PASSWORD\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["token"])')

SKIP_SUITE='*** Settings ***
Documentation     B8 对比/趋势浏览器回归夹具（第二轮：乙跳过）
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
    Skip   本轮不执行（用于对比页「无（本轮未执行）」标注）
B8 用例丙
    Log    round2 ok
'
python3 - "$API" "$TOKEN" "$SKIP_SUITE" "$EV" <<'PY'
import json, pathlib, sys, urllib.request
api, token, content, ev = sys.argv[1], sys.argv[2], sys.argv[3], pathlib.Path(sys.argv[4])
def call(method, path, body=None):
    req = urllib.request.Request(api + path, method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + token})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read())
print("save version:", call("PUT", "/api/editor/save", {"projectId": 29, "fileId": None,
      "path": "suites/b8/compare-fixture.robot", "content": content, "parseOk": True,
      "comment": "B8 浏览器回归夹具（跳过版）"})["data"]["version"])
run = call("POST", "/api/execution/runs", {"projectId": 29,
      "suitePaths": ["suites/b8/compare-fixture.robot"], "concurrency": 1,
      "timeoutSeconds": 180, "runRequestId": "b8-r3"})["data"]["runId"]
(ev / "raw/run3.txt").write_text(run + "\n")
print("run3=" + run)
PY
R3=$(cat "$EV/raw/run3.txt")
echo "run3(latest)=$R3"
for i in $(seq 1 90); do
  ST=$(MYSQL_PWD="$DB_PASSWORD" mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" "$DB_NAME" -N -B \
       -e "select run_status from run where run_id='$R3'" 2>/dev/null)
  case "$ST" in finished|failed|canceled) echo "$R3 -> $ST (${i}s)"; break;; esac
  sleep 1
done
echo "$R3" > "$EV/raw/run3.txt"
curl -s --noproxy '*' -H "Authorization: Bearer $TOKEN" "$API/api/reports/$R3/compare?projectId=29" \
  -o "$EV/raw/api-compare-run3.json" -w "compare http=%{http_code}\n"
python3 - "$EV" "$API" "$TOKEN" "$R3" <<'PY'
import json, pathlib, sys, urllib.request
ev, api, token, run = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3], sys.argv[4]
comp = json.loads((ev / "raw/api-compare-run3.json").read_text())["data"]
rows = comp.get("rows") or []
print("compare baseline:", comp.get("baseline"), "rows:", len(rows), "summary:", comp.get("summary"))
keys = [r["caseKey"] for r in rows if r.get("caseKey")]
print("caseKeys:", keys)
if keys:
    import urllib.parse
    q = urllib.parse.urlencode({"projectId": 29, "caseKey": keys[0], "limit": 5})
    req = urllib.request.Request(f"{api}/api/reports/{run}/trend?{q}",
                                 headers={"Authorization": "Bearer " + token})
    with urllib.request.urlopen(req, timeout=30) as r:
        data = json.loads(r.read())["data"]
    (ev / "raw/api-trend-run3.json").write_text(json.dumps({"data": data}, ensure_ascii=False, indent=1))
    print("trend points:", len(data.get("points") or []), "series:", len(data.get("series") or []),
          "baselineMs:", data.get("baselineMs"))
PY
