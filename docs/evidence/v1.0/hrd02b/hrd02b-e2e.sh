#!/usr/bin/env bash
# HRD-02 残余验证：补录的 metadata 是否真的进了报告视图与对外导出，以及补录后门禁是否重算。
#
# 背景：HRD-02 首批只做到「按项补录 + 审计」，补录值仅存在于 run_metadata 表，
# 报告详情 / 打印页 / PDF / DOCX 仍只看解析出的 metadata_json，于是出现
# 「补全了十项，导出依旧 400」的死角。本脚本用一份只带 4 项 metadata 的真实 RF 产物，
# 走「解析入库（不可导出）→ HTTP 补录六项 → 详情翻绿 → 对外 PDF/DOCX 逐字命中
# → 重复补录不写审计 → 删除必填项回落不可导出」全链路。
set -uo pipefail

BASE="http://127.0.0.1:8081"
PID_=1
RID="RUN-HRD02B-$(date +%Y%m%d-%H%M%S)"
EVID="$(cd "$(dirname "$0")" && pwd)"
ART="$EVID/artifacts"
WORK="/tmp/hrd02b-work"
FAILS=0

rm -rf "$WORK"; mkdir -p "$WORK" "$ART" "$EVID/logs" "$EVID/suite"
RUN_DIR="$HOME/robot-autotest/var/artifacts/$RID"
mkdir -p "$RUN_DIR/shard-0"

pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*"; FAILS=$((FAILS + 1)); }
chk() { if [ "$2" = "$3" ]; then pass "$1 ($2)"; else fail "$1：期望 $3，实际 $2"; fi; }
json() { python3 -c "import json,sys;d=json.load(open('$1'));print($2)"; }

set -a
. /home/evergreen/.config/robot-autotest/env
set +a
C=mysql; command -v mariadb >/dev/null 2>&1 && C=mariadb
q() {
  "$C" --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --password="$DB_PASSWORD" \
    --batch --skip-column-names "$DB_NAME" -e "$1" 2>/dev/null
}

echo "RUN_ID=$RID"

# ---------- 1. 造真实 RF 产物：只带 4 项 metadata（缺 DUT/LINK/SW_BUILD/BASELINE/STARTED_AT/CASE_KIND） ----------
STARTED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
cat > "$EVID/suite/partial_metadata.robot" <<EOF
*** Settings ***
Documentation      HRD-02 补录样本：解析时只有 4 项 metadata，六项靠补录补齐
Metadata           CASE_ID       HRD02-PATCH-001
Metadata           RUN_ID        $RID
Metadata           QA_PROJECT    robot-autotest
Metadata           OPERATOR      evergreen

*** Test Cases ***
补录前不可对外导出的样本
    [Tags]    hrd02
    Log    该套件故意缺六项 metadata，验证补录能把门禁扳绿
    Should Be Equal    1    1

第二条通过用例
    [Tags]    hrd02
    Log    用于验证补录后用例结论仍可读
    Should Contain    robot-autotest    robot

故意失败用例
    [Tags]    hrd02    known-fail
    Log    故意失败，验证补录不影响用例结论
    Should Be Equal    1    2    msg=故意失败：补录场景样本
EOF

"$HOME/rf-stack/.venv/bin/robot" --outputdir "$RUN_DIR/shard-0" \
  --logtitle "HRD-02 patch sample" --reporttitle "HRD-02 patch sample" \
  "$EVID/suite/partial_metadata.robot" > "$EVID/logs/01-robot-run.log" 2>&1
chk "robot 执行（允许 1 = 有用例失败）" "$?" "1"
cp "$RUN_DIR/shard-0/output.xml" "$ART/output.xml"
chk "产物 meta 标签数" "$(grep -c '<meta name=' "$ART/output.xml")" "4"

# ---------- 2. 登记 run ----------
q "insert into \`run\` (run_id, project_id, run_status, started_at, finished_at, artifacts_dir,
      total_tests, passed_tests, failed_tests, created_by, trigger_type)
    values ('$RID', $PID_, 'finished', now(3) - interval 3 second, now(3),
            '$RUN_DIR', 3, 2, 1, 1, 'manual')"
chk "run 行已登记" "$(q "select count(*) from \`run\` where run_id='$RID'")" "1"

# ---------- 3. 临时会话令牌 ----------
read -r TOKEN HASH <<< "$(python3 - <<'EOF'
import hashlib, secrets
t = secrets.token_urlsafe(32)
print(t, hashlib.sha256(t.encode()).hexdigest())
EOF
)"
q "insert into api_token (user_id, name, token_hash, token_kind, expires_at)
   values (1, 'hrd02b-e2e', '$HASH', 'session', now(3) + interval 2 hour)"
echo "[setup] 临时令牌 ${TOKEN:0:6}…"

call() { # name method url [extras...] —— 进度写 stderr，stdout 只回 HTTP 码
  local name=$1 method=$2 url=$3; shift 3
  local out
  out=$(curl -s -o "$WORK/$name.body" -D "$EVID/logs/$name.hdr" \
        -w '%{http_code} %{time_total}s %{size_download}B' \
        -X "$method" -H "Authorization: Bearer $TOKEN" "$@" "$url")
  echo "[$name] $out" >&2
  printf '%s\n' "${out%% *}"
}

# ---------- 4. 解析入库：4 项 metadata → 不可导出，缺 6 项 ----------
code=$(call 02-parse POST "$BASE/api/reports/$RID/parse?projectId=$PID_")
cp "$WORK/02-parse.body" "$EVID/logs/02-parse.json"
chk "解析入库 HTTP" "$code" "200"
chk "解析后 exportable" "$(json "$WORK/02-parse.body" "str(d['data']['exportable']).lower()")" "false"
chk "解析后缺失项数" "$(json "$WORK/02-parse.body" "len(d['data']['metadataMissing'] or [])")" "6"
chk "解析后 metadata 项数" "$(json "$WORK/02-parse.body" "len(d['data']['metadata'] or {})")" "4"
chk "解析后缺失清单" "$(json "$WORK/02-parse.body" "'/'.join(d['data']['metadataMissing'])")" \
  "DUT/LINK/SW_BUILD/BASELINE/STARTED_AT/CASE_KIND"

# ---------- 5. 补录前：对外导出被门禁拒绝 ----------
code=$(call 03-export-before POST "$BASE/api/reports/$RID/export?projectId=$PID_&mode=external&format=pdf")
cp "$WORK/03-export-before.body" "$EVID/logs/03-export-before.json"
chk "补录前对外导出 HTTP" "$code" "400"

# ---------- 6. 补录六项（HTTP PATCH） ----------
cat > "$WORK/patch-six.json" <<EOF
{"metadata": {
  "DUT": "WSL-DEV-NODE-02",
  "LINK": "wsl2-lo -> backend:8081 (patched)",
  "SW_BUILD": "Ubuntu 24.04 / RF 7.5.1 (patched)",
  "BASELINE": "2 passed 1 failed (patched)",
  "STARTED_AT": "$STARTED",
  "CASE_KIND": "metadata-patch"
}}
EOF
code=$(call 04-patch-six PATCH "$BASE/api/execution/runs/$RID/metadata?projectId=$PID_" \
  -H "Content-Type: application/json" --data-binary "@$WORK/patch-six.json")
cp "$WORK/04-patch-six.body" "$EVID/logs/04-patch-six.json"
chk "补录六项 HTTP" "$code" "200"
chk "补录响应项数" "$(json "$WORK/04-patch-six.body" "len(d['data'] or {})")" "6"
chk "补录响应 DUT" "$(json "$WORK/04-patch-six.body" "d['data']['DUT']")" "WSL-DEV-NODE-02"

# ---------- 7. 补录后：详情视图叠加补录值，门禁翻绿 ----------
code=$(call 05-detail POST "$BASE/api/reports/$RID/parse?projectId=$PID_")  # 幂等重析一次，确认不会把补录冲掉
chk "重析 HTTP" "$code" "200"
chk "重析后 exportable 仍为 true（补录未被解析覆盖）" \
  "$(json "$WORK/05-detail.body" "str(d['data']['exportable']).lower()")" "true"

code=$(call 06-detail GET "$BASE/api/reports/$RID?projectId=$PID_")
cp "$WORK/06-detail.body" "$EVID/logs/06-detail.json"
chk "详情 HTTP" "$code" "200"
chk "详情 exportable" "$(json "$WORK/06-detail.body" "str(d['data']['exportable']).lower()")" "true"
chk "详情缺失项数" "$(json "$WORK/06-detail.body" "len(d['data']['metadataMissing'] or [])")" "0"
chk "详情 metadata 项数" "$(json "$WORK/06-detail.body" "len(d['data']['metadata'] or {})")" "10"
chk "详情 DUT（补录值）" "$(json "$WORK/06-detail.body" "d['data']['metadata']['DUT']")" "WSL-DEV-NODE-02"
chk "详情 CASE_ID（解析值）" "$(json "$WORK/06-detail.body" "d['data']['metadata']['CASE_ID']")" "HRD02-PATCH-001"
python3 - "$WORK/06-detail.body" <<'EOF' | tee "$EVID/logs/07-detail-metadata.txt"
import json, sys
d = json.load(open(sys.argv[1]))['data']
print("exportable=%s missing=%s" % (d['exportable'], d['metadataMissing']))
for k, v in d['metadata'].items():
    print("  %-12s = %s" % (k, v))
EOF

# ---------- 8. 落库口径：run 表门禁已回写，metadata_json 仍是解析原值 ----------
q "select exportable, ifnull(metadata_missing,'(null)') from \`run\` where run_id='$RID'" \
  | tee "$EVID/logs/08-run-flags.tsv"
chk "run.exportable 已回写" "$(q "select exportable from \`run\` where run_id='$RID'")" "1"
chk "run.metadata_missing 已清空" "$(q "select ifnull(metadata_missing,'(null)') from \`run\` where run_id='$RID'")" "(null)"
chk "metadata_json 未被补录改写（不含 DUT）" \
  "$(q "select count(*) from \`run\` where run_id='$RID' and metadata_json not like '%\"DUT\"%'")" "1"
chk "run_metadata 补录行数" "$(q "select count(*) from run_metadata where run_id='$RID'")" "6"

# ---------- 9. 重复补录同样内容 → 不落库、不写审计 ----------
AUD_BEFORE=$(q "select count(*) from audit_log where action='run:metadata-patch' and resource_id='$RID'")
code=$(call 09-patch-again PATCH "$BASE/api/execution/runs/$RID/metadata?projectId=$PID_" \
  -H "Content-Type: application/json" --data-binary "@$WORK/patch-six.json")
chk "重复补录 HTTP" "$code" "200"
AUD_AFTER=$(q "select count(*) from audit_log where action='run:metadata-patch' and resource_id='$RID'")
chk "未变化不写审计（前后一致）" "$AUD_AFTER" "$AUD_BEFORE"
chk "当前审计条数" "$AUD_BEFORE" "1"

# ---------- 10. 对外 PDF 导出：补录值必须逐字出现 ----------
code=$(call 10-export-pdf POST "$BASE/api/reports/$RID/export?projectId=$PID_&mode=external&format=pdf")
cp "$WORK/10-export-pdf.body" "$EVID/logs/10-export-pdf.json"
chk "补录后对外 PDF 导出 HTTP" "$code" "200"
PDF_ID=$(json "$WORK/10-export-pdf.body" "d['data']['exportId']")
code=$(curl -s -o "$ART/report_patched.pdf" -D "$EVID/logs/11-download-pdf.hdr" \
      -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
      "$BASE/api/exports/$PDF_ID/download?projectId=$PID_")
chk "PDF 下载 HTTP" "$code" "200"
chk "PDF 魔数" "$(head -c 5 "$ART/report_patched.pdf")" "%PDF-"
LISTED=$(json "$WORK/10-export-pdf.body" "d['data']['sha256']")
chk "PDF sha256（响应=文件）" "$(sha256sum "$ART/report_patched.pdf" | cut -d' ' -f1)" "$LISTED"

if [ ! -x /tmp/pdfenv/bin/python ] || ! /tmp/pdfenv/bin/python -c "import pypdf" 2>/dev/null; then
  python3 -m venv /tmp/pdfenv >/dev/null 2>&1
  /tmp/pdfenv/bin/pip install -q pypdf >/dev/null 2>&1
fi
cat > "$EVID/pdf-text.py" <<'EOF'
"""从导出的 PDF 抽文本：pypdf 逐页抽取，用于补录值逐字比对。"""
import sys
from pypdf import PdfReader

texts = [(p.extract_text() or "") for p in PdfReader(sys.argv[1]).pages]
print("pages=%d" % len(texts))
for i, t in enumerate(texts, 1):
    print("---- page %d ----" % i)
    print(t)
EOF
/tmp/pdfenv/bin/python "$EVID/pdf-text.py" "$ART/report_patched.pdf" > "$EVID/logs/12-pdf-text.txt" 2>&1
head -1 "$EVID/logs/12-pdf-text.txt"
assert_pdf() {
  if grep -qF -- "$2" "$EVID/logs/12-pdf-text.txt"; then pass "PDF 含补录 $1（$2）"; else fail "PDF 缺补录 $1（$2）"; fi
}
assert_pdf DUT "WSL-DEV-NODE-02"
assert_pdf LINK "wsl2-lo -> backend:8081 (patched)"
assert_pdf SW_BUILD "Ubuntu 24.04 / RF 7.5.1 (patched)"
assert_pdf BASELINE "2 passed 1 failed (patched)"
assert_pdf STARTED_AT "$STARTED"
assert_pdf CASE_KIND "metadata-patch"
if grep -qF "可对外" "$EVID/logs/12-pdf-text.txt"; then pass "PDF 带「可对外」标识"; else fail "PDF 缺「可对外」标识"; fi
if grep -qF "内部版" "$EVID/logs/12-pdf-text.txt"; then fail "对外 PDF 出现内部版标识"; else pass "对外 PDF 无内部版标识"; fi

# ---------- 11. 对外 DOCX 导出：同样逐字命中 ----------
code=$(call 13-export-docx POST "$BASE/api/reports/$RID/export?projectId=$PID_&mode=external&format=docx")
cp "$WORK/13-export-docx.body" "$EVID/logs/13-export-docx.json"
chk "补录后对外 DOCX 导出 HTTP" "$code" "200"
DOCX_ID=$(json "$WORK/13-export-docx.body" "d['data']['exportId']")
code=$(curl -s -o "$ART/report_patched.docx" -D "$EVID/logs/14-download-docx.hdr" \
      -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
      "$BASE/api/exports/$DOCX_ID/download?projectId=$PID_")
chk "DOCX 下载 HTTP" "$code" "200"
chk "DOCX 魔数（zip）" "$(head -c 2 "$ART/report_patched.docx")" "PK"
unzip -p "$ART/report_patched.docx" word/document.xml > "$WORK/docx-document.xml"
python3 - "$WORK/docx-document.xml" <<'EOF' | tee "$EVID/logs/15-docx-text.txt"
import html, re, sys
xml = open(sys.argv[1], encoding='utf-8').read()
for t in re.findall(r'<w:t(?:\s[^>]*)?>(.*?)</w:t>', xml, re.S):
    t = html.unescape(t).strip()
    if t:
        print(t)
EOF
assert_docx() {
  if grep -qF -- "$2" "$EVID/logs/15-docx-text.txt"; then pass "DOCX 含补录 $1（$2）"; else fail "DOCX 缺补录 $1（$2）"; fi
}
assert_docx DUT "WSL-DEV-NODE-02"
assert_docx LINK "wsl2-lo -> backend:8081 (patched)"
assert_docx SW_BUILD "Ubuntu 24.04 / RF 7.5.1 (patched)"
assert_docx BASELINE "2 passed 1 failed (patched)"
assert_docx STARTED_AT "$STARTED"
assert_docx CASE_KIND "metadata-patch"

# ---------- 12. 删除必填项 → 门禁回落不可导出，导出重新 400 ----------
code=$(call 16-delete-dut PATCH "$BASE/api/execution/runs/$RID/metadata?projectId=$PID_" \
  -H "Content-Type: application/json" --data-binary '{"metadata":{"DUT":""}}')
cp "$WORK/16-delete-dut.body" "$EVID/logs/16-delete-dut.json"
chk "删除 DUT HTTP" "$code" "200"
chk "删除后 run_metadata 行数" "$(q "select count(*) from run_metadata where run_id='$RID'")" "5"
code=$(call 17-detail-after-delete GET "$BASE/api/reports/$RID?projectId=$PID_")
chk "删除后详情 exportable" "$(json "$WORK/17-detail-after-delete.body" "str(d['data']['exportable']).lower()")" "false"
chk "删除后缺失项" "$(json "$WORK/17-detail-after-delete.body" "','.join(d['data']['metadataMissing'])")" "DUT"
chk "删除后 run.exportable 回写" "$(q "select exportable from \`run\` where run_id='$RID'")" "0"
chk "删除后 metadata_missing 回写" "$(q "select metadata_missing from \`run\` where run_id='$RID'")" "DUT"
code=$(call 18-export-after-delete POST "$BASE/api/reports/$RID/export?projectId=$PID_&mode=external&format=pdf")
chk "删除后对外导出 HTTP" "$code" "400"

# ---------- 13. 审计：两次变更各一条，删除记「（删除）」 ----------
q "select id, action, resource_type, resource_id, left(summary,200) from audit_log
     where action='run:metadata-patch' and resource_id='$RID' order by id" | tee "$EVID/logs/19-audit.tsv"
chk "audit_log 条数" "$(q "select count(*) from audit_log where action='run:metadata-patch' and resource_id='$RID'")" "2"
chk "首条记 before=…after=…" \
  "$(q "select count(*) from audit_log where action='run:metadata-patch' and resource_id='$RID' and summary like 'after={%DUT=WSL-DEV-NODE-02%'")" "1"
chk "删除记录含（删除）标记" \
  "$(q "select count(*) from audit_log where action='run:metadata-patch' and resource_id='$RID' and summary like '%（删除）%'")" "1"
q "select id, run_key, export_mode, export_format, export_status, size_bytes, left(sha256,12)
     from export_record where run_key='$RID' order by id" | tee "$EVID/logs/20-export-records.tsv"
chk "export_record 条数（补录后两次成功）" "$(q "select count(*) from export_record where run_key='$RID'")" "2"

# ---------- 14. 清理：临时令牌撤销，run 与衍生行删除（产物留在 artifacts） ----------
q "update api_token set revoked_at = now(3) where token_hash = '$HASH'"
RUN_DB_ID=$(q "select id from \`run\` where run_id='$RID'")
q "delete from run_metadata where run_id='$RID'"
q "delete from keyword_result where run_id=$RUN_DB_ID"
q "delete from test_result where run_id=$RUN_DB_ID"
q "delete from export_record where run_key='$RID'"
q "delete from \`run\` where id=$RUN_DB_ID"
echo "[cleanup] 令牌已撤销；run#${RUN_DB_ID} 及其衍生行已删除；产物保留于 $RUN_DIR / $ART"

echo
if [ "$FAILS" -eq 0 ]; then echo "== 全部检查通过 =="; else echo "== 失败 $FAILS 项 =="; fi
exit "$FAILS"
