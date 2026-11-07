#!/usr/bin/env bash
# B2 造数：真实 run，向 stdout 打 10000 行（Log To Console），用于 1 万行日志页性能取证
set -u
set -a; . "$HOME/.config/robot-autotest/env"; set +a
EV="$HOME/robot-autotest/docs/evidence/v2.0/b2-log-10k"
mkdir -p "$EV/raw"
API=http://127.0.0.1:8081
TOKEN=$(curl -s --noproxy '*' -X POST $API/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"qa-e2e-admin\",\"password\":\"$QA_E2E_PASSWORD\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["token"])')

SUITE='*** Settings ***
Documentation     B2 一万行日志页性能夹具
Metadata          CASE_ID      B2-LOG-10K
Metadata          RUN_ID       B2-LOG
Metadata          QA_PROJECT   robot-autotest
Metadata          DUT          LOCAL-HOST
Metadata          LINK         LOCAL
Metadata          SW_BUILD     Ubuntu 24.04 / RF 7.x

*** Test Cases ***
B2 输出一万行
    FOR    ${i}    IN RANGE    10000
        Log To Console    第 ${i} 行 B2 log line payload
    END
'
python3 - "$API" "$TOKEN" "$SUITE" "$EV" <<'PY'
import json, sys, urllib.request, pathlib
api, token, content, ev = sys.argv[1], sys.argv[2], sys.argv[3], pathlib.Path(sys.argv[4])
def call(method, path, body=None):
    req = urllib.request.Request(api + path, method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + token})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read())
ver = call("PUT", "/api/editor/save", {"projectId": 29, "fileId": None,
      "path": "suites/b2/log10k.robot", "content": content, "parseOk": True,
      "comment": "B2 一万行日志夹具"})["data"]["version"]
print("save version:", ver)
run = call("POST", "/api/execution/runs", {"projectId": 29,
      "suitePaths": ["suites/b2/log10k.robot"], "concurrency": 1,
      "timeoutSeconds": 600, "runRequestId": "b2-log10k"})["data"]["runId"]
(ev / "raw/run.txt").write_text(run + "\n")
print("run=" + run)
PY
RID=$(cat "$EV/raw/run.txt")
for i in $(seq 1 180); do
  ST=$(MYSQL_PWD="$DB_PASSWORD" mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" "$DB_NAME" -N -B \
       -e "select run_status from run where run_id='$RID'" 2>/dev/null)
  case "$ST" in finished|failed|canceled) echo "$RID -> $ST (${i}s)"; break;; esac
  sleep 1
done
echo '=== stdout.log 行数 ==='
wc -l "$HOME/robot-test/artifacts/$RID/shard-0/stdout.log" 2>/dev/null || find "$HOME/robot-test/artifacts/$RID" -name 'stdout.log' -exec wc -l {} \;
echo '=== /log?limit=20000 ==='
curl -s --noproxy '*' -H "Authorization: Bearer $TOKEN" \
  "$API/api/execution/runs/$RID/log?projectId=29&limit=20000" -o "$EV/raw/log-20000.json" \
  -w 'http=%{http_code} bytes=%{size_download} time=%{time_total}s\n'
python3 - "$EV" <<'PY'
import json, pathlib, sys
d = json.loads((pathlib.Path(sys.argv[1])/"raw/log-20000.json").read_text())["data"]
print("lines returned:", len(d), "first:", d[0], "last:", d[-1])
PY
