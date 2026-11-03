#!/usr/bin/env bash
# 秘密扫描：仓库内不得出现明文口令（FR-v0.1-07 / TC-v0.1-031）
# 命中即退出 1；排除文档与依赖目录
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "[secret-scan] 扫描明文口令赋值…"
if git grep -nEi "password\\s*=\\s*['\"][^'\"]+" -- \
        ':!*.md' ':!*.log' ':!frontend/package-lock.json' ':!artifacts/**' 2>/dev/null; then
    echo "[secret-scan] FAIL：发现疑似明文口令（见上）" >&2
    exit 1
fi

echo "[secret-scan] PASS（无命中）"
