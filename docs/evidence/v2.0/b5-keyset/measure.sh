#!/usr/bin/env bash
# B5 SQL 计时：audit_log 的 LIMIT/OFFSET 与 keyset（含 EXPLAIN），以及 case_key 查询。
# 用法：bash measure.sh <label>   # label 只用于输出标题，如 before / after
set -u
cd ~/robot-autotest
set -a; . ~/.config/robot-autotest/env; set +a
export MYSQL_PWD="${DB_PASSWORD:-}"
M="mysql -h${DB_HOST:-127.0.0.1} -P${DB_PORT:-3306} -u${DB_USERNAME:-root} ${DB_NAME:-robot_dev}"
LABEL=${1:-current}

CUR=$($M -N -e "SELECT occurred_at, id FROM audit_log WHERE project_id=-1 ORDER BY occurred_at DESC, id DESC LIMIT 1 OFFSET 500000")
CAT=${CUR%%$'\t'*}; CID=${CUR##*$'\t'}
GCUR=$($M -N -e "SELECT occurred_at, id FROM audit_log ORDER BY occurred_at DESC, id DESC LIMIT 1 OFFSET 500000")
GAT=${GCUR%%$'\t'*}; GID=${GCUR##*$'\t'}

run() { local label="$1" sql="$2" best=999999999 t0 t1 ms
  for _ in 1 2 3; do t0=$(date +%s%N); $M -N -e "$sql" >/dev/null; t1=$(date +%s%N)
    ms=$(( (t1 - t0) / 1000000 )); [ "$ms" -lt "$best" ] && best=$ms; done
  printf '%-46s %8s ms\n' "$label" "$best"; }

echo "# B5 [$LABEL] @ $(date -Is) rows=$($M -N -e 'select count(*) from audit_log')"
echo "# cursor(project=-1): $CAT / $CID ｜ cursor(global): $GAT / $GID"
run "P-OFFSET-0"    "SELECT * FROM audit_log WHERE project_id=-1 ORDER BY occurred_at DESC, id DESC LIMIT 20 OFFSET 0"
run "P-OFFSET-200k" "SELECT * FROM audit_log WHERE project_id=-1 ORDER BY occurred_at DESC, id DESC LIMIT 20 OFFSET 200000"
run "P-OFFSET-500k" "SELECT * FROM audit_log WHERE project_id=-1 ORDER BY occurred_at DESC, id DESC LIMIT 20 OFFSET 500000"
run "P-OFFSET-900k" "SELECT * FROM audit_log WHERE project_id=-1 ORDER BY occurred_at DESC, id DESC LIMIT 20 OFFSET 900000"
run "P-KEYSET (OR form)" "SELECT * FROM audit_log WHERE project_id=-1 AND (occurred_at < '$CAT' OR (occurred_at = '$CAT' AND id < $CID)) ORDER BY occurred_at DESC, id DESC LIMIT 20"
run "P-KEYSET (row-ctor 反例)" "SELECT * FROM audit_log WHERE project_id=-1 AND (occurred_at, id) < ('$CAT', $CID) ORDER BY occurred_at DESC, id DESC LIMIT 20"
run "G-OFFSET-500k" "SELECT * FROM audit_log ORDER BY occurred_at DESC, id DESC LIMIT 20 OFFSET 500000"
run "G-KEYSET (OR form)" "SELECT * FROM audit_log WHERE (occurred_at < '$GAT' OR (occurred_at = '$GAT' AND id < $GID)) ORDER BY occurred_at DESC, id DESC LIMIT 20"
echo
echo "## EXPLAIN P-KEYSET"
$M -e "EXPLAIN SELECT * FROM audit_log WHERE project_id=-1 AND (occurred_at < '$CAT' OR (occurred_at = '$CAT' AND id < $CID)) ORDER BY occurred_at DESC, id DESC LIMIT 20"
echo "## EXPLAIN G-KEYSET"
$M -e "EXPLAIN SELECT * FROM audit_log WHERE (occurred_at < '$GAT' OR (occurred_at = '$GAT' AND id < $GID)) ORDER BY occurred_at DESC, id DESC LIMIT 20"

# case_key 索引评估（需先跑 seed.sh 造出克隆表 test_result_b5）
if [ "$($M -N -e "select count(*) from information_schema.tables where table_schema=database() and table_name='test_result_b5'")" = "1" ]; then
  KEY='suites/b5/case_0001.robot.B5 用例 1'
  echo
  echo "## case_key：无索引"
  $M -e "ALTER TABLE test_result_b5 DROP INDEX idx_test_result_case_key" 2>/dev/null || true
  run "TREND case_key join" "SELECT r.run_id, r.started_at, t.verdict FROM test_result_b5 t JOIN \`run\` r ON r.id = t.run_id WHERE r.project_id = 29 AND t.case_key = '$KEY' AND r.started_at IS NOT NULL ORDER BY r.started_at DESC, r.id DESC LIMIT 20"
  $M -e "EXPLAIN SELECT r.run_id, r.started_at, t.verdict FROM test_result_b5 t JOIN \`run\` r ON r.id = t.run_id WHERE r.project_id = 29 AND t.case_key = '$KEY' AND r.started_at IS NOT NULL ORDER BY r.started_at DESC, r.id DESC LIMIT 20"
  echo "## case_key：有 (case_key, run_id)"
  $M -e "ALTER TABLE test_result_b5 ADD INDEX idx_test_result_case_key (case_key, run_id)"
  run "TREND case_key join" "SELECT r.run_id, r.started_at, t.verdict FROM test_result_b5 t JOIN \`run\` r ON r.id = t.run_id WHERE r.project_id = 29 AND t.case_key = '$KEY' AND r.started_at IS NOT NULL ORDER BY r.started_at DESC, r.id DESC LIMIT 20"
  $M -e "EXPLAIN SELECT r.run_id, r.started_at, t.verdict FROM test_result_b5 t JOIN \`run\` r ON r.id = t.run_id WHERE r.project_id = 29 AND t.case_key = '$KEY' AND r.started_at IS NOT NULL ORDER BY r.started_at DESC, r.id DESC LIMIT 20"
fi
