#!/usr/bin/env bash
# B5 造数：audit_log 100 万行（project_id=-1 / action='B5-SEED'），
#          test_result_b5 100 万行（克隆表，无外键，用于 case_key 索引评估）。
# 幂等：先删同标记的行与克隆表，再分批插入。
set -u
cd ~/robot-autotest
set -a; . ~/.config/robot-autotest/env; set +a
export MYSQL_PWD="${DB_PASSWORD:-}"
M="mysql -h${DB_HOST:-127.0.0.1} -P${DB_PORT:-3306} -u${DB_USERNAME:-root} ${DB_NAME:-robot_dev}"

$M -e "DELETE FROM audit_log WHERE project_id = -1 AND action = 'B5-SEED'"
echo "seed audit_log: start $(date +%T)"
for i in $(seq 0 9); do
  lo=$((i * 100000 + 1)); hi=$((i * 100000 + 100000))
  $M -e "
    SET SESSION cte_max_recursion_depth = 100000;
    INSERT INTO audit_log (project_id, actor_id, operator, action, resource_type, resource_id, detail, summary, occurred_at)
    WITH RECURSIVE seq(n) AS (SELECT $lo UNION ALL SELECT n + 1 FROM seq WHERE n < $hi)
    SELECT -1, NULL, 'b5-seed', 'B5-SEED', 'b5', CAST(n AS CHAR), CONCAT('seed row ', n), 'b5',
           TIMESTAMP('2026-01-01 00:00:00.000') + INTERVAL n SECOND FROM seq"
  printf '  batch %s..%s %s rows=%s\n' "$lo" "$hi" "$(date +%T)" "$($M -N -e 'select count(*) from audit_log')"
done

$M -e "DROP TABLE IF EXISTS test_result_b5"
$M -e "CREATE TABLE test_result_b5 LIKE test_result"
echo "seed test_result_b5: start $(date +%T)"
for i in $(seq 0 9); do
  lo=$((i * 100000 + 1)); hi=$((i * 100000 + 100000))
  $M -e "
    SET SESSION cte_max_recursion_depth = 100000;
    INSERT INTO test_result_b5 (run_id, case_id, round_no, verdict, duration_ms, failure_msg, case_key)
    WITH RECURSIVE seq(n) AS (SELECT $lo UNION ALL SELECT n + 1 FROM seq WHERE n < $hi)
    SELECT 1, NULL, 1, IF(n % 7 = 0, 'FAIL', 'PASS'), 100 + (n % 5000), NULL,
           CONCAT('suites/b5/case_', LPAD(n % 5000, 4, '0'), '.robot.B5 用例 ', (n % 5000))
    FROM seq"
  printf '  batch %s..%s %s\n' "$lo" "$hi" "$(date +%T)"
done
$M -N -e "select 'audit_log', count(*) from audit_log where project_id=-1
          union all select 'test_result_b5', count(*) from test_result_b5"
