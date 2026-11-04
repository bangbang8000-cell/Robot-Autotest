#!/usr/bin/env bash
# 秘密扫描：仓库内不得出现明文口令（FR-v0.1-07 / TC-v0.1-031）
# 命中即退出 1；排除文档与依赖目录
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "[secret-scan] 扫描明文口令赋值…"
# 只认「引号内首字符不是 $」的字面量；变量引用（"${DB_PASSWORD:-}" 与 --password="$DB_PASSWORD"）不算明文。
# 双向自检：放宽正则后必须仍能拦住真明文，且不得把变量引用误判成明文——避免「全跳过=PASS」。
REGEX="password[[:space:]]*=[[:space:]]*['\"][^'\"\$][^'\"]*['\"]"
if ! printf '%s
' 'password="hunter2-selftest"' | grep -Eq "$REGEX"; then
    echo "[secret-scan] 自检 FAIL：正则已无法识别真明文" >&2
    exit 1
fi
if printf '%s
' 'DB_PASSWORD="${DB_PASSWORD:-}"' | grep -Eq "$REGEX"; then
    echo "[secret-scan] 自检 FAIL：变量引用被误判为明文" >&2
    exit 1
fi

# 排除本脚本自身：上面两行自检样例含 password="..." 字面量，否则会扫到自己（假红）。
# 正则本身由自检双向锁住，本脚本不含任何真实凭据。
if git grep -nEi "$REGEX" -- \
        ':!*.md' ':!*.log' ':!frontend/package-lock.json' ':!artifacts/**' \
        ':!scripts/secret-scan.sh' 2>/dev/null; then
    echo "[secret-scan] FAIL：发现疑似明文口令（见上）" >&2
    exit 1
fi

echo "[secret-scan] PASS（无命中，双向自检已过）"
