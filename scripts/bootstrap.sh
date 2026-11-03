#!/usr/bin/env bash
# WS-01 骨架期引导脚本：只校验目录与工具链、创建运行时目录，不安装任何依赖。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

REQUIRED_DIRS=(backend frontend packages/editor db/migration db/down docs scripts)
RUNTIME_DIRS=(artifacts logs)

fail=0
warn=0

echo "=============================================================="
echo "bootstrap: $ROOT"
echo "=============================================================="

echo ""
echo "[1/3] 目录结构"
for d in "${REQUIRED_DIRS[@]}"; do
    if [[ -d "$d" ]]; then
        echo "  OK    $d"
    else
        echo "  MISS  $d"
        fail=$((fail + 1))
    fi
done

echo ""
echo "[2/3] 运行时目录"
for d in "${RUNTIME_DIRS[@]}"; do
    mkdir -p "$d"
    echo "  READY $d/"
done

echo ""
echo "[3/3] 工具链（骨架期缺失只告警，WS-02/03 起改为致命）"
check_tool() {
    local name="$1"; shift
    if command -v "$name" >/dev/null 2>&1; then
        local ver
        ver="$("$@" 2>&1 | head -n 1 | tr -d '\r')"
        echo "  OK    $name  $ver"
    else
        echo "  WARN  $name  未安装"
        warn=$((warn + 1))
    fi
}
check_tool java  java -version
check_tool mvn   mvn -v
check_tool node  node --version
check_tool npm   npm --version
check_tool make  make --version

echo ""
echo "--------------------------------------------------------------"
if [[ $fail -gt 0 ]]; then
    echo "bootstrap: FAIL（缺 $fail 个必需目录），warn=$warn"
    exit 1
fi
echo "bootstrap: PASS（warn=$warn）"
