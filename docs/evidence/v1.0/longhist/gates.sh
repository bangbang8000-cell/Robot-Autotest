#!/usr/bin/env bash
# 长历史压测批次：八步门禁（本轮不改业务代码，门禁用于确认 HEAD 仍可交付）
set -uo pipefail
cd /home/evergreen/robot-autotest || exit 1
L=docs/evidence/v1.0/longhist/logs
mkdir -p "$L"
FAILS=0

run() {
  local no=$1 name=$2 cmd=$3
  local log="$L/$no-$name.log"
  local t0=$SECONDS
  if bash -lc "$cmd" > "$log" 2>&1; then
    printf '  PASS  %-24s %3ds  %s\n' "$no-$name" "$((SECONDS - t0))" "$(tail -n 1 "$log" | cut -c1-90)"
  else
    printf '  FAIL  %-24s %3ds  %s\n' "$no-$name" "$((SECONDS - t0))" "$(tail -n 1 "$log" | cut -c1-90)"
    FAILS=$((FAILS + 1))
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
echo "== 失败 $FAILS 项 =="
exit "$FAILS"
