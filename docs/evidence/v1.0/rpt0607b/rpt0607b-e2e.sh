#!/usr/bin/env bash
# RPT-06 + RPT-07 残余验证
#
# RPT-07：门禁原本只在「解析入库」算一次，导出时读 run.exportable 列。列一旦与 metadata 脱节
#        （重析、手工改库），外部版就会放行。本脚本把列手工改成 1，再用真实 HTTP 验证：
#        导出期按 metadata 重算 → 仍拒绝；内部版照常放行；被拒绝的导出不落 export_record。
# RPT-06：在本地 WSL 用真实大产物（候选 410 MB）跑一次完整清理：dry-run 不落手 → 执行删文件
#        并置 purged → run/test_result 一行不动 → 三类保护不入选 → 重复执行幂等 → 审计逐条可查。
set -uo pipefail

BASE="http://127.0.0.1:8081"
EVID="$(cd "$(dirname "$0")" && pwd)"
WORK="/tmp/rpt0607b-work"
ART="$EVID/artifacts"
LOGS="$EVID/logs"
CODE="RPT0607B"
SUITE="$EVID/suite/partial_metadata.robot"
FAILS=0

rm -rf "$WORK"
mkdir -p "$WORK" "$ART" "$LOGS" "$EVID/suite"

pass() { printf '  PASS  %s\n' "$*"; }
fail() { printf '  FAIL  %s\n' "$*"; FAILS=$((FAILS + 1)); }
chk() { if [ "$2" = "$3" ]; then pass "$1 ($2)"; else fail "$1：期望 $3，实际 $2"; fi; }
json() { python3 -c "import json;d=json.load(open('$1'));print($2)"; }

set -a
. /home/evergreen/.config/robot-autotest/env
set +a
C=mysql; command -v mariadb >/dev/null 2>&1 && C=mariadb
q() {
  "$C" --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --password="$DB_PASSWORD" \
    --batch --skip-column-names "$DB_NAME" -e "$1" 2>/dev/null
}
mb() { echo $(( $1 * 1024 * 1024 )); }

echo "== 0. 环境 =="
"$C" --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --password="$DB_PASSWORD" \
  "$DB_NAME" -e "select 1" >/dev/null 2>&1
chk "MySQL 可达" "$?" "0"

read -r TOKEN HASH <<< "$(python3 - <<'EOF'
import hashlib, secrets
t = secrets.token_urlsafe(32)
print(t, hashlib.sha256(t.encode()).hexdigest())
EOF
)"
q "insert into api_token (user_id, name, token_hash, token_kind, expires_at)
   values (1, 'rpt0607b-e2e', '$HASH', 'session', now(3) + interval 2 hour)"
chk "临时令牌已建" "$(q "select count(*) from api_token where token_hash='$HASH'")" "1"

call() { # name method url [extras...] —— 进度写 stderr，stdout 只回 HTTP 码
  local name=$1 method=$2 url=$3; shift 3
  local out
  out=$(curl -s -o "$WORK/$name.body" -D "$LOGS/$name.hdr" \
        -w '%{http_code} %{time_total}s %{size_download}B' \
        -X "$method" -H "Authorization: Bearer $TOKEN" "$@" "$url")
  echo "[$name] $out" >&2
  printf '%s\n' "${out%% *}"
}

# 清掉上次失败残留（按 code 定位项目）
OLD_PID=$(q "select id from project where code='$CODE'")
if [ -n "$OLD_PID" ]; then
  q "delete from run_metadata where run_id in (select run_id from \`run\` where project_id=$OLD_PID)"
  q "delete from keyword_result where run_id in (select id from \`run\` where project_id=$OLD_PID)"
  q "delete from test_result where run_id in (select id from \`run\` where project_id=$OLD_PID)"
  q "delete from run_artifact where run_id in (select id from \`run\` where project_id=$OLD_PID)"
  q "delete from export_record where project_id=$OLD_PID"
  q "delete from \`run\` where project_id=$OLD_PID"
  q "delete from project where id=$OLD_PID"
fi
# run_id 全局唯一：上一轮失败残留可能挂在不存在的项目下，按前缀再清一次
q "delete from run_metadata where run_id like 'RPT06-%' or run_id like 'RPT07-%'"
q "delete from keyword_result where run_id in (select id from \`run\` where run_id like 'RPT06-%' or run_id like 'RPT07-%')"
q "delete from test_result where run_id in (select id from \`run\` where run_id like 'RPT06-%' or run_id like 'RPT07-%')"
q "delete from run_artifact where run_id in (select id from \`run\` where run_id like 'RPT06-%' or run_id like 'RPT07-%')"
q "delete from \`run\` where run_id like 'RPT06-%' or run_id like 'RPT07-%'"

q "insert into project (code, name, status, created_by) values ('$CODE', 'RPT-06/07 验证专用', 1, 1)"
PID_=$(q "select id from project where code='$CODE'")
chk "专用项目已建" "$([ -n "$PID_" ] && echo ok)" "ok"

echo
echo "== 1. RPT-07 样本：只有 4 项 metadata 的真实 RF 产物 =="
R07="RPT07-EXPORT-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$WORK/rpt07/shard-0"
cat > "$SUITE" <<EOF
*** Settings ***
Documentation      RPT-06/07 样本：解析时只有 4 项 metadata，六项靠补录补齐
Metadata           CASE_ID       RPT07-PARTIAL-001
Metadata           RUN_ID        $R07
Metadata           QA_PROJECT    robot-autotest
Metadata           OPERATOR      evergreen

*** Test Cases ***
样本通过用例
    [Tags]    rpt07
    Log    缺六项 metadata，解析后不可对外导出
    Should Be Equal    1    1
EOF

"$HOME/rf-stack/.venv/bin/robot" --outputdir "$WORK/rpt07/shard-0" \
  --logtitle "RPT-07 sample" --reporttitle "RPT-07 sample" "$SUITE" \
  > "$LOGS/01-robot-run.log" 2>&1
chk "robot 执行" "$?" "0"
cp "$WORK/rpt07/shard-0/output.xml" "$ART/output.xml"
chk "产物 meta 标签数" "$(grep -c '<meta name=' "$ART/output.xml")" "4"

q "insert into \`run\` (run_id, project_id, run_status, started_at, finished_at, total_tests,
      passed_tests, failed_tests, created_by, trigger_type)
    values ('$R07', $PID_, 'finished', now(3) - interval 5 second, now(3), 1, 1, 0, 1, 'manual')"
R07_DB=$(q "select id from \`run\` where run_id='$R07'")
q "update \`run\` set artifacts_dir='$WORK/rpt07' where id=$R07_DB"
chk "run 行已登记" "$(q "select count(*) from \`run\` where id=$R07_DB")" "1"

code=$(call 02-parse POST "$BASE/api/reports/$R07/parse?projectId=$PID_")
cp "$WORK/02-parse.body" "$LOGS/02-parse.json"
chk "解析入库 HTTP" "$code" "200"
chk "解析后 exportable" "$(json "$WORK/02-parse.body" "str(d['data']['exportable']).lower()")" "false"
chk "解析后缺失项数" "$(json "$WORK/02-parse.body" "len(d['data']['metadataMissing'] or [])")" "6"
chk "解析后缺失清单" "$(json "$WORK/02-parse.body" "'/'.join(d['data']['metadataMissing'])")" \
  "DUT/LINK/SW_BUILD/BASELINE/STARTED_AT/CASE_KIND"

echo
echo "== 2. RPT-07：入库门禁为红时，导出期独立再判 =="
code=$(call 03-export-external-blocked POST "$BASE/api/reports/$R07/export?projectId=$PID_&mode=external&format=docx")
cp "$WORK/03-export-external-blocked.body" "$LOGS/03-export-blocked.json"
chk "外部导出 HTTP" "$code" "400"
chk "400 的 data.metadataMissing" \
  "$(json "$WORK/03-export-external-blocked.body" "'/'.join(d['data']['metadataMissing'])")" \
  "DUT/LINK/SW_BUILD/BASELINE/STARTED_AT/CASE_KIND"

code=$(call 04-export-internal POST "$BASE/api/reports/$R07/export?projectId=$PID_&mode=internal&format=docx")
cp "$WORK/04-export-internal.body" "$LOGS/04-export-internal.json"
chk "内部版不受门禁限制 HTTP" "$code" "200"

# 关键一步：把库里的 exportable 手工改成 1，模拟列与 metadata 脱节
q "update \`run\` set exportable=1, metadata_missing=null where id=$R07_DB"
chk "库列已被手工改成可导出" "$(q "select exportable from \`run\` where id=$R07_DB")" "1"
code=$(call 05-export-with-stale-column POST "$BASE/api/reports/$R07/export?projectId=$PID_&mode=external&format=docx")
cp "$WORK/05-export-with-stale-column.body" "$LOGS/05-export-stale.json"
chk "列说谎时外部导出仍 400（导出期重算）" "$code" "400"
chk "拒绝项仍来自 metadata" \
  "$(json "$WORK/05-export-with-stale-column.body" "'/'.join(d['data']['metadataMissing'])")" \
  "DUT/LINK/SW_BUILD/BASELINE/STARTED_AT/CASE_KIND"
chk "被拒绝的外部导出不落 export_record" \
  "$(q "select count(*) from export_record where run_key='$R07' and export_mode='external'")" "0"

echo
echo "== 3. RPT-07：补录六项后放行 =="
cat > "$WORK/patch-six.json" <<'EOF'
{"metadata":{"DUT":"WSL-DEV-NODE-02","LINK":"p1.enp23s0np0 -> p2.enp23s0np0",
"SW_BUILD":"Ubuntu 24.04.5 / Python 3.14","BASELINE":"188~195 Gbps",
"STARTED_AT":"2026-11-06T02:00:00Z","CASE_KIND":"perf-throughput"}}
EOF
code=$(call 06-patch-six PATCH "$BASE/api/execution/runs/$R07/metadata?projectId=$PID_" \
  -H 'Content-Type: application/json' -d @"$WORK/patch-six.json")
cp "$WORK/06-patch-six.body" "$LOGS/06-patch-six.json"
chk "补录 HTTP" "$code" "200"
chk "补录后 run.exportable" "$(q "select exportable from \`run\` where id=$R07_DB")" "1"

code=$(call 07-export-allowed POST "$BASE/api/reports/$R07/export?projectId=$PID_&mode=external&format=docx")
cp "$WORK/07-export-allowed.body" "$LOGS/07-export-allowed.json"
chk "补录后外部导出 HTTP" "$code" "200"
EXP_ID=$(json "$WORK/07-export-allowed.body" "d['data']['exportId']")
code=$(call 08-download GET "$BASE/api/exports/$EXP_ID/download?projectId=$PID_")
chk "下载 HTTP" "$code" "200"
cp "$WORK/08-download.body" "$ART/report_external.docx"
SZ=$(stat -c %s "$ART/report_external.docx")
chk "DOCX 非空（>3 KB）" "$([ "$SZ" -gt 3000 ] && echo ok)" "ok"
chk "DOCX 是可打开 zip 且含正文" \
  "$(python3 -c "import zipfile;z=zipfile.ZipFile('$ART/report_external.docx');print('ok' if 'word/document.xml' in z.namelist() else 'no')")" "ok"
q "select id, run_key, export_mode, export_format, export_status, size_bytes, left(sha256,12)
     from export_record where run_key='$R07' order by id" | tee "$LOGS/09-export-record.tsv"
chk "export_record 一条 external" \
  "$(q "select count(*) from export_record where run_key='$R07' and export_mode='external'")" "1"

echo
echo "== 4. RPT-06 样本：25 个运行 + 真实大产物（候选 410 MB / 全项目 650 MB） =="
# 19 个最近的空运行：把「最近 20 次保护」占满，让老运行进入候选
for i in $(seq 1 19); do
  q "insert into \`run\` (run_id, project_id, run_status, started_at, finished_at, total_tests,
        passed_tests, failed_tests, created_by, trigger_type)
      values ('RPT06-KEEP-$i-$(date +%s)', $PID_, 'finished', now(3) - interval $i minute,
              now(3) - interval $i minute, 0, 0, 0, 1, 'manual')"
done

seed_run() { # runId 天数 每例结论数
  q "insert into \`run\` (run_id, project_id, run_status, started_at, finished_at, total_tests,
        passed_tests, failed_tests, created_by, trigger_type)
      values ('$1', $PID_, 'finished', now(3) - interval $2 day, now(3) - interval $2 day,
              $3, $3, 0, 1, 'manual')"
  local id; id=$(q "select id from \`run\` where run_id='$1'")
  for k in $(seq 1 "$3"); do
    q "insert into test_result (run_id, round_no, verdict, duration_ms) values ($id, 1, 'PASS', 12)"
  done
  echo "$id"
}

BIG1="RPT06-BIG-001"; BIG2="RPT06-BIG-002"; BIG3="RPT06-BIG-003"
PIN="RPT06-PINNED-001"; P0="RPT06-P0-001"; EXP="RPT06-EXPORTED-001"; DECOY="RPT06-DECOY-001"
mkdir -p "$WORK/art" "$WORK/art2" "$WORK/art3" "$WORK/artp" "$WORK/art0" "$WORK/arte"
seed_run "$DECOY" 150 0 > "$WORK/decoy.id"
BIG1_DB=$(seed_run "$BIG1" 200 3); BIG2_DB=$(seed_run "$BIG2" 201 2); BIG3_DB=$(seed_run "$BIG3" 202 1)
PIN_DB=$(seed_run "$PIN" 210 1); P0_DB=$(seed_run "$P0" 220 1); EXP_DB=$(seed_run "$EXP" 230 1)

dd if=/dev/zero of="$WORK/art/output.xml"  bs=1M count=150 status=none
dd if=/dev/zero of="$WORK/art/log.html"    bs=1M count=40  status=none
dd if=/dev/zero of="$WORK/art2/output.xml" bs=1M count=120 status=none
dd if=/dev/zero of="$WORK/art2/log.html"   bs=1M count=30  status=none
dd if=/dev/zero of="$WORK/art3/output.xml" bs=1M count=60  status=none
dd if=/dev/zero of="$WORK/art3/log.html"   bs=1M count=10  status=none
dd if=/dev/zero of="$WORK/artp/output.xml" bs=1M count=90  status=none
dd if=/dev/zero of="$WORK/art0/output.xml" bs=1M count=80  status=none
dd if=/dev/zero of="$WORK/arte/output.xml" bs=1M count=70  status=none

reg() { # runDbId kind path sizeMb pinned noteLiteral
  q "insert into run_artifact (run_id, project_id, kind, path, size_bytes, availability, note, pinned, created_by)
     values ($1, $PID_, '$2', '$3', $(mb "$4"), 'ok', $6, $5, 1)"
}
reg "$BIG1_DB" output_xml "$WORK/art/output.xml"  150 0 "NULL"
reg "$BIG1_DB" log_html   "$WORK/art/log.html"     40 0 "NULL"
reg "$BIG2_DB" output_xml "$WORK/art2/output.xml" 120 0 "NULL"
reg "$BIG2_DB" log_html   "$WORK/art2/log.html"    30 0 "NULL"
reg "$BIG3_DB" output_xml "$WORK/art3/output.xml"  60 0 "NULL"
reg "$BIG3_DB" log_html   "$WORK/art3/log.html"    10 0 "NULL"
reg "$PIN_DB"  output_xml "$WORK/artp/output.xml"  90 1 "NULL"
reg "$P0_DB"   output_xml "$WORK/art0/output.xml"  80 0 "'p0_evidence: 客户验收原始记录'"
reg "$EXP_DB"  output_xml "$WORK/arte/output.xml"  70 0 "NULL"
q "insert into export_record (project_id, run_key, export_format, export_status, requested_by)
   values ($PID_, '$EXP', 'docx', 'created', 1)"

ALL_BYTES=$(mb 650)
CLEAN_BYTES=$(mb 410)
KEEP_BYTES=$(mb 240)
chk "全项目登记占用 650 MB" "$(q "select sum(size_bytes) from run_artifact
   where project_id=$PID_ and availability='ok'")" "$ALL_BYTES"
chk "run 行数 27（19 保留 + 1 干扰 + 6 候选 + R07）" \
  "$(q "select count(*) from \`run\` where project_id=$PID_")" "27"
DISK_BEFORE=$(du -sb "$WORK/art" "$WORK/art2" "$WORK/art3" | awk '{s+=$1} END {print s}')
echo "[disk] 待清理三个目录合计 ${DISK_BEFORE} 字节" | tee "$LOGS/10-disk-before.txt"

echo
echo "== 5. RPT-06 dry-run：只出清单 =="
code=$(call 11-retention-dry POST "$BASE/api/reports/retention/run?projectId=$PID_")
cp "$WORK/11-retention-dry.body" "$LOGS/11-retention-dry.json"
chk "dry-run HTTP" "$code" "200"
chk "dryRun=true" "$(json "$WORK/11-retention-dry.body" "str(d['data']['dryRun']).lower()")" "true"
chk "候选运行数" "$(json "$WORK/11-retention-dry.body" "d['data']['runCount']")" "3"
chk "候选字节数 410 MB" "$(json "$WORK/11-retention-dry.body" "d['data']['totalBytes']")" "$CLEAN_BYTES"
chk "项目总占用 650 MB" "$(json "$WORK/11-retention-dry.body" "d['data']['projectBytes']")" "$ALL_BYTES"
chk "dry-run 不删文件" "$(json "$WORK/11-retention-dry.body" "d['data']['deletedFiles']")" "0"
chk "dry-run 不动 availability" "$(q "select count(*) from run_artifact
   where project_id=$PID_ and availability='purged'")" "0"
chk "dry-run 不写审计" "$(q "select count(*) from audit_log where action='artifact_purge'
   and resource_id like 'RPT06-%'")" "0"
chk "dry-run 后文件仍在" "$([ -f "$WORK/art/output.xml" ] && echo ok)" "ok"
json "$WORK/11-retention-dry.body" "'|'.join('%s:%s' % (i['runId'], i['reason']) for i in d['data']['items'])" \
  | tee "$LOGS/11-retention-items.txt"

echo
echo "== 6. RPT-06 执行：删文件 + 置 purged，结论不动 =="
RUNS_BEFORE=$(q "select count(*) from \`run\` where project_id=$PID_")
TESTS_BEFORE=$(q "select count(*) from test_result t join \`run\` r on r.id=t.run_id where r.project_id=$PID_")
code=$(call 12-retention-exec POST "$BASE/api/reports/retention/run?projectId=$PID_&dryRun=false")
cp "$WORK/12-retention-exec.body" "$LOGS/12-retention-exec.json"
chk "执行 HTTP" "$code" "200"
chk "dryRun=false" "$(json "$WORK/12-retention-exec.body" "str(d['data']['dryRun']).lower()")" "false"
chk "清理运行数" "$(json "$WORK/12-retention-exec.body" "d['data']['runCount']")" "3"
chk "删除文件数" "$(json "$WORK/12-retention-exec.body" "d['data']['deletedFiles']")" "6"
chk "释放字节数 410 MB" "$(json "$WORK/12-retention-exec.body" "d['data']['totalBytes']")" "$CLEAN_BYTES"

chk "6 个文件全部删除" "$([ ! -f "$WORK/art/output.xml" ] && [ ! -f "$WORK/art/log.html" ] \
  && [ ! -f "$WORK/art2/output.xml" ] && [ ! -f "$WORK/art2/log.html" ] \
  && [ ! -f "$WORK/art3/output.xml" ] && [ ! -f "$WORK/art3/log.html" ] && echo ok)" "ok"
chk "purged 行数" "$(q "select count(*) from run_artifact
   where project_id=$PID_ and availability='purged'")" "6"
chk "purged 保留 size_bytes（可查占用量）" "$(q "select sum(size_bytes) from run_artifact
   where project_id=$PID_ and availability='purged'")" "$CLEAN_BYTES"
chk "run 行数不变" "$(q "select count(*) from \`run\` where project_id=$PID_")" "$RUNS_BEFORE"
chk "test_result 行数不变" "$(q "select count(*) from test_result t join \`run\` r on r.id=t.run_id
   where r.project_id=$PID_")" "$TESTS_BEFORE"
chk "BIG-001 三条 PASS 仍可查" "$(q "select count(*) from test_result where run_id=$BIG1_DB and verdict='PASS'")" "3"
chk "剩余占用只剩受保护的 240 MB" "$(q "select sum(size_bytes) from run_artifact
   where project_id=$PID_ and availability='ok'")" "$KEEP_BYTES"
# ArtifactFileStoreImpl 删完最后一个文件会顺手移除已空目录，所以这里断言的是「目录已不在」，
# 而不是「目录还在但变空」。
chk "三个产物目录已清空移除" "$([ ! -d "$WORK/art" ] && [ ! -d "$WORK/art2" ] \
  && [ ! -d "$WORK/art3" ] && echo ok)" "ok"

echo
echo "== 7. 三类保护：不做清理 =="
chk "pinned 产物仍 ok" "$(q "select availability from run_artifact where path='$WORK/artp/output.xml'")" "ok"
chk "pinned 文件仍在" "$([ -f "$WORK/artp/output.xml" ] && echo ok)" "ok"
chk "p0 证据产物仍 ok" "$(q "select availability from run_artifact where path='$WORK/art0/output.xml'")" "ok"
chk "p0 文件仍在" "$([ -f "$WORK/art0/output.xml" ] && echo ok)" "ok"
chk "有导出记录的产物仍 ok" "$(q "select availability from run_artifact where path='$WORK/arte/output.xml'")" "ok"
chk "有导出记录的文件仍在" "$([ -f "$WORK/arte/output.xml" ] && echo ok)" "ok"

echo
echo "== 8. 审计与幂等 =="
q "select id, action, resource_type, resource_id, detail from audit_log
     where action='artifact_purge' and resource_id like 'RPT06-%' order by id" | tee "$LOGS/14-audit.tsv"
chk "审计三条（每个被清理的运行一条）" \
  "$(q "select count(*) from audit_log where action='artifact_purge' and resource_id like 'RPT06-%'")" "3"
chk "审计 detail 带字节数（BIG-001 = 150+40 MB）" \
  "$(q "select count(*) from audit_log where action='artifact_purge' and resource_id='$BIG1'
        and detail like '%\"bytes\":199229440%'")" "1"

code=$(call 15-retention-again POST "$BASE/api/reports/retention/run?projectId=$PID_&dryRun=false")
cp "$WORK/15-retention-again.body" "$LOGS/15-retention-again.json"
chk "重复执行 HTTP" "$code" "200"
chk "重复执行无候选" "$(json "$WORK/15-retention-again.body" "d['data']['runCount']")" "0"
chk "重复执行不重复计数" "$(json "$WORK/15-retention-again.body" "d['data']['deletedFiles']")" "0"
chk "审计仍三条（不重复写）" \
  "$(q "select count(*) from audit_log where action='artifact_purge' and resource_id like 'RPT06-%'")" "3"

echo
echo "== 9. 清理 =="
q "update api_token set revoked_at = now(3) where token_hash='$HASH'"
q "delete from run_metadata where run_id in (select run_id from \`run\` where project_id=$PID_)"
q "delete from keyword_result where run_id in (select id from \`run\` where project_id=$PID_)"
q "delete from test_result where run_id in (select id from \`run\` where project_id=$PID_)"
q "delete from run_artifact where run_id in (select id from \`run\` where project_id=$PID_)"
q "delete from export_record where project_id=$PID_"
q "delete from audit_log where action='artifact_purge' and resource_id like 'RPT06-%'"
q "delete from \`run\` where project_id=$PID_"
q "delete from project where id=$PID_"
chk "专用项目已删除" "$(q "select count(*) from project where code='$CODE'")" "0"
rm -rf "$WORK"

echo
if [ "$FAILS" -eq 0 ]; then echo "== 全部检查通过 =="; else echo "== 失败 $FAILS 项 =="; fi
exit "$FAILS"
