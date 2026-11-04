#!/usr/bin/env bash
# 回滚：按版本号倒序执行 db/down/*.down.sql（ADR-0003：Flyway 社区版无 undo）
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/db-common.sh"

# 安全阀：库名必须以 _it 结尾，否则直接拒绝（防止误清开发库/生产库，与 db-reset.sh 对齐）
if [[ "$DB_NAME" != *_it ]]; then
    echo "[db-undo] 拒绝执行：DB_NAME=$DB_NAME 不以 _it 结尾（只允许回滚集成测试库）" >&2
    exit 1
fi

echo "[db-undo] target=$DB_USERNAME@$DB_HOST:$DB_PORT/$DB_NAME"

# 倒序：V2 先于 V1 回滚
mapfile -t DOWN_FILES < <(find "$ROOT/db/down" -maxdepth 1 -name 'V*.down.sql' | sort -rV)
if [[ ${#DOWN_FILES[@]} -eq 0 ]]; then
    echo "[db-undo] 没有 down 脚本，跳过"
    exit 0
fi

for f in "${DOWN_FILES[@]}"; do
    echo "[db-undo] 执行 $(basename "$f")"
    # down SQL 里全是裸表名，必须显式选库；否则 mysql 报 ERROR 1046 No database selected
    db_exec "$DB_NAME" < "$f"
done

echo "[db-undo] 完成；剩余表（应只有 flyway_schema_history）："
db_exec -e "SELECT table_name FROM information_schema.tables WHERE table_schema='$DB_NAME';"
