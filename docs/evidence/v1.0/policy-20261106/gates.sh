#!/usr/bin/env bash
# P0 口径回写批次 · 八步门禁（模板来自 docs/evidence/v1.0/longhist/gates.sh）+ 五项后补
# 后补：11-editor_test / 12-db_audit（v1.0）/ 13-css_vars（v2.1 ORCH-02）
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
# 编辑器包有独立的 vitest 工程（unplugin-vue + jsdom），frontend 的 vitest 扫不到它。
# EDT-03/EDT-05 的回归用例住在这里，不接进来就会出现「门禁全绿但编辑器回归没人跑」。
run 11 editor_test     "cd packages/editor && npx vitest run"
# 库结构体检（TC-v0.1-023/024）。2026-11-06 之前它从不在门禁里，于是 `db-audit` 红了整一个版本
# 没人发现（V8_1..V8_6 共 13 张表缺审计列 / 两张关联表复合主键）。
# 显式指向 robot_dev：本脚本其它步骤会把 DB_NAME 覆盖成 robot_it（集成测试库，随时被 clean）。
# 需要 ~/.config/robot-autotest/env 里的 DB_* —— 门禁本就要求先 source 该文件。
run 12 db_audit        "DB_NAME=robot_dev bash scripts/db-audit.sh"
# CSS 变量引用体检（v2.1 ORCH-02 起）。写错变量名不会报错：声明在计算期失效、属性回落初始值，
# SVG 的 fill 初始值是黑色 —— 节点变黑块、文字看不见，而 vue-tsc / vitest(jsdom) / vite build
# 三关全绿。判据只能落在「引用的名字必须有定义」这一条上，所以做成脚本进门禁。
run 13 css_vars        "python3 tools/check_css_vars.py"

printf '\nGATES_RESULT FAILS=%s\n' "$FAILS"
exit "$FAILS"
