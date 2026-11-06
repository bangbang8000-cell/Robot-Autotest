#!/usr/bin/env bash
# PLN-02 残余验证：跳过事件列表的分页与时间范围
#
# 原残余：前端把「依赖跳过事件」写死在第一页 20 条，多了看不到、也没法按时间筛。
# 后端 /api/audit 的 page/size/from/to 一直是有的，本批只补前端入口，并用真实 HTTP
# 证明这三个参数的实际语义（total 口径、from 含当天、to 含当天、越界页返回空）。
set -uo pipefail

BASE="http://127.0.0.1:8081"
EVID="$(cd "$(dirname "$0")" && pwd)"
WORK="/tmp/pln02c-work"
LOGS="$EVID/logs"
CODE="PLN02C"
ACTION="PLAN_DOWNSTREAM_SKIPPED"
FAILS=0

rm -rf "$WORK"
mkdir -p "$WORK" "$LOGS"

pass() { printf '  PASS  %s\n' "$*"; }
fail() { printf '  FAIL  %s\n' "$*"; FAILS=$((FAILS + 1)); }
chk() { if [ "$2" = "$3" ]; then pass "$1 ($2)"; else fail "$1：期望 $3，实际 $2"; fi; }
json() { python3 -c "import json;d=json.load(open('$1'))['data'];print($2)"; }

set -a
. /home/evergreen/.config/robot-autotest/env
set +a
C=mysql; command -v mariadb >/dev/null 2>&1 && C=mariadb
q() {
  "$C" --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --password="$DB_PASSWORD" \
    --batch --skip-column-names "$DB_NAME" -e "$1" 2>/dev/null
}

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
   values (1, 'pln02c-e2e', '$HASH', 'session', now(3) + interval 2 hour)"
chk "临时令牌已建" "$(q "select count(*) from api_token where token_hash='$HASH'")" "1"

get() { # name query-string —— stdout 回 HTTP 码
  local name=$1 qs=$2
  local out
  out=$(curl -s -o "$WORK/$name.body" -w '%{http_code}' \
        -H "Authorization: Bearer $TOKEN" "$BASE/api/audit?$qs")
  echo "[$name] http=$out" >&2
  printf '%s\n' "$out"
}

# 清残留：审计行按项目 code 反查，项目与令牌随后删
OLD_PID=$(q "select id from project where code='$CODE'")
if [ -n "$OLD_PID" ]; then
  q "delete from audit_log where project_id=$OLD_PID"
  q "delete from project where id=$OLD_PID"
fi

q "insert into project (code, name, status, created_by) values ('$CODE', 'PLN-02 分页验证专用', 1, 1)"
PID_=$(q "select id from project where code='$CODE'")
chk "专用项目已建" "$([ -n "$PID_" ] && echo ok)" "ok"

echo
echo "== 1. 造 25 条跳过事件（10 条 10-01 / 10 条 11-01 / 5 条 11-05） =="
seed() { # 日期 条数
  local d=$1 n=$2 i
  for ((i = 1; i <= n; i++)); do
    q "insert into audit_log (project_id, actor_id, operator, action, resource_type,
         resource_id, detail, occurred_at, created_by)
       values ($PID_, 0, 'system', '$ACTION', 'execution_plan', '1',
         'mark=skipped_dependency, downstream=$i, reason=upstream_failed, upstream_run=PLN02-UP-$i',
         '$d 02:00:00.000', 1)"
  done
}
seed 2026-10-01 10
seed 2026-11-01 10
seed 2026-11-05 5
chk "审计行已落库" "$(q "select count(*) from audit_log where project_id=$PID_ and action='$ACTION'")" "25"

echo
echo "== 2. 分页：total 与 items 长度对得上 =="
chk "第 1 页 GET 200" "$(get page1 "projectId=$PID_&action=$ACTION&page=1&size=10")" "200"
chk "   total=25" "$(json "$WORK/page1.body" "d['total']")" "25"
chk "   本页 10 条" "$(json "$WORK/page1.body" "len(d['items'])")" "10"
chk "   回显 page/size" "$(json "$WORK/page1.body" "f\"{d['page']}/{d['size']}\"")" "1/10"

chk "第 3 页 GET 200" "$(get page3 "projectId=$PID_&action=$ACTION&page=3&size=10")" "200"
chk "   末页 5 条" "$(json "$WORK/page3.body" "len(d['items'])")" "5"

chk "越界第 4 页 GET 200" "$(get page4 "projectId=$PID_&action=$ACTION&page=4&size=10")" "200"
chk "   越界页返回 0 条但 total 不变" \
  "$(json "$WORK/page4.body" "f\"{len(d['items'])}/{d['total']}\"")" "0/25"

echo
echo "== 3. 时间范围：from / to 都含当天 =="
chk "from=2026-11-01&to=2026-11-04 GET 200" \
  "$(get range "projectId=$PID_&action=$ACTION&from=2026-11-01&to=2026-11-04&page=1&size=10")" "200"
chk "   只命中 10 条（11-01 当天全算）" "$(json "$WORK/range.body" "d['total']")" "10"

chk "from=2026-11-05 GET 200" \
  "$(get from5 "projectId=$PID_&action=$ACTION&from=2026-11-05&page=1&size=10")" "200"
chk "   只命中 5 条" "$(json "$WORK/from5.body" "d['total']")" "5"

chk "to=2026-10-31 GET 200" \
  "$(get to31 "projectId=$PID_&action=$ACTION&to=2026-10-31&page=1&size=10")" "200"
chk "   只命中 10 条" "$(json "$WORK/to31.body" "d['total']")" "10"

chk "范围 + 翻页组合（11 月内 page=2&size=10）" \
  "$(get range2 "projectId=$PID_&action=$ACTION&from=2026-11-01&to=2026-11-30&page=2&size=10")" "200"
chk "   第 2 页 5 条 / 总 15" \
  "$(json "$WORK/range2.body" "f\"{len(d['items'])}/{d['total']}\"")" "5/15"

echo
echo "== 4. 清理 =="
q "delete from audit_log where project_id=$PID_"
q "delete from project where id=$PID_"
q "delete from api_token where token_hash='$HASH'"
chk "审计行已清" "$(q "select count(*) from audit_log where project_id=$PID_")" "0"
chk "项目已清" "$(q "select count(*) from project where code='$CODE'")" "0"
chk "令牌已撤销" "$(q "select count(*) from api_token where token_hash='$HASH' and revoked_at is null")" "0"

echo
if [ "$FAILS" -eq 0 ]; then echo "== 全部检查通过 =="; else echo "== 失败 $FAILS 项 =="; fi
exit "$FAILS"
