#!/usr/bin/env bash
# G2-3 / G2-4 本机 HTTP 实测复跑脚本（2026-11-05）
# 前置：dev 后端 :8081 用「含 EXEC-15 的 jar」启动；robot_dev 已迁到 V7_5。
#       口令从 ~/.config/robot-autotest/qa-ui-pwd 读取，脚本内不含任何凭据。
# 用法：bash docs/evidence/v1.0/g2-3/http-verify.sh
set -u
cd ~/robot-autotest || exit 1
BASE=http://127.0.0.1:8081
PWD_QA=$(cat ~/.config/robot-autotest/qa-ui-pwd)
OUT=/tmp/http_verify.log
: > "$OUT"
say() { echo "$@" | tee -a "$OUT"; }

say "## 0. 环境"
say "时间: $(date -Iseconds)"
say "后端: $BASE （jar 构建于 $(stat -c %y backend/target/robot-autotest-backend-0.1.0-SNAPSHOT.jar)）"
say "数据库: $(grep ^DB_NAME ~/.config/robot-autotest/env | cut -d= -f2)（已迁到 V7_5）"

say ""
say "## 1. 登录 POST /api/auth/login"
LOGIN=$(curl -s --noproxy '*' -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"qa-ui\",\"password\":\"$PWD_QA\"}")
say "$(echo "$LOGIN" | python3 -c 'import sys,json;d=json.load(sys.stdin);t=d["data"].get("token");d["data"]["token"]=(t[:8]+"…（已截断）") if t else t;print(json.dumps(d,ensure_ascii=False))' 2>/dev/null || echo "$LOGIN")"
TOKEN=$(echo "$LOGIN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])' 2>/dev/null)
if [ -z "${TOKEN:-}" ]; then say "!! 登录失败，终止"; exit 1; fi
H="Authorization: Bearer $TOKEN"

say ""
say "## 2. 列表已返回 safetyLevel  GET /api/projects/1/cases?page=1&size=2"
LIST=$(curl -s --noproxy '*' -H "$H" "$BASE/api/projects/1/cases?page=1&size=2")
say "$(echo "$LIST" | python3 -c 'import sys,json;d=json.load(sys.stdin);it=d["data"]["items"][0];print(json.dumps({k:it[k] for k in it if k in ("id","caseCode","title","safetyLevel","reviewStatus")},ensure_ascii=False))' 2>/dev/null || echo "$LIST")"
CASE_ID=$(echo "$LIST" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["items"][0]["id"])' 2>/dev/null)
CASE_CODE=$(echo "$LIST" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["items"][0]["caseCode"])' 2>/dev/null)
say "取样用例: id=$CASE_ID code=$CASE_CODE"

say ""
say "## 3. 人工补判  POST /api/projects/1/cases/batch  {action:setSafetyLevel, safetyLevel:L2}"
R3=$(curl -s --noproxy '*' -w '\nHTTP=%{http_code}' -X POST "$BASE/api/projects/1/cases/batch" \
  -H "$H" -H 'Content-Type: application/json' \
  -d "{\"action\":\"setSafetyLevel\",\"ids\":[$CASE_ID],\"safetyLevel\":\"L2\"}")
say "$R3"

say ""
say "## 4. 执行所选（L2）  POST /api/execution/cases/run  —— 期望 403"
R4=$(curl -s --noproxy '*' -w '\nHTTP=%{http_code}' -X POST "$BASE/api/execution/cases/run?projectId=1" \
  -H "$H" -H 'Content-Type: application/json' \
  -d "{\"caseIds\":[$CASE_ID]}")
say "$R4"

say ""
say "## 5. 非法安全级 L9  —— 期望 400"
R5=$(curl -s --noproxy '*' -w '\nHTTP=%{http_code}' -X POST "$BASE/api/projects/1/cases/batch" \
  -H "$H" -H 'Content-Type: application/json' \
  -d "{\"action\":\"setSafetyLevel\",\"ids\":[$CASE_ID],\"safetyLevel\":\"L9\"}")
say "$R5"

say ""
say "## 6. 还原 LX 后再执行  —— 期望同样 403（LX 按 L2 对待）"
curl -s --noproxy '*' -o /dev/null -X POST "$BASE/api/projects/1/cases/batch" \
  -H "$H" -H 'Content-Type: application/json' \
  -d "{\"action\":\"setSafetyLevel\",\"ids\":[$CASE_ID],\"safetyLevel\":\"LX\"}"
R6=$(curl -s --noproxy '*' -w '\nHTTP=%{http_code}' -X POST "$BASE/api/execution/cases/run?projectId=1" \
  -H "$H" -H 'Content-Type: application/json' \
  -d "{\"caseIds\":[$CASE_ID]}")
say "$R6"

say ""
say "## 7. G2-4a 弱口令被拒（短）  POST /api/users/3/reset-password"
R7=$(curl -s --noproxy '*' -w '\nHTTP=%{http_code}' -X POST "$BASE/api/users/3/reset-password" \
  -H "$H" -H 'Content-Type: application/json' -d '{"password":"Ab1"}')
say "$R7"

say ""
say "## 8. G2-4a 弱口令被拒（无大写）"
R8=$(curl -s --noproxy '*' -w '\nHTTP=%{http_code}' -X POST "$BASE/api/users/3/reset-password" \
  -H "$H" -H 'Content-Type: application/json' -d '{"password":"abcdefgh123"}')
say "$R8"

say ""
say "## 9. G2-4b 会话续期  POST /api/auth/renew"
EXP_BEFORE=$(echo "$LOGIN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("expiresAt"))' 2>/dev/null)
R9=$(curl -s --noproxy '*' -w '\nHTTP=%{http_code}' -X POST "$BASE/api/auth/renew" -H "$H")
say "登录时 expiresAt=$EXP_BEFORE（TTL=8h，服务端时间口径）"
say "$R9"

say ""
say "## 10. 收尾：取样用例已还原"
curl -s --noproxy '*' -H "$H" "$BASE/api/projects/1/cases/$CASE_ID" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"]["testCase"];print(json.dumps({k:d[k] for k in ("id","caseCode","safetyLevel")},ensure_ascii=False))' 2>/dev/null | tee -a "$OUT"
say ""
say "（原始记录结束）"
