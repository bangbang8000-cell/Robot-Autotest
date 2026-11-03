#!/usr/bin/env bash
# WS-05：本地手工跑 CI 三关（runner 未就绪时的降级路径，见 DEV-PLAN §8）
# 顺序与 .github/workflows/pr.yml 一致：lint → test → build，前一关失败即停。
#
#   bash scripts/ci-local.sh                 # 完整三关（有 DB_PASSWORD 时先 db-reset）
#   SKIP_DB_RESET=1 bash scripts/ci-local.sh # 不动数据库
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mkdir -p artifacts
LOG=artifacts/ci-local.log
: > "$LOG"

step() {
    echo ""
    echo "======== [$1] $(date -Is) ========" | tee -a "$LOG"
}

run() {
    # 命令与输出同时进 artifacts/ci-local.log，便于贴证据
    bash -o pipefail -c "$*" 2>&1 | tee -a "$LOG"
}

step "lint"
run "make lint"

step "test"
if [[ "${SKIP_DB_RESET:-0}" == "1" ]]; then
    echo "[ci-local] 跳过 db-reset"
elif [[ -n "${DB_PASSWORD:-}" ]]; then
    run "make db-reset"
else
    echo "[ci-local] 未提供 DB_PASSWORD，跳过 db-reset；集成测试也会跳过"
fi
run "make test"

step "build"
run "make build"

echo ""
echo "[ci-local] PASS（三关全绿，完整日志 artifacts/ci-local.log）"
