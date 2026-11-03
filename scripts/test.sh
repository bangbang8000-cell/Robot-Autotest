#!/usr/bin/env bash
# 后端单测 + ArchUnit 总是跑；DB_PASSWORD 存在时追加 @Tag("it") 集成测试。
# 集成测试串行（同库互斥，ADR-0002），不开 JUnit 并行。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p artifacts

if [[ -f backend/pom.xml ]]; then
    command -v mvn >/dev/null 2>&1 || { echo "[test] 缺少 mvn" >&2; exit 1; }

    echo "[test] backend: mvn -B test（单测 + ArchUnit）"
    (cd backend && mvn -B test) 2>&1 | tee artifacts/mvn-test.log

    if [[ -n "${DB_PASSWORD:-}" ]]; then
        echo "[test] backend: mvn -B -Pit test（集成测试，库 $DB_NAME）"
        (cd backend && mvn -B -Pit test) 2>&1 | tee artifacts/mvn-it.log
    else
        echo "[test] 未提供 DB_PASSWORD，跳过 @Tag(\"it\") 集成测试（dev 环境正常，CI 必跑）"
    fi
fi

if [[ -f frontend/package.json ]]; then
    command -v npm >/dev/null 2>&1 || { echo "[test] 缺少 npm" >&2; exit 1; }
    if [[ ! -d frontend/node_modules ]]; then
        (cd frontend && npm ci)
    fi
    echo "[test] frontend: vue-tsc --noEmit（v0.1 无单测框架，只做类型检查）"
    (cd frontend && npx vue-tsc --noEmit)
fi

echo "[test] PASS"
