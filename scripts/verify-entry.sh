#!/usr/bin/env bash
# ENVX-01 / FR-v2.0-53 的实测脚本：统一入口 8001 的存活/就绪、SPA、缓存、反代、静态数据。
# 用法：bash scripts/verify-entry.sh        # 默认 http://127.0.0.1:8001
#       ENTRY_URL=http://127.0.0.1:9001 bash scripts/verify-entry.sh
set -euo pipefail

BASE="${ENTRY_URL:-http://127.0.0.1:8001}"
CURL=(curl -sS --noproxy '*')
PASS=0
FAIL=0

check() {
  local name="$1"
  local actual="$2"
  local expected="$3"
  if [[ "$actual" == *"$expected"* ]]; then
    printf '  PASS  %-28s %s\n' "$name" "$actual"
    PASS=$((PASS + 1))
  else
    printf '  FAIL  %-28s got=%s want=%s\n' "$name" "$actual" "$expected"
    FAIL=$((FAIL + 1))
  fi
}

echo "[verify-entry] $BASE"

check "healthz 存活探针" "$("${CURL[@]}" "$BASE/healthz")" '"status":"UP"'
check "api/health 就绪探针" "$("${CURL[@]}" "$BASE/api/health")" '"check":"readiness"'
check "首页 200" "$("${CURL[@]}" -o /dev/null -w '%{http_code}' "$BASE/")" "200"
check "深链接 /settings 200" "$("${CURL[@]}" -o /dev/null -w '%{http_code}' "$BASE/settings")" "200"
check "深链接 /reports/R1 200" "$("${CURL[@]}" -o /dev/null -w '%{http_code}' "$BASE/reports/R1")" "200"
check "CHANGELOG 静态数据 200" "$("${CURL[@]}" -o /dev/null -w '%{http_code}' "$BASE/CHANGELOG.md")" "200"
check "缺失静态资源 404" "$("${CURL[@]}" -o /dev/null -w '%{http_code}' "$BASE/assets/missing.js")" "404"

INDEX_CACHE="$("${CURL[@]}" -sSI "$BASE/" | tr -d '\r' | grep -i '^cache-control:' || true)"
check "index.html no-cache" "$INDEX_CACHE" "no-cache"

ASSET="$(find frontend/dist/assets -maxdepth 1 -name 'index-*.js' -printf '%f\n' 2>/dev/null | head -1 || true)"
if [[ -n "$ASSET" ]]; then
  ASSET_CACHE="$("${CURL[@]}" -sSI "$BASE/assets/$ASSET" | tr -d '\r' | grep -i '^cache-control:' || true)"
  check "指纹资源 immutable" "$ASSET_CACHE" "immutable"
else
  echo "  SKIP  指纹资源（未找到 dist/assets/index-*.js）"
fi

# 未登录请求 /api：响应必须来自后端（统一信封），证明反代把请求送到了 8081 而不是被 SPA 吞掉
API_UNAUTH="$("${CURL[@]}" "$BASE/api/system/settings")"
check "api 反代到后端鉴权" "$API_UNAUTH" '"code"'
check "build-info 端点存在" "$("${CURL[@]}" "$BASE/api/system/build-info")" '"code"'
check "后端 /_ping 可达" "$("${CURL[@]}" "$BASE/api/_ping")" '"status":"UP"'

echo "[verify-entry] PASS=$PASS FAIL=$FAIL"
[[ "$FAIL" == "0" ]]
