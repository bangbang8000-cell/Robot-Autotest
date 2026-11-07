#!/usr/bin/env bash
# B5 down/up 回放 + 造数清理。注意：Flyway 已记录 10.1 成功，这里直接回放 SQL，
# 不再走 flyway（否则 migrate 会因版本已应用而跳过，索引留在缺失状态）。
set -u
cd ~/robot-autotest
set -a; . ~/.config/robot-autotest/env; set +a
export MYSQL_PWD="${DB_PASSWORD:-}"
M="mysql -h${DB_HOST:-127.0.0.1} -P${DB_PORT:-3306} -u${DB_USERNAME:-root} ${DB_NAME:-robot_dev}"

echo "# down 回放"
$M < db/down/V10_1__audit_keyset.down.sql
echo "down 后 audit_log 索引: $($M -N -e "select ifnull(group_concat(index_name),'(none)') from (select distinct index_name from information_schema.statistics where table_schema=database() and table_name='audit_log' and index_name='idx_audit_time_id') x")"
echo "down 后 test_result 索引: $($M -N -e "select ifnull(group_concat(index_name),'(none)') from (select distinct index_name from information_schema.statistics where table_schema=database() and table_name='test_result' and index_name='idx_test_result_case_key') x")"
echo
echo "# up 回放"
T0=$(date +%s%N); $M < db/migration/V10_1__audit_keyset.sql; T1=$(date +%s%N)
echo "up 耗时 $(( (T1 - T0) / 1000000 )) ms"
$M -N -e "select table_name, index_name, group_concat(column_name order by seq_in_index)
          from information_schema.statistics
          where table_schema=database() and index_name in ('idx_audit_time_id','idx_test_result_case_key')
          group by table_name, index_name"
echo
echo "# 清理造数"
for i in $(seq 1 20); do
  $M -e "DELETE FROM audit_log WHERE project_id=-1 AND action='B5-SEED' LIMIT 100000"
  n=$($M -N -e "select count(*) from audit_log where project_id=-1 and action='B5-SEED'")
  printf '  batch %2s 剩余 %s\n' "$i" "$n"
  [ "$n" -eq 0 ] && break
done
$M -e "DROP TABLE IF EXISTS test_result_b5"
echo "清理后 audit_log 行数: $($M -N -e 'select count(*) from audit_log')"
LEFT=$($M -N -e "select count(*) from information_schema.tables where table_schema=database() and table_name='test_result_b5'")
echo "test_result_b5 是否还在: $LEFT"
