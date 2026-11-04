#!/usr/bin/env bash
set -u
OUT="$HOME/robot-autotest/docs/evidence/v0.2/prj04/prj04-api.log"
mkdir -p "$(dirname "$OUT")"
TOKEN="${QA_TOKEN:?先 export 登录令牌到 QA_TOKEN}"
BASE="http://127.0.0.1:8081"
set -a
. "$HOME/.config/robot-autotest/env"
set +a
M() { mysql --protocol=tcp -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" -N -e "$1" 2>/dev/null; }

{
echo "=== 环境：$(date '+%F %T %Z')　后端 8081（当前代码 jar）· 前端 vite 5273 · MySQL robot_dev ==="
echo
echo "=== 1. GET /api/projects/1/report-digest?days=30（HTTP 状态） ==="
curl -s --noproxy '*' -w '\nHTTP %{http_code}\n' \
  -H "Authorization: Bearer $TOKEN" "$BASE/api/projects/1/report-digest?days=30"
echo
echo "=== 2. SQL 对账：project=1 的已完成运行 ==="
M "SELECT run_id, run_status, total_tests, passed_tests, failed_tests, pass_count, fail_count, skip_count, finished_at
     FROM run WHERE project_id=1 AND run_status IN ('finished','failed') ORDER BY finished_at DESC;"
echo
echo "=== 3. SQL 对账：这些运行的 test_result 明细（run.id <- test_result.run_id） ==="
M "SELECT t.run_id, t.case_key, t.verdict FROM test_result t
     JOIN run r ON r.id = t.run_id
     WHERE r.project_id=1 AND r.run_status IN ('finished','failed');"
echo
echo "=== 4. SQL 对账：用例总数（status=1） / 覆盖数 ==="
M "SELECT COUNT(*) AS case_total FROM test_case WHERE project_id=1 AND status=1;"
M "SELECT COUNT(*) AS covered FROM test_case c
     WHERE c.project_id=1 AND c.status=1
       AND EXISTS (SELECT 1 FROM test_result t JOIN run r ON r.id=t.run_id
                   WHERE r.project_id=c.project_id AND r.run_status IN ('finished','failed')
                     AND t.case_key=c.case_code);"
echo
echo "=== 5. SQL 对账：FAIL 用例（窗口内，应为 0 行） ==="
M "SELECT t.case_key, COUNT(*) AS failures FROM test_result t JOIN run r ON r.id=t.run_id
     WHERE r.project_id=1 AND t.verdict='FAIL' GROUP BY t.case_key;"
echo "（空 = 失败 Top10 为空态，与接口一致）"
echo
echo "=== 6. days 夹取 ==="
curl -s --noproxy '*' -H "Authorization: Bearer $TOKEN" "$BASE/api/projects/1/report-digest?days=0"   | python3 -c 'import json,sys;print("days=0   -> windowDays",json.load(sys.stdin)["data"]["windowDays"])'
curl -s --noproxy '*' -H "Authorization: Bearer $TOKEN" "$BASE/api/projects/1/report-digest?days=999" | python3 -c 'import json,sys;print("days=999 -> windowDays",json.load(sys.stdin)["data"]["windowDays"])'
echo
echo "=== 7. 鉴权对照 ==="
curl -s --noproxy '*' -o /dev/null -w 'no-token:                  HTTP %{http_code}\n' "$BASE/api/projects/1/report-digest"
curl -s --noproxy '*' -o /dev/null -w 'admin 跨项目 project=999:  HTTP %{http_code}（全局 admin 放行，见 AccessControlService:86 注释）\n' \
  -H "Authorization: Bearer $TOKEN" "$BASE/api/projects/999/report-digest"
} 2>&1 | tee "$OUT"
