#!/usr/bin/env bash
# 回滚：按版本号倒序执行 db/down/*.down.sql（ADR-0003：Flyway 社区版无 undo）
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/db-common.sh"

echo "[db-undo] target=$DB_USERNAME@$DB_HOST:$DB_PORT/$DB_NAME"

# 倒序：V2 先于 V1 回滚
mapfile -t DOWN_FILES < <(find "$ROOT/db/down" -maxdepth 1 -name 'V*.down.sql' | sort -rV)
if [[ ${#DOWN_FILES[@]} -eq 0 ]]; then
    echo "[db-undo] 没有 down 脚本，跳过"
    exit 0
fi

for f in "${DOWN_FILES[@]}"; do
    echo "[db-undo] 执行 $(basename "$f")"
    db_exec < "$f"
done

echo "[db-undo] 完成；剩余表（应只有 flyway_schema_history）："
db_exec -e "SELECT table_name FROM information_schema.tables WHERE table_schema='$DB_NAME';"
