#!/usr/bin/env bash
# RPT-05 残余验证：对外（external）模式成功分支
#
# 背景：v0.5 的端到端样本（RUN-20261104155814-952426）没有 metadata，只能验证
# 「external 被门禁拒绝」这条分支。本脚本造一份 10 项 metadata 齐全的真实 RF 产物，
# 走「解析入库 → 打印页取数 → PDF / DOCX 对外导出 → 下载 → 落库/审计」全链路，
# 断言对外导出为 200 且两格式产物里逐字出现 10 项 metadata，且都不含内部版标识。
set -uo pipefail

BASE="http://127.0.0.1:8081"
FE="http://127.0.0.1:5273"
PID_=1
RID="RUN-RPT05EXT-$(date +%Y%m%d-%H%M%S)"
EVID="$(cd "$(dirname "$0")" && pwd)"
ART="$EVID/artifacts"
WORK="/tmp/rpt05b-work"
FAILS=0

rm -rf "$WORK"; mkdir -p "$WORK" "$ART" "$EVID/logs"
mkdir -p "$HOME/robot-autotest/var/artifacts/$RID/shard-0"
RUN_DIR="$HOME/robot-autotest/var/artifacts/$RID"

pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*"; FAILS=$((FAILS + 1)); }
chk() { if [ "$2" = "$3" ]; then pass "$1 ($2)"; else fail "$1：期望 $3，实际 $2"; fi; }

set -a
. /home/evergreen/.config/robot-autotest/env
set +a
C=mysql; command -v mariadb >/dev/null 2>&1 && C=mariadb
q() {
  "$C" --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --password="$DB_PASSWORD" \
    --batch --skip-column-names "$DB_NAME" -e "$1" 2>/dev/null
}

echo "RUN_ID=$RID"

# ---------- 1. 造真实 RF 产物（10 项 metadata，键名大写、值 <= 40 字符） ----------
mkdir -p "$EVID/suite"
STARTED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
cat > "$EVID/suite/print_metadata.robot" <<EOF
*** Settings ***
Documentation      RPT-05 对外导出正样本：10 项 metadata 齐全
Metadata           CASE_ID       RPT05-EXT-001
Metadata           RUN_ID        $RID
Metadata           QA_PROJECT    robot-autotest
Metadata           DUT           WSL-DEV-NODE-01
Metadata           LINK          wsl2-lo -> backend:8081
Metadata           SW_BUILD      Ubuntu 24.04 / RF 7.5.1
Metadata           BASELINE      2 passed 1 failed
Metadata           OPERATOR      evergreen
Metadata           STARTED_AT    $STARTED
Metadata           CASE_KIND     report-export

*** Test Cases ***
元数据齐全的外部导出样本
    [Tags]    rpt05
    Log    这条用例用于 RPT-05 对外成功分支
    Should Be Equal    1    1
    Log    第二步：关键字步骤也要进对外报告

第二条通过用例
    [Tags]    rpt05
    Log    用于验证多用例的结论统计
    Should Contain    robot-autotest    robot

故意失败用例
    [Tags]    rpt05    known-fail
    Log    故意失败，验证失败结论在对外报告里仍可读
    Should Be Equal    1    2    msg=故意失败：验证失败用例渲染
EOF

"$HOME/rf-stack/.venv/bin/robot" --outputdir "$RUN_DIR/shard-0" \
  --logtitle "RPT-05 external sample" --reporttitle "RPT-05 external sample" \
  "$EVID/suite/print_metadata.robot" > "$EVID/logs/01-robot-run.log" 2>&1
RC=$?
chk "robot 执行（允许 1 = 有用例失败）" "$RC" "1"
cp "$RUN_DIR/shard-0/output.xml" "$ART/output.xml"
grep -c '<meta name=' "$ART/output.xml" | sed 's/^/  meta 标签数: /'

# ---------- 2. 登记 run（真实执行产物 + 目录布局与执行链路一致） ----------
q "insert into \`run\` (run_id, project_id, run_status, started_at, finished_at, artifacts_dir,
      total_tests, passed_tests, failed_tests, created_by, trigger_type)
    values ('$RID', $PID_, 'finished', now(3) - interval 3 second, now(3),
            '$RUN_DIR', 3, 2, 1, 1, 'manual')"
chk "run 行已登记" "$(q "select count(*) from \`run\` where run_id='$RID'")" "1"

# ---------- 3. 临时会话令牌（等价一次登录，只写哈希） ----------
read -r TOKEN HASH <<< "$(python3 - <<'EOF'
import hashlib, secrets
t = secrets.token_urlsafe(32)
print(t, hashlib.sha256(t.encode()).hexdigest())
EOF
)"
q "insert into api_token (user_id, name, token_hash, token_kind, expires_at)
   values (1, 'rpt05b-e2e', '$HASH', 'session', now(3) + interval 2 hour)"
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

# ---------- 4. 解析入库（走 artifacts_dir 回退，不传显式路径） ----------
code=$(call 02-parse POST "$BASE/api/reports/$RID/parse?projectId=$PID_")
cp "$WORK/02-parse.body" "$EVID/logs/02-parse.json"
chk "解析入库 HTTP" "$code" "200"
python3 - "$WORK/02-parse.body" <<'EOF' | tee "$EVID/logs/03-parse-assert.txt"
import json, sys
d = json.load(open(sys.argv[1]))['data']
meta = d.get('metadata') or {}
print("exportable=%s missing=%s keys=%d counts=%s" % (
    d.get('exportable'), d.get('metadataMissing'), len(meta), d.get('counts')))
for k, v in meta.items():
    print("  %-12s = %s" % (k, v))
EOF
chk "exportable" "$(python3 -c "import json;print(str(json.load(open('$WORK/02-parse.body'))['data']['exportable']).lower())")" "true"
chk "metadata 项数" "$(python3 -c "import json;print(len(json.load(open('$WORK/02-parse.body'))['data']['metadata']))")" "10"
chk "缺失项个数" "$(python3 -c "import json;d=json.load(open('$WORK/02-parse.body'))['data'];print(len(d['metadataMissing'] or []))")" "0"

# ---------- 5. 打印页取数（external 模式，经 Vite 代理） ----------
PT=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" \
      "$BASE/api/reports/$RID/print-token?projectId=$PID_&mode=external" \
      | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['token'])")
code=$(curl -s -o "$WORK/05-print-data.body" -D "$EVID/logs/05-print-data.hdr" \
      -w '%{http_code}' "$FE/api/print/$RID?token=$PT")
chk "打印页取数（Vite 5173→5273 代理）HTTP" "$code" "200"
cp "$WORK/05-print-data.body" "$EVID/logs/05-print-data.json"
python3 - "$WORK/05-print-data.body" <<'EOF' | tee "$EVID/logs/05-print-data-summary.txt"
import json, sys
d = json.load(open(sys.argv[1]))['data']
meta = d.get('metadata') or {}
print("mode=%s internal=%s exportable=%s missing=%d metadata=%d" % (
    d.get('mode'), d.get('internal'), d.get('exportable'),
    len(d.get('metadataMissing') or []), len(meta)))
print("metadata keys:", ",".join(sorted(meta)))
EOF
chk "打印页 internal 标记" "$(python3 -c "import json;print(str(json.load(open('$WORK/05-print-data.body'))['data']['internal']).lower())")" "false"

# ---------- 6. 对外 PDF 导出 + 下载 ----------
code=$(call 06-export-pdf POST "$BASE/api/reports/$RID/export?projectId=$PID_&mode=external&format=pdf")
chk "对外 PDF 导出 HTTP" "$code" "200"
cp "$WORK/06-export-pdf.body" "$EVID/logs/06-export-pdf.json"
PDF_ID=$(python3 -c "import json;print(json.load(open('$WORK/06-export-pdf.body'))['data']['exportId'])" 2>/dev/null || echo "")
code=$(curl -s -o "$ART/report_external.pdf" -D "$EVID/logs/07-download-pdf.hdr" \
      -w '%{http_code} %{size_download}B' -H "Authorization: Bearer $TOKEN" \
      "$BASE/api/exports/$PDF_ID/download?projectId=$PID_")
echo "[07-download-pdf] $code"
chk "PDF 下载 HTTP" "$(echo "$code" | cut -d' ' -f1)" "200"
chk "PDF 魔数" "$(head -c 5 "$ART/report_external.pdf")" "%PDF-"
LISTED=$(python3 -c "import json;print(json.load(open('$WORK/06-export-pdf.body'))['data']['sha256'])")
ACTUAL=$(sha256sum "$ART/report_external.pdf" | cut -d' ' -f1)
chk "PDF sha256（响应=文件）" "$ACTUAL" "$LISTED"
DBH=$(q "select sha256 from export_record where id=$PDF_ID")
chk "PDF sha256（=export_record）" "$DBH" "$LISTED"

# ---------- 7. PDF 文本抽取与逐字比对（pypdf，临时 venv） ----------
if [ ! -x /tmp/pdfenv/bin/python ] || ! /tmp/pdfenv/bin/python -c "import pypdf" 2>/dev/null; then
  python3 -m venv /tmp/pdfenv >/dev/null 2>&1
  /tmp/pdfenv/bin/pip install -q pypdf >/dev/null 2>&1
fi
cat > "$EVID/pdf-text.py" <<'EOF'
"""从导出的 PDF 抽文本：pypdf 逐页抽取，用于 metadata 文案逐字比对。"""
import sys
from pypdf import PdfReader

reader = PdfReader(sys.argv[1])
texts = [(p.extract_text() or "") for p in reader.pages]
print("pages=%d" % len(texts))
for i, t in enumerate(texts, 1):
    print("---- page %d ----" % i)
    print(t)
EOF
/tmp/pdfenv/bin/python "$EVID/pdf-text.py" "$ART/report_external.pdf" > "$EVID/logs/08-pdf-text.txt" 2>&1
head -1 "$EVID/logs/08-pdf-text.txt"
PAGES=$(head -1 "$EVID/logs/08-pdf-text.txt" | sed -n 's/pages=//p')
chk "PDF 有页" "$([ "${PAGES:-0}" -ge 1 ] && echo yes || echo no)" "yes"

assert_pdf_text() {
  local needle="$1" label="$2"
  if grep -qF -- "$needle" "$EVID/logs/08-pdf-text.txt"; then pass "PDF 含 $label（$needle）"; else fail "PDF 缺 $label（$needle）"; fi
}
assert_pdf_text "RPT05-EXT-001" "CASE_ID"
assert_pdf_text "$RID" "RUN_ID"
assert_pdf_text "robot-autotest" "QA_PROJECT"
assert_pdf_text "WSL-DEV-NODE-01" "DUT"
assert_pdf_text "wsl2-lo -> backend:8081" "LINK"
assert_pdf_text "Ubuntu 24.04 / RF 7.5.1" "SW_BUILD"
assert_pdf_text "2 passed 1 failed" "BASELINE"
assert_pdf_text "evergreen" "OPERATOR"
assert_pdf_text "report-export" "CASE_KIND"
assert_pdf_text "$STARTED" "STARTED_AT"
if grep -qF "内部版" "$EVID/logs/08-pdf-text.txt"; then fail "对外 PDF 出现内部版标识"; else pass "对外 PDF 无内部版标识"; fi
if grep -qF "可对外" "$EVID/logs/08-pdf-text.txt"; then pass "对外 PDF 带「可对外」标识"; else fail "对外 PDF 缺「可对外」标识"; fi

# ---------- 8. 对外 DOCX 导出 + 下载 + document.xml 逐字比对 ----------
code=$(call 09-export-docx POST "$BASE/api/reports/$RID/export?projectId=$PID_&mode=external&format=docx")
chk "对外 DOCX 导出 HTTP" "$code" "200"
cp "$WORK/09-export-docx.body" "$EVID/logs/09-export-docx.json"
DOCX_ID=$(python3 -c "import json;print(json.load(open('$WORK/09-export-docx.body'))['data']['exportId'])" 2>/dev/null || echo "")
code=$(curl -s -o "$ART/report_external.docx" -D "$EVID/logs/10-download-docx.hdr" \
      -w '%{http_code} %{size_download}B' -H "Authorization: Bearer $TOKEN" \
      "$BASE/api/exports/$DOCX_ID/download?projectId=$PID_")
echo "[10-download-docx] $code"
chk "DOCX 下载 HTTP" "$(echo "$code" | cut -d' ' -f1)" "200"
chk "DOCX 魔数（zip）" "$(head -c 2 "$ART/report_external.docx")" "PK"
unzip -p "$ART/report_external.docx" word/document.xml > "$WORK/docx-document.xml"
python3 - "$WORK/docx-document.xml" <<'EOF' | tee "$EVID/logs/11-docx-text.txt"
import html, re, sys
xml = open(sys.argv[1], encoding='utf-8').read()
# 按文档顺序取每个 <w:t> 的文本（表格单元格里的段落同样覆盖），逐行输出
for t in re.findall(r'<w:t(?:\s[^>]*)?>(.*?)</w:t>', xml, re.S):
    t = html.unescape(t).strip()
    if t:
        print(t)
EOF
assert_docx_text() {
  local needle="$1" label="$2"
  if grep -qF -- "$needle" "$EVID/logs/11-docx-text.txt"; then pass "DOCX 含 $label（$needle）"; else fail "DOCX 缺 $label（$needle）"; fi
}
assert_docx_text "RPT05-EXT-001" "CASE_ID"
assert_docx_text "$RID" "RUN_ID"
assert_docx_text "robot-autotest" "QA_PROJECT"
assert_docx_text "WSL-DEV-NODE-01" "DUT"
assert_docx_text "wsl2-lo -> backend:8081" "LINK"
assert_docx_text "Ubuntu 24.04 / RF 7.5.1" "SW_BUILD"
assert_docx_text "2 passed 1 failed" "BASELINE"
assert_docx_text "evergreen" "OPERATOR"
assert_docx_text "report-export" "CASE_KIND"
assert_docx_text "$STARTED" "STARTED_AT"
assert_docx_text "故意失败用例" "失败用例名"
if grep -qF "内部版" "$EVID/logs/11-docx-text.txt"; then fail "对外 DOCX 出现内部版标识"; else pass "对外 DOCX 无内部版标识"; fi
if grep -qF "可对外" "$EVID/logs/11-docx-text.txt"; then pass "对外 DOCX 带「可对外」标识"; else fail "对外 DOCX 缺「可对外」标识"; fi

# ---------- 9. 反向对照：旧的无 metadata 样本仍必须被拒 ----------
code=$(call 12-control-old-run POST "$BASE/api/reports/RUN-20261104155814-952426/export?projectId=$PID_&mode=external&format=pdf")
cp "$WORK/12-control-old-run.body" "$EVID/logs/12-control-old-run.json"
chk "旧样本对外导出被门禁拒绝" "$code" "400"

# ---------- 10. 落库与审计 ----------
q "select id, run_key, export_mode, export_format, export_status, size_bytes, left(sha256,12)
     from export_record where run_key='$RID' order by id" | tee "$EVID/logs/13-export-records.tsv"
chk "export_record 条数" "$(q "select count(*) from export_record where run_key='$RID'")" "2"
chk "export_status 全为 created" "$(q "select count(*) from export_record where run_key='$RID' and export_status='created'")" "2"
q "select id, action, resource_type, resource_id, left(detail,80) from audit_log
     where action='report_export' and resource_id='$RID' order by id" | tee "$EVID/logs/14-audit.tsv"
chk "audit_log report_export 条数" "$(q "select count(*) from audit_log where action='report_export' and resource_id='$RID'")" "2"
q "select exportable, ifnull(metadata_missing,'(null)'), pass_count, fail_count from \`run\` where run_id='$RID'" \
  | tee "$EVID/logs/15-run-flags.tsv"

# ---------- 11. 清理临时令牌 ----------
q "update api_token set revoked_at = now(3) where token_hash = '$HASH'"
echo "[cleanup] 临时令牌已撤销；run 与导出产物保留（$RUN_DIR / $ART）"

echo
if [ "$FAILS" -eq 0 ]; then echo "== 全部检查通过 =="; else echo "== 失败 $FAILS 项 =="; fi
exit "$FAILS"
