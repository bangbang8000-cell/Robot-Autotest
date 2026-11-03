#!/usr/bin/env bash
# 正向迁移：调用 backend 的 Flyway Maven 插件，脚本真源在 db/migration（FR-v0.1-12/13）
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/db-common.sh"

echo "[db-migrate] $DB_USERNAME@$DB_HOST:$DB_PORT/$DB_NAME"
cd "$ROOT/backend"
mvn -B -q flyway:migrate
echo "[db-migrate] 完成；当前业务表数："
db_exec -e "SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema='$DB_NAME' AND table_name<>'flyway_schema_history';"
