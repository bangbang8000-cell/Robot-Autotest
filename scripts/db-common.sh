#!/usr/bin/env bash
# 数据库脚本公共部分：统一读取连接参数，缺口令直接失败（不落盘任何默认口令）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

: "${DB_HOST:=127.0.0.1}"
: "${DB_PORT:=3306}"
: "${DB_NAME:=robot_it}"
: "${DB_USERNAME:=robot}"

if [[ -z "${DB_PASSWORD:-}" ]]; then
    echo "[db] 缺少环境变量 DB_PASSWORD（仓库不存放任何口令，见 AGENTS.md §9）" >&2
    exit 1
fi

export DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD

# MariaDB 客户端：优先 mariadb，回落 mysql
if command -v mariadb >/dev/null 2>&1; then
    DB_CLIENT=mariadb
elif command -v mysql >/dev/null 2>&1; then
    DB_CLIENT=mysql
else
    echo "[db] 未找到 mariadb / mysql 客户端" >&2
    exit 1
fi

db_exec() {
    "$DB_CLIENT" --host="$DB_HOST" --port="$DB_PORT" \
        --user="$DB_USERNAME" --password="$DB_PASSWORD" \
        --default-character-set=utf8mb4 --batch --skip-column-names "$@"
}
