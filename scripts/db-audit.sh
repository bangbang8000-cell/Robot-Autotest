#!/usr/bin/env bash
# schema 体检：TC-v0.1-023 / 024 / 028 的脚本化版本（不依赖 JUnit，可单独跑）
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/db-common.sh"

echo "[db-audit] $DB_USERNAME@$DB_HOST:$DB_PORT/$DB_NAME"
fail=0

check() {
    local title="$1" expected="$2" sql="$3"
    local actual
    actual="$(db_exec -e "$sql" | tr -d '[:space:]')"
    if [[ "$actual" == "$expected" ]]; then
        echo "  OK    $title（$actual）"
    else
        echo "  FAIL  $title（期望 $expected，实测 $actual）"
        fail=$((fail + 1))
    fi
}

check "业务表数 = 38" "38" \
    "SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema='$DB_NAME' AND table_name<>'flyway_schema_history';"

check "缺审计四列的表的数量 = 0" "0" \
    "SELECT COUNT(*) FROM (
       SELECT table_name FROM information_schema.columns
       WHERE table_schema='$DB_NAME' AND table_name<>'flyway_schema_history'
         AND column_name IN ('created_at','created_by','updated_at','updated_by')
       GROUP BY table_name HAVING COUNT(*) < 4
     ) t;"

check "主键不是 id 的表数量 = 0" "0" \
    "SELECT COUNT(*) FROM (
       SELECT c.table_name FROM information_schema.columns c
       LEFT JOIN information_schema.key_column_usage k
         ON k.table_schema=c.table_schema AND k.table_name=c.table_name
        AND k.constraint_name='PRIMARY' AND k.column_name='id'
       WHERE c.table_schema='$DB_NAME' AND c.table_name<>'flyway_schema_history'
         AND c.column_name='id' AND k.column_name IS NULL
     ) t;"

check "缺主键的表数量 = 0" "0" \
    "SELECT COUNT(*) FROM (
       SELECT t.table_name FROM information_schema.tables t
       LEFT JOIN information_schema.key_column_usage k
         ON k.table_schema=t.table_schema AND k.table_name=t.table_name
        AND k.constraint_name='PRIMARY'
       WHERE t.table_schema='$DB_NAME' AND t.table_name<>'flyway_schema_history'
         AND k.column_name IS NULL
       GROUP BY t.table_name
     ) x;"

check "时间列不是 datetime(3) 的表数量 = 0" "0" \
    "SELECT COUNT(*) FROM (
       SELECT table_name FROM information_schema.columns
       WHERE table_schema='$DB_NAME' AND column_name IN ('created_at','updated_at')
         AND data_type<>'datetime'
       GROUP BY table_name
     ) t;"

check "非 utf8mb4 的表数量 = 0" "0" \
    "SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema='$DB_NAME' AND table_name<>'flyway_schema_history'
       AND table_collation NOT LIKE 'utf8mb4%';"

echo "--------------------------------------------------------------"
if [[ $fail -gt 0 ]]; then
    echo "db-audit: FAIL（$fail 项不符）"
    exit 1
fi
echo "db-audit: PASS"
