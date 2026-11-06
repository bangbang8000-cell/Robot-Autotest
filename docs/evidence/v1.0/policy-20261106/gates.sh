#!/usr/bin/env bash
# P0 口径回写批次 · 八步门禁（模板来自 docs/evidence/v1.0/longhist/gates.sh）+ 两项本包专属
set -uo pipefail
cd ~/robot-autotest || exit 1
L=docs/evidence/v1.0/policy-20261106/logs
mkdir -p "$L"
FAILS=0
run() {
  local no="$1" name="$2" cmd="$3"
  local log="$L/$no-$name.log" t0=$SECONDS
  if bash -lc "$cmd" > "$log" 2>&1; then
    printf '  PASS  %s (%ss)\n' "$no-$name" "$((SECONDS-t0))"
  else
    printf '  FAIL  %s (%ss) -> %s\n' "$no-$name" "$((SECONDS-t0))" "$log"
    FAILS=$((FAILS+1))
  fi
}

run 01 subset_font     "python3 tools/subset_font.py"
run 02 frontend_test   "cd frontend && npx vitest run"
run 03 type_check      "cd frontend && npx vue-tsc --noEmit"
run 04 frontend_build  "cd frontend && npx vite build"
run 05 backend_test    "cd backend && mvn -o -B test"
run 06 lint            "bash scripts/lint.sh"
run 07 release_docs    "python3 tools/check_release_docs.py"
run 08 wbs_consistency "python3 tools/check_wbs_consistency.py"
run 09 md_tables       "python3 docs/evidence/v1.0/policy-20261106/check_md_tables.py"
run 10 diff_check      "git diff --check"

printf '\nGATES_RESULT FAILS=%s\n' "$FAILS"
exit "$FAILS"
