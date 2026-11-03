#!/usr/bin/env bash
# WS-06：环境基线体检。硬项失败即非零退出；可选项目只告警（如 Windows 上没有 rf-stack）。
# 基线口径见 docs/env/环境基线.md。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail=0
ok()   { echo "  [ok]   $*"; }
warn() { echo "  [warn] $*"; }
bad()  { echo "  [FAIL] $*"; fail=1; }

version_ge() { # version_ge 1.2.3 1.2
    local a="${1%%.*}" b="${2%%.*}"
    [[ "$a" -ge "$b" ]]
}

echo "[env] 1. JDK（后端编译目标 21）"
if command -v java >/dev/null 2>&1; then
    jv=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
    if [[ "$jv" =~ ^[0-9]+$ ]] && [[ "$jv" -ge 21 ]]; then ok "java $jv"; else bad "java 主版本 $jv，需要 >= 21"; fi
else
    bad "未找到 java"
fi

echo "[env] 2. Maven（3.9.x）"
if command -v mvn >/dev/null 2>&1; then
    mv_line=$(mvn -v 2>/dev/null | head -1)
    ok "$mv_line"
    grep -q 'Apache Maven 3\.9\.' <<<"$mv_line" || warn "Maven 不是 3.9.x，构建脚本按 3.9 验证过"
else
    bad "未找到 mvn"
fi

echo "[env] 3. Node（22.x）与 npm"
if command -v node >/dev/null 2>&1; then
    nv=$(node -v | sed 's/v//')
    ok "node $nv"
    version_ge "$nv" 22 || warn "node 低于 22，前端构建未在该版本验证"
else
    bad "未找到 node"
fi
command -v npm >/dev/null 2>&1 && ok "npm $(npm -v)" || bad "未找到 npm"

echo "[env] 4. 时区（必须是 Asia/Shanghai）"
tz_now="$(date +%Z)"
if [[ -n "${TZ:-}" ]]; then
    ok "TZ=$TZ"
elif [[ "$tz_now" == "CST" || "$tz_now" == "UTC+8" ]]; then
    ok "当前时区 $tz_now"
else
    warn "当前时区 $tz_now，不是 CST/UTC+8；149 上应为 Asia/Shanghai"
fi

echo "[env] 5. 数据库变量（缺 DB_PASSWORD 即无法跑迁移与集成测试）"
for v in DB_HOST DB_PORT DB_NAME DB_USERNAME; do
    printf '  %-12s %s\n' "$v" "${!v:-（未设置，脚本有缺省值）}"
done
if [[ -n "${DB_PASSWORD:-}" ]]; then
    ok "DB_PASSWORD 已提供"
else
    warn "DB_PASSWORD 未设置：db-* 与集成测试会跳过"
fi
if command -v mariadb >/dev/null 2>&1 || command -v mysql >/dev/null 2>&1; then
    ok "已装 MariaDB/MySQL 客户端"
else
    warn "没有 mariadb / mysql 客户端（仅用 flyway-maven-plugin 时不影响）"
fi

echo "[env] 6. RF 运行时（仅 149 有：~/rf-stack/.venv）"
if [[ -x "$HOME/rf-stack/.venv/bin/robot" ]]; then
    ok "robot $("$HOME/rf-stack/.venv/bin/robot" --version 2>/dev/null | head -1)"
    [[ -x "$HOME/rf-stack/.venv/bin/pabot" ]] && ok "pabot 存在" || warn "缺 pabot"
else
    warn "无 ~/rf-stack/.venv（本机开发不需要，149 上必须有）"
fi

echo "[env] 7. 产物根"
mkdir -p artifacts/tmp
[[ -w artifacts/tmp ]] && ok "artifacts/ 可写" || bad "artifacts/ 不可写"

echo ""
if [[ $fail -ne 0 ]]; then
    echo "[env] FAIL：硬项不满足，先修再构建"
    exit 1
fi
echo "[env] PASS（告警项按需处理）"
