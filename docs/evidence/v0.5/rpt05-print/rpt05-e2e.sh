#!/usr/bin/env bash
# RPT-05 打印链路端到端验证
# 链路：后端导出 → 发放一次性 token → 打印页 URL → 无头浏览器渲染 → PDF → 下载接口
set -uo pipefail

BASE="http://127.0.0.1:8081"
FE="http://127.0.0.1:5273"
PID_=1
RID="RUN-20261104155814-952426"
WORK="/tmp/rpt05-e2e"

rm -rf "$WORK"; mkdir -p "$WORK"

# ---------- 准备：注入临时会话令牌（等价于一次登录，只写哈希、不留口令） ----------
set -a
. /home/evergreen/.config/robot-autotest/env
set +a
CLIENT=mysql
command -v mariadb >/dev/null 2>&1 && CLIENT=mariadb

read -r TOKEN HASH <<< "$(python3 - <<'EOF'
import hashlib, secrets
t = secrets.token_urlsafe(32)
print(t, hashlib.sha256(t.encode()).hexdigest())
EOF
)"
"$CLIENT" --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --password="$DB_PASSWORD" \
  --batch --skip-column-names "$DB_NAME" \
  -e "insert into api_token (user_id, name, token_hash, token_kind, expires_at)
      values (1, 'rpt05-e2e', '$HASH', 'session', now(3) + interval 2 hour)" 2>/dev/null
echo "[setup] 临时会话令牌已注入，token 前缀 ${TOKEN:0:6}…（仅本次验证使用，结束后撤销）"

call() { # name method url [extras...]
  local name=$1 method=$2 url=$3; shift 3
  local out
  out=$(curl -s -o "$WORK/$name.body" -D "$WORK/$name.hdr" \
        -w '%{http_code} %{time_total}s %{size_download}B' \
        -X "$method" -H "Authorization: Bearer $TOKEN" "$@" "$url")
  echo "[$name] $out"
}

# ---------- 1. 鉴权自检 ----------
call 01-projects GET "$BASE/api/projects"

# ---------- 2. 打印页一次性 token（内部模式） ----------
call 02-print-token POST "$BASE/api/reports/$RID/print-token?projectId=$PID_&mode=internal"
PRINT_URL=$(python3 -c "import json;print(json.load(open('$WORK/02-print-token.body'))['data']['printUrl'])")
PTOKEN=$(python3 -c "import json;print(json.load(open('$WORK/02-print-token.body'))['data']['token'])")
echo "[02] printUrl=$PRINT_URL"

# ---------- 3. 通过前端端口（Vite 代理）取打印数据，验证前端→后端这一段 ----------
code=$(curl -s -o "$WORK/03-print-data.body" -D "$WORK/03-print-data.hdr" \
       -w '%{http_code} %{time_total}s %{size_download}B' \
       "$FE/api/print/$RID?token=$PTOKEN")
echo "[03-print-data-via-vite] $code"
python3 - <<EOF
import json
d = json.load(open('$WORK/03-print-data.body'))['data']
print('[03] mode=%s internalMark=%s exportable=%s missing=%d suites=%d' % (
    d.get('mode'), d.get('internalMark'), d.get('exportable'),
    len(d.get('metadataMissing') or []), len(d.get('cases') or d.get('tree') or [])))
EOF

# ---------- 4. token 复用应被拒（一次性） ----------
code=$(curl -s -o "$WORK/04-print-data-replay.body" -D "$WORK/04-print-data-replay.hdr" \
       -w '%{http_code} %{time_total}s %{size_download}B' \
       "$FE/api/print/$RID?token=$PTOKEN")
echo "[04-print-token-replay] $code"
head -c 300 "$WORK/04-print-data-replay.body"; echo

# ---------- 5. 内部模式 PDF 导出（走无头浏览器渲染打印页） ----------
call 05-export-internal POST "$BASE/api/reports/$RID/export?projectId=$PID_&mode=internal&format=pdf"
head -c 400 "$WORK/05-export-internal.body"; echo

# ---------- 6. 对外模式应被 metadata 门禁拒绝 ----------
call 06-export-external POST "$BASE/api/reports/$RID/export?projectId=$PID_&mode=external&format=pdf"
head -c 400 "$WORK/06-export-external.body"; echo

# ---------- 7. 下载与校核 ----------
EXPORT_ID=$(python3 -c "import json;print(json.load(open('$WORK/05-export-internal.body'))['data']['exportId'])" 2>/dev/null || echo "")
echo "[05] exportId=$EXPORT_ID"
if [ -n "$EXPORT_ID" ]; then
  code=$(curl -s -o "$WORK/07-download.pdf" -D "$WORK/07-download.hdr" \
         -w '%{http_code} %{time_total}s %{size_download}B' \
         -H "Authorization: Bearer $TOKEN" \
         "$BASE/api/exports/$EXPORT_ID/download?projectId=$PID_")
  echo "[07-download] $code"
  echo "[07] header=$(head -c 8 "$WORK/07-download.pdf") sha256=$(sha256sum "$WORK/07-download.pdf" | cut -d' ' -f1)"
  echo "[07] pages(/Type /Page)=$(grep -a -c '/Type /Page' "$WORK/07-download.pdf" || true)"
  python3 - <<EOF
import json
d = json.load(open('$WORK/05-export-internal.body'))['data']
print('[05] export record: status=%s bytes=%s sha256=%s file=%s' % (
    d.get('status'), d.get('bytes'), d.get('sha256'), d.get('fileName')))
EOF
fi

# ---------- 8. 导出历史 ----------
call 08-export-history GET "$BASE/api/reports/$RID/exports?projectId=$PID_"
head -c 500 "$WORK/08-export-history.body"; echo

# ---------- 9. 落库核对 ----------
"$CLIENT" --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --password="$DB_PASSWORD" \
  --batch --skip-column-names "$DB_NAME" \
  -e "select id, run_key, export_mode, export_format, export_status, size_bytes, left(sha256,12), file_name
        from export_record where project_id=$PID_ order by id desc limit 5" > "$WORK/09-export-records.tsv" 2>/dev/null
echo "== export_record =="
cat "$WORK/09-export-records.tsv"

# ---------- 10. 清理：撤销临时会话令牌 ----------
"$CLIENT" --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --password="$DB_PASSWORD" \
  --batch --skip-column-names "$DB_NAME" \
  -e "update api_token set revoked_at = now(3) where token_hash = '$HASH'" 2>/dev/null
echo "[cleanup] 临时会话令牌已撤销"

echo "== 工作目录 $WORK =="
ls -l "$WORK"
