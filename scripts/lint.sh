#!/usr/bin/env bash
# 后端 lint 占位：v0.1 只做密文扫描 + 编译；checkstyle 随 DOC-02（v0.2）接入。
# 前端有真实 eslint。这里不假装有后端静态检查。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p artifacts

echo "[lint] 密文扫描"
bash scripts/secret-scan.sh

if [[ -f frontend/package.json ]]; then
    command -v npm >/dev/null 2>&1 || { echo "[lint] 缺少 npm" >&2; exit 1; }
    if [[ ! -d frontend/node_modules ]]; then
        echo "[lint] frontend/node_modules 不存在，先 npm ci"
        (cd frontend && npm ci)
    fi
    echo "[lint] frontend: npm run lint"
    (cd frontend && npm run lint)
fi

if [[ -f backend/pom.xml ]]; then
    command -v mvn >/dev/null 2>&1 || { echo "[lint] 缺少 mvn" >&2; exit 1; }
    # 编译即最小静态检查（类型 / 语法）；checkstyle 未接入，见 docs/plan/WBS-STATUS.md 偏差记录
    echo "[lint] backend: mvn -B -DskipTests compile"
    (cd backend && mvn -B -q -DskipTests compile) 2>&1 | tee "$ROOT/artifacts/mvn-compile.log"
fi

echo "[lint] PASS"
