#!/usr/bin/env bash
# 重置：DROP + CREATE 目标库，然后 migrate。CI 与集成测试前必跑（ADR-0002 串行）
# 安全阀：库名必须以 _it 结尾，否则直接拒绝（防止误清开发库/生产库）
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/db-common.sh"

if [[ "$DB_NAME" != *_it ]]; then
    echo "[db-reset] 拒绝执行：DB_NAME=$DB_NAME 不以 _it 结尾（只允许清理集成测试库）" >&2
    exit 1
fi

if [[ "${FORCE_DB_RESET:-}" != "1" ]]; then
    read -r -p "[db-reset] 将删除 $DB_NAME 库及其中全部数据，继续？(yes/NO) " answer
    [[ "$answer" == "yes" ]] || { echo "[db-reset] 已取消"; exit 0; }
fi

echo "[db-reset] 重建库 $DB_NAME"
db_exec -e "DROP DATABASE IF EXISTS \`$DB_NAME\`;
            CREATE DATABASE \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

bash "$ROOT/scripts/db-migrate.sh"
